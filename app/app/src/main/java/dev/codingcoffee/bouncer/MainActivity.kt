package dev.codingcoffee.bouncer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.codingcoffee.bouncer.ui.AppRoot
import dev.codingcoffee.bouncer.util.recipientsFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val threadRequests = Channel<ThreadRequest>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(threadRequests.receiveAsFlow()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId >= 0) {
            threadRequests.trySend(ThreadRequest(threadId))
            return
        }
        if (intent.action != Intent.ACTION_SENDTO && intent.action != Intent.ACTION_SEND) return
        val recipients = recipientsFromUri(intent.data)
        if (recipients.isEmpty()) return
        val draft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { container.messaging.threadIdFor(recipients) }
            threadRequests.send(ThreadRequest(id, draft))
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
    }
}

data class ThreadRequest(val threadId: Long, val draft: String? = null)
