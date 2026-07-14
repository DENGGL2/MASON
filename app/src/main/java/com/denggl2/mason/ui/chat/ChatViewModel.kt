package com.denggl2.mason.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.annotateTaskRun
import com.denggl2.mason.agent.createTaskRun
import com.denggl2.mason.agent.extractTaskRunMarker
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.agent.taskStepId
import com.denggl2.mason.agent.updateStep
import com.denggl2.mason.agent.withSteps
import com.denggl2.mason.agent.withToolSteps
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.InstalledSkill
import com.denggl2.mason.data.SkillAutomationStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val taskRun: TaskRun? = null,
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
    private val skillStore: SkillAutomationStore,
) : ViewModel() {

    private companion object {
        const val LOCAL_INFERENCE_TIMEOUT_MS = 120_000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val apiConfig: StateFlow<ApiConfig> = apiConfigDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())
    private val _installedSkills = MutableStateFlow<List<InstalledSkill>>(emptyList())
    val installedSkills: StateFlow<List<InstalledSkill>> = _installedSkills.asStateFlow()

    private var currentConversationId: Long? = savedStateHandle.get<Long>("conversationId")?.takeIf { it > 0L }
    private var isFirstMessage = currentConversationId == null
    private var pendingToolBatch: PendingToolBatch? = null
    private var pendingRetryStepId: String? = null
    private var activeTaskRun: TaskRun? = null
    private var generationJob: Job? = null
    @Volatile private var localInferenceActive = false

    init {
        refreshInstalledSkills()
        currentConversationId?.let { convId ->
            _uiState.value = _uiState.value.copy(conversationId = convId)
            loadHistory(convId)
        }
    }

    fun refreshInstalledSkills() {
        viewModelScope.launch {
            _installedSkills.value = skillStore.listInstalledSkills(enabledOnly = true)
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
                val restoredTaskRun = messages.asReversed()
                    .firstNotNullOfOrNull { message -> extractTaskRunMarker(message.content.orEmpty()) }
                val chatMessages = messages.map { msg ->
                    ChatMessage(
                        role = msg.role,
                        content = msg.content?.let(::stripTaskRunMarkers),
                        tool_call_id = msg.toolCallId,
                        name = msg.toolCallName,
                        timestamp = msg.timestamp,
                    )
                }
                val taskInFlight = generationJob?.isActive == true ||
                    _uiState.value.isStreaming ||
                    _uiState.value.pendingToolApproval != null
                val taskRun = if (taskInFlight) activeTaskRun else restoredTaskRun
                activeTaskRun = taskRun
                _uiState.value = _uiState.value.copy(
                    messages = chatMessages,
                    taskRun = taskRun,
                    taskSteps = taskRun?.steps.orEmpty(),
                )
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
        if (content.isBlank() || generationJob?.isActive == true) return

        val startedAt = System.currentTimeMillis()
        val taskRun = createTaskRun(content, startedAt)
        activeTaskRun = taskRun
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
            taskSteps = taskRun.steps,
            taskRun = taskRun,
            pendingToolApproval = null,
        )

        launchGeneration {
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
            _uiState.value = _uiState.value.copy(
                taskSteps = _uiState.value.taskSteps
                    .updateStep("plan", TaskStepStatus.Completed, "已识别目标和所需能力")
                    .updateStep("prepare-inputs", TaskStepStatus.Completed, "已整理本轮输入材料")
                    .updateStep("execute", TaskStepStatus.Running, "正在等待模型生成方案"),
            )
            val directLocalEnabled = apiConfig.value.localModelDirectEnabled
            val responseFlow = if (directLocalEnabled) {
                localCapabilityGuidance(content)?.let { guidance ->
                    flowOf(ChatResponse.TextChunk(guidance))
                } ?: invokeLocalModel(
                    messages = _uiState.value.messages,
                    modelId = selectedLocalModelId(apiConfig.value),
                ).asFlow()
            } else {
                chatClient.chat(_uiState.value.messages)
            }
            responseFlow.collect { response ->
                when (response) {
                    is ChatResponse.ToolCallsRequested -> {
                        _uiState.value = _uiState.value.copy(
                            taskSteps = _uiState.value.taskSteps
                                .updateStep("plan", TaskStepStatus.Completed, "已判断需要调用手机能力")
                                .updateStep("prepare-inputs", TaskStepStatus.Completed, "已整理本轮输入材料")
                                .updateStep("execute", TaskStepStatus.Completed, "模型已生成 ${response.calls.size} 个工具调用")
                                .withToolSteps(response.calls),
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
                                    .updateStep(approval.call.taskStepId(), TaskStepStatus.WaitingForUser, "等待用户确认 ${approval.toolName}")
                                    .updateStep("summary", TaskStepStatus.Pending, "确认后继续生成结果"),
                            )
                            return@collect
                        }

                        val toolMessages = mutableListOf<ChatMessage>()
                        for (call in response.calls) {
                            _uiState.value = _uiState.value.copy(
                                toolCallStatus = "正在执行 ${call.function.name} 工具...",
                                taskSteps = _uiState.value.taskSteps.updateStep(
                                    call.taskStepId(),
                                    TaskStepStatus.Running,
                                    "正在执行 ${call.function.name}",
                                ),
                            )

                            val args = try {
                                Json.parseToJsonElement(call.function.arguments)
                                    .jsonObject
                                    .mapValues { it.value.jsonPrimitive.content }
                            } catch (_: Exception) {
                                emptyMap()
                            }
                            val result = toolExecutor.execute(call.function.name, args)
                            _uiState.value = _uiState.value.copy(
                                taskSteps = _uiState.value.taskSteps.updateStep(
                                    call.taskStepId(),
                                    if (result.success) TaskStepStatus.Completed else TaskStepStatus.Failed,
                                    if (result.success) "${call.function.name} 执行完成" else result.error ?: "工具执行失败",
                                ),
                            )
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
                        val finalSteps = completeOpenTaskSteps(
                            _uiState.value.taskSteps
                                .updateStep("execute", TaskStepStatus.Completed, "模型已生成回答")
                                .updateStep("review", TaskStepStatus.Completed, "已检查回答完整性")
                                .updateStep("summary", TaskStepStatus.Completed, "已生成回复"),
                        )
                        val finalContent = prepareAssistantContent(response.text, taskSteps = finalSteps)
                        val assistantMessage = ChatMessage(role = "assistant", content = finalContent, timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                            lastProcessingMs = System.currentTimeMillis() - startedAt,
                            taskSteps = finalSteps,
                            taskRun = activeTaskRun,
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
                        val guidedContent = if (directLocalEnabled) {
                            "本地模型调用失败：${response.message}"
                        } else {
                            tryLocalFallback(response.message, _uiState.value.messages)
                                ?: formatGuidedError(response.message)
                        }
                        val failedSteps = _uiState.value.taskSteps
                            .updateStep("summary", TaskStepStatus.Failed, response.message)
                        val persistedError = annotateCurrentTaskRun(guidedContent, failedSteps)
                        val errorMessage = ChatMessage(role = "assistant", content = persistedError, timestamp = System.currentTimeMillis())
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + errorMessage,
                            lastProcessingMs = System.currentTimeMillis() - startedAt,
                            taskSteps = failedSteps,
                            taskRun = activeTaskRun,
                        )
                        currentConversationId?.let { convId ->
                            syncManager.saveMessage(convId, role = "assistant", content = persistedError)
                        }
                    }
                }
            }

            if (_uiState.value.pendingToolApproval != null) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    toolCallStatus = null,
                    requestStartedAt = null,
                    taskRun = activeTaskRun?.withSteps(_uiState.value.taskSteps),
                )
                return@launchGeneration
            }

            if (_uiState.value.streamingContent.isNotEmpty()) {
                modelAnswered = true
                val finalSteps = completeOpenTaskSteps(
                    completeTaskStepUnlessFailed(
                        _uiState.value.taskSteps.updateStep("review", TaskStepStatus.Completed, "已检查执行结果"),
                        "summary",
                        "已生成最终回复",
                    ),
                )
                val finalContent = prepareAssistantContent(
                    _uiState.value.streamingContent,
                    producedArtifacts,
                    finalSteps,
                )
                val assistantMessage = ChatMessage(role = "assistant", content = finalContent, timestamp = System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    isStreaming = false,
                    streamingContent = "",
                    requestStartedAt = null,
                    lastProcessingMs = System.currentTimeMillis() - startedAt,
                    lastUsageMissing = modelAnswered && !usageSeen,
                    taskSteps = finalSteps,
                    taskRun = activeTaskRun,
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

    fun retryTaskStep(stepId: String) {
        if (_uiState.value.isStreaming || _uiState.value.pendingToolApproval != null) return
        val step = _uiState.value.taskSteps.firstOrNull { it.id == stepId && it.retryable } ?: return
        val call = step.toolCall ?: return
        if (apiConfig.value.requireToolConfirmation && ToolPolicy.requiresUserApproval(call.function.name)) {
            pendingRetryStepId = stepId
            _uiState.value = _uiState.value.copy(
                pendingToolApproval = ToolApprovalRequest(
                    toolName = call.function.name,
                    riskLevel = ToolPolicy.riskFor(call.function.name),
                    reason = "这是失败步骤的重试。${ToolPolicy.approvalReason(call.function.name)}",
                    call = call,
                ),
                taskSteps = _uiState.value.taskSteps.updateStep(
                    stepId,
                    TaskStepStatus.WaitingForUser,
                    "等待确认后重试 ${call.function.name}",
                ),
            )
            return
        }
        launchToolStepRetry(stepId, call)
    }

    fun stopGeneration() {
        val job = generationJob?.takeIf { it.isActive } ?: return
        markGenerationStopped()
        viewModelScope.launch {
            liteRtModelEngine.cancelActiveInvocation()
            job.cancel(CancellationException("用户停止生成"))
        }
    }

    fun onAppBackgrounded() {
        if (localInferenceActive) {
            stopGeneration()
        } else {
            viewModelScope.launch { liteRtModelEngine.release() }
        }
    }

    fun approvePendingToolCall() {
        val retryStepId = pendingRetryStepId
        if (retryStepId != null) {
            val call = _uiState.value.pendingToolApproval?.call ?: return
            pendingRetryStepId = null
            _uiState.value = _uiState.value.copy(pendingToolApproval = null)
            launchToolStepRetry(retryStepId, call)
            return
        }
        val batch = pendingToolBatch ?: return
        val approval = _uiState.value.pendingToolApproval
        pendingToolBatch = null
        _uiState.value = _uiState.value.copy(
            pendingToolApproval = null,
            isStreaming = true,
            requestStartedAt = batch.startedAt,
            taskSteps = _uiState.value.taskSteps
                .let { steps ->
                    approval?.call?.let { call ->
                        steps.updateStep(call.taskStepId(), TaskStepStatus.Running, "用户已确认，正在执行工具")
                    } ?: steps
                }
                .updateStep("summary", TaskStepStatus.Pending, "工具完成后继续生成结果"),
        )
        launchGeneration {
            executeApprovedToolBatch(batch)
            notifyTaskCompletedIfNeeded()
        }
    }

    fun rejectPendingToolCall() {
        val approval = _uiState.value.pendingToolApproval ?: return
        val retryStepId = pendingRetryStepId
        if (retryStepId != null) {
            pendingRetryStepId = null
            _uiState.value = _uiState.value.copy(
                pendingToolApproval = null,
                taskSteps = _uiState.value.taskSteps.updateStep(
                    retryStepId,
                    TaskStepStatus.Failed,
                    "用户取消了这次重试",
                ),
            )
            return
        }
        pendingToolBatch = null
        val finalSteps = _uiState.value.taskSteps.map { step ->
            when {
                step.id == approval.call.taskStepId() -> step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "用户已拒绝 ${approval.toolName}",
                    finishedAt = System.currentTimeMillis(),
                )
                step.status == TaskStepStatus.Pending -> step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "前置操作已取消",
                    finishedAt = System.currentTimeMillis(),
                )
                else -> step
            }
        }.updateStep("summary", TaskStepStatus.Completed, "已停止本轮任务")
        val content = annotateCurrentTaskRun(
            "引导：已取消 ${approval.toolName} 工具调用。\n最终总结：这次操作没有继续执行，手机状态不会被更改。",
            finalSteps,
        )
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
            taskSteps = finalSteps,
            taskRun = activeTaskRun,
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
                    localModelDirectEnabled = false,
                    toolsEnabled = model?.supportsTools ?: currentConfig.toolsEnabled,
                ),
            )
        }
    }

    fun localModelStates(): List<LocalModelFileState> =
        localModelStore.states(LocalModelCatalog.gemmaModels)

    fun selectLocalModelDirect(enabled: Boolean) {
        val currentConfig = apiConfig.value
        viewModelScope.launch {
            if (!enabled && localInferenceActive) stopGeneration()
            apiConfigDataStore.updateConfig(
                currentConfig.copy(
                    localModel = selectedLocalModelId(currentConfig),
                    localModelDirectEnabled = enabled,
                ),
            )
            if (!enabled) liteRtModelEngine.release()
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

    private fun launchToolStepRetry(stepId: String, call: ToolCall) {
        val startedAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isStreaming = true,
            requestStartedAt = startedAt,
            taskSteps = _uiState.value.taskSteps.updateStep(
                stepId,
                TaskStepStatus.Running,
                "正在重试 ${call.function.name}",
            ),
        )
        launchGeneration {
            val args = try {
                Json.parseToJsonElement(call.function.arguments)
                    .jsonObject
                    .mapValues { it.value.jsonPrimitive.content }
            } catch (_: Exception) {
                emptyMap()
            }
            val result = toolExecutor.execute(call.function.name, args)
            val retriedSteps = _uiState.value.taskSteps.updateStep(
                stepId,
                if (result.success) TaskStepStatus.Completed else TaskStepStatus.Failed,
                if (result.success) "${call.function.name} 重试成功" else result.error ?: "工具重试失败",
            )
            val resultText = if (result.success) {
                result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "工具已执行完成" }
            } else {
                "执行失败：${result.error ?: "未知错误"}"
            }
            val artifacts = if (call.function.name == "file_write" && result.success) {
                listOfNotNull(artifactStore.metadataForExistingFile(result.artifactPath()))
            } else {
                emptyList()
            }
            val responseText = if (result.success) {
                "进行中：已单独重试 ${call.function.name}。\n最终总结：$resultText"
            } else {
                "引导：${call.function.name} 重试仍然失败。\n最终总结：$resultText"
            }
            val finalContent = prepareAssistantContent(responseText, artifacts, retriedSteps)
            val toolMessage = ChatMessage(
                role = "tool",
                content = resultText,
                tool_call_id = call.id,
                name = call.function.name,
                timestamp = System.currentTimeMillis(),
            )
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = finalContent,
                timestamp = System.currentTimeMillis(),
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + toolMessage + assistantMessage,
                isStreaming = false,
                requestStartedAt = null,
                lastProcessingMs = System.currentTimeMillis() - startedAt,
                taskSteps = retriedSteps,
                taskRun = activeTaskRun,
            )
            currentConversationId?.let { convId ->
                syncManager.saveMessage(
                    convId,
                    role = toolMessage.role,
                    content = toolMessage.content,
                    toolCallId = toolMessage.tool_call_id,
                    toolCallName = toolMessage.name,
                )
                syncManager.saveMessage(convId, role = "assistant", content = finalContent)
            }
            notifyTaskCompletedIfNeeded()
        }
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
                    .updateStep(call.taskStepId(), TaskStepStatus.Running, "正在执行 ${call.function.name}"),
            )

            val args = try {
                Json.parseToJsonElement(call.function.arguments)
                    .jsonObject
                    .mapValues { it.value.jsonPrimitive.content }
            } catch (_: Exception) {
                emptyMap()
            }
            val result = toolExecutor.execute(call.function.name, args)
            _uiState.value = _uiState.value.copy(
                taskSteps = _uiState.value.taskSteps.updateStep(
                    call.taskStepId(),
                    if (result.success) TaskStepStatus.Completed else TaskStepStatus.Failed,
                    if (result.success) "${call.function.name} 执行完成" else result.error ?: "工具执行失败",
                ),
            )
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
            val finalSteps = completeOpenTaskSteps(
                completeTaskStepUnlessFailed(
                    _uiState.value.taskSteps.updateStep("review", TaskStepStatus.Completed, "已检查执行结果"),
                    "summary",
                    "已生成最终回复",
                ),
            )
            val finalContent = prepareAssistantContent(
                _uiState.value.streamingContent,
                toolArtifacts,
                finalSteps,
            )
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
                taskSteps = finalSteps,
                taskRun = activeTaskRun,
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
            invokeLocalModel(messages, localModel.id).forEach { response ->
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

    private fun selectedLocalModelId(config: ApiConfig): String =
        config.localModel.ifBlank { LocalModelCatalog.gemmaModels.firstOrNull()?.id.orEmpty() }

    private fun localCapabilityGuidance(content: String): String? {
        if (content.contains("Mason 附加上下文")) {
            return "引导：本地 Gemma 目前只支持文字问答，不能读取图片、文件或调用 Skill。请切换到远程模型后重试。"
        }

        val normalized = content.lowercase()
        val actionWords = listOf(
            "帮我", "请帮", "打开", "关闭", "发送", "删除", "读取", "查看", "查询",
            "设置", "创建", "写入", "拨打", "拍照", "录音", "截图", "获取",
            "open", "close", "send", "delete", "read", "check", "set", "get",
        )
        val capabilityWords = listOf(
            "手机", "电量", "存储", "内存", "位置", "联系人", "短信", "电话", "闹钟",
            "日历", "应用", "系统设置", "wifi", "wi-fi", "蓝牙", "剪贴板", "相机",
            "phone", "battery", "storage", "location", "contacts", "sms", "alarm", "calendar",
            "bluetooth", "clipboard", "camera",
        )
        val explicitFileOperations = listOf("删除文件", "读取文件", "打开文件", "查看文件")
        if (
            (actionWords.any(normalized::contains) && capabilityWords.any(normalized::contains)) ||
            explicitFileOperations.any(normalized::contains)
        ) {
            return "引导：这个请求需要读取或操作手机能力，本地 Gemma 不能直接执行。请切换到远程模型，由 Mason 在确认权限后处理。"
        }
        return null
    }

    private suspend fun invokeLocalModel(
        messages: List<ChatMessage>,
        modelId: String,
    ): List<ChatResponse> {
        localInferenceActive = true
        return try {
            withTimeoutOrNull(LOCAL_INFERENCE_TIMEOUT_MS) {
                liteRtModelEngine.invoke(
                    ModelInvocation(
                        modality = ModelModality.Text,
                        messages = messages,
                        modelId = modelId,
                    ),
                ).toList()
            } ?: run {
                liteRtModelEngine.release()
                listOf(ChatResponse.Error("本地模型响应超过 120 秒，已停止并释放内存"))
            }
        } finally {
            localInferenceActive = false
        }
    }

    private fun launchGeneration(block: suspend CoroutineScope.() -> Unit) {
        if (generationJob?.isActive == true) return
        val job = viewModelScope.launch(block = block)
        generationJob = job
        job.invokeOnCompletion { cause ->
            viewModelScope.launch {
                if (generationJob !== job) return@launch
                generationJob = null
                if (cause is CancellationException) {
                    markGenerationStopped()
                    liteRtModelEngine.release()
                }
            }
        }
    }

    private fun markGenerationStopped() {
        if (!_uiState.value.isStreaming) return
        val stoppedSteps = _uiState.value.taskSteps.map { step ->
            if (step.status == TaskStepStatus.Running || step.status == TaskStepStatus.Pending) {
                step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "已停止生成",
                    finishedAt = System.currentTimeMillis(),
                )
            } else {
                step
            }
        }
        val stoppedContent = annotateCurrentTaskRun("最终总结：已停止生成。", stoppedSteps)
        val stoppedMessage = ChatMessage(
            role = "assistant",
            content = stoppedContent,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + stoppedMessage,
            isStreaming = false,
            streamingContent = "",
            toolCallStatus = null,
            requestStartedAt = null,
            taskSteps = stoppedSteps,
            taskRun = activeTaskRun,
        )
        currentConversationId?.let { convId ->
            viewModelScope.launch {
                syncManager.saveMessage(convId, role = "assistant", content = stoppedContent)
            }
        }
    }

    private suspend fun prepareAssistantContent(
        content: String,
        existingArtifacts: List<ArtifactMetadata> = emptyList(),
        taskSteps: List<TaskStep> = _uiState.value.taskSteps,
    ): String {
        val artifactContent = artifactStore.saveArtifactsAndAnnotate(
            content = content,
            existingArtifacts = existingArtifacts,
        ).content
        return annotateCurrentTaskRun(artifactContent, taskSteps)
    }

    private fun annotateCurrentTaskRun(content: String, steps: List<TaskStep>): String {
        val snapshot = activeTaskRun?.withSteps(steps)
        activeTaskRun = snapshot
        return annotateTaskRun(content, snapshot)
    }

    private fun completeOpenTaskSteps(steps: List<TaskStep>): List<TaskStep> = steps.map { step ->
        if (step.status == TaskStepStatus.Pending || step.status == TaskStepStatus.Running) {
            step.copy(
                status = TaskStepStatus.Completed,
                finishedAt = System.currentTimeMillis(),
            )
        } else {
            step
        }
    }

    private fun completeTaskStepUnlessFailed(
        steps: List<TaskStep>,
        stepId: String,
        detail: String,
    ): List<TaskStep> {
        val step = steps.firstOrNull { it.id == stepId }
        return if (step?.status == TaskStepStatus.Failed) {
            steps
        } else {
            steps.updateStep(stepId, TaskStepStatus.Completed, detail)
        }
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
