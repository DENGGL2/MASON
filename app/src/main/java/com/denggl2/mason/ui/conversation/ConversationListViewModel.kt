package com.denggl2.mason.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationListItem(
    val conversation: Conversation,
    val lastMessage: String?,
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val syncManager: SyncManager,
    configDataStore: ApiConfigDataStore,
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationListItem>>(emptyList())
    val conversations: StateFlow<List<ConversationListItem>> = _conversations.asStateFlow()
    val apiConfig: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    init {
        viewModelScope.launch {
            syncManager.getConversationsFlow().collect { convList ->
                val items = convList.map { conv ->
                    val lastMsg = syncManager.getLastMessage(conv.id)
                    val preview = lastMsg?.content?.let { content ->
                        val roleLabel = when (lastMsg.role) {
                            "user" -> "你: "
                            "assistant" -> ""
                            "tool" -> "[工具] "
                            else -> ""
                        }
                        val body = content.replace("\n", " ").trim()
                        roleLabel + if (body.length > 40) body.take(40) + "..." else body
                    }
                    ConversationListItem(conversation = conv, lastMessage = preview)
                }
                _conversations.value = items
            }
        }
    }

    fun createConversation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = syncManager.createOrGetConversation("新对话")
            onCreated(id)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            syncManager.deleteConversation(id)
        }
    }
}
