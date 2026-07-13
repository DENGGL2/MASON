package com.denggl2.mason.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.MasonTaskPlanner
import com.denggl2.mason.agent.updateStep
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.LiteRtModelEngine
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.TokenUsage
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.tool.ToolExecutor
import com.denggl2.mason.tool.ToolResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    val conversationId: Long? = null,
    val requestStartedAt: Long? = null,
    val lastProcessingMs: Long? = null,
    val lastUsage: TokenUsage? = null,
    val conversationUsage: TokenUsage = TokenUsage(),
    val lastUsageMissing: Boolean = false,
    val taskSteps: List<TaskStep> = emptyList(),
    val pendingToolApproval: ToolApprovalRequest? = null,
)

private data class PendingToolBatch(
    val assistantMessage: ChatMessage,
    val calls: List<ToolCall>,
    val startedAt: Long,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatClient: ChatClient,
    private val toolExecutor: ToolExecutor,
    private val syncManager: SyncManager,
    private val notificationTool: NotificationTool,
    private val uiPreferencesDataStore: UiPreferencesDataStore,
    private val apiConfigDataStore: ApiConfigDataStore,
    private val artifactStore: ArtifactStore,
    private val localModelStore: LocalModelStore,
    private val liteRtModelEngine: LiteRtModelEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val apiConfig: StateFlow<ApiConfig> = apiConfigDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    private var currentConversationId: Long? = savedStateHandle.get<Long>("conversationId")?.takeIf { it > 0L }
    private var isFirstMessage = currentConversationId == null
    private var pendingToolBatch: PendingToolBatch? = null

    init {
        currentConversationId?.let { convId ->
            _uiState.value = _uiState.value.copy(conversationId = convId)
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

        val startedAt = System.currentTimeMillis()
        var usageSeen = false
        var modelAnswered = false
        val userMessage = ChatMessage(role = "user", content = content, timestamp = startedAt)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isStreaming = true,
            streamingContent = "",
            toolCallStatus = null,
            requestStartedAt = startedAt,
            lastProcessingMs = null,
            lastUsage = null,
            lastUsageMissing = false,
            taskSteps = MasonTaskPlanner.createInitialPlan(content).steps,
            pendingToolApproval = null,
        )

        viewModelScope.launch {
            // Ensure a conversation exists and save user message
            if (currentConversationId == null) {
                val title = content.take(20)
                currentConversationId = syncManager.createOrGetConversation(title)
                _uiState.value = _uiState.value.copy(
                    conversationId = currentConversationId,
                    conversationTitle = title,
                )
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

            val producedArtifacts = mutableListOf<ArtifactMetadata>()
            chatClient.chat(_uiState.value.messages).collect { response ->
                when (response) {
                    is ChatResponse.ToolCallsRequested -> {
                        _uiState.value = _uiState.value.copy(
                            taskSteps = _uiState.value.taskSteps
                                .updateStep("plan", TaskStepStatus.Completed, "已判断需要调用手机能力")
                                .updateStep("execute", TaskStepStatus.Running, "准备执行 ${response.calls.size} 个工具调用"),
                        )

                        val approval = response.calls
                            .firstOrNull { ToolPolicy.requiresUserApproval(it.function.name) }
                            ?.let { call ->
                                ToolApprovalRequest(
                                    toolName = call.function.name,
                                    riskLevel = ToolPolicy.riskFor(call.function.name),
                                    reason = ToolPolicy.approvalReason(call.function.name),
                                    call = call,
                                )
                            }

                        if (approval != null && apiConfig.value.requireToolConfirmation) {
                            pendingToolBatch = PendingToolBatch(
                                assistantMessage = response.assistantMessage,
                                calls = response.calls,
                                startedAt = startedAt,
                            )
                            _uiState.value = _uiState.value.copy(
                                isStreaming = false,
                                requestStartedAt = null,
                                toolCallStatus = null,
                                pendingToolApproval = approval,
                                taskSteps = _uiState.value.taskSteps
                                    .updateStep("execute", TaskStepStatus.WaitingForUser, "等待用户确认 ${approval.toolName}")
                                    .updateStep("summary", TaskStepStatus.Pending, "确认后继续生成结果"),
                            )
                            return@collect
                        }

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
                            if (call.function.name == "file_write" && result.success) {
                                artifactStore.metadataForExistingFile(result.artifactPath())?.let(producedArtifacts::add)
                            }
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
                            modelAnswered = true
                            _uiState.value = _uiState.value.copy(
                                streamingContent = _uiState.value.streamingContent + streamResponse.text,
                                taskSteps = _uiState.value.taskSteps
                                    .updateStep("execute", TaskStepStatus.Completed, "工具调用完成")
                                    .updateStep("review", TaskStepStatus.Completed, "已检查工具返回结果")
                                    .updateStep("summary", TaskStepStatus.Running, "正在生成最终回复"),
                            )
                        }
                                is ChatResponse.UsageReceived -> {
                                    usageSeen = true
                                    recordUsage(streamResponse.usage)
                                }
                                is ChatResponse.Error -> {
                                    _uiState.value = _uiState.value.copy(
                                        streamingContent = _uiState.value.streamingContent +
                                            "\n\n${formatGuidedError(streamResponse.message)}",
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    is ChatResponse.TextChunk -> {
                        modelAnswered = true
                        val finalContent = prepareAssistantContent(response.text)
                        val assistantMessage = ChatMessage(role = "assistant", content = finalContent, timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                            lastProcessingMs = System.currentTimeMillis() - startedAt,
                            taskSteps = _uiState.value.taskSteps
                                .updateStep("plan", TaskStepStatus.Completed, "已完成理解")
                                .updateStep("execute", TaskStepStatus.Completed, "无需调用额外能力")
                                .updateStep("review", TaskStepStatus.Completed, "已检查回答完整性")
                                .updateStep("summary", TaskStepStatus.Completed, "已生成回复"),
                        )
                        currentConversationId?.let { convId ->
                            syncManager.saveMessage(convId, role = "assistant", content = finalContent)
                        }
                    }

                    is ChatResponse.UsageReceived -> {
                        usageSeen = true
                        recordUsage(response.usage)
                    }

                    is ChatResponse.Error -> {
                        val guidedContent = tryLocalFallback(response.message, _uiState.value.messages)
                            ?: formatGuidedError(response.message)
                        val errorMessage = ChatMessage(role = "assistant", content = guidedContent, timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + errorMessage,
                            lastProcessingMs = System.currentTimeMillis() - startedAt,
                            taskSteps = _uiState.value.taskSteps
                                .updateStep("summary", TaskStepStatus.Failed, response.message),
                        )
                        currentConversationId?.let { convId ->
                            syncManager.saveMessage(convId, role = "assistant", content = guidedContent)
                        }
                    }
                }
            }

            if (_uiState.value.pendingToolApproval != null) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    toolCallStatus = null,
                    requestStartedAt = null,
                    taskSteps = _uiState.value.taskSteps
                        .updateStep("execute", TaskStepStatus.WaitingForUser),
                )
                return@launch
            }

            if (_uiState.value.streamingContent.isNotEmpty()) {
                modelAnswered = true
                val finalContent = prepareAssistantContent(_uiState.value.streamingContent, producedArtifacts)
                val assistantMessage = ChatMessage(role = "assistant", content = finalContent, timestamp = System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    isStreaming = false,
                    streamingContent = "",
                    requestStartedAt = null,
                    lastProcessingMs = System.currentTimeMillis() - startedAt,
                    lastUsageMissing = modelAnswered && !usageSeen,
                    taskSteps = _uiState.value.taskSteps
                        .updateStep("review", TaskStepStatus.Completed, "已检查执行结果")
                        .updateStep("summary", TaskStepStatus.Completed, "已生成最终回复"),
                )
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(convId, role = "assistant", content = finalContent)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    toolCallStatus = null,
                    requestStartedAt = null,
                    lastProcessingMs = System.currentTimeMillis() - startedAt,
                    lastUsageMissing = modelAnswered && !usageSeen,
                    taskSteps = if (_uiState.value.pendingToolApproval == null) {
                        _uiState.value.taskSteps
                    } else {
                        _uiState.value.taskSteps
                            .updateStep("execute", TaskStepStatus.WaitingForUser)
                    },
                )
            }
            notifyTaskCompletedIfNeeded()
        }
    }

    fun retryLastUserMessage() {
        if (_uiState.value.isStreaming) return
        val content = _uiState.value.messages
            .lastOrNull { it.role == "user" }
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: return
        sendMessage(content)
    }

    fun approvePendingToolCall() {
        val batch = pendingToolBatch ?: return
        pendingToolBatch = null
        _uiState.value = _uiState.value.copy(
            pendingToolApproval = null,
            isStreaming = true,
            requestStartedAt = batch.startedAt,
            taskSteps = _uiState.value.taskSteps
                .updateStep("execute", TaskStepStatus.Running, "用户已确认，正在执行工具")
                .updateStep("summary", TaskStepStatus.Pending, "工具完成后继续生成结果"),
        )
        viewModelScope.launch {
            executeApprovedToolBatch(batch)
            notifyTaskCompletedIfNeeded()
        }
    }

    fun rejectPendingToolCall() {
        val approval = _uiState.value.pendingToolApproval ?: return
        pendingToolBatch = null
        val content = "引导：已取消 ${approval.toolName} 工具调用。\n最终总结：这次操作没有继续执行，手机状态不会被更改。"
        val assistantMessage = ChatMessage(
            role = "assistant",
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.value = _uiState.value.copy(
            pendingToolApproval = null,
            isStreaming = false,
            requestStartedAt = null,
            messages = _uiState.value.messages + assistantMessage,
            taskSteps = _uiState.value.taskSteps
                .updateStep("execute", TaskStepStatus.Cancelled, "用户已拒绝工具调用")
                .updateStep("review", TaskStepStatus.Cancelled, "无需继续检查")
                .updateStep("summary", TaskStepStatus.Completed, "已停止本轮任务"),
        )
        currentConversationId?.let { convId ->
            viewModelScope.launch {
                syncManager.saveMessage(convId, role = "assistant", content = content)
            }
        }
    }

    fun selectChatModel(modelId: String) {
        val currentConfig = apiConfig.value
        val model = AiProviderCatalog.getModel(currentConfig.providerId, modelId)
        viewModelScope.launch {
            apiConfigDataStore.updateConfig(
                currentConfig.copy(
                    model = modelId,
                    toolsEnabled = model?.supportsTools ?: currentConfig.toolsEnabled,
                ),
            )
        }
    }

    private fun recordUsage(usage: TokenUsage) {
        if (usage.isEmpty) return
        val current = _uiState.value
        _uiState.value = current.copy(
            lastUsage = usage,
            conversationUsage = current.conversationUsage + usage,
            lastUsageMissing = false,
        )
    }

    private suspend fun executeApprovedToolBatch(batch: PendingToolBatch) {
        var usageSeen = false
        var modelAnswered = false
        val toolMessages = mutableListOf<ChatMessage>()
        val toolArtifacts = mutableListOf<ArtifactMetadata>()

        for (call in batch.calls) {
            _uiState.value = _uiState.value.copy(
                toolCallStatus = "正在执行 ${call.function.name} 工具...",
                taskSteps = _uiState.value.taskSteps
                    .updateStep("execute", TaskStepStatus.Running, "正在执行 ${call.function.name}"),
            )

            val args = try {
                Json.parseToJsonElement(call.function.arguments)
                    .jsonObject
                    .mapValues { it.value.jsonPrimitive.content }
            } catch (_: Exception) {
                emptyMap()
            }
            val result = toolExecutor.execute(call.function.name, args)
            if (call.function.name == "file_write" && result.success) {
                artifactStore.metadataForExistingFile(result.artifactPath())?.let(toolArtifacts::add)
            }
            val resultStr = if (result.success) {
                result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            } else {
                "执行失败: ${result.error}"
            }
            toolMessages.add(
                ChatMessage(
                    role = "tool",
                    content = resultStr,
                    tool_call_id = call.id,
                    name = call.function.name,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }

        val nextMessages = _uiState.value.messages + batch.assistantMessage + toolMessages
        _uiState.value = _uiState.value.copy(
            messages = nextMessages,
            toolCallStatus = null,
            taskSteps = _uiState.value.taskSteps
                .updateStep("execute", TaskStepStatus.Completed, "工具调用完成")
                .updateStep("review", TaskStepStatus.Running, "正在检查工具返回结果")
                .updateStep("summary", TaskStepStatus.Running, "正在生成最终回复"),
        )

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
                    modelAnswered = true
                    _uiState.value = _uiState.value.copy(
                        streamingContent = _uiState.value.streamingContent + streamResponse.text,
                    )
                }
                is ChatResponse.UsageReceived -> {
                    usageSeen = true
                    recordUsage(streamResponse.usage)
                }
                is ChatResponse.Error -> {
                    _uiState.value = _uiState.value.copy(
                        streamingContent = _uiState.value.streamingContent +
                            "\n\n${formatGuidedError(streamResponse.message)}",
                        taskSteps = _uiState.value.taskSteps
                            .updateStep("summary", TaskStepStatus.Failed, streamResponse.message),
                    )
                }
                else -> {}
            }
        }

        if (_uiState.value.streamingContent.isNotEmpty()) {
            val finalContent = prepareAssistantContent(_uiState.value.streamingContent, toolArtifacts)
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = finalContent,
                timestamp = System.currentTimeMillis(),
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage,
                isStreaming = false,
                streamingContent = "",
                requestStartedAt = null,
                lastProcessingMs = System.currentTimeMillis() - batch.startedAt,
                lastUsageMissing = modelAnswered && !usageSeen,
                taskSteps = _uiState.value.taskSteps
                    .updateStep("review", TaskStepStatus.Completed, "已检查执行结果")
                    .updateStep("summary", TaskStepStatus.Completed, "已生成最终回复"),
            )
            currentConversationId?.let { convId ->
                syncManager.saveMessage(convId, role = "assistant", content = finalContent)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                toolCallStatus = null,
                requestStartedAt = null,
                lastProcessingMs = System.currentTimeMillis() - batch.startedAt,
                lastUsageMissing = modelAnswered && !usageSeen,
            )
        }
    }

    private fun formatGuidedError(message: String): String {
        return if (message.contains("API Key", ignoreCase = true)) {
            """
            引导：请先进入左侧菜单的设置，选择服务商并配置 API Key；如果使用自定义中转站，也要确认 API 地址和模型名称。
            最终总结：当前请求还没有发送给模型，所以 Mason 暂时无法继续处理。
            """.trimIndent()
        } else {
            """
            引导：可以先检查网络、API 地址、模型名称和服务商额度；如果刚切换过模型，建议在设置里测试连接。
            最终总结：请求失败：$message
            """.trimIndent()
        }
    }

    private suspend fun tryLocalFallback(remoteError: String, messages: List<ChatMessage>): String? {
        val config = apiConfig.value
        if (!config.offlineFallbackEnabled) return null
        val localModelId = config.localModel.ifBlank {
            LocalModelCatalog.gemmaModels.firstOrNull()?.id.orEmpty()
        }
        val localModel = LocalModelCatalog.get(localModelId) ?: return null
        val state = localModelStore.stateFor(localModel)
        val unavailableStatus = when (state.state) {
            LocalModelInstallState.Installed,
            LocalModelInstallState.DeviceMayBeUnsupported -> null
            LocalModelInstallState.FileMissing -> {
                "本地模型文件异常，请到设置里的“本地模型”重新导入 ${localModel.name}。"
            }
            LocalModelInstallState.NotInstalled -> {
                "本地模型还没安装，请到设置里的“本地模型”导入 ${localModel.name} 的 LiteRT-LM 文件。"
            }
        }
        if (unavailableStatus == null) {
            var localText: String? = null
            var localError: String? = null
            liteRtModelEngine.invoke(
                ModelInvocation(
                    modality = ModelModality.Text,
                    messages = messages,
                    modelId = localModel.id,
                ),
            ).collect { response ->
                when (response) {
                    is ChatResponse.TextChunk -> {
                        localText = (localText.orEmpty() + response.text).trim()
                    }
                    is ChatResponse.Error -> {
                        localError = response.message
                    }
                    else -> {}
                }
            }
            localText?.takeIf { it.isNotBlank() }?.let { text ->
                return """
                    进行中：远程模型失败，已切换到本地 ${localModel.name}。
                    最终总结：$text
                """.trimIndent()
            }
            localError?.let { error ->
                return """
                    引导：远程模型调用失败，Mason 已尝试本地 ${localModel.name} 兜底。
                    最终总结：本地模型也未能完成推理：$error

                    远程失败原因：$remoteError
                """.trimIndent()
            }
        }
        return """
            引导：远程模型调用失败，Mason 已检查离线兜底配置。
            进行中：已选择 ${localModel.name} 作为本地兜底候选。
            最终总结：$unavailableStatus

            远程失败原因：$remoteError
        """.trimIndent()
    }

    private suspend fun prepareAssistantContent(
        content: String,
        existingArtifacts: List<ArtifactMetadata> = emptyList(),
    ): String {
        return artifactStore.saveArtifactsAndAnnotate(
            content = content,
            existingArtifacts = existingArtifacts,
        ).content
    }

    private fun ToolResult.artifactPath(): String? =
        data["path"] ?: data["file"] ?: data["absolute_path"] ?: data["target_path"]

    private suspend fun notifyTaskCompletedIfNeeded() {
        val preferences = uiPreferencesDataStore.preferences.first()
        if (!preferences.notificationIslandEnabled || !preferences.notifyOnTaskComplete) return

        notificationTool.execute(
            mapOf(
                "title" to "Mason 已完成",
                "text" to "对话事务已处理完成",
            ),
        )
    }
}
