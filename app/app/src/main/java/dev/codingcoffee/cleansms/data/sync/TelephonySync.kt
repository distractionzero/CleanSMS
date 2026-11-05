package dev.codingcoffee.cleansms.data.sync

import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.room.withTransaction
import dev.codingcoffee.cleansms.data.db.CleanSmsDatabase
import dev.codingcoffee.cleansms.data.db.Box
import dev.codingcoffee.cleansms.data.db.ConversationEntity
import dev.codingcoffee.cleansms.data.db.MessageEntity
import dev.codingcoffee.cleansms.data.db.MmsPartEntity
import dev.codingcoffee.cleansms.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncState {
    data object Idle : SyncState
    data class Importing(val done: Int, val total: Int) : SyncState
}

/** Mirrors the system SMS/MMS provider into the local Room database. */
class TelephonySync(
    private val context: Context,
    private val db: CleanSmsDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val mutex = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var observing = false

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun isDefaultSmsApp(): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!isDefaultSmsApp()) return
        scope.launch(Dispatchers.IO) { if (!settings.initialSyncDone) fullImport() else incremental() }
        if (observing) return
        observing = true
        resolver.registerContentObserver(
            Uri.parse("content://mms-sms/"),
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    changes.tryEmit(Unit)
                }
            },
        )
        scope.launch(Dispatchers.IO) { changes.debounce(300).collect { incremental() } }
    }

    private suspend fun fullImport() = mutex.withLock {
        val total = count(Telephony.Sms.CONTENT_URI) + count(Telephony.Mms.CONTENT_URI)
        _state.value = SyncState.Importing(0, total)
        var done = 0
        val progress: (Int) -> Unit = { n ->
            done += n
            _state.value = SyncState.Importing(done, total)
        }
        val smsResult = importSms(0, progress)
        val mmsResult = importMms(0, onProgress = progress)
        rebuildConversations(null)
        settings.lastSmsId = smsResult.first
        settings.lastMmsId = mmsResult.first
        settings.initialSyncDone = true
        _state.value = SyncState.Idle
    }

    suspend fun incremental() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (smsMax, smsThreads) = importSms(settings.lastSmsId) {}
            val (mmsMax, mmsThreads) = importMms(settings.lastMmsId) {}
            settings.lastSmsId = smsMax
            settings.lastMmsId = mmsMax
            val touched = smsThreads + mmsThreads
            if (touched.isNotEmpty()) rebuildConversations(touched)
        }
    }

    /** Re-reads one MMS (and its parts) after its parts were written. */
    suspend fun refreshMms(providerId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (_, threads) = importMms(providerId - 1, until = providerId) {}
            if (threads.isNotEmpty()) rebuildConversations(threads)
        }
    }

    suspend fun refreshThreads(threadIds: Collection<Long>) = withContext(Dispatchers.IO) {
        mutex.withLock { rebuildConversations(threadIds.toSet()) }
    }

    private fun count(uri: Uri): Int =
        resolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count } ?: 0

    private suspend fun importSms(afterId: Long, onProgress: (Int) -> Unit): Pair<Long, Set<Long>> {
        var maxId = afterId
        val threads = HashSet<Long>()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
        )
        resolver.query(
            Telephony.Sms.CONTENT_URI, projection,
            "${Telephony.Sms._ID} > ? AND ${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_DRAFT}",
            arrayOf(afterId.toString()), "${Telephony.Sms._ID} ASC",
        )?.use { c ->
            val batch = ArrayList<MessageEntity>(BATCH)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val threadId = c.getLong(1)
                batch += MessageEntity(
                    id = id,
                    threadId = threadId,
                    address = c.getString(2).orEmpty(),
                    body = c.getString(3).orEmpty(),
                    date = c.getLong(4),
                    box = c.getInt(5),
                    read = c.getInt(6) == 1,
                    isMms = false,
                    hasAttachment = false,
                )
                threads += threadId
                if (id > maxId) maxId = id
                if (batch.size == BATCH) {
                    db.messages().insertMessages(batch)
                    onProgress(batch.size)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                db.messages().insertMessages(batch)
                onProgress(batch.size)
            }
        }
        return maxId to threads
    }

    private suspend fun importMms(
        afterId: Long,
        until: Long = Long.MAX_VALUE,
        onProgress: (Int) -> Unit,
    ): Pair<Long, Set<Long>> {
        var maxId = afterId
        val threads = HashSet<Long>()
        val projection = arrayOf(
            Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.READ,
        )
        resolver.query(
            Telephony.Mms.CONTENT_URI, projection,
            "${Telephony.Mms._ID} > ? AND ${Telephony.Mms._ID} <= ? AND ${Telephony.Mms.MESSAGE_TYPE} IN ($M_SEND_REQ, $M_RETRIEVE_CONF)" +
                " AND ${Telephony.Mms.MESSAGE_BOX} != ${Telephony.Mms.MESSAGE_BOX_DRAFTS}",
            arrayOf(afterId.toString(), until.toString()), "${Telephony.Mms._ID} ASC",
        )?.use { c ->
            val rows = ArrayList<MmsRow>(BATCH)
            while (c.moveToNext()) {
                rows += MmsRow(c.getLong(0), c.getLong(1), c.getLong(2) * 1000, c.getInt(3), c.getInt(4) == 1)
                if (rows.size == BATCH) {
                    persistMms(rows)
                    onProgress(rows.size)
                    rows.forEach { threads += it.threadId; if (it.id > maxId) maxId = it.id }
                    rows.clear()
                }
            }
            if (rows.isNotEmpty()) {
                persistMms(rows)
                onProgress(rows.size)
                rows.forEach { threads += it.threadId; if (it.id > maxId) maxId = it.id }
            }
        }
        return maxId to threads
    }

    private suspend fun persistMms(rows: List<MmsRow>) {
        val partsByMessage = loadParts(rows.map { it.id })
        val messages = rows.map { row ->
            val parts = partsByMessage[row.id].orEmpty()
            MessageEntity(
                id = -row.id,
                threadId = row.threadId,
                address = if (row.box == Box.INBOX) mmsSender(row.id) else "",
                body = parts.mapNotNull { if (it.contentType == "text/plain") it.text else null }.joinToString("\n"),
                date = row.date,
                box = row.box,
                read = row.read,
                isMms = true,
                hasAttachment = parts.any { it.isImage },
            )
        }
        db.withTransaction {
            db.messages().insertMessages(messages)
            rows.forEach { db.messages().deleteParts(-it.id) }
            db.messages().insertParts(partsByMessage.values.flatten())
        }
    }

    private fun loadParts(mmsIds: List<Long>): Map<Long, List<MmsPartEntity>> {
        val result = HashMap<Long, MutableList<MmsPartEntity>>()
        mmsIds.chunked(CHUNK).forEach { chunk ->
            resolver.query(
                Uri.parse("content://mms/part"), arrayOf("_id", "mid", "ct", "text"),
                "mid IN (${chunk.joinToString(",")})", null, "_id ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val ct = c.getString(2).orEmpty()
                    if (ct == "application/smil") continue
                    val mid = c.getLong(1)
                    result.getOrPut(mid) { mutableListOf() } +=
                        MmsPartEntity(id = c.getLong(0), messageId = -mid, contentType = ct, text = c.getString(3))
                }
            }
        }
        return result
    }

    private fun mmsSender(mmsId: Long): String =
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf("address"), "type = $PDU_FROM", null, null,
        )?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" } ?: ""

    private suspend fun rebuildConversations(threadIds: Set<Long>?) {
        val dao = db.conversations()
        val stats = if (threadIds == null) db.messages().statsForAll()
        else threadIds.chunked(CHUNK).flatMap { db.messages().statsFor(it) }
        val existing = (if (threadIds == null) dao.all() else threadIds.chunked(CHUNK).flatMap { dao.byIds(it) })
            .associateBy { it.threadId }
        val needAddresses = stats.map { it.threadId }.filter { existing[it]?.addresses.isNullOrEmpty() || threadIds == null }
        val addresses = threadAddresses(needAddresses)
        val updated = stats.map { s ->
            val old = existing[s.threadId]
            ConversationEntity(
                threadId = s.threadId,
                addresses = addresses[s.threadId]?.joinToString(ConversationEntity.ADDRESS_SEPARATOR)
                    ?: old?.addresses.orEmpty(),
                snippet = s.body.ifBlank { if (s.hasAttachment) "Photo" else "Message" }.replace('\n', ' ').take(160),
                date = s.date,
                unreadCount = s.unread,
                lastFromMe = s.box != Box.INBOX,
                archived = old?.archived ?: false,
            )
        }
        val emptied = (threadIds ?: existing.keys) - stats.map { it.threadId }.toSet()
        db.withTransaction {
            updated.chunked(CHUNK).forEach { dao.upsert(it) }
            emptied.chunked(CHUNK).forEach { dao.delete(it) }
        }
    }

    fun threadAddresses(threadIds: Collection<Long>): Map<Long, List<String>> {
        if (threadIds.isEmpty()) return emptyMap()
        val recipientIds = HashMap<Long, List<Long>>()
        threadIds.chunked(CHUNK).forEach { chunk ->
            resolver.query(
                Uri.parse("content://mms-sms/conversations?simple=true"), arrayOf("_id", "recipient_ids"),
                "_id IN (${chunk.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    recipientIds[c.getLong(0)] = c.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                }
            }
        }
        val canonical = HashMap<Long, String>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)
            ?.use { c -> while (c.moveToNext()) canonical[c.getLong(0)] = c.getString(1).orEmpty() }
        return recipientIds.mapValues { (_, ids) -> ids.mapNotNull { canonical[it] } }
    }

    private class MmsRow(val id: Long, val threadId: Long, val date: Long, val box: Int, val read: Boolean)

    companion object {
        private const val BATCH = 1000
        private const val CHUNK = 500
        const val M_SEND_REQ = 128
        const val M_RETRIEVE_CONF = 132
        const val PDU_FROM = 137
        const val PDU_TO = 151
    }
}
