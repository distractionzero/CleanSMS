package dev.codingcoffee.bouncer.ui.thread

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.contacts.Contact
import dev.codingcoffee.bouncer.data.contacts.ContactRepository
import dev.codingcoffee.bouncer.data.db.MessageEntity
import dev.codingcoffee.bouncer.notify.ActiveThread
import dev.codingcoffee.bouncer.ui.components.Avatar
import dev.codingcoffee.bouncer.ui.components.rememberContacts
import dev.codingcoffee.bouncer.ui.components.title
import dev.codingcoffee.bouncer.util.TimeFormat
import dev.codingcoffee.bouncer.util.rememberImage
import kotlinx.coroutines.flow.first

private const val GROUP_WINDOW_MS = 2 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(threadId: Long, focusMessageId: Long?, initialDraft: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val vm: ThreadViewModel = viewModel(key = "thread-$threadId") { ThreadViewModel(container, threadId) }
    val messages = vm.messages.collectAsLazyPagingItems()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val contacts = rememberContacts(addresses)
    val contactsByKey = remember(contacts) { contacts.associateBy { ContactRepository.key(it.address) } }
    val isGroup = addresses.size > 1
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf(initialDraft) }
    var attachment by rememberSaveable { mutableStateOf<Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LifecycleResumeEffect(threadId) {
        ActiveThread.id = threadId
        container.notifier.cancel(threadId)
        onPauseOrDispose { if (ActiveThread.id == threadId) ActiveThread.id = null }
    }
    LaunchedEffect(conversation?.unreadCount) {
        if ((conversation?.unreadCount ?: 0) > 0) vm.markRead()
    }
    LaunchedEffect(focusMessageId) {
        val position = focusMessageId?.let { vm.positionOf(it) } ?: return@LaunchedEffect
        snapshotFlow { messages.itemCount }.first { it > position }
        listState.scrollToItem(position)
    }
    LaunchedEffect(Unit) {
        var previous = -1
        snapshotFlow { messages.itemCount }.collect { count ->
            if (previous > 0 && count > previous && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
            previous = count
        }
    }
    val loadParts = remember(vm) { vm::parts }
    val retry = remember(vm) { vm::retry }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachment = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contacts, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(contacts.title(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            val subtitle = contacts.singleOrNull()?.takeIf { it.name != null }?.address
                                ?: if (isGroup) "${addresses.size} people" else null
                            subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                actions = {
                    if (!isGroup && addresses.isNotEmpty()) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", addresses.first(), null)))
                        }) { Icon(Icons.Outlined.Call, "Call") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val archived = conversation?.archived == true
                            DropdownMenuItem(
                                text = { Text(if (archived) "Unarchive" else "Archive") },
                                onClick = {
                                    menuOpen = false
                                    vm.setArchived(!archived)
                                    if (!archived) onBack()
                                },
                            )
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                text = draft,
                onTextChange = { draft = it },
                attachment = attachment,
                isMms = isGroup || attachment != null,
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveAttachment = { attachment = null },
                onSend = {
                    vm.send(draft, attachment)
                    draft = ""
                    attachment = null
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(
                count = messages.itemCount,
                key = messages.itemKey { it.id },
                contentType = messages.itemContentType { if (it.isIncoming) 0 else 1 },
            ) { index ->
                val message = messages[index]
                if (message == null) {
                    Spacer(Modifier.height(56.dp))
                } else {
                    val older = if (index + 1 < messages.itemCount) messages.peek(index + 1) else null
                    val newer = if (index > 0) messages.peek(index - 1) else null
                    val layout = remember(message, older, newer) { layoutFor(message, older, newer, index + 1 >= messages.itemCount) }
                    MessageItem(
                        message = message,
                        layout = layout,
                        sender = if (message.isIncoming) senderFor(message, contactsByKey, contacts) else null,
                        isGroup = isGroup,
                        loadParts = loadParts,
                        onRetry = retry,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete conversation?") },
            text = { Text("Messages will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun senderFor(message: MessageEntity, byKey: Map<String, Contact>, all: List<Contact>): Contact? =
    if (message.address.isBlank()) all.firstOrNull()
    else byKey[ContactRepository.key(message.address)] ?: all.singleOrNull() ?: Contact(message.address, null, null)

private fun layoutFor(message: MessageEntity, older: MessageEntity?, newer: MessageEntity?, isOldest: Boolean): MessageLayout {
    val dayChanges = older == null && isOldest || older != null && !TimeFormat.sameDay(older.date, message.date)
    fun joins(a: MessageEntity, b: MessageEntity) =
        a.isIncoming == b.isIncoming && a.address == b.address &&
            kotlin.math.abs(a.date - b.date) < GROUP_WINDOW_MS && TimeFormat.sameDay(a.date, b.date)
    return MessageLayout(
        showDayHeader = dayChanges,
        joinsOlder = older != null && !dayChanges && joins(message, older),
        joinsNewer = newer != null && joins(message, newer),
    )
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachment: Uri?,
    isMms: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val canSend = text.isNotBlank() || attachment != null
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
            Column {
                if (attachment != null) {
                    val px = with(LocalDensity.current) { 96.dp.roundToPx() }
                    val image = rememberImage(attachment, px)
                    Box(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
                        if (image != null) {
                            Image(image, null, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant))
                        }
                        IconButton(
                            onClick = onRemoveAttachment,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                                .background(colors.inverseSurface.copy(alpha = 0.7f), CircleShape),
                        ) { Icon(Icons.Filled.Close, "Remove", tint = colors.inverseOnSurface, modifier = Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = onAttach) { Icon(Icons.Outlined.AddPhotoAlternate, "Attach photo") }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp, end = 16.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        maxLines = 6,
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        if (isMms) "MMS message" else "Text message",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}
