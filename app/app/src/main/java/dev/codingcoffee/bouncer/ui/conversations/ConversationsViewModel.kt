package dev.codingcoffee.bouncer.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dev.codingcoffee.bouncer.AppContainer
import kotlinx.coroutines.launch

class ConversationsViewModel(private val container: AppContainer, archived: Boolean) : ViewModel() {
    val conversations = Pager(
        PagingConfig(pageSize = 50, prefetchDistance = 100, initialLoadSize = 100, enablePlaceholders = true),
    ) { container.db.conversations().paging(archived) }.flow.cachedIn(viewModelScope)

    val syncState = container.sync.state

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
