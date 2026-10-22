package dev.codingcoffee.bouncer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.codingcoffee.bouncer.util.recipientsFromUri
import kotlinx.coroutines.launch

/** Handles "respond via message" from the incoming call screen. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipients = recipientsFromUri(intent?.data)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (recipients.isEmpty() || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        container.scope.launch {
            try {
                val messaging = container.messaging
                messaging.send(messaging.threadIdFor(recipients), recipients, text, null)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}
