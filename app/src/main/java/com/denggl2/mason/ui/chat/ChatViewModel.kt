package com.denggl2.mason.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.TaskStepFactory
import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.annotateTaskRun
import com.denggl2.mason.agent.extractTaskRunMarker
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.agent.taskStepId
import com.denggl2.mason.agent.updateStep
import com.denggl2.mason.agent.withSteps
import com.denggl2.mason.agent.withToolSteps
import com.denggl2.mason.agent.AgentRuntime
import com.denggl2.mason.automation.AutomationDraft
import com.denggl2.mason.automation.AutomationDraftService
import com.denggl2.mason.automation.AutomationDraftService.Companion.toMarker
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.InstalledSkill
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.TokenUsage
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.data.UserMemoryStore
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.model.MasonModelRouter
import com.denggl2.mason.agent.GovernedToolExecutor
import com.denggl2.mason.agent.ToolExecutionContext
import com.denggl2.mason.agent.ToolExecutionSource
import com.denggl2.mason.agent.ToolGrantStore
import android.util.Base64
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val toolExecutor: GovernedToolExecutor,
    private val syncManager: SyncManager,
    private val notificationTool: NotificationTool,
    private val uiPreferencesDataStore: UiPreferencesDataStore,
    private val apiConfigDataStore: ApiConfigDataStore,
    private val artifactStore: ArtifactStore,
    private val localModelStore: LocalModelStore,
    private val skillStore: SkillAutomationStore,
    private val automationDraftService: AutomationDraftService,
    private val modelRouter: MasonModelRouter,
    private val agentRuntime: AgentRuntime,
    private val toolGrantStore: ToolGrantStore,
    private val toolRegistry: ToolRegistry,
    private val userMemoryStore: UserMemoryStore,
) : ViewModel() {

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
    private var resumedTaskRun: TaskRun? = null

    init {
        refreshInstalledSkills()
        viewModelScope.launch {
            uiState.map { it.taskSteps }
                .distinctUntilChanged()
                .collect { steps ->
                    val current = activeTaskRun ?: return@collect
                    val snapshot = current.copy(conversationId = currentConversationId).withSteps(steps)
                    activeTaskRun = snapshot
                    agentRuntime.persist(snapshot)
                }
        }
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
            val recoverableTaskRun = agentRuntime.recover(convId)
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
                val taskRun = if (taskInFlight) activeTaskRun else recoverableTaskRun ?: restoredTaskRun
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
        val taskRun = resumedTaskRun?.also { resumedTaskRun = null }
            ?: agentRuntime.begin(content, currentConversationId).copy(createdAt = startedAt, updatedAt = startedAt)
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
                activeTaskRun = activeTaskRun?.copy(conversationId = currentConversationId)
                activeTaskRun?.let { agentRuntime.persist(it) }
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

            userMemoryStore.rememberExplicitStatement(content)?.let { memory ->
                _uiState.value = _uiState.value.copy(
                    taskSteps = _uiState.value.taskSteps.updateStep(
                        "plan",
                        TaskStepStatus.Completed,
                        "已按明确指令保存本机记忆：${memory.label}",
                    ),
                )
            }

            if (AutomationDraftService.looksLikeAutomationRequest(content)) {
                createAutomationDraft(content, startedAt)
                return@launchGeneration
            }

            val producedArtifacts = mutableListOf<ArtifactMetadata>()
            _uiState.value = _uiState.value.copy(
                taskSteps = _uiState.value.taskSteps
                    .updateStep("plan", TaskStepStatus.Completed, "已识别目标和所需能力")
                    .updateStep("prepare-inputs", TaskStepStatus.Completed, "已整理本轮输入材料")
                    .updateStep("execute", TaskStepStatus.Running, "正在等待模型生成方案"),
            )
            val routedResponse = modelRouter.route(
                messages = _uiState.value.messages,
                toolsEnabled = apiConfig.value.toolsEnabled,
            )
            val directLocalEnabled = routedResponse.decision.engineId == "litert-lm"
            val responseFlow = if (directLocalEnabled) {
                localCapabilityGuidance(content)?.let { guidance ->
                    flowOf(ChatResponse.TextChunk(guidance))
                } ?: routedResponse.responses
            } else {
                routedResponse.responses
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
                            .firstOrNull {
                                ToolPolicy.requiresUserApproval(it.function.name) &&
                                    (!ToolPolicy.canRememberApproval(it.function.name) ||
                                        !toolGrantStore.isAlwaysAllowed(it.function.name))
                            }
                            ?.let { call ->
                                val tool = toolRegistry.get(call.function.name)
                                ToolApprovalRequest(
                                    toolName = call.function.name,
                                    displayName = tool?.displayName ?: call.function.name,
                                    actionSummary = tool?.approvalDescription.orEmpty(),
                                    integrationProtocol = when {
                                        call.function.name.startsWith("a2a__") -> "A2A"
                                        call.function.name.startsWith("mcp__") -> "MCP"
                                        else -> null
                                    },
                                    allowPersistentGrant = ToolPolicy.canRememberApproval(call.function.name),
                                    riskLevel = ToolPolicy.riskFor(call.function.name),
                                    reason = ToolPolicy.approvalReason(call.function.name),
                                    call = call,
                                )
                            }

                        if (approval != null &&
                            (apiConfig.value.requireToolConfirmation ||
                                ToolPolicy.requiresMandatoryApproval(approval.toolName))
                        ) {
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
                            val result = toolExecutor.execute(
                                call.function.name,
                                args,
                                ToolExecutionContext(
                                    source = ToolExecutionSource.Chat,
                                    taskRunId = activeTaskRun?.id,
                                    userConfirmed = true,
                                ),
                            )
                            mergeExternalTaskState()
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
                            result.artifactPaths().mapNotNull(artifactStore::metadataForExistingFile)
                                .forEach(producedArtifacts::add)
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

                        modelRouter.route(nextMessages, toolsEnabled = true).responses.collect { streamResponse ->
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

                    is ChatResponse.ImageGenerated -> {
                        modelAnswered = true
                        if (response.isBase64) {
                            val artifact = artifactStore.saveBinaryArtifact(
                                fileName = "generated-image-${System.currentTimeMillis()}.png",
                                bytes = Base64.decode(response.data, Base64.DEFAULT),
                                mimeType = response.mimeType,
                            )
                            producedArtifacts += artifact
                            _uiState.value = _uiState.value.copy(
                                streamingContent = "最终总结：图片已生成并保存到产出中心。",
                                taskSteps = _uiState.value.taskSteps
                                    .updateStep("execute", TaskStepStatus.Completed, "生图模型已返回图片")
                                    .updateStep("review", TaskStepStatus.Completed, "已校验图片产出")
                                    .updateStep("summary", TaskStepStatus.Completed, "已保存图片"),
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                streamingContent = "最终总结：图片已生成：${response.data}",
                            )
                        }
                    }

                    is ChatResponse.Error -> {
                        val guidedContent = if (directLocalEnabled) {
                            "本地模型调用失败：${response.message}"
                        } else {
                            formatGuidedError(response.message)
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

    fun applyAutomationDraft(draft: AutomationDraft, runTest: Boolean) {
        if (generationJob?.isActive == true || _uiState.value.isStreaming) return
        val startedAt = System.currentTimeMillis()
        val taskRun = agentRuntime.begin("创建自动化：${draft.name}", currentConversationId)
            .copy(createdAt = startedAt, updatedAt = startedAt)
        activeTaskRun = taskRun
        _uiState.value = _uiState.value.copy(
            isStreaming = true,
            requestStartedAt = startedAt,
            taskRun = taskRun,
            taskSteps = taskRun.steps
                .updateStep("plan", TaskStepStatus.Completed, "已读取并校验自动化草稿")
                .updateStep("prepare-inputs", TaskStepStatus.Completed, "已准备触发条件和执行动作")
                .updateStep("execute", TaskStepStatus.Running, if (runTest) "正在创建并测试" else "正在创建"),
        )
        launchGeneration {
            val outcome = runCatching { automationDraftService.applyDraft(draft, runTest) }
            val result = outcome.getOrNull()
            val successful = result?.status == "success"
            val finalSteps = completeOpenTaskSteps(
                _uiState.value.taskSteps
                    .updateStep(
                        "execute",
                        if (result != null) TaskStepStatus.Completed else TaskStepStatus.Failed,
                        result?.message ?: outcome.exceptionOrNull()?.message ?: "自动化创建失败",
                    )
                    .updateStep(
                        "review",
                        if (successful) TaskStepStatus.Completed else TaskStepStatus.Failed,
                        when {
                            result == null -> "没有写入自动化"
                            successful -> "已检查保存、测试和调度状态"
                            else -> "自动化已保存，但测试未通过"
                        },
                    )
                    .updateStep(
                        "summary",
                        TaskStepStatus.Completed,
                        result?.message ?: "已返回失败原因",
                    ),
            )
            val messages = mutableListOf<ChatMessage>()
            if (result != null) {
                val markerContent = annotateCurrentTaskRun(result.toMarker(), finalSteps)
                messages += ChatMessage(
                    role = "tool",
                    name = AutomationDraftService.APPLY_TOOL_NAME,
                    content = markerContent,
                    timestamp = System.currentTimeMillis(),
                )
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(
                        convId,
                        role = "tool",
                        content = markerContent,
                        toolCallName = AutomationDraftService.APPLY_TOOL_NAME,
                    )
                }
                val artifact = artifactStore.metadataForExistingFile(result.artifactPath)
                if (artifact != null) {
                    val assistantContent = prepareAssistantContent(
                        content = "最终总结：测试产出已保存，可以直接预览、编辑或分享。",
                        existingArtifacts = listOf(artifact),
                        taskSteps = finalSteps,
                    )
                    messages += ChatMessage(
                        role = "assistant",
                        content = assistantContent,
                        timestamp = System.currentTimeMillis(),
                    )
                    currentConversationId?.let { convId ->
                        syncManager.saveMessage(convId, role = "assistant", content = assistantContent)
                    }
                }
            } else {
                val errorContent = annotateCurrentTaskRun(
                    "最终总结：自动化创建失败：${outcome.exceptionOrNull()?.message ?: "未知错误"}",
                    finalSteps,
                )
                messages += ChatMessage(
                    role = "assistant",
                    content = errorContent,
                    timestamp = System.currentTimeMillis(),
                )
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(convId, role = "assistant", content = errorContent)
                }
            }
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + messages,
                isStreaming = false,
                requestStartedAt = null,
                lastProcessingMs = System.currentTimeMillis() - startedAt,
                taskSteps = finalSteps,
                taskRun = activeTaskRun,
            )
            notifyTaskCompletedIfNeeded()
        }
    }

    private suspend fun createAutomationDraft(content: String, startedAt: Long) {
        _uiState.value = _uiState.value.copy(
            taskSteps = _uiState.value.taskSteps
                .updateStep("plan", TaskStepStatus.Completed, "已识别为自动化创建请求")
                .updateStep("prepare-inputs", TaskStepStatus.Completed, "已提取触发条件和执行目标")
                .updateStep("execute", TaskStepStatus.Running, "正在让模型生成结构化草稿"),
        )
        val outcome = runCatching { automationDraftService.draftFromNaturalLanguage(content) }
        val draftResult = outcome.getOrNull()
        val draft = draftResult?.draft
        val finalSteps = completeOpenTaskSteps(
            _uiState.value.taskSteps
                .updateStep(
                    "execute",
                    if (draftResult != null) TaskStepStatus.Completed else TaskStepStatus.Failed,
                    when {
                        draft != null -> "已生成自动化草稿"
                        draftResult?.clarificationQuestion != null -> "需要用户补充信息"
                        else -> outcome.exceptionOrNull()?.message ?: "草稿生成失败"
                    },
                )
                .updateStep("review", TaskStepStatus.Completed, "已校验触发器、动作和执行条件")
                .updateStep("summary", TaskStepStatus.Completed, if (draft != null) "等待用户确认创建" else "已返回补充问题"),
        )
        val message = if (draft != null) {
            ChatMessage(
                role = "tool",
                name = AutomationDraftService.DRAFT_TOOL_NAME,
                content = annotateCurrentTaskRun(draft.toMarker(), finalSteps),
                timestamp = System.currentTimeMillis(),
            )
        } else {
            val explanation = draftResult?.clarificationQuestion
                ?: "自动化草稿生成失败：${outcome.exceptionOrNull()?.message ?: "未知错误"}"
            ChatMessage(
                role = "assistant",
                content = annotateCurrentTaskRun("引导：$explanation", finalSteps),
                timestamp = System.currentTimeMillis(),
            )
        }
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message,
            isStreaming = false,
            requestStartedAt = null,
            lastProcessingMs = System.currentTimeMillis() - startedAt,
            taskSteps = finalSteps,
            taskRun = activeTaskRun,
        )
        currentConversationId?.let { convId ->
            syncManager.saveMessage(
                convId,
                role = message.role,
                content = message.content,
                toolCallName = message.name,
            )
        }
        notifyTaskCompletedIfNeeded()
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
        if (_uiState.value.taskSteps.any { it.id == stepId && it.status == TaskStepStatus.WaitingForUser }) {
            resumeCurrentTask()
            return
        }
        val step = _uiState.value.taskSteps.firstOrNull { it.id == stepId && it.retryable } ?: return
        val call = step.toolCall ?: return
        if ((apiConfig.value.requireToolConfirmation || ToolPolicy.requiresMandatoryApproval(call.function.name)) &&
            ToolPolicy.requiresUserApproval(call.function.name)
        ) {
            val tool = toolRegistry.get(call.function.name)
            pendingRetryStepId = stepId
            _uiState.value = _uiState.value.copy(
                pendingToolApproval = ToolApprovalRequest(
                    toolName = call.function.name,
                    displayName = tool?.displayName ?: call.function.name,
                    actionSummary = tool?.approvalDescription.orEmpty(),
                    integrationProtocol = when {
                        call.function.name.startsWith("a2a__") -> "A2A"
                        call.function.name.startsWith("mcp__") -> "MCP"
                        else -> null
                    },
                    allowPersistentGrant = ToolPolicy.canRememberApproval(call.function.name),
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

    fun pauseCurrentTask() {
        val run = activeTaskRun ?: return
        if (generationJob?.isActive != true) return
        val paused = agentRuntime.pause(run.withSteps(_uiState.value.taskSteps))
        activeTaskRun = paused
        generationJob?.cancel(CancellationException("用户暂停任务"))
        viewModelScope.launch { modelRouter.cancelActive() }
        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            requestStartedAt = null,
            taskRun = paused,
            taskSteps = paused.steps,
        )
    }

    fun resumeCurrentTask() {
        if (_uiState.value.isStreaming) return
        val run = activeTaskRun ?: return
        if (run.status != com.denggl2.mason.agent.TaskRunStatus.WaitingForUser) return
        val resumed = agentRuntime.resume(run).copy(
            steps = TaskStepFactory.initial(run.goal),
            finishedAt = null,
        )
        activeTaskRun = resumed
        resumedTaskRun = resumed
        sendMessage(run.goal)
    }

    fun stopGeneration() {
        val job = generationJob?.takeIf { it.isActive } ?: return
        markGenerationStopped()
        viewModelScope.launch {
            modelRouter.cancelActive()
            job.cancel(CancellationException("用户停止生成"))
        }
    }

    fun onAppBackgrounded() {
        if (generationJob?.isActive == true) {
            stopGeneration()
        } else {
            viewModelScope.launch { modelRouter.releaseLocal() }
        }
    }

    fun approvePendingToolCall(alwaysAllow: Boolean = false) {
        val approval = _uiState.value.pendingToolApproval
        if (alwaysAllow && approval?.allowPersistentGrant == true) {
            toolGrantStore.allowAlways(approval.toolName)
        }
        val retryStepId = pendingRetryStepId
        if (retryStepId != null) {
            val call = _uiState.value.pendingToolApproval?.call ?: return
            pendingRetryStepId = null
            _uiState.value = _uiState.value.copy(pendingToolApproval = null)
            launchToolStepRetry(retryStepId, call)
            return
        }
        val batch = pendingToolBatch ?: return
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
            if (!enabled && generationJob?.isActive == true) stopGeneration()
            apiConfigDataStore.updateConfig(
                currentConfig.copy(
                    localModel = selectedLocalModelId(currentConfig),
                    localModelDirectEnabled = enabled,
                ),
            )
            if (!enabled) modelRouter.releaseLocal()
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
            val result = toolExecutor.execute(
                call.function.name,
                args,
                ToolExecutionContext(
                    source = ToolExecutionSource.Chat,
                    taskRunId = activeTaskRun?.id,
                    userConfirmed = true,
                ),
            )
            mergeExternalTaskState()
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
            val artifacts = buildList {
                if (call.function.name == "file_write" && result.success) {
                    artifactStore.metadataForExistingFile(result.artifactPath())?.let(::add)
                }
                result.artifactPaths().mapNotNull(artifactStore::metadataForExistingFile).forEach(::add)
            }.distinctBy { it.path }
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
            val result = toolExecutor.execute(
                call.function.name,
                args,
                ToolExecutionContext(
                    source = ToolExecutionSource.Chat,
                    taskRunId = activeTaskRun?.id,
                    userConfirmed = true,
                ),
            )
            mergeExternalTaskState()
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
            result.artifactPaths().mapNotNull(artifactStore::metadataForExistingFile)
                .forEach(toolArtifacts::add)
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

        modelRouter.route(nextMessages, toolsEnabled = true).responses.collect { streamResponse ->
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
                    modelRouter.releaseLocal()
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
        val artifactResult = artifactStore.saveArtifactsAndAnnotate(
            content = content.finalAnswerOnly(),
            existingArtifacts = existingArtifacts,
        )
        activeTaskRun = activeTaskRun?.copy(
            summary = stripArtifactMarkers(artifactResult.content).take(1_000),
            artifactPaths = (activeTaskRun?.artifactPaths.orEmpty() + artifactResult.artifacts.map(ArtifactMetadata::path))
                .distinct(),
            lastError = taskSteps.firstOrNull { it.status == TaskStepStatus.Failed }?.error,
        )
        return annotateCurrentTaskRun(artifactResult.content, taskSteps)
    }

    private fun String.finalAnswerOnly(): String {
        val labels = setOf("思考", "进行中", "引导", "最终总结")
        if (lines().none { line -> labels.any { label -> line.trim().startsWith(label) } }) return this
        val kept = mutableListOf<String>()
        var include = false
        lines().forEach { raw ->
            val line = raw.trim()
            val label = labels.firstOrNull { candidate ->
                line == candidate || line.startsWith("$candidate：") || line.startsWith("$candidate:")
            }
            if (label != null) {
                include = label == "引导" || label == "最终总结"
                if (include) {
                    kept += line.removePrefix(label).trimStart('：', ':', ' ')
                }
            } else if (include) {
                kept += raw
            }
        }
        return kept.joinToString("\n").trim().ifBlank { this }
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

    private fun ToolResult.artifactPaths(): List<String> = data["artifactPaths"]
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.toList()
        .orEmpty()

    private suspend fun mergeExternalTaskState() {
        val current = activeTaskRun ?: return
        val persisted = agentRuntime.get(current.id) ?: return
        val externalSteps = persisted.steps.filter { it.id.startsWith("a2a:") }
        if (externalSteps.isEmpty()) return
        val externalIds = externalSteps.map(TaskStep::id).toSet()
        val localSteps = _uiState.value.taskSteps.filterNot { it.id in externalIds }.toMutableList()
        val insertionIndex = localSteps.indexOfFirst { it.kind == com.denggl2.mason.agent.TaskStepKind.Review }
            .takeIf { it >= 0 }
            ?: localSteps.size
        localSteps.addAll(insertionIndex, externalSteps)
        activeTaskRun = current.copy(
            artifactPaths = (current.artifactPaths + persisted.artifactPaths).distinct(),
            summary = persisted.summary ?: current.summary,
            lastError = persisted.lastError ?: current.lastError,
        ).withSteps(localSteps)
        _uiState.value = _uiState.value.copy(taskSteps = localSteps, taskRun = activeTaskRun)
    }

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
