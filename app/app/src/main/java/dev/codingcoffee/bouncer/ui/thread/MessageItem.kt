package dev.codingcoffee.bouncer.ui.thread

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.codingcoffee.bouncer.data.contacts.Contact
import dev.codingcoffee.bouncer.data.db.Box as MessageBox
import dev.codingcoffee.bouncer.data.db.MessageEntity
import dev.codingcoffee.bouncer.data.db.MmsPartEntity
import dev.codingcoffee.bouncer.ui.components.Avatar
import dev.codingcoffee.bouncer.ui.components.linkify
import dev.codingcoffee.bouncer.util.TimeFormat
import dev.codingcoffee.bouncer.util.rememberImage

@Immutable
data class MessageLayout(
    val showDayHeader: Boolean,
    val joinsOlder: Boolean,
    val joinsNewer: Boolean,
)

private val BIG = 20.dp
private val SMALL = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: MessageEntity,
    layout: MessageLayout,
    sender: Contact?,
    isGroup: Boolean,
    loadParts: suspend (Long) -> List<MmsPartEntity>,
    onRetry: (MessageEntity) -> Unit,
) {
    val context = LocalContext.current
    val incoming = message.isIncoming
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (layout.showDayHeader) {
            Text(
                remember(message.date) { TimeFormat.dayHeader(message.date) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (incoming && isGroup && !layout.joinsOlder && sender != null) {
            Text(
                sender.displayName,
                modifier = Modifier.padding(start = 56.dp, top = 6.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = if (layout.joinsOlder) 2.dp else 6.dp),
            horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (incoming) {
                if (!layout.joinsNewer && sender != null) Avatar(listOf(sender), size = 36.dp) else Spacer(Modifier.width(36.dp))
                Spacer(Modifier.width(8.dp))
            }
            val shape = if (incoming) {
                RoundedCornerShape(
                    topStart = if (layout.joinsOlder) SMALL else BIG, topEnd = BIG,
                    bottomEnd = BIG, bottomStart = if (layout.joinsNewer) SMALL else BIG,
                )
            } else {
                RoundedCornerShape(
                    topStart = BIG, topEnd = if (layout.joinsOlder) SMALL else BIG,
                    bottomEnd = if (layout.joinsNewer) SMALL else BIG, bottomStart = BIG,
                )
            }
            Column(
                Modifier.widthIn(max = 300.dp),
                horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
            ) {
                if (message.hasAttachment) MmsImages(message.id, loadParts)
                if (message.body.isNotEmpty()) {
                    Text(
                        remember(message.body) { linkify(message.body) },
                        modifier = Modifier
                            .clip(shape)
                            .background(if (incoming) colors.surfaceContainerHigh else colors.primaryContainer)
                            .combinedClickable(
                                onClick = { if (message.box == MessageBox.FAILED) onRetry(message) },
                                onLongClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("message", message.body))
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (incoming) colors.onSurface else colors.onPrimaryContainer,
                    )
                }
            }
        }
        if (!layout.joinsNewer || message.box != MessageBox.INBOX && message.box != MessageBox.SENT) {
            Status(message, Modifier.align(if (incoming) Alignment.Start else Alignment.End), onRetry)
        }
    }
}

@Composable
private fun Status(message: MessageEntity, modifier: Modifier, onRetry: (MessageEntity) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val (text, color) = when (message.box) {
        MessageBox.OUTBOX, MessageBox.QUEUED -> "Sending…" to colors.onSurfaceVariant
        MessageBox.FAILED -> "Not sent · Tap to retry" to colors.error
        else -> remember(message.date) { TimeFormat.clock(context, message.date) } to colors.onSurfaceVariant
    }
    Text(
        text,
        modifier = modifier
            .padding(start = if (message.isIncoming) 56.dp else 0.dp, top = 4.dp, end = 4.dp)
            .then(if (message.box == MessageBox.FAILED) Modifier.clickable { onRetry(message) } else Modifier),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun MmsImages(messageId: Long, loadParts: suspend (Long) -> List<MmsPartEntity>) {
    val parts by produceState(emptyList<MmsPartEntity>(), messageId) { value = loadParts(messageId) }
    val maxPx = with(LocalDensity.current) { 260.dp.roundToPx() }
    parts.filter { it.isImage }.forEach { part ->
        val image = rememberImage(Uri.parse("content://mms/part/${part.id}"), maxPx)
        val modifier = Modifier.padding(bottom = 4.dp).width(240.dp).clip(RoundedCornerShape(BIG))
        if (image != null) {
            Image(
                image, null,
                modifier.aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier.height(180.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
    if (parts.isEmpty()) {
        Box(Modifier.padding(bottom = 4.dp).size(240.dp, 180.dp).clip(RoundedCornerShape(BIG)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
