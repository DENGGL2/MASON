package com.denggl2.mason.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.Message
import com.denggl2.mason.tool.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val toolCallStatus: String? = null,
    val conversationTitle: String = "Mason",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatClient: ChatClient,
    private val toolExecutor: ToolExecutor,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentConversationId: Long? = savedStateHandle.get<Long>("conversationId")
    private var isFirstMessage = true

    init {
        currentConversationId?.let { convId ->
            loadHistory(convId)
        }
    }

    private fun loadHistory(convId: Long) {
        viewModelScope.launch {
            // Load title
            syncManager.getConversationTitle(convId)?.let { title ->
                _uiState.value = _uiState.value.copy(conversationTitle = title)
            }

            // Load messages
            syncManager.getMessagesFlow(convId).collect { messages ->
                val chatMessages = messages.map { msg ->
                    ChatMessage(
                        role = msg.role,
                        content = msg.content,
                        tool_call_id = msg.toolCallId,
                        name = msg.toolCallName,
                        timestamp = msg.timestamp,
                    )
                }
                _uiState.value = _uiState.value.copy(messages = chatMessages)
            }
        }
    }

    fun updateConversationTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(conversationTitle = newTitle)
        currentConversationId?.let { convId ->
            viewModelScope.launch {
                syncManager.updateConversationTitle(convId, newTitle)
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = ChatMessage(role = "user", content = content, timestamp = System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isStreaming = true,
            streamingContent = "",
        )

        viewModelScope.launch {
            // Ensure a conversation exists and save user message
            if (currentConversationId == null) {
                val title = content.take(20)
                currentConversationId = syncManager.createOrGetConversation(title)
                _uiState.value = _uiState.value.copy(conversationTitle = title)
            }
            currentConversationId?.let { convId ->
                syncManager.saveMessage(convId, role = "user", content = content)

                // Auto-title on first user message
                if (isFirstMessage) {
                    val autoTitle = content.take(20)
                    syncManager.updateConversationTitle(convId, autoTitle)
                    _uiState.value = _uiState.value.copy(conversationTitle = autoTitle)
                    isFirstMessage = false
                }
            }

            chatClient.chat(_uiState.value.messages).collect { response ->
                when (response) {
                    is ChatResponse.ToolCallsRequested -> {
                        val toolMessages = mutableListOf<ChatMessage>()
                        for (call in response.calls) {
                            _uiState.value = _uiState.value.copy(
                                toolCallStatus = "正在执行 ${call.function.name} 工具..."
                            )

                            val args = try {
                                Json.parseToJsonElement(call.function.arguments)
                                    .jsonObject
                                    .mapValues { it.value.jsonPrimitive.content }
                            } catch (_: Exception) {
                                emptyMap()
                            }
                            val result = toolExecutor.execute(call.function.name, args)
                            val resultStr = if (result.success) {
                                result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            } else {
                                "执行失败: ${result.error}"
                            }
                            toolMessages.add(ChatMessage(
                                role = "tool",
                                content = resultStr,
                                tool_call_id = call.id,
                                name = call.function.name,
                                timestamp = System.currentTimeMillis(),
                            ))
                        }
                        val nextMessages = _uiState.value.messages + response.assistantMessage + toolMessages
                        _uiState.value = _uiState.value.copy(
                            messages = nextMessages,
                            toolCallStatus = null,
                        )

                        // Save tool messages
                        currentConversationId?.let { convId ->
                            toolMessages.forEach { msg ->
                                syncManager.saveMessage(
                                    convId,
                                    role = msg.role,
                                    content = msg.content,
                                    toolCallId = msg.tool_call_id,
                                    toolCallName = msg.name,
                                )
                            }
                        }

                        chatClient.streamChat(nextMessages).collect { streamResponse ->
                            when (streamResponse) {
                                is ChatResponse.TextChunk -> {
                                    _uiState.value = _uiState.value.copy(
                                        streamingContent = _uiState.value.streamingContent + streamResponse.text,
                                    )
                                }
                                is ChatResponse.Error -> {
                                    _uiState.value = _uiState.value.copy(
                                        streamingContent = _uiState.value.streamingContent + "\n[${streamResponse.message}]",
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    is ChatResponse.TextChunk -> {
                        val assistantMessage = ChatMessage(role = "assistant", content = response.text, timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                        )
                        currentConversationId?.let { convId ->
                            syncManager.saveMessage(convId, role = "assistant", content = response.text)
                        }
                    }

                    is ChatResponse.Error -> {
                        val errorMessage = ChatMessage(role = "assistant", content = "错误: ${response.message}", timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + errorMessage,
                        )
                        currentConversationId?.let { convId ->
                            syncManager.saveMessage(convId, role = "assistant", content = "错误: ${response.message}")
                        }
                    }
                }
            }

            if (_uiState.value.streamingContent.isNotEmpty()) {
                val finalContent = _uiState.value.streamingContent
                val assistantMessage = ChatMessage(role = "assistant", content = finalContent, timestamp = System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    isStreaming = false,
                    streamingContent = "",
                )
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(convId, role = "assistant", content = finalContent)
                }
            } else {
                _uiState.value = _uiState.value.copy(isStreaming = false, toolCallStatus = null)
            }
        }
    }
}
