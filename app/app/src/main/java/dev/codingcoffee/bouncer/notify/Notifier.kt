package dev.codingcoffee.bouncer.notify

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
import dev.codingcoffee.bouncer.MainActivity
import dev.codingcoffee.bouncer.R
import dev.codingcoffee.bouncer.data.contacts.ContactRepository
import dev.codingcoffee.bouncer.data.db.BouncerDatabase
import dev.codingcoffee.bouncer.receivers.NotificationActionReceiver

/** Thread currently on screen; its incoming messages are not notified. */
object ActiveThread {
    @Volatile
    var id: Long? = null
}

class Notifier(
    private val context: Context,
    private val db: BouncerDatabase,
    private val contacts: ContactRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun notifyThread(threadId: Long) {
        if (ActiveThread.id == threadId) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val unread = db.messages().unreadInThread(threadId).takeLast(1)
        if (unread.isEmpty()) return cancel(threadId)
        val conversation = db.conversations().byId(threadId)
        val addresses = conversation?.addressList.orEmpty()

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        if (addresses.size > 1) {
            style.isGroupConversation = true
            style.conversationTitle = addresses.map { contacts.get(it).displayName }.joinToString(", ")
        }
        unread.forEach { message ->
            val sender = contacts.get(message.address.ifBlank { addresses.firstOrNull().orEmpty() })
            val text = message.body.ifBlank { if (message.hasAttachment) "Photo" else "Message" }
            style.addMessage(text, message.date, Person.Builder().setName(sender.displayName).setKey(sender.address).build())
        }

        val requestCode = threadId.toInt()
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
            actionIntent(NotificationActionReceiver.ACTION_REPLY, threadId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context, requestCode,
            actionIntent(NotificationActionReceiver.ACTION_MARK_READ, threadId),
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
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        @Suppress("MissingPermission")
        manager.notify(threadId.toInt(), notification)
    }

    fun cancel(threadId: Long) = manager.cancel(threadId.toInt())

    private fun actionIntent(action: String, threadId: Long) =
        Intent(action, null, context, NotificationActionReceiver::class.java)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)

    companion object {
        const val CHANNEL_MESSAGES = "messages"
    }
}
