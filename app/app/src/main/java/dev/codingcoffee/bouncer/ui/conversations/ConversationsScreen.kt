package dev.codingcoffee.bouncer.ui.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.classify.LabelState
import dev.codingcoffee.bouncer.data.db.ConversationEntity
import dev.codingcoffee.bouncer.data.sync.SyncState
import dev.codingcoffee.bouncer.ui.components.Avatar
import dev.codingcoffee.bouncer.ui.components.LabelFilter
import dev.codingcoffee.bouncer.ui.components.rememberContacts
import dev.codingcoffee.bouncer.ui.components.title
import dev.codingcoffee.bouncer.util.TimeFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    archived: Boolean,
    onOpenThread: (Long) -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onArchived: () -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.container
    val vm: ConversationsViewModel = viewModel(key = "conversations-$archived") { ConversationsViewModel(container, archived) }
    val conversations = vm.conversations.collectAsLazyPagingItems()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
    val labelState by vm.labelState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun refilter(apply: () -> Unit) {
        apply()
        scope.launch { listState.scrollToItem(0) }
    }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") }
                    },
                    actions = {
                        IconButton(onClick = { vm.markRead(selected); selected = emptySet() }) {
                            Icon(Icons.Outlined.MarkChatRead, "Mark as read")
                        }
                        IconButton(onClick = { vm.setArchived(selected, !archived); selected = emptySet() }) {
                            Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archived) "Unarchive" else "Archive")
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            } else {
                TopAppBar(
                    title = { Text(if (archived) "Archived" else "Clean SMS") },
                    navigationIcon = {
                        if (archived) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (!archived) {
                            WithTooltip("Search") {
                                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                            }
                        }
                        WithTooltip("Unread only") {
                            IconToggleButton(checked = unreadOnly, onCheckedChange = { refilter(vm::toggleUnreadOnly) }) {
                                Icon(if (unreadOnly) Icons.Filled.MarkChatUnread else Icons.Outlined.MarkChatUnread, "Unread only")
                            }
                        }
                        if (!archived) {
                            WithTooltip("Archived") {
                                IconButton(onClick = onArchived) { Icon(Icons.Outlined.Archive, "Archived") }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!archived && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = onNewChat,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null) },
                    text = { Text("Start chat") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            (labelState as? LabelState.Sorting)?.let { state ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sorting messages… ${state.done} of ${state.total}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            LabelFilter(selected = labels, onToggle = { refilter { vm.toggleLabel(it) } })
            if (labels.isNotEmpty()) {
                TextButton(onClick = { refilter(vm::clearLabels) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("Clear filters")
                }
            }
            (syncState as? SyncState.Importing)?.let { state ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Importing messages… ${state.done} of ${state.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (conversations.itemCount == 0 && conversations.loadState.refresh is LoadState.NotLoading && syncState is SyncState.Idle) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            unreadOnly -> "No unread conversations"
                            labels.isNotEmpty() -> "No conversations labelled ${labels.joinToString(" or ") { it.name }}"
                            archived -> "No archived conversations"
                            else -> "No conversations yet"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(count = conversations.itemCount, key = conversations.itemKey { it.key }, contentType = { 0 }) { index ->
                    val conversation = conversations[index]?.conversation
                    if (conversation == null) {
                        Spacer(Modifier.height(ROW_HEIGHT))
                    } else {
                        val isSelected = conversation.threadId in selected
                        ConversationRow(
                            conversation = conversation,
                            selected = isSelected,
                            onClick = {
                                if (selecting) selected = selected.toggle(conversation.threadId)
                                else onOpenThread(conversation.threadId)
                            },
                            onLongClick = { selected = selected.toggle(conversation.threadId) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (selected.size == 1) "Delete conversation?" else "Delete ${selected.size} conversations?") },
            text = { Text("Messages will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(selected)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val ROW_HEIGHT = 76.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val addresses = remember(conversation.addresses) { conversation.addressList }
    val contacts = rememberContacts(addresses)
    val unread = conversation.unreadCount > 0
    val time = remember(conversation.date) { TimeFormat.conversation(context, conversation.date) }
    val emphasis = if (unread) FontWeight.Bold else FontWeight.Normal

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contacts, selected = selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contacts.title(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = emphasis,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (conversation.lastFromMe) "You: ${conversation.snippet}" else conversation.snippet,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = emphasis,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .widthIn(min = 20.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

