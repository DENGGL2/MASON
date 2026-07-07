package com.denggl2.mason.ui.chat

import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatClient: ChatClient,
) : CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = ChatMessage(role = "user", content = content)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isStreaming = true,
            streamingContent = "",
        )

        launch {
            val fullContent = StringBuilder()
            chatClient.chat(_uiState.value.messages).collect { chunk ->
                fullContent.append(chunk)
                _uiState.value = _uiState.value.copy(streamingContent = fullContent.toString())
            }

            val assistantMessage = ChatMessage(role = "assistant", content = fullContent.toString())
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage,
                isStreaming = false,
                streamingContent = "",
            )
        }
    }
}
