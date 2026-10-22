package dev.codingcoffee.bouncer.ui.newchat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.contacts.Contact
import dev.codingcoffee.bouncer.data.contacts.PhoneEntry
import dev.codingcoffee.bouncer.ui.components.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Recipient(val name: String, val number: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenThread: (Long) -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<PhoneEntry>()) }
    var recipients by remember { mutableStateOf(emptyList<Recipient>()) }
    var groupMode by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(container.contacts.hasPermission()) }
    val focus = remember { FocusRequester() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        container.contacts.onPermissionChanged()
    }

    LaunchedEffect(query, hasPermission) {
        delay(120)
        results = container.contacts.search(query)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun open(numbers: List<String>) = scope.launch {
        if (numbers.isEmpty()) return@launch
        val threadId = withContext(Dispatchers.IO) { container.messaging.threadIdFor(numbers) }
        onOpenThread(threadId)
    }

    fun pick(recipient: Recipient) {
        if (groupMode) {
            if (recipients.none { it.number == recipient.number }) recipients = recipients + recipient
            query = ""
        } else {
            open(listOf(recipient.number))
        }
    }

    val typedNumber = query.trim().takeIf { q -> q.isNotEmpty() && q.all { it.isDigit() || it in "+-() " } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (groupMode) "New group" else "New conversation") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (groupMode && recipients.isNotEmpty()) {
                        TextButton(onClick = { open(recipients.map { it.number }) }) { Text("Next") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("To", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("Type name, phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { typedNumber?.let { pick(Recipient(it, it)) } }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
            if (recipients.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recipients.forEach { recipient ->
                        InputChip(
                            selected = false,
                            onClick = { recipients = recipients - recipient },
                            label = { Text(recipient.name) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (!groupMode && query.isEmpty()) {
                    item(key = "group") {
                        ActionRow(onClick = { groupMode = true }) {
                            Icon(Icons.Outlined.GroupAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("Create group", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                typedNumber?.let { number ->
                    item(key = "typed") {
                        EntryRow(Contact(number, null, null), "Send to $number", null) { pick(Recipient(number, number)) }
                    }
                }
                if (!hasPermission) {
                    item(key = "permission") {
                        ActionRow(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) {
                            Text("Allow access to contacts", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                items(results, key = { "${it.contactId}:${it.number}" }) { entry ->
                    EntryRow(Contact(entry.number, entry.name, entry.photoUri), entry.name, "${entry.number} · ${entry.label}") {
                        pick(Recipient(entry.name, entry.number))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun EntryRow(contact: Contact, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(listOf(contact), size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
