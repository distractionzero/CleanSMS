package dev.codingcoffee.bouncer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.contacts.Contact
import dev.codingcoffee.bouncer.util.rememberImage

private val avatarHues = floatArrayOf(4f, 28f, 145f, 175f, 205f, 230f, 265f, 320f)

@Composable
fun rememberContacts(addresses: List<String>): List<Contact> {
    val repo = LocalContext.current.container.contacts
    val version by repo.version.collectAsState()
    var contacts by remember(addresses, version) {
        mutableStateOf(addresses.map { repo.peek(it) ?: Contact(it, null, null) })
    }
    LaunchedEffect(addresses, version) {
        if (addresses.any { repo.peek(it) == null }) contacts = addresses.map { repo.get(it) }
    }
    return contacts
}

fun List<Contact>.title(): String = when {
    isEmpty() -> "Unknown"
    size == 1 -> first().displayName
    else -> joinToString(", ") { it.name?.substringBefore(' ') ?: it.address }
}

@Composable
fun Avatar(contacts: List<Contact>, modifier: Modifier = Modifier, size: Dp = 48.dp, selected: Boolean = false) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val seed = contacts.firstOrNull()?.address.orEmpty()
    val hue = avatarHues[(seed.hashCode() and Int.MAX_VALUE) % avatarHues.size]
    val background = if (dark) Color.hsl(hue, 0.35f, 0.32f) else Color.hsl(hue, 0.6f, 0.86f)
    val foreground = if (dark) Color.hsl(hue, 0.6f, 0.86f) else Color.hsl(hue, 0.45f, 0.3f)
    val single = contacts.singleOrNull()
    val px = with(LocalDensity.current) { size.roundToPx() }
    val photo = rememberImage(single?.photoUri, px)

    Box(
        modifier.size(size).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
            photo != null -> Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            contacts.size > 1 -> Icon(Icons.Filled.Group, null, tint = foreground, modifier = Modifier.size(size * 0.55f))
            single?.name != null -> Text(
                single.name.first().uppercase(),
                color = foreground,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleMedium,
            )
            else -> Icon(Icons.Filled.Person, null, tint = foreground, modifier = Modifier.size(size * 0.6f))
        }
    }
}
