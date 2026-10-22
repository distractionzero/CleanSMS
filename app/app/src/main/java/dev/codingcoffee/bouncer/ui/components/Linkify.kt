package dev.codingcoffee.bouncer.ui.components

import android.util.Patterns
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

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
