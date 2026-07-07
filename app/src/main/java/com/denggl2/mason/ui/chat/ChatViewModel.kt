package com.denggl2.mason.ui.chat

import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.tool.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatClient: ChatClient,
    private val toolExecutor: ToolExecutor,
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
            chatClient.chat(_uiState.value.messages).collect { response ->
                when (response) {
                    is ChatResponse.ToolCallsRequested -> {
                        val toolMessages = mutableListOf<ChatMessage>()
                        for (call in response.calls) {
                            val args = try {
                                Json.parseToJsonElement(call.arguments)
                                    .jsonObject
                                    .mapValues { it.value.jsonPrimitive.content }
                            } catch (_: Exception) {
                                emptyMap()
                            }
                            val result = toolExecutor.execute(call.name, args)
                            val resultStr = if (result.success) {
                                result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            } else {
                                "执行失败: ${result.error}"
                            }
                            toolMessages.add(ChatMessage(
                                role = "tool",
                                content = resultStr,
                                name = call.name,
                            ))
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + toolMessages,
                        )

                        chatClient.streamChat(_uiState.value.messages).collect { streamResponse ->
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
                        val assistantMessage = ChatMessage(role = "assistant", content = response.text)
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                        )
                    }

                    is ChatResponse.Error -> {
                        val errorMessage = ChatMessage(role = "assistant", content = "错误: ${response.message}")
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + errorMessage,
                        )
                    }
                }
            }

            if (_uiState.value.streamingContent.isNotEmpty()) {
                val assistantMessage = ChatMessage(role = "assistant", content = _uiState.value.streamingContent)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    isStreaming = false,
                    streamingContent = "",
                )
            } else {
                _uiState.value = _uiState.value.copy(isStreaming = false)
            }
        }
    }
}
