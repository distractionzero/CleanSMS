package dev.codingcoffee.bouncer.data.mms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.FileProvider
import dev.codingcoffee.bouncer.data.db.BouncerDatabase
import dev.codingcoffee.bouncer.data.db.Box
import dev.codingcoffee.bouncer.data.mms.pdu.MmsPart
import dev.codingcoffee.bouncer.data.mms.pdu.Pdu
import dev.codingcoffee.bouncer.data.mms.pdu.PduReader
import dev.codingcoffee.bouncer.data.mms.pdu.PduWriter
import dev.codingcoffee.bouncer.data.sync.TelephonySync
import dev.codingcoffee.bouncer.receivers.MmsResultReceiver
import dev.codingcoffee.bouncer.util.ownNumbers
import dev.codingcoffee.bouncer.util.sameNumber
import dev.codingcoffee.bouncer.util.smsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MmsService(
    private val context: Context,
    private val db: BouncerDatabase,
    private val sync: TelephonySync,
) {
    private val resolver = context.contentResolver
    private val dir get() = File(context.cacheDir, "mms").apply { mkdirs() }

    suspend fun send(threadId: Long, recipients: List<String>, text: String, image: Uri?) = withContext(Dispatchers.IO) {
        val parts = buildList {
            image?.let { prepareImage(it) }?.let { add(MmsPart("image/jpeg", "image.jpg", it)) }
            if (text.isNotBlank()) add(MmsPart("text/plain", "text.txt", text.toByteArray(), Pdu.CHARSET_UTF8))
        }
        if (parts.isEmpty()) return@withContext
        val id = persistOutgoing(threadId, recipients, parts)
        sync.refreshMms(id)
        dispatch(id, recipients, parts)
    }

    suspend fun resend(providerId: Long) = withContext(Dispatchers.IO) {
        val recipients = addresses(providerId, TelephonySync.PDU_TO)
        val parts = storedParts(providerId)
        updateBox(providerId, Box.OUTBOX)
        dispatch(providerId, recipients, parts)
    }

    private fun dispatch(providerId: Long, recipients: List<String>, parts: List<MmsPart>) {
        val pdu = PduWriter.sendReq(recipients, parts, "T${System.currentTimeMillis().toString(16)}")
        val file = File(dir, "send_$providerId.pdu").apply { writeBytes(pdu) }
        val intent = Intent(ACTION_SENT, messageUri(providerId), context, MmsResultReceiver::class.java)
            .putExtra(EXTRA_FILE, file.path)
        val pending = PendingIntent.getBroadcast(
            context, providerId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.smsManager().sendMultimediaMessage(context, shareable(file), null, null, pending)
    }

    suspend fun onSent(messageUri: Uri, filePath: String?, success: Boolean) = withContext(Dispatchers.IO) {
        filePath?.let { File(it).delete() }
        updateBox(ContentUris.parseId(messageUri), if (success) Box.SENT else Box.FAILED)
    }

    fun download(notification: ByteArray) {
        val pdu = PduReader(notification).parse() ?: return
        if (pdu.messageType != Pdu.NOTIFICATION_IND) return
        val location = pdu.contentLocation ?: return
        val file = File(dir, "rx_${System.nanoTime()}.pdu")
        val intent = Intent(ACTION_DOWNLOADED, null, context, MmsResultReceiver::class.java)
            .putExtra(EXTRA_FILE, file.path)
        val pending = PendingIntent.getBroadcast(
            context, file.name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.smsManager().downloadMultimediaMessage(context, location, shareable(file), null, pending)
    }

    /** Stores a downloaded m-retrieve-conf and returns its thread id. */
    suspend fun onDownloaded(filePath: String?, success: Boolean): Long? = withContext(Dispatchers.IO) {
        val file = filePath?.let(::File) ?: return@withContext null
        val bytes = if (success && file.exists()) file.readBytes() else null
        file.delete()
        val pdu = bytes?.let { PduReader(it).parse() } ?: return@withContext null
        if (pdu.messageType != Pdu.RETRIEVE_CONF) return@withContext null
        val (id, threadId) = persistIncoming(pdu)
        sync.refreshMms(id)
        threadId
    }

    private fun persistOutgoing(threadId: Long, recipients: List<String>, parts: List<MmsPart>): Long {
        val values = baseValues(threadId, System.currentTimeMillis() / 1000).apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.MESSAGE_TYPE, TelephonySync.M_SEND_REQ)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
        }
        val id = ContentUris.parseId(resolver.insert(Telephony.Mms.CONTENT_URI, values)!!)
        insertAddress(id, "insert-address-token", TelephonySync.PDU_FROM)
        recipients.forEach { insertAddress(id, it, TelephonySync.PDU_TO) }
        parts.forEach { insertPart(id, it) }
        return id
    }

    private fun persistIncoming(pdu: Pdu): Pair<Long, Long> {
        val own = context.ownNumbers()
        val from = pdu.from.orEmpty()
        val others = (pdu.to + pdu.cc).filter { address -> own.none { context.sameNumber(it, address) } }
            .let { if (own.isEmpty() && pdu.to.size == 1 && pdu.cc.isEmpty()) emptyList() else it }
        val participants = (listOf(from) + others).filter { it.isNotBlank() }
            .distinctBy { it.filter(Char::isDigit).takeLast(10).ifEmpty { it } }
        val threadId = Telephony.Threads.getOrCreateThreadId(context, participants.toSet())
        val values = baseValues(threadId, pdu.dateSeconds ?: (System.currentTimeMillis() / 1000)).apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.MESSAGE_TYPE, TelephonySync.M_RETRIEVE_CONF)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            pdu.subject?.let { put(Telephony.Mms.SUBJECT, it) }
            pdu.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            pdu.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
        }
        val id = ContentUris.parseId(resolver.insert(Telephony.Mms.CONTENT_URI, values)!!)
        insertAddress(id, from, TelephonySync.PDU_FROM)
        pdu.to.forEach { insertAddress(id, it, TelephonySync.PDU_TO) }
        pdu.cc.forEach { insertAddress(id, it, PDU_CC) }
        pdu.parts.filter { it.contentType != "application/smil" }.forEach { insertPart(id, it) }
        return id to threadId
    }

    private fun baseValues(threadId: Long, dateSeconds: Long) = ContentValues().apply {
        put(Telephony.Mms.THREAD_ID, threadId)
        put(Telephony.Mms.DATE, dateSeconds)
        put(Telephony.Mms.MMS_VERSION, 0x12)
        put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
        put(Telephony.Mms.MESSAGE_CLASS, "personal")
    }

    private fun insertAddress(id: Long, address: String, type: Int) {
        resolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, Pdu.CHARSET_UTF8)
        })
    }

    private fun insertPart(id: Long, part: MmsPart) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            put(Telephony.Mms.Part.CONTENT_ID, "<${part.name}>")
            put(Telephony.Mms.Part.CONTENT_LOCATION, part.name)
            put(Telephony.Mms.Part.NAME, part.name)
            if (part.isText) {
                put(Telephony.Mms.Part.CHARSET, Pdu.CHARSET_UTF8)
                put(Telephony.Mms.Part.TEXT, part.text())
            }
        }
        val uri = resolver.insert(Uri.parse("content://mms/$id/part"), values) ?: return
        if (!part.isText) resolver.openOutputStream(uri)?.use { it.write(part.data) }
    }

    private fun addresses(id: Long, type: Int): List<String> =
        resolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = $type", null, null)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.orEmpty()

    private fun storedParts(id: Long): List<MmsPart> =
        resolver.query(Uri.parse("content://mms/$id/part"), arrayOf("_id", "ct", "cl", "text"), null, null, "_id ASC")
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val ct = c.getString(1).orEmpty()
                        if (ct == "application/smil") continue
                        val name = c.getString(2) ?: "part${c.getLong(0)}"
                        val data = if (ct == "text/plain") c.getString(3).orEmpty().toByteArray()
                        else resolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() }
                            ?: continue
                        add(MmsPart(ct, name, data, if (ct == "text/plain") Pdu.CHARSET_UTF8 else 0))
                    }
                }
            }.orEmpty()

    private suspend fun updateBox(providerId: Long, box: Int) {
        resolver.update(messageUri(providerId), ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null)
        db.messages().updateBox(-providerId, box)
    }

    private fun prepareImage(uri: Uri): ByteArray? = runCatching {
        var maxPx = 1600
        repeat(4) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxPx) {
                    val scale = maxPx.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
            for (quality in intArrayOf(85, 70, 55, 40)) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (bytes.size() <= MAX_IMAGE_BYTES) return@runCatching bytes.toByteArray()
            }
            maxPx = maxPx * 3 / 4
        }
        null
    }.getOrNull()

    private fun shareable(file: File): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        MMS_PACKAGES.forEach { runCatching { context.grantUriPermission(it, uri, flags) } }
        return uri
    }

    private fun messageUri(providerId: Long) = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId)

    companion object {
        const val ACTION_SENT = "dev.codingcoffee.bouncer.MMS_SENT"
        const val ACTION_DOWNLOADED = "dev.codingcoffee.bouncer.MMS_DOWNLOADED"
        const val EXTRA_FILE = "file"
        private const val PDU_CC = 130
        private const val MAX_IMAGE_BYTES = 300 * 1024
        private val MMS_PACKAGES = listOf("com.android.phone", "com.android.mms.service")
    }
}
