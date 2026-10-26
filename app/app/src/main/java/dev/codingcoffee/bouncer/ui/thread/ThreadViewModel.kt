package dev.codingcoffee.bouncer.ui.thread

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dev.codingcoffee.bouncer.AppContainer
import dev.codingcoffee.bouncer.data.MessageDetails
import dev.codingcoffee.bouncer.data.db.MessageEntity
import dev.codingcoffee.bouncer.data.db.MmsPartEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreadViewModel(private val container: AppContainer, private val threadId: Long) : ViewModel() {
    val messages = Pager(
        PagingConfig(pageSize = 60, prefetchDistance = 120, initialLoadSize = 120, enablePlaceholders = true),
    ) { container.db.messages().pagingForThread(threadId) }.flow.cachedIn(viewModelScope)

    val conversation = container.db.conversations().observe(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    init {
        viewModelScope.launch {
            _addresses.value = container.db.conversations().byId(threadId)?.addressList
                ?: withContext(Dispatchers.IO) { container.sync.threadAddresses(listOf(threadId))[threadId].orEmpty() }
        }
    }

    fun send(text: String, image: Uri?) {
        val recipients = addresses.value
        container.scope.launch { container.messaging.send(threadId, recipients, text.trim(), image) }
    }

    fun retry(message: MessageEntity) {
        container.scope.launch { container.messaging.retry(message) }
    }

    fun markRead() {
        container.scope.launch { container.messaging.markRead(threadId) }
    }

    fun setArchived(archived: Boolean) {
        container.scope.launch { container.messaging.setArchived(listOf(threadId), archived) }
    }

    fun delete() {
        container.scope.launch { container.messaging.delete(listOf(threadId)) }
    }

    suspend fun messagesFor(ids: Set<Long>): List<MessageEntity> =
        ids.chunked(500).flatMap { container.db.messages().byIds(it) }.sortedWith(compareBy({ it.date }, { it.id }))

    suspend fun allIds(): Set<Long> = container.db.messages().idsInThread(threadId).toSet()

    suspend fun details(id: Long): MessageDetails? =
        container.db.messages().byId(id)?.let { container.messaging.details(it) }

    fun deleteMessages(ids: Set<Long>) {
        container.scope.launch { container.messaging.deleteMessages(threadId, messagesFor(ids)) }
    }

    suspend fun positionOf(messageId: Long): Int? {
        val message = container.db.messages().byId(messageId) ?: return null
        return container.db.messages().positionInThread(threadId, message.date, message.id)
    }

    suspend fun parts(messageId: Long): List<MmsPartEntity> = container.db.messages().parts(messageId)
}
