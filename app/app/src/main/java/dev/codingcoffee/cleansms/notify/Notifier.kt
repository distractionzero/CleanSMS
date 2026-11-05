package dev.codingcoffee.cleansms.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dev.codingcoffee.cleansms.MainActivity
import dev.codingcoffee.cleansms.R
import dev.codingcoffee.cleansms.data.contacts.ContactRepository
import dev.codingcoffee.cleansms.data.db.CleanSmsDatabase
import dev.codingcoffee.cleansms.data.db.Label
import dev.codingcoffee.cleansms.data.db.MessageEntity
import dev.codingcoffee.cleansms.data.prefs.SettingsStore
import dev.codingcoffee.cleansms.receivers.NotificationActionReceiver

/** Thread currently on screen; its incoming messages are not notified. */
object ActiveThread {
    @Volatile
    var id: Long? = null
}

class Notifier(
    private val context: Context,
    private val db: CleanSmsDatabase,
    private val contacts: ContactRepository,
    private val settings: SettingsStore,
    private val labelOf: suspend (MessageEntity) -> Label,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun notifyThread(threadId: Long) {
        val message = db.messages().unreadInThread(threadId).lastOrNull() ?: return cancel(threadId)
        post(message, silent = false)
    }

    /** The system clears our notifications on app update; re-post the ones that were showing. */
    suspend fun restore() = settings.notifiedMessages.forEach { id ->
        val message = db.messages().byId(id)
        if (message != null && message.isIncoming && !message.read) post(message, silent = true)
        else settings.setMessageNotified(id, false)
    }

    private suspend fun post(message: MessageEntity, silent: Boolean) {
        val threadId = message.threadId
        if (ActiveThread.id == threadId) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val label = labelOf(message)
        if (label in settings.mutedLabels.value) return settings.setMessageNotified(message.id, false)
        val addresses = db.conversations().byId(threadId)?.addressList.orEmpty()

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        if (addresses.size > 1) {
            style.isGroupConversation = true
            style.conversationTitle = addresses.map { contacts.get(it).displayName }.joinToString(", ")
        }
        val sender = contacts.get(message.address.ifBlank { addresses.firstOrNull().orEmpty() })
        val text = message.body.ifBlank { if (message.hasAttachment) "Photo" else "Message" }
        val name = label.takeIf { it != Label.Unknown }?.let { "${sender.displayName} · [${it.name}]" } ?: sender.displayName
        style.addMessage(text, message.date, Person.Builder().setName(name).setKey(sender.address).build())

        val requestCode = message.id.toInt()
        val open = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val reply = PendingIntent.getBroadcast(
            context, requestCode,
            actionIntent(NotificationActionReceiver.ACTION_REPLY, message),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context, requestCode,
            actionIntent(NotificationActionReceiver.ACTION_MARK_READ, message),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, requestCode,
            actionIntent(NotificationActionReceiver.ACTION_DISMISS, message),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_notification, "Reply", reply)
            .addRemoteInput(RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel("Message").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()
        val readAction = NotificationCompat.Action.Builder(R.drawable.ic_notification, "Mark as read", markRead)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setSilent(silent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        @Suppress("MissingPermission")
        manager.notify(threadId.toString(), message.id.toInt(), notification)
        settings.setMessageNotified(message.id, true)
    }

    fun cancel(threadId: Long) = manager.activeNotifications
        .filter { it.tag == threadId.toString() }
        .forEach { cancel(threadId, it.id.toLong()) }

    fun cancel(threadId: Long, messageId: Long) {
        manager.cancel(threadId.toString(), messageId.toInt())
        settings.setMessageNotified(messageId, false)
    }

    private fun actionIntent(action: String, message: MessageEntity) =
        Intent(action, null, context, NotificationActionReceiver::class.java)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, message.threadId)
            .putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, message.id)

    companion object {
        const val CHANNEL_MESSAGES = "messages"
    }
}
