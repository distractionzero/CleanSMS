package dev.codingcoffee.cleansms.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import dev.codingcoffee.cleansms.AppContainer
import dev.codingcoffee.cleansms.data.db.ConversationEntity
import dev.codingcoffee.cleansms.data.db.Label
import dev.codingcoffee.cleansms.data.db.MessageEntity
import dev.codingcoffee.cleansms.util.Otp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Keys are scoped to the filter so the list doesn't anchor to an item from the previous filter.
data class FilteredConversation(val key: String, val conversation: ConversationEntity, val label: Label?)

data class RecentOtp(val message: MessageEntity, val code: String)

class ConversationsViewModel(private val container: AppContainer, archived: Boolean) : ViewModel() {
    private val _labels = MutableStateFlow(emptySet<Label>())
    val labels = _labels.asStateFlow()
    private val _unreadOnly = MutableStateFlow(false)
    val unreadOnly = _unreadOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations = combine(_labels, _unreadOnly, ::Pair).flatMapLatest { (labels, unreadOnly) ->
        Pager(
            PagingConfig(pageSize = 50, prefetchDistance = 100, initialLoadSize = 100, enablePlaceholders = true),
        ) { container.db.conversations().paging(archived, labels, labels.isEmpty(), unreadOnly) }.flow
            .map { data -> data.map { FilteredConversation("$labels:$unreadOnly:${it.conversation.threadId}", it.conversation, it.label) } }
    }.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentOtp = container.db.messages().observeLatest(Label.OTP).flatMapLatest { message ->
        flow {
            val code = message?.let { Otp.extract(it.body) }
            val remaining = message?.let { it.date + OTP_WINDOW_MS - System.currentTimeMillis() } ?: 0
            if (code != null && remaining > 0) {
                emit(RecentOtp(message, code))
                delay(remaining)
            }
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncState = container.sync.state
    val labelState = container.labeler.state

    fun toggleUnreadOnly() = _unreadOnly.update { !it }

    fun toggleLabel(label: Label) = _labels.update { if (label in it) it - label else it + label }

    fun clearLabels() = _labels.update { emptySet() }

    fun setArchived(threadIds: Set<Long>, archived: Boolean) = container.scope.launch {
        container.messaging.setArchived(threadIds.toList(), archived)
    }

    fun markRead(threadIds: Set<Long>) = container.scope.launch {
        threadIds.forEach { container.messaging.markRead(it) }
    }

    fun delete(threadIds: Set<Long>) = container.scope.launch {
        container.messaging.delete(threadIds.toList())
    }

    private companion object {
        const val OTP_WINDOW_MS = 2 * 60 * 1000L
    }
}
