package dev.codingcoffee.bouncer.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

object Box {
    const val INBOX = 1
    const val SENT = 2
    const val OUTBOX = 4
    const val FAILED = 5
    const val QUEUED = 6
}

enum class Label { Personal, OTP, Government, Reminder, Transaction, Service, Promotional, Spam, Unknown }

/** SMS rows keep the provider `_id`; MMS rows use the negated provider `_id`. */
@Immutable
@Entity(
    tableName = "messages",
    indices = [Index(value = ["threadId", "date"]), Index(value = ["threadId", "read"])],
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val box: Int,
    val read: Boolean,
    val isMms: Boolean,
    val hasAttachment: Boolean,
    val label: Label? = null,
) {
    val isIncoming: Boolean get() = box == Box.INBOX
    val providerId: Long get() = if (isMms) -id else id
}

@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "messages_fts")
data class MessageFts(val body: String)

@Immutable
@Entity(tableName = "mms_parts", indices = [Index("messageId")])
data class MmsPartEntity(
    @PrimaryKey val id: Long,
    val messageId: Long,
    val contentType: String,
    val text: String?,
) {
    val isImage: Boolean get() = contentType.startsWith("image/")
}

@Immutable
@Entity(tableName = "conversations", indices = [Index(value = ["archived", "date"])])
data class ConversationEntity(
    @PrimaryKey val threadId: Long,
    val addresses: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val lastFromMe: Boolean,
    val archived: Boolean,
) {
    val addressList: List<String> get() = addresses.split(ADDRESS_SEPARATOR).filter { it.isNotBlank() }

    companion object {
        const val ADDRESS_SEPARATOR = "|"
    }
}

@Entity(tableName = "label_changes", indices = [Index("messageId")])
data class LabelChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val receivedAt: Long,
    val sentAt: Long?,
    val isMms: Boolean,
    val hasAttachment: Boolean,
    val isGroup: Boolean,
    val senderInContacts: Boolean,
    val sim: String?,
    val fromLabel: Label?,
    val toLabel: Label,
    val modelLabel: Label,
    val modelScores: String,
    val modelVersion: String,
    val changedAt: Long,
)

data class LabelOverride(
    val messageId: Long,
    val toLabel: Label,
)

data class ThreadStats(
    val threadId: Long,
    val body: String,
    val date: Long,
    val box: Int,
    val hasAttachment: Boolean,
    val unread: Int,
)

data class MessageStats(
    val total: Int,
    val received: Int,
    val sent: Int,
    val conversations: Int,
)

data class MessageText(
    val id: Long,
    val body: String,
)

data class DailyCount(
    val day: String,
    val count: Int,
)
