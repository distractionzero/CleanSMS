package dev.codingcoffee.cleansms.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.RemoteInput
import dev.codingcoffee.cleansms.container
import dev.codingcoffee.cleansms.data.mms.MmsService
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = messages.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        runAsync(context) { context.container.messaging.receiveSms(address, body, first.timestampMillis) }
    }
}

class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data") ?: return
        runAsync(context) { context.container.mms.download(pdu) }
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val success = resultCode == Activity.RESULT_OK
        runAsync(context) { context.container.messaging.onSmsSent(uri, success) }
    }

    companion object {
        const val ACTION_SENT = "dev.codingcoffee.cleansms.SMS_SENT"
    }
}

class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val success = resultCode == Activity.RESULT_OK
        val file = intent.getStringExtra(MmsService.EXTRA_FILE)
        val container = context.container
        runAsync(context) {
            when (intent.action) {
                MmsService.ACTION_SENT ->
                    intent.data?.let { container.mms.onSent(it, file, success) }
                MmsService.ACTION_DOWNLOADED ->
                    container.mms.onDownloaded(file, success)?.let { container.messaging.onIncoming(it) }
            }
        }
    }
}

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runAsync(context) { context.container.notifier.restore() }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1).takeIf { it >= 0 } ?: return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0)
        val container = context.container
        when (intent.action) {
            ACTION_DISMISS -> container.notifier.cancel(threadId, messageId)
            ACTION_MARK_READ -> runAsync(context) { container.messaging.markRead(threadId, messageId) }
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
                if (text.isNullOrBlank()) return
                runAsync(context) {
                    val addresses = container.db.conversations().byId(threadId)?.addressList
                        ?: container.sync.threadAddresses(listOf(threadId))[threadId].orEmpty()
                    container.messaging.send(threadId, addresses, text, null)
                    container.messaging.markRead(threadId)
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.codingcoffee.cleansms.REPLY"
        const val ACTION_MARK_READ = "dev.codingcoffee.cleansms.MARK_READ"
        const val ACTION_DISMISS = "dev.codingcoffee.cleansms.DISMISS"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val KEY_REPLY = "reply"
    }
}
