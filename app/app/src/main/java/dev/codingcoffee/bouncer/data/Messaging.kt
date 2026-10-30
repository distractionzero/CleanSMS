package dev.codingcoffee.bouncer.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.room.withTransaction
import dev.codingcoffee.bouncer.data.db.BouncerDatabase
import dev.codingcoffee.bouncer.data.db.Box
import dev.codingcoffee.bouncer.data.db.MessageEntity
import dev.codingcoffee.bouncer.data.mms.MmsService
import dev.codingcoffee.bouncer.data.sync.TelephonySync
import dev.codingcoffee.bouncer.notify.Notifier
import dev.codingcoffee.bouncer.receivers.SmsStatusReceiver
import dev.codingcoffee.bouncer.util.simLabel
import dev.codingcoffee.bouncer.util.smsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MessageDetails(
    val isMms: Boolean,
    val incoming: Boolean,
    val address: String,
    val sentAt: Long?,
    val receivedAt: Long?,
    val sim: String?,
)

class Messaging(
    private val context: Context,
    private val db: BouncerDatabase,
    private val sync: TelephonySync,
    private val mms: MmsService,
    private val notifier: Notifier,
) {
    private val resolver = context.contentResolver

    fun threadIdFor(addresses: List<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    suspend fun send(threadId: Long, addresses: List<String>, text: String, image: Uri?) {
        if (addresses.isEmpty()) return
        if (image == null && addresses.size == 1) sendSms(threadId, addresses.first(), text)
        else mms.send(threadId, addresses, text, image)
    }

    private suspend fun sendSms(threadId: Long, address: String, text: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, text)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        val uri = resolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return@withContext
        sync.incremental()
        dispatchSms(uri, address, text)
    }

    private fun dispatchSms(uri: Uri, address: String, text: String) {
        val manager = context.smsManager()
        val sent = PendingIntent.getBroadcast(
            context, ContentUris.parseId(uri).toInt(),
            Intent(SmsStatusReceiver.ACTION_SENT, uri, context, SmsStatusReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val parts = manager.divideMessage(text)
        if (parts.size <= 1) manager.sendTextMessage(address, null, text, sent, null)
        else manager.sendMultipartTextMessage(address, null, parts, ArrayList(List(parts.size) { sent }), null)
    }

    /** A failed part always wins, so multipart results can arrive in any order. */
    suspend fun onSmsSent(uri: Uri, success: Boolean) = withContext(Dispatchers.IO) {
        if (success) {
            resolver.update(
                uri, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) },
                "${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_FAILED}", null,
            )
        } else {
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }, null, null)
        }
        val box = resolver.query(uri, arrayOf(Telephony.Sms.TYPE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else null } ?: return@withContext
        db.messages().updateBox(ContentUris.parseId(uri), box)
    }

    suspend fun retry(message: MessageEntity) = withContext(Dispatchers.IO) {
        if (message.isMms) return@withContext mms.resend(message.providerId)
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.providerId)
        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX) }, null, null)
        db.messages().updateBox(message.id, Box.OUTBOX)
        dispatchSms(uri, message.address, message.body)
    }

    suspend fun receiveSms(address: String, body: String, sentAt: Long): Long? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return@withContext null
        val threadId = resolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@withContext null
        onIncoming(threadId)
        threadId
    }

    suspend fun onIncoming(threadId: Long) {
        sync.incremental()
        db.conversations().setArchived(listOf(threadId), false)
        notifier.notifyThread(threadId)
    }

    suspend fun markRead(threadId: Long) = withContext(Dispatchers.IO) {
        notifier.cancel(threadId)
        val where = "${Telephony.Sms.THREAD_ID} = $threadId AND ${Telephony.Sms.READ} = 0"
        resolver.update(Telephony.Sms.CONTENT_URI, readValues(), where, null)
        resolver.update(Telephony.Mms.CONTENT_URI, readValues(), where, null)
        db.withTransaction {
            db.messages().markThreadRead(threadId)
            db.conversations().clearUnread(threadId)
        }
    }

    suspend fun markRead(threadId: Long, messageId: Long) = withContext(Dispatchers.IO) {
        notifier.cancel(threadId, messageId)
        val message = db.messages().byId(messageId) ?: return@withContext
        resolver.update(providerUri(message), readValues(), null, null)
        db.messages().markRead(messageId)
        sync.refreshThreads(listOf(threadId))
    }

    private fun readValues() = ContentValues().apply {
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.SEEN, 1)
    }

    suspend fun setArchived(threadIds: List<Long>, archived: Boolean) =
        db.conversations().setArchived(threadIds, archived)

    suspend fun delete(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        threadIds.forEach { threadId ->
            notifier.cancel(threadId)
            resolver.delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null)
            db.withTransaction {
                db.messages().deleteThreadParts(threadId)
                db.messages().deleteThread(threadId)
            }
        }
        db.conversations().delete(threadIds)
    }

    suspend fun deleteMessages(threadId: Long, messages: List<MessageEntity>) = withContext(Dispatchers.IO) {
        messages.forEach { resolver.delete(providerUri(it), null, null) }
        db.withTransaction {
            messages.map { it.id }.chunked(500).forEach { ids ->
                db.messages().deletePartsFor(ids)
                db.messages().deleteByIds(ids)
            }
        }
        sync.refreshThreads(listOf(threadId))
    }

    suspend fun details(message: MessageEntity): MessageDetails = withContext(Dispatchers.IO) {
        var networkSent: Long? = null
        var sim: String? = null
        resolver.query(providerUri(message), arrayOf(Telephony.Sms.DATE_SENT, Telephony.Sms.SUBSCRIPTION_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                networkSent = c.getLong(0).takeIf { it > 0 }?.let { if (message.isMms) it * 1000 else it }
                sim = context.simLabel(c.getInt(1))
            }
        }
        MessageDetails(
            isMms = message.isMms,
            incoming = message.isIncoming,
            address = message.address,
            sentAt = if (message.isIncoming) networkSent else message.date,
            receivedAt = if (message.isIncoming) message.date else null,
            sim = sim,
        )
    }

    private fun providerUri(message: MessageEntity): Uri =
        ContentUris.withAppendedId(if (message.isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI, message.providerId)

    suspend fun deleteAll() = delete(db.conversations().all().map { it.threadId })
}
