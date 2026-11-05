package dev.codingcoffee.cleansms.ui.components

import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

fun linkify(text: String): AnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    if (!matcher.find()) return AnnotatedString(text)
    matcher.reset()
    val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var last = 0
        while (matcher.find()) {
            append(text, last, matcher.start())
            val raw = matcher.group()
            val url = if (raw.contains("://")) raw else "https://$raw"
            withLink(LinkAnnotation.Url(url, style)) { append(raw) }
            last = matcher.end()
        }
        append(text, last, text.length)
    }
}

/** Asks before opening any link tapped inside [content]. */
@Composable
fun ConfirmLinks(knownSender: Boolean, content: @Composable () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var pending by remember { mutableStateOf<String?>(null) }
    val confirming = remember {
        object : UriHandler {
            override fun openUri(uri: String) { pending = uri }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides confirming, content = content)

    pending?.let { url ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (knownSender) "Open link?" else "Caution: Unknown sender") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!knownSender) Text("Links from unknown senders may be harmful")
                    Text(boldDomain(url), color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    uriHandler.openUri(url)
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Disallow") } },
        )
    }
}

private fun boldDomain(url: String): AnnotatedString {
    val host = Uri.parse(url).host ?: return AnnotatedString(url)
    val labels = host.split('.')
    val keep = if (MULTI_PART_SUFFIXES.any { host.endsWith(".$it", ignoreCase = true) }) 3 else 2
    val domain = labels.takeLast(keep).joinToString(".")
    val hostStart = url.indexOf(host, ignoreCase = true)
    if (hostStart < 0) return AnnotatedString(url)
    val start = hostStart + host.length - domain.length
    return buildAnnotatedString {
        append(url)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + domain.length)
    }
}

private val MULTI_PART_SUFFIXES = listOf("bank.in")
