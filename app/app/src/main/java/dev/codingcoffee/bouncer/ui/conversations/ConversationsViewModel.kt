package dev.codingcoffee.bouncer.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dev.codingcoffee.bouncer.AppContainer
import dev.codingcoffee.bouncer.data.db.Label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationsViewModel(private val container: AppContainer, archived: Boolean) : ViewModel() {
    private val _label = MutableStateFlow<Label?>(null)
    val label = _label.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations = _label.flatMapLatest { label ->
        Pager(
            PagingConfig(pageSize = 50, prefetchDistance = 100, initialLoadSize = 100, enablePlaceholders = true),
        ) { container.db.conversations().paging(archived, label) }.flow
    }.cachedIn(viewModelScope)

    val syncState = container.sync.state

    fun toggleLabel(label: Label) = _label.update { if (it == label) null else label }

    fun setArchived(threadIds: Set<Long>, archived: Boolean) = container.scope.launch {
        container.messaging.setArchived(threadIds.toList(), archived)
    }

    fun markRead(threadIds: Set<Long>) = container.scope.launch {
        threadIds.forEach { container.messaging.markRead(it) }
    }

    fun delete(threadIds: Set<Long>) = container.scope.launch {
        container.messaging.delete(threadIds.toList())
    }
}
