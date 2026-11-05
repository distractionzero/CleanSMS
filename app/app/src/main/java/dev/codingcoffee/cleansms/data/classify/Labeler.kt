package dev.codingcoffee.cleansms.data.classify

import android.content.Context
import androidx.room.withTransaction
import dev.codingcoffee.cleansms.BuildConfig
import dev.codingcoffee.cleansms.data.Messaging
import dev.codingcoffee.cleansms.data.contacts.ContactRepository
import dev.codingcoffee.cleansms.data.db.CleanSmsDatabase
import dev.codingcoffee.cleansms.data.db.Label
import dev.codingcoffee.cleansms.data.db.LabelChangeEntity
import dev.codingcoffee.cleansms.data.db.MessageEntity
import dev.codingcoffee.cleansms.data.prefs.SettingsStore
import dev.codingcoffee.cleansms.util.isPhoneNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Instant

sealed interface LabelState {
    data object Idle : LabelState
    data class Sorting(val done: Int, val total: Int) : LabelState
}

/** Labels every received message that has no label yet, including ones that arrive later. */
class Labeler(
    private val context: Context,
    private val db: CleanSmsDatabase,
    private val settings: SettingsStore,
    private val contacts: ContactRepository,
    private val messaging: Messaging,
    private val scope: CoroutineScope,
) {
    private val classifier by lazy { context.assets.open(BuildConfig.SMS_MODEL_ASSET).use(SmsClassifier::load) }
    private val labels by lazy { classifier.labels.map(Label::valueOf) }

    private val _state = MutableStateFlow<LabelState>(LabelState.Idle)
    val state: StateFlow<LabelState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            if (settings.labelModel != BuildConfig.SMS_MODEL_ASSET) {
                db.messages().clearLabels()
                settings.labelModel = BuildConfig.SMS_MODEL_ASSET
            }
            db.messages().observeUnlabelledCount().conflate().collect { if (it > 0) labelPending(it) }
        }
    }

    private suspend fun labelPending(pending: Int) {
        val dao = db.messages()
        val showProgress = pending >= SHOW_PROGRESS_FROM
        var done = 0
        var total = pending
        while (true) {
            val batch = dao.unlabelled(BATCH)
            if (batch.isEmpty()) break
            if (showProgress) _state.value = LabelState.Sorting(done, total)
            val overrides = db.labelChanges().overridesFor(batch.map { it.id }).associate { it.messageId to it.toLabel }
            val predicted = batch.map { it.id to (overrides[it.id] ?: predict(it.address, it.body)) }
            db.withTransaction { predicted.forEach { (id, label) -> dao.setLabel(id, label) } }
            done += batch.size
            total = done + dao.unlabelledCount()
        }
        _state.value = LabelState.Idle
    }

    suspend fun labelOf(message: MessageEntity): Label {
        message.label?.let { return it }
        val label = db.labelChanges().overridesFor(listOf(message.id)).lastOrNull()?.toLabel
            ?: predict(message.address, message.body)
        db.messages().setLabel(message.id, label)
        return label
    }

    /** Drops every label so all received messages are sorted again; manual changes are reapplied. */
    fun relabelAll() = scope.launch { db.messages().clearLabels() }

    private fun predict(address: String, body: String): Label {
        val fromNumber = isPhoneNumber(address)
        if (body.isBlank()) return if (fromNumber) Label.Personal else Label.Promotional
        val scores = classifier.scores(body)
        val candidates = scores.indices.filter { fromNumber || labels[it] != Label.Personal }
        return labels[candidates.maxBy { scores[it] }]
    }

    suspend fun setManually(messages: List<MessageEntity>, label: Label) {
        val now = System.currentTimeMillis()
        val changes = messages.filter { it.isIncoming && it.label != label }.map { message ->
            val details = messaging.details(message)
            val scores = classifier.scores(message.body)
            LabelChangeEntity(
                messageId = message.id,
                threadId = message.threadId,
                address = message.address,
                body = message.body,
                receivedAt = message.date,
                sentAt = details.sentAt,
                isMms = message.isMms,
                hasAttachment = message.hasAttachment,
                isGroup = (db.conversations().byId(message.threadId)?.addressList?.size ?: 1) > 1,
                senderInContacts = contacts.get(message.address).name != null,
                sim = details.sim,
                fromLabel = message.label,
                toLabel = label,
                modelLabel = labels[scores.indices.maxBy { scores[it] }],
                modelScores = JSONObject(classifier.labels.zip(scores.toList()).toMap()).toString(),
                modelVersion = BuildConfig.SMS_MODEL_ASSET,
                changedAt = now,
            )
        }
        if (changes.isEmpty()) return
        db.withTransaction {
            db.labelChanges().insert(changes)
            changes.forEach { db.messages().setLabel(it.messageId, label) }
        }
    }

    suspend fun exportChanges(out: OutputStream) {
        val rows = JSONArray()
        db.labelChanges().all().forEach {
            rows.put(
                JSONObject()
                    .put("messageId", it.messageId)
                    .put("threadId", it.threadId)
                    .put("sender", it.address)
                    .put("text", it.body)
                    .put("receivedAt", iso(it.receivedAt))
                    .put("sentAt", it.sentAt?.let(::iso) ?: JSONObject.NULL)
                    .put("isMms", it.isMms)
                    .put("hasAttachment", it.hasAttachment)
                    .put("isGroup", it.isGroup)
                    .put("senderInContacts", it.senderInContacts)
                    .put("sim", it.sim ?: JSONObject.NULL)
                    .put("from", it.fromLabel?.name ?: JSONObject.NULL)
                    .put("to", it.toLabel.name)
                    .put("modelLabel", it.modelLabel.name)
                    .put("modelScores", JSONObject(it.modelScores))
                    .put("modelVersion", it.modelVersion)
                    .put("changedAt", iso(it.changedAt)),
            )
        }
        out.bufferedWriter().use { it.write(rows.toString(2)) }
    }

    private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()

    private companion object {
        const val BATCH = 500
        const val SHOW_PROGRESS_FROM = 50
    }
}
