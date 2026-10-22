package dev.codingcoffee.bouncer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.codingcoffee.bouncer.AppContainer
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.db.MessageEntity
import dev.codingcoffee.bouncer.ui.components.Avatar
import dev.codingcoffee.bouncer.ui.components.rememberContacts
import dev.codingcoffee.bouncer.ui.components.title
import dev.codingcoffee.bouncer.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    val results = query.debounce(250).flatMapLatest { raw ->
        val fts = toFtsQuery(raw)
        if (fts == null) flowOf(PagingData.empty())
        else Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) { container.db.messages().search(fts) }.flow
    }.cachedIn(viewModelScope)

    suspend fun addressesFor(threadId: Long): List<String> =
        container.db.conversations().byId(threadId)?.addressList.orEmpty()
}

internal fun toFtsQuery(raw: String): String? {
    val tokens = raw.split(Regex("\\s+")).map { token -> token.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    return if (tokens.isEmpty()) null else tokens.joinToString(" ") { "$it*" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenMessage: (threadId: Long, messageId: Long) -> Unit) {
    val container = LocalContext.current.container
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results = vm.results.collectAsLazyPagingItems()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text("Search messages") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Clear") }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding()) {
            items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                results[index]?.let { message ->
                    SearchRow(message, query, vm::addressesFor) { onOpenMessage(message.threadId, message.id) }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    message: MessageEntity,
    query: String,
    addressesFor: suspend (Long) -> List<String>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val addresses by produceState(emptyList<String>(), message.threadId) { value = addressesFor(message.threadId) }
    val contacts = rememberContacts(addresses)
    val highlighted = remember(message.body, query) { highlight(message.body, query) }
    val highlightColor = MaterialTheme.colorScheme.primary

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(contacts, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contacts.title(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    remember(message.date) { TimeFormat.conversation(context, message.date) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                highlighted.withColor(highlightColor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class Highlighted(val text: String, val ranges: List<IntRange>) {
    fun withColor(color: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        ranges.forEach { range ->
            append(text, last, range.first)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text, range.first, range.last + 1) }
            last = range.last + 1
        }
        append(text, last, text.length)
    }
}

private fun highlight(body: String, query: String): Highlighted {
    val terms = query.split(Regex("\\s+")).map { t -> t.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    val firstMatch = terms.mapNotNull { body.indexOf(it, ignoreCase = true).takeIf { i -> i >= 0 } }.minOrNull() ?: 0
    val start = if (firstMatch > 40) body.lastIndexOf(' ', firstMatch - 20).coerceAtLeast(firstMatch - 30) else 0
    val text = (if (start > 0) "…" else "") + body.substring(start).replace('\n', ' ')
    val ranges = terms.flatMap { term ->
        Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
    }.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, range ->
        if (acc.isEmpty() || range.first > acc.last().last) acc += range
        acc
    }
    return Highlighted(text, ranges)
}
