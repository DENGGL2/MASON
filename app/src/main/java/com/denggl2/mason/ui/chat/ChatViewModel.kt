package com.denggl2.mason.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.TaskStepFactory
import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.agent.TaskInterruptionReason
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.ToolRiskLevel
import com.denggl2.mason.agent.annotateTaskRun
import com.denggl2.mason.agent.extractTaskRunMarker
import com.denggl2.mason.agent.taskStepId
import com.denggl2.mason.agent.updateStep
import com.denggl2.mason.agent.withSteps
import com.denggl2.mason.agent.withToolSteps
import com.denggl2.mason.agent.AgentRuntime
import com.denggl2.mason.agent.AgentExecutionCheckpoint
import com.denggl2.mason.agent.canExecute
import com.denggl2.mason.agent.fingerprint
import com.denggl2.mason.automation.AutomationDraft
import com.denggl2.mason.automation.AutomationDraftService
import com.denggl2.mason.automation.AutomationDraftService.Companion.toMarker
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.resolvedChatModelRef
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.ModelCapabilityHealthSnapshot
import com.denggl2.mason.data.ModelCapabilityHealthStore
import com.denggl2.mason.data.ModelContribution
import com.denggl2.mason.data.annotateModelParticipation
import com.denggl2.mason.data.extractModelParticipation
import com.denggl2.mason.data.mergeModelContribution
import com.denggl2.mason.data.InstalledSkill
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.TokenUsage
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.data.UserMemoryStore
import com.denggl2.mason.data.directMemoryLookupAnswer
import com.denggl2.mason.integration.CapabilityRequirement
import com.denggl2.mason.integration.CapabilityRequirementResolver
import com.denggl2.mason.integration.extractCapabilityRequirementMarker
import com.denggl2.mason.integration.toMarker
import com.denggl2.mason.skill.SkillActivationTool
import com.denggl2.mason.skill.SkillRuntime
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.tool.MemoryWriteTool
import com.denggl2.mason.tool.SensitiveMemoryWriteTool
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.tool.ConversationDispatchTool
import com.denggl2.mason.model.MasonModelRouter
import com.denggl2.mason.agent.GovernedToolExecutor
import com.denggl2.mason.agent.ToolExecutionContext
import com.denggl2.mason.agent.ToolExecutionSource
import com.denggl2.mason.agent.ToolGrantStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
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
    val modelContributions: List<ModelContribution> = emptyList(),
)

private data class PendingToolBatch(
    val assistantMessage: ChatMessage,
    val calls: List<ToolCall>,
    val startedAt: Long,
    val checkpoint: AgentExecutionCheckpoint,
)

private data class AgentLoopResult(
    val usageSeen: Boolean = false,
    val modelAnswered: Boolean = false,
    val paused: Boolean = false,
)

private enum class TaskNotificationEvent {
    Started,
    Completed,
    Failed,
    Paused,
    Stopped,
    Cancelled,
}

private const val TASK_USER_CONTEXT_SEPARATOR = "\n---\nMason 附加上下文"

internal fun shouldRecordTaskGoal(isResumingTask: Boolean): Boolean = !isResumingTask

internal fun taskExecutionMessages(
    messages: List<ChatMessage>,
    goal: String,
    timestamp: Long,
): List<ChatMessage> {
    val latestUserGoal = messages
        .lastOrNull { it.role == "user" }
        ?.content
        .orEmpty()
        .substringBefore(TASK_USER_CONTEXT_SEPARATOR)
        .trim()
        .take(500)
    return if (latestUserGoal == goal) {
        messages
    } else {
        messages + ChatMessage(role = "user", content = goal, timestamp = timestamp)
    }
}

internal fun taskCompletionNotificationText(artifactPaths: List<String>): String {
    val artifactName = artifactPaths.lastOrNull()
        ?.let(::File)
        ?.name
        .orEmpty()
    return if (artifactName.isBlank()) "对话任务已处理完成" else "已生成：$artifactName"
}

internal fun canStartModelTask(
    config: ApiConfig,
    selectedLocalModelInstalled: Boolean,
): Boolean =
    config.configuredChatModelRef() != null ||
        (
            config.localModel.isNotBlank() &&
                selectedLocalModelInstalled &&
                (
                    config.configuredChatModelRef() == null ||
                        config.localModelDirectEnabled ||
                        config.dynamicLocalRoutingEnabled
                    )
            )

internal enum class AppBackgroundAction {
    PersistRunningTask,
    ReleaseLocalRuntime,
}

internal fun appBackgroundAction(
    generationActive: Boolean,
    hasActiveTask: Boolean,
): AppBackgroundAction = if (generationActive && hasActiveTask) {
    AppBackgroundAction.PersistRunningTask
} else {
    AppBackgroundAction.ReleaseLocalRuntime
}

internal fun buildLocalDeviceInfoToolCall(
    content: String,
    directLocalEnabled: Boolean,
    phoneToolsEnabled: Boolean,
    taskRunId: String,
): ToolCall? {
    if (!directLocalEnabled || !phoneToolsEnabled) return null

    val normalized = content.trim().lowercase()
    val chineseTarget = listOf(
        "设备信息",
        "手机信息",
        "本机信息",
        "设备参数",
        "手机参数",
        "设备型号",
        "手机型号",
    ).any(normalized::contains)
    val chineseAction = listOf(
        "检测",
        "查看",
        "获取",
        "读取",
        "查询",
        "检查",
        "显示",
        "告诉我",
        "是什么",
    ).any(normalized::contains)
    val englishIntent = Regex(
        pattern = """\b(check|detect|get|inspect|show)\b.*\b(device|phone)\b.*\b(info|information|details|model)\b""",
        option = RegexOption.IGNORE_CASE,
    ).containsMatchIn(normalized)
    if (!(chineseTarget && chineseAction) && !englishIntent) return null

    return ToolCall(
        id = "local-device-info-${taskRunId.ifBlank { "request" }}",
        function = FunctionCall(
            name = "get_device_info",
            arguments = "{}",
        ),
    )
}

internal fun formatLocalDeviceInfoResult(result: ToolResult): String {
    if (!result.success) {
        return "设备信息读取失败：${result.error ?: "未知错误"}"
    }

    val data = result.data
    val lines = buildList {
        val deviceName = listOf(data["manufacturer"], data["brand"], data["model"])
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" ")
        if (deviceName.isNotBlank()) add("设备：$deviceName")

        val android = data["android_version"].orEmpty()
        val sdk = data["sdk_level"].orEmpty()
        if (android.isNotBlank() || sdk.isNotBlank()) {
            add(
                when {
                    android.isNotBlank() && sdk.isNotBlank() -> "系统：Android $android（SDK $sdk）"
                    android.isNotBlank() -> "系统：Android $android"
                    else -> "系统：SDK $sdk"
                },
            )
        }
        data["security_patch"]?.takeIf(String::isNotBlank)?.let { add("安全补丁：$it") }
        data["locale"]?.takeIf(String::isNotBlank)?.let { add("系统语言：$it") }

        val display = listOf(data["resolution"], data["density_dpi"])
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString("，")
        if (display.isNotBlank()) add("屏幕：$display")

        val hardware = listOf(data["product"], data["device"], data["board"])
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" / ")
        if (hardware.isNotBlank()) add("硬件标识：$hardware")
        data["treble_support"]?.takeIf(String::isNotBlank)?.let { add("Treble：$it") }
    }

    return if (lines.isEmpty()) {
        "已读取本机设备信息，但没有返回可显示的数据。"
    } else {
        "已检测到本机设备信息：\n${lines.joinToString("\n") { "- $it" }}"
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val toolExecutor: GovernedToolExecutor,
    private val syncManager: SyncManager,
    private val notificationTool: NotificationTool,
    private val uiPreferencesDataStore: UiPreferencesDataStore,
    private val apiConfigDataStore: ApiConfigDataStore,
    private val modelCapabilityHealthStore: ModelCapabilityHealthStore,
    private val artifactStore: ArtifactStore,
    private val localModelStore: LocalModelStore,
    private val skillStore: SkillAutomationStore,
    private val automationDraftService: AutomationDraftService,
    private val modelRouter: MasonModelRouter,
    private val agentRuntime: AgentRuntime,
    private val taskRunStore: TaskRunStore,
    private val toolGrantStore: ToolGrantStore,
    private val toolRegistry: ToolRegistry,
    private val capabilityRequirementResolver: CapabilityRequirementResolver,
    private val userMemoryStore: UserMemoryStore,
    private val skillRuntime: SkillRuntime,
    private val skillActivationTool: SkillActivationTool,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val apiConfig: StateFlow<ApiConfig> = apiConfigDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())
    val modelCapabilityHealth: StateFlow<ModelCapabilityHealthSnapshot> = modelCapabilityHealthStore.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelCapabilityHealthSnapshot())

    fun hasCurrentModelCapabilityHealth(): Boolean =
        modelCapabilityHealthStore.isCurrent(apiConfig.value, modelCapabilityHealth.value)
    private val _installedSkills = MutableStateFlow<List<InstalledSkill>>(emptyList())
    val installedSkills: StateFlow<List<InstalledSkill>> = _installedSkills.asStateFlow()

    private var currentConversationId: Long? = savedStateHandle.get<Long>("conversationId")?.takeIf { it > 0L }
    private var isFirstMessage = currentConversationId == null
    private var pendingToolBatch: PendingToolBatch? = null
    private var pendingRetryStepId: String? = null
    private var activeTaskRun: TaskRun? = null
    private var generationJob: Job? = null
    private var interjectionJob: Job? = null
    private var latestTaskRecoveryJob: Job? = null
    private var historyLoadJob: Job? = null
    private var restoresLatestTask = true
    private var trackedActiveConversationId: Long? = null

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
        viewModelScope.launch {
            uiState.map { state -> state.conversationId to state.isStreaming }
                .distinctUntilChanged()
                .collect { (conversationId, isStreaming) ->
                    trackedActiveConversationId
                        ?.takeIf { !isStreaming || it != conversationId }
                        ?.let(taskRunStore::markConversationInactive)
                    trackedActiveConversationId = conversationId?.takeIf { isStreaming }
                    trackedActiveConversationId?.let(taskRunStore::markConversationActive)
                }
        }
        currentConversationId?.let { convId ->
            _uiState.value = _uiState.value.copy(conversationId = convId)
            loadHistory(convId)
        } ?: recoverLatestTaskRun()
    }

    fun refreshInstalledSkills() {
        viewModelScope.launch {
            runCatching { skillStore.listInstalledSkills(enabledOnly = true) }
                .onSuccess { _installedSkills.value = it }
            runCatching { skillRuntime.refresh() }
        }
    }

    fun startFreshConversation() {
        latestTaskRecoveryJob?.cancel()
        historyLoadJob?.cancel()
        restoresLatestTask = false
        currentConversationId = null
        isFirstMessage = true
        pendingToolBatch = null
        pendingRetryStepId = null
        activeTaskRun = null
        _uiState.value = ChatUiState()
    }

    private fun loadHistory(convId: Long) {
        historyLoadJob?.cancel()
        historyLoadJob = viewModelScope.launch {
            val recoverableTaskRun = agentRuntime.recover(convId)
            if (currentConversationId != convId) return@launch
            // Load title
            syncManager.getConversationTitle(convId)?.let { title ->
                if (currentConversationId != convId) return@let
                _uiState.value = _uiState.value.copy(conversationTitle = title)
            }

            // Load messages
            syncManager.getMessagesFlow(convId).collect { messages ->
                if (currentConversationId != convId) return@collect
                val restoredTaskRun = messages.asReversed()
                    .firstNotNullOfOrNull { message -> extractTaskRunMarker(message.content.orEmpty()) }
                val chatMessages = messages.map { msg ->
                    ChatMessage(
                        role = msg.role,
                        content = msg.content,
                        tool_call_id = msg.toolCallId,
                        name = msg.toolCallName,
                        timestamp = msg.timestamp,
                    )
                }
                val taskInFlight = generationJob?.isActive == true ||
                    _uiState.value.isStreaming ||
                    _uiState.value.pendingToolApproval != null
                val restoredTerminalTaskRun = restoredTaskRun?.takeIf { restored ->
                    restored.id == recoverableTaskRun?.id && restored.status in setOf(
                        TaskRunStatus.Completed,
                        TaskRunStatus.Failed,
                        TaskRunStatus.Cancelled,
                    )
                }
                val taskRun = when {
                    taskInFlight -> activeTaskRun
                    restoredTerminalTaskRun != null -> agentRuntime.persist(restoredTerminalTaskRun)
                    else -> recoverableTaskRun ?: restoredTaskRun
                }
                activeTaskRun = taskRun
                val restorableCheckpoint = taskRun
                    ?.takeIf { it.status == com.denggl2.mason.agent.TaskRunStatus.WaitingForUser }
                    ?.agentExecution
                val restoredApproval = restorableCheckpoint?.pendingApprovalCallId
                    ?.let { callId -> restorableCheckpoint.pendingCalls.firstOrNull { it.id == callId } }
                    ?.let { call -> createApprovalRequest(call, restorableCheckpoint.approvedCallIds, forceRestore = true) }
                val pendingAssistantMessage = restorableCheckpoint?.pendingAssistantMessage
                if (restoredApproval != null && pendingAssistantMessage != null) {
                    pendingToolBatch = PendingToolBatch(
                        assistantMessage = pendingAssistantMessage,
                        calls = restorableCheckpoint.pendingCalls,
                        startedAt = taskRun?.updatedAt ?: System.currentTimeMillis(),
                        checkpoint = restorableCheckpoint,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    messages = chatMessages,
                    taskRun = taskRun,
                    taskSteps = taskRun?.steps.orEmpty(),
                    pendingToolApproval = restoredApproval ?: _uiState.value.pendingToolApproval,
                    modelContributions = taskRun?.modelContributions
                        ?.takeIf { it.isNotEmpty() }
                        ?: messages.asReversed().firstNotNullOfOrNull { message ->
                            extractModelParticipation(message.content.orEmpty())?.contributions
                        }.orEmpty(),
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
        if (content.isBlank() || interjectionJob?.isActive == true) return
        generationJob?.takeIf { it.isActive }?.let { activeJob ->
            interjectMessage(content, activeJob)
            return
        }
        sendMessageWhenIdle(content)
    }

    private fun sendMessageWhenIdle(content: String, resumedRun: TaskRun? = null) {
        val currentConfig = apiConfig.value
        if (!canStartModelTask(currentConfig, isLocalModelInstalled(currentConfig.localModel))) return
        if (activeTaskRun?.agentExecution?.waitingForInput == true) {
            continueAgentWithUserInput(content)
            return
        }
        if (
            resumedRun == null &&
            activeTaskRun?.status == TaskRunStatus.WaitingForUser &&
            activeTaskRun?.agentExecution?.pendingApprovalCallId == null &&
            isTaskContinuationCommand(content)
        ) {
            if (activeTaskRun?.agentExecution != null) {
                continueAgentCheckpoint(null)
            } else {
                resumeCurrentTask()
            }
            return
        }

        val startedAt = System.currentTimeMillis()
        val isResumingTask = resumedRun != null
        val shouldRecordUserMessage = shouldRecordTaskGoal(isResumingTask)
        val taskRun = (resumedRun
            ?: agentRuntime.begin(content, currentConversationId).copy(createdAt = startedAt, updatedAt = startedAt))
            .copy(modelContributions = emptyList())
        activeTaskRun = taskRun
        var usageSeen = false
        var modelAnswered = false
        var handledError = false
        val userMessage = ChatMessage(role = "user", content = content, timestamp = startedAt)
        _uiState.value = _uiState.value.copy(
            messages = if (shouldRecordUserMessage) {
                _uiState.value.messages + userMessage
            } else {
                _uiState.value.messages
            },
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
            // Contributions are scoped to this user turn. They are rebuilt from
            // ModelExecutionStarted events below, including tool follow-up calls.
            modelContributions = emptyList(),
        )

        launchGeneration {
            if (shouldRecordUserMessage) {
                // Ensure a conversation exists and save a newly submitted user message.
                currentConversationId?.let { conversationId ->
                    if (syncManager.getConversationTitle(conversationId) == null) {
                        currentConversationId = null
                        isFirstMessage = true
                    }
                }
                if (currentConversationId == null) {
                    val title = summarizeConversationTitle(content)
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
                        val autoTitle = summarizeConversationTitle(content)
                        syncManager.updateConversationTitle(convId, autoTitle)
                        _uiState.value = _uiState.value.copy(conversationTitle = autoTitle)
                        isFirstMessage = false
                    }
                }
            }

            notifyTaskEvent(TaskNotificationEvent.Started)

            val producedArtifacts = mutableListOf<ArtifactMetadata>()
            userMemoryStore.explicitCandidate(content)?.let { memory ->
                val toolName = if (memory.sensitive) {
                    SensitiveMemoryWriteTool.NAME
                } else {
                    MemoryWriteTool.NAME
                }
                val call = ToolCall(
                    id = "memory-${activeTaskRun?.id.orEmpty()}",
                    function = FunctionCall(
                        name = toolName,
                        arguments = buildJsonObject {
                            put("label", memory.label)
                            put("value", memory.value)
                            put("type", memory.type.name)
                            put("explicit_request", true)
                        }.toString(),
                    ),
                )
                val loop = runAgentToolLoop(
                    firstResponse = ChatResponse.ToolCallsRequested(
                        assistantMessage = ChatMessage(role = "assistant", tool_calls = listOf(call)),
                        calls = listOf(call),
                    ),
                    checkpoint = AgentExecutionCheckpoint(
                        messages = taskExecutionMessages(_uiState.value.messages, content, startedAt),
                    ),
                    startedAt = startedAt,
                    producedArtifacts = producedArtifacts,
                )
                if (!loop.paused) {
                    finalizeAgentResponse(
                        startedAt = startedAt,
                        artifacts = producedArtifacts,
                        usageSeen = loop.usageSeen,
                        modelAnswered = loop.modelAnswered,
                    )
                }
                return@launchGeneration
            }

            capabilityRequirementResolver.resolve(content, activeTaskRun?.id.orEmpty())?.let { requirement ->
                presentCapabilityRequirement(requirement, startedAt)
                return@launchGeneration
            }

            if (AutomationDraftService.looksLikeAutomationRequest(content)) {
                createAutomationDraft(content, startedAt)
                return@launchGeneration
            }

            val directMemoryAnswer = directMemoryLookupAnswer(
                query = content,
                memories = userMemoryStore.relevant(
                    query = content,
                    conversationScopeId = currentConversationId?.toString(),
                    limit = 1,
                ),
            )
            if (directMemoryAnswer != null) {
                _uiState.value = _uiState.value.copy(
                    streamingContent = directMemoryAnswer,
                    taskSteps = _uiState.value.taskSteps
                        .updateStep("plan", TaskStepStatus.Completed, "已识别长期记忆查询")
                        .updateStep("prepare-inputs", TaskStepStatus.Completed, "已检索相关本机记忆")
                        .updateStep("execute", TaskStepStatus.Completed, "已生成本机记忆回答"),
                )
                finalizeAgentResponse(
                    startedAt = startedAt,
                    artifacts = emptyList(),
                    usageSeen = false,
                    modelAnswered = false,
                )
                return@launchGeneration
            }

            refreshAutomaticSkillTool(content)
            _uiState.value = _uiState.value.copy(
                taskSteps = _uiState.value.taskSteps
                    .updateStep("plan", TaskStepStatus.Completed, "已识别目标和所需能力")
                    .updateStep("prepare-inputs", TaskStepStatus.Completed, "已整理本轮输入材料")
                    .updateStep("execute", TaskStepStatus.Running, "正在等待模型生成方案"),
            )
            val routedResponse = modelRouter.route(
                messages = taskExecutionMessages(_uiState.value.messages, content, startedAt),
                toolsEnabled = apiConfig.value.toolsEnabled,
                memoryScopeId = currentConversationId?.toString(),
            )
            val directLocalEnabled = routedResponse.decision.engineId in setOf("litert-lm", "llama-cpp")
            val localDeviceInfoCall = buildLocalDeviceInfoToolCall(
                content = content,
                directLocalEnabled = directLocalEnabled,
                phoneToolsEnabled = apiConfig.value.phoneToolsEnabled,
                taskRunId = activeTaskRun?.id.orEmpty(),
            )
            if (localDeviceInfoCall != null) {
                val loop = runAgentToolLoop(
                    firstResponse = ChatResponse.ToolCallsRequested(
                        assistantMessage = ChatMessage(
                            role = "assistant",
                            tool_calls = listOf(localDeviceInfoCall),
                        ),
                        calls = listOf(localDeviceInfoCall),
                    ),
                    checkpoint = AgentExecutionCheckpoint(
                        messages = taskExecutionMessages(_uiState.value.messages, content, startedAt),
                    ),
                    startedAt = startedAt,
                    producedArtifacts = producedArtifacts,
                    terminalToolResultFormatter = { call, result ->
                        if (call.function.name == "get_device_info") {
                            formatLocalDeviceInfoResult(result)
                        } else {
                            null
                        }
                    },
                )
                usageSeen = usageSeen || loop.usageSeen
                modelAnswered = modelAnswered || loop.modelAnswered
                if (!loop.paused) {
                    finalizeAgentResponse(
                        startedAt = startedAt,
                        artifacts = producedArtifacts,
                        usageSeen = usageSeen,
                        modelAnswered = modelAnswered,
                    )
                }
                return@launchGeneration
            }
            val responseFlow = if (directLocalEnabled) {
                localCapabilityGuidance(content)?.let { guidance ->
                    flowOf(ChatResponse.TextChunk(guidance))
                } ?: routedResponse.responses
            } else {
                routedResponse.responses
            }
            responseFlow.collect { response ->
                when (response) {
                    is ChatResponse.ModelExecutionStarted -> recordModelContribution(response)
                    is ChatResponse.ToolCallsRequested -> {
                        val loop = runAgentToolLoop(
                            firstResponse = response,
                            checkpoint = AgentExecutionCheckpoint(
                                messages = taskExecutionMessages(_uiState.value.messages, content, startedAt),
                            ),
                            startedAt = startedAt,
                            producedArtifacts = producedArtifacts,
                        )
                        usageSeen = usageSeen || loop.usageSeen
                        modelAnswered = modelAnswered || loop.modelAnswered
                        if (loop.paused) return@collect
                    }

                    is ChatResponse.TextChunk -> {
                        modelAnswered = true
                        val finalSteps = completeOpenTaskSteps(
                            _uiState.value.taskSteps
                                .updateStep("execute", TaskStepStatus.Completed, "模型已生成回答")
                                .updateStep("review", TaskStepStatus.Completed, "已检查回答完整性")
                                .updateStep("summary", TaskStepStatus.Completed, "已生成回复"),
                        )
                        _uiState.value = _uiState.value.copy(
                            streamingContent = _uiState.value.streamingContent + response.text,
                            taskSteps = finalSteps,
                            taskRun = activeTaskRun,
                        )
                    }

                    is ChatResponse.UsageReceived -> {
                        usageSeen = true
                        recordUsage(response.usage)
                    }

                    is ChatResponse.ImageGenerated -> {
                        modelAnswered = true
                        val artifactResult = runCatching {
                            if (response.isBase64) {
                                artifactStore.saveGeneratedImageBase64Artifact(
                                    encoded = response.data,
                                    declaredMimeType = response.mimeType,
                                )
                            } else {
                                artifactStore.saveRemoteImageArtifact(response.data)
                            }
                        }
                        artifactResult.onSuccess { artifact ->
                            producedArtifacts += artifact
                            _uiState.value = _uiState.value.copy(
                                streamingContent = "图片已生成并保存到产出中心。",
                                taskSteps = _uiState.value.taskSteps
                                    .updateStep("execute", TaskStepStatus.Completed, "生图模型已返回图片")
                                    .updateStep("review", TaskStepStatus.Completed, "已校验图片产出")
                                    .updateStep("summary", TaskStepStatus.Completed, "已保存图片"),
                            )
                        }.onFailure { error ->
                            _uiState.value = _uiState.value.copy(
                                streamingContent = "图片已生成，但保存失败：${error.message ?: "未知错误"}",
                                taskSteps = _uiState.value.taskSteps
                                    .updateStep("execute", TaskStepStatus.Completed, "生图模型已返回图片")
                                    .updateStep("review", TaskStepStatus.Failed, "图片保存失败")
                                    .updateStep("summary", TaskStepStatus.Completed, "已返回保存失败原因"),
                            )
                        }
                    }

                    is ChatResponse.Error -> {
                        handledError = true
                        val guidedContent = if (directLocalEnabled) {
                            "本地模型调用失败：${response.message}"
                        } else {
                            formatGuidedError(response.message)
                        }
                        val failedSteps = failOpenTaskSteps(_uiState.value.taskSteps, response.message)
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

            ensureActive()
            if (_uiState.value.pendingToolApproval != null) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    toolCallStatus = null,
                    requestStartedAt = null,
                    taskRun = activeTaskRun?.withSteps(_uiState.value.taskSteps),
                )
                return@launchGeneration
            }

            if (handledError) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    streamingContent = "",
                    toolCallStatus = null,
                    requestStartedAt = null,
                    lastUsageMissing = false,
                    taskRun = activeTaskRun,
                )
                updateAgentCheckpoint(null)
            } else if (_uiState.value.streamingContent.isNotEmpty()) {
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
                if (finalSteps.none { it.status == TaskStepStatus.WaitingForUser }) {
                    updateAgentCheckpoint(null)
                }
            } else {
                val reason = "模型未返回可用内容"
                val failedSteps = failOpenTaskSteps(_uiState.value.taskSteps, reason)
                val persistedError = annotateCurrentTaskRun(
                    "$reason。请检查模型服务或重试。",
                    failedSteps,
                )
                val errorMessage = ChatMessage(
                    role = "assistant",
                    content = persistedError,
                    timestamp = System.currentTimeMillis(),
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMessage,
                    isStreaming = false,
                    toolCallStatus = null,
                    requestStartedAt = null,
                    lastProcessingMs = System.currentTimeMillis() - startedAt,
                    lastUsageMissing = modelAnswered && !usageSeen,
                    taskSteps = failedSteps,
                    taskRun = activeTaskRun,
                )
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(convId, role = "assistant", content = persistedError)
                }
            }
            notifyTaskCompletedIfNeeded()
        }
    }

    private fun interjectMessage(content: String, activeJob: Job) {
        generationJob = null
        val job = viewModelScope.launch {
            activeJob.cancel(CancellationException("用户发送新消息"))
            runCatching { modelRouter.cancelActive() }
            activeJob.join()
            markGenerationInterrupted()
            runCatching { modelRouter.releaseLocal() }
            sendMessageWhenIdle(content)
        }
        interjectionJob = job
        job.invokeOnCompletion {
            if (interjectionJob === job) interjectionJob = null
        }
    }

    private fun recoverLatestTaskRun() {
        latestTaskRecoveryJob = viewModelScope.launch {
            val recovered = agentRuntime.recover(conversationId = null) ?: return@launch
            if (!restoresLatestTask) return@launch
            activeTaskRun = recovered
            currentConversationId = recovered.conversationId
            _uiState.value = _uiState.value.copy(
                conversationId = recovered.conversationId,
                taskRun = recovered,
                taskSteps = recovered.steps,
            )
            recovered.conversationId?.let(::loadHistory)
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
            modelContributions = emptyList(),
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
                        content = "测试产出已保存，可以直接预览、编辑或分享。",
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
                    "自动化创建失败：${outcome.exceptionOrNull()?.message ?: "未知错误"}",
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

    fun recheckPendingCapability() {
        if (_uiState.value.isStreaming) return
        val requirement = _uiState.value.messages.asReversed()
            .firstNotNullOfOrNull { message -> extractCapabilityRequirementMarker(message.content.orEmpty()) }
            ?: return
        val run = activeTaskRun ?: return
        if (run.id != requirement.taskRunId ||
            run.status != com.denggl2.mason.agent.TaskRunStatus.WaitingForUser ||
            !capabilityRequirementResolver.isSatisfied(requirement)
        ) return
        resumeCurrentTask()
    }

    fun retryTaskStep(stepId: String) {
        if (_uiState.value.isStreaming || _uiState.value.pendingToolApproval != null) return
        if (_uiState.value.taskSteps.any { it.id == stepId && it.status == TaskStepStatus.WaitingForUser }) {
            resumeCurrentTask()
            return
        }
        val step = _uiState.value.taskSteps.firstOrNull { it.id == stepId && it.retryable } ?: return
        val call = step.toolCall
        if (call == null) {
            val run = activeTaskRun ?: return
            val retried = agentRuntime.retry(run.withSteps(_uiState.value.taskSteps), stepId)
            val resetSteps = retried.steps.map { candidate ->
                if (candidate.id == "summary" && candidate.status == TaskStepStatus.Failed) {
                    candidate.copy(
                        status = TaskStepStatus.Pending,
                        detail = "等待模型重试完成后生成总结",
                        error = null,
                        finishedAt = null,
                    )
                } else {
                    candidate
                }
            }
            val resumed = retried.withSteps(resetSteps)
            activeTaskRun = resumed
            _uiState.value = _uiState.value.copy(
                taskSteps = resumed.steps,
                taskRun = resumed,
            )
            sendMessageWhenIdle(resumed.goal, resumedRun = resumed)
            return
        }
        val profile = toolSecurityProfile(call.function.name)
        if ((apiConfig.value.requireToolConfirmation || profile.mandatoryApproval) &&
            profile.risk == ToolRiskLevel.High
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
                    allowPersistentGrant = profile.persistentGrantAllowed,
                    riskLevel = profile.risk,
                    reason = "这是失败步骤的重试。${ToolPolicy.approvalReason(profile)}",
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
        val job = generationJob
        // Detach before cancellation so completion handling does not turn a pause into a cancellation.
        generationJob = null
        job?.cancel(CancellationException("用户暂停任务"))
        viewModelScope.launch { modelRouter.cancelActive() }
        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            requestStartedAt = null,
            taskRun = paused,
            taskSteps = paused.steps,
        )
        viewModelScope.launch {
            agentRuntime.persist(paused)
            notifyTaskEvent(TaskNotificationEvent.Paused)
        }
    }

    fun cancelCurrentTask() {
        val run = activeTaskRun ?: return
        if (run.status in setOf(TaskRunStatus.Completed, TaskRunStatus.Failed, TaskRunStatus.Cancelled)) return
        val cancelled = agentRuntime.cancel(run.withSteps(_uiState.value.taskSteps))
        activeTaskRun = cancelled
        val job = generationJob
        generationJob = null
        job?.cancel(CancellationException("用户取消任务"))
        pendingToolBatch = null
        pendingRetryStepId = null
        viewModelScope.launch {
            modelRouter.cancelActive()
            agentRuntime.persist(cancelled)
            notifyTaskEvent(TaskNotificationEvent.Cancelled)
        }
        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            streamingContent = "",
            toolCallStatus = null,
            requestStartedAt = null,
            pendingToolApproval = null,
            taskRun = cancelled,
            taskSteps = cancelled.steps,
        )
    }

    fun resumeCurrentTask() {
        if (_uiState.value.isStreaming) return
        val run = activeTaskRun ?: return
        if (run.status != com.denggl2.mason.agent.TaskRunStatus.WaitingForUser) return
        if (run.agentExecution?.pendingApprovalCallId != null) return
        if (run.agentExecution != null) {
            if (!run.agentExecution.waitingForInput) continueAgentCheckpoint(null)
            return
        }
        val resumed = agentRuntime.resume(run).copy(finishedAt = null)
        activeTaskRun = resumed
        sendMessageWhenIdle(run.goal, resumedRun = resumed)
    }

    private fun continueAgentWithUserInput(content: String) = continueAgentCheckpoint(content)

    private fun continueAgentCheckpoint(userContent: String?) {
        val run = activeTaskRun ?: return
        val checkpoint = run.agentExecution ?: return
        val startedAt = System.currentTimeMillis()
        val userMessage = userContent?.let {
            ChatMessage(role = "user", content = it, timestamp = startedAt)
        }
        val nextMessages = checkpoint.messages + listOfNotNull(userMessage)
        val resumedBase = if (userContent != null && checkpoint.waitingForInput) {
            run.withSteps(run.steps.map { step ->
                if (step.status == TaskStepStatus.WaitingForUser) {
                    step.copy(
                        status = TaskStepStatus.Completed,
                        detail = "已收到用户补充信息",
                        finishedAt = startedAt,
                    )
                } else step
            })
        } else {
            agentRuntime.resume(run)
        }
        val resumed = resumedBase.copy(
            agentExecution = checkpoint.copy(
                messages = nextMessages,
                waitingForInput = false,
                pendingAssistantMessage = null,
                pendingCalls = emptyList(),
                pendingApprovalCallId = null,
            ),
            modelContributions = emptyList(),
            finishedAt = null,
        )
        activeTaskRun = resumed
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + listOfNotNull(userMessage),
            isStreaming = true,
            streamingContent = "",
            requestStartedAt = startedAt,
            taskSteps = resumed.steps,
            taskRun = resumed,
            modelContributions = emptyList(),
        )
        launchGeneration {
            notifyTaskEvent(TaskNotificationEvent.Started)
            userMessage?.let { message ->
                currentConversationId?.let { convId ->
                    syncManager.saveMessage(convId, role = "user", content = message.content)
                }
            }
            refreshAutomaticSkillTool(userContent.orEmpty())
            var usageSeen = false
            var modelAnswered = false
            var toolResponse: ChatResponse.ToolCallsRequested? = null
            modelRouter.route(
                nextMessages,
                toolsEnabled = true,
                includeMemory = false,
                memoryScopeId = currentConversationId?.toString(),
                contribution = "继续未完成的回答",
            ).responses.collect { response ->
                when (response) {
                    is ChatResponse.ModelExecutionStarted -> recordModelContribution(response)
                    is ChatResponse.ToolCallsRequested -> toolResponse = response
                    is ChatResponse.TextChunk -> {
                        modelAnswered = true
                        _uiState.value = _uiState.value.copy(
                            streamingContent = _uiState.value.streamingContent + response.text,
                        )
                    }
                    is ChatResponse.UsageReceived -> {
                        usageSeen = true
                        recordUsage(response.usage)
                    }
                    is ChatResponse.Error -> {
                        modelAnswered = true
                        _uiState.value = _uiState.value.copy(
                            streamingContent = _uiState.value.streamingContent + formatGuidedError(response.message),
                        )
                    }
                    else -> Unit
                }
            }
            ensureActive()
            val artifacts = mutableListOf<ArtifactMetadata>()
            toolResponse?.let { response ->
                val loop = runAgentToolLoop(
                    firstResponse = response,
                    checkpoint = resumed.agentExecution ?: checkpoint,
                    startedAt = startedAt,
                    producedArtifacts = artifacts,
                )
                usageSeen = usageSeen || loop.usageSeen
                modelAnswered = modelAnswered || loop.modelAnswered
                if (loop.paused) return@launchGeneration
            }
            finalizeAgentResponse(startedAt, artifacts, usageSeen, modelAnswered)
        }
    }

    fun stopGeneration() {
        val job = generationJob?.takeIf { it.isActive } ?: return
        // Ignore late native callbacks after the user has stopped this request.
        generationJob = null
        markGenerationStopped()
        viewModelScope.launch {
            job.cancel(CancellationException("用户停止生成"))
            modelRouter.cancelActive()
            modelRouter.releaseLocal()
        }
    }

    fun onAppBackgrounded() {
        when (appBackgroundAction(generationJob?.isActive == true, activeTaskRun != null)) {
            AppBackgroundAction.PersistRunningTask -> {
                val checkpoint = activeTaskRun
                    ?.withSteps(_uiState.value.taskSteps)
                    ?.copy(conversationId = currentConversationId)
                activeTaskRun = checkpoint
                viewModelScope.launch {
                    checkpoint?.let { agentRuntime.persist(it) }
                }
            }
            AppBackgroundAction.ReleaseLocalRuntime -> {
                viewModelScope.launch { modelRouter.releaseLocal() }
            }
        }
    }

    override fun onCleared() {
        trackedActiveConversationId?.let(taskRunStore::markConversationInactive)
        super.onCleared()
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
        activeTaskRun = activeTaskRun?.copy(
            agentExecution = activeTaskRun?.agentExecution?.copy(
                pendingAssistantMessage = null,
                pendingCalls = emptyList(),
                pendingApprovalCallId = null,
            ),
        )
        val finalSteps = _uiState.value.taskSteps.map { step ->
            when {
                step.id == approval.call.taskStepId() -> step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "用户已拒绝 ${approval.toolName}",
                    finishedAt = System.currentTimeMillis(),
                )
                step.status == TaskStepStatus.Pending ||
                    step.status == TaskStepStatus.Running ||
                    step.status == TaskStepStatus.WaitingForUser -> step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "前置操作已取消",
                    finishedAt = System.currentTimeMillis(),
                )
                else -> step
            }
        }.updateStep("summary", TaskStepStatus.Completed, "已停止本轮任务")
        val content = annotateCurrentTaskRun(
            "已取消 ${approval.toolName} 工具调用。这次操作没有继续执行，手机状态不会被更改。",
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
                activeTaskRun?.let { agentRuntime.persist(it) }
                syncManager.saveMessage(convId, role = "assistant", content = content)
            }
        }
    }

    fun selectChatModel(modelId: String) {
        val currentConfig = apiConfig.value
        val model = AiProviderCatalog.getModel(currentConfig.providerId, modelId)
        val currentReference = currentConfig.resolvedChatModelRef()
        viewModelScope.launch {
            apiConfigDataStore.updateConfig(
                currentConfig.copy(
                    model = modelId,
                    chatModelRef = ModelReference(currentReference.connectionId, modelId),
                    localModelDirectEnabled = false,
                    toolsEnabled = model?.supportsTools ?: currentConfig.toolsEnabled,
                ),
            )
        }
    }

    fun localModelStates(): List<LocalModelFileState> =
        localModelStore.states(LocalModelCatalog.models)

    fun isLocalModelInstalled(modelId: String): Boolean =
        modelId.isNotBlank() && localModelStore.readyModelPath(modelId) != null

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

    private suspend fun recordModelContribution(event: ChatResponse.ModelExecutionStarted) {
        val contributions = mergeModelContribution(
            current = _uiState.value.modelContributions,
            modelId = event.modelId,
            engineId = event.engineId,
            part = event.contribution,
        )
        if (contributions == _uiState.value.modelContributions) return
        activeTaskRun = activeTaskRun?.copy(modelContributions = contributions)
        _uiState.value = _uiState.value.copy(
            modelContributions = contributions,
            taskRun = activeTaskRun,
        )
        activeTaskRun?.let { agentRuntime.persist(it) }
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
            val args = parseToolArguments(call.function.arguments)
            val result = toolExecutor.execute(
                call.function.name,
                args,
                ToolExecutionContext(
                    source = ToolExecutionSource.Chat,
                    taskRunId = activeTaskRun?.id,
                    conversationId = currentConversationId?.toString(),
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
                "已单独重试 ${call.function.name}。\n$resultText"
            } else {
                "${call.function.name} 重试仍然失败。\n$resultText"
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
        val toolArtifacts = mutableListOf<ArtifactMetadata>()
        val approvedCheckpoint = batch.checkpoint.copy(
            pendingApprovalCallId = null,
            approvedCallIds = (batch.checkpoint.approvedCallIds +
                batch.checkpoint.pendingApprovalCallId.orEmpty()).filter(String::isNotBlank).distinct(),
        )
        val result = runAgentToolLoop(
            firstResponse = ChatResponse.ToolCallsRequested(batch.assistantMessage, batch.calls),
            checkpoint = approvedCheckpoint,
            startedAt = batch.startedAt,
            producedArtifacts = toolArtifacts,
        )
        if (result.paused) return
        finalizeAgentResponse(
            startedAt = batch.startedAt,
            artifacts = toolArtifacts,
            usageSeen = result.usageSeen,
            modelAnswered = result.modelAnswered,
        )
    }

    private suspend fun runAgentToolLoop(
        firstResponse: ChatResponse.ToolCallsRequested,
        checkpoint: AgentExecutionCheckpoint,
        startedAt: Long,
        producedArtifacts: MutableList<ArtifactMetadata>,
        terminalToolResultFormatter: ((ToolCall, ToolResult) -> String?)? = null,
    ): AgentLoopResult {
        var state = checkpoint
        var pending = firstResponse
        var usageSeen = false
        var modelAnswered = false

        while (true) {
            if (!state.canExecute(pending.calls)) {
                val reason = if (state.round >= com.denggl2.mason.agent.MAX_AGENT_TOOL_ROUNDS) {
                    "工具执行已达到最大轮数，已停止以避免任务失控。"
                } else {
                    "模型重复请求同一个工具，已停止以避免循环执行。"
                }
                _uiState.value = _uiState.value.copy(
                    streamingContent = "$reason\n已保留当前执行记录，可调整要求后重试。",
                    taskSteps = failOpenTaskSteps(_uiState.value.taskSteps, reason),
                )
                updateAgentCheckpoint(null)
                return AgentLoopResult(usageSeen, true)
            }

            _uiState.value = _uiState.value.copy(
                taskSteps = _uiState.value.taskSteps
                    .updateStep("plan", TaskStepStatus.Completed, "已判断需要调用外部能力")
                    .updateStep("prepare-inputs", TaskStepStatus.Completed, "已整理本轮输入材料")
                    .updateStep("execute", TaskStepStatus.Running, "正在执行第 ${state.round + 1} 轮任务")
                    .withToolSteps(pending.calls),
            )

            val approval = pending.calls.firstNotNullOfOrNull { call ->
                createApprovalRequest(call, state.approvedCallIds)
            }
            if (approval != null) {
                state = state.copy(
                    pendingAssistantMessage = pending.assistantMessage,
                    pendingCalls = pending.calls,
                    pendingApprovalCallId = approval.call.id,
                )
                pendingToolBatch = PendingToolBatch(
                    assistantMessage = pending.assistantMessage,
                    calls = pending.calls,
                    startedAt = startedAt,
                    checkpoint = state,
                )
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    requestStartedAt = null,
                    toolCallStatus = null,
                    pendingToolApproval = approval,
                    taskSteps = _uiState.value.taskSteps
                        .updateStep(approval.call.taskStepId(), TaskStepStatus.WaitingForUser, "等待用户确认 ${approval.displayName}")
                        .updateStep("summary", TaskStepStatus.Pending, "确认后继续生成结果"),
                )
                updateAgentCheckpoint(state)
                return AgentLoopResult(usageSeen, modelAnswered, paused = true)
            }

            val toolMessages = mutableListOf<ChatMessage>()
            val completedToolResults = mutableListOf<Pair<ToolCall, ToolResult>>()
            var waitingForInput = false
            for (call in pending.calls) {
                val isSkillActivation = call.function.name == SkillActivationTool.NAME
                _uiState.value = _uiState.value.copy(
                    toolCallStatus = if (isSkillActivation) "正在选择合适的 Skill..." else "正在执行 ${call.function.name} 工具...",
                    taskSteps = _uiState.value.taskSteps.updateStep(
                        call.taskStepId(),
                        TaskStepStatus.Running,
                        if (isSkillActivation) "正在匹配任务与 Skill" else "正在执行 ${call.function.name}",
                    ),
                )
                var result = toolExecutor.execute(
                    call.function.name,
                    parseToolArguments(call.function.arguments),
                    ToolExecutionContext(
                        source = ToolExecutionSource.Chat,
                        taskRunId = activeTaskRun?.id,
                        conversationId = currentConversationId?.toString(),
                        userConfirmed = isToolCallConfirmed(call, state.approvedCallIds),
                    ),
                )
                val runAfterFirstAttempt = activeTaskRun?.withSteps(_uiState.value.taskSteps)
                if (!result.success && runAfterFirstAttempt != null &&
                    agentRuntime.shouldAutoRetry(runAfterFirstAttempt, call.taskStepId())
                ) {
                    _uiState.value = _uiState.value.copy(
                        toolCallStatus = "正在重试 ${call.function.name}...",
                        taskSteps = _uiState.value.taskSteps.updateStep(
                            call.taskStepId(),
                            TaskStepStatus.Running,
                            "首次执行失败，正在进行一次安全重试",
                        ),
                    )
                    result = toolExecutor.execute(
                        call.function.name,
                        parseToolArguments(call.function.arguments),
                        ToolExecutionContext(
                            source = ToolExecutionSource.Chat,
                            taskRunId = activeTaskRun?.id,
                            conversationId = currentConversationId?.toString(),
                            userConfirmed = isToolCallConfirmed(call, state.approvedCallIds),
                        ),
                    )
                }
                mergeExternalTaskState()
                val needsInput = result.success && result.data["status"] == "needs_input"
                waitingForInput = waitingForInput || needsInput
                _uiState.value = _uiState.value.copy(
                    taskSteps = _uiState.value.taskSteps.updateStep(
                        call.taskStepId(),
                        when {
                            needsInput -> TaskStepStatus.WaitingForUser
                            result.success -> TaskStepStatus.Completed
                            else -> TaskStepStatus.Failed
                        },
                        when {
                            needsInput -> "等待补充 ${result.data["missing_parameters"].orEmpty()}"
                            isSkillActivation && result.success -> "已选择 ${result.data["skill_name"].orEmpty()}"
                            result.success -> "${call.function.name} 执行完成"
                            else -> result.error ?: "工具执行失败"
                        },
                    ),
                )
                if (call.function.name == "file_write" && result.success) {
                    artifactStore.metadataForExistingFile(result.artifactPath())?.let(producedArtifacts::add)
                }
                result.artifactPaths().mapNotNull(artifactStore::metadataForExistingFile)
                    .forEach(producedArtifacts::add)
                completedToolResults += call to result
                toolMessages += ChatMessage(
                    role = "tool",
                    content = if (result.success) {
                        result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    } else {
                        "执行失败: ${result.error}"
                    },
                    tool_call_id = call.id,
                    name = call.function.name,
                    timestamp = System.currentTimeMillis(),
                )
            }

            val nextMessages = state.messages + pending.assistantMessage + toolMessages
            state = state.copy(
                messages = nextMessages,
                round = state.round + 1,
                callFingerprints = state.callFingerprints + pending.calls.map(ToolCall::fingerprint),
                completedCallFingerprints = state.completedCallFingerprints + pending.calls
                    .filter { call -> toolMessages.any { it.tool_call_id == call.id && !it.content.orEmpty().startsWith("执行失败:") } }
                    .map(ToolCall::fingerprint),
                pendingAssistantMessage = null,
                pendingCalls = emptyList(),
                pendingApprovalCallId = null,
                waitingForInput = waitingForInput,
            )
            _uiState.value = _uiState.value.copy(messages = nextMessages, toolCallStatus = null)
            persistToolMessages(toolMessages)
            updateAgentCheckpoint(state)

            val terminalContent = completedToolResults.singleOrNull()?.let { (call, result) ->
                terminalToolResultFormatter?.invoke(call, result)
            }
            if (terminalContent != null) {
                modelAnswered = true
                _uiState.value = _uiState.value.copy(
                    streamingContent = _uiState.value.streamingContent + terminalContent,
                    taskSteps = completeOpenTaskSteps(
                        _uiState.value.taskSteps
                            .updateStep("execute", TaskStepStatus.Completed, "设备信息读取完成")
                            .updateStep("review", TaskStepStatus.Completed, "已检查设备信息")
                            .updateStep("summary", TaskStepStatus.Completed, "已生成设备信息回复"),
                    ),
                )
                updateAgentCheckpoint(null)
                return AgentLoopResult(usageSeen, modelAnswered)
            }

            var nextToolResponse: ChatResponse.ToolCallsRequested? = null
            modelRouter.route(
                nextMessages,
                toolsEnabled = true,
                includeMemory = false,
                memoryScopeId = currentConversationId?.toString(),
                contribution = "整理工具结果并生成回答",
            ).responses.collect { response ->
                when (response) {
                    is ChatResponse.ModelExecutionStarted -> recordModelContribution(response)
                    is ChatResponse.ToolCallsRequested -> nextToolResponse = response
                    is ChatResponse.TextChunk -> {
                        modelAnswered = true
                        _uiState.value = _uiState.value.copy(
                            streamingContent = _uiState.value.streamingContent + response.text,
                            taskSteps = _uiState.value.taskSteps
                                .updateStep("review", TaskStepStatus.Completed, "已检查工具返回结果")
                                .updateStep("summary", TaskStepStatus.Running, "正在整理回复"),
                        )
                    }
                    is ChatResponse.UsageReceived -> {
                        usageSeen = true
                        recordUsage(response.usage)
                    }
                    is ChatResponse.Error -> {
                        modelAnswered = true
                        _uiState.value = _uiState.value.copy(
                            streamingContent = _uiState.value.streamingContent + "\n\n${formatGuidedError(response.message)}",
                            taskSteps = _uiState.value.taskSteps.updateStep("summary", TaskStepStatus.Failed, response.message),
                        )
                    }
                    else -> Unit
                }
            }
            if (waitingForInput) {
                if (_uiState.value.streamingContent.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        streamingContent = "引导：请补充 Skill 要求的参数后，我会从当前任务继续。",
                    )
                }
                updateAgentCheckpoint(state.copy(waitingForInput = true))
                return AgentLoopResult(usageSeen, modelAnswered = true)
            }
            nextToolResponse?.let {
                pending = it
                continue
            }
            updateAgentCheckpoint(state.takeIf { waitingForInput })
            return AgentLoopResult(usageSeen, modelAnswered)
        }
    }

    private fun createApprovalRequest(
        call: ToolCall,
        approvedCallIds: List<String>,
        forceRestore: Boolean = false,
    ): ToolApprovalRequest? {
        val profile = toolSecurityProfile(call.function.name)
        if (call.id in approvedCallIds || profile.risk != ToolRiskLevel.High) return null
        if (!forceRestore && !apiConfig.value.requireToolConfirmation && !profile.mandatoryApproval) return null
        if (!forceRestore && profile.persistentGrantAllowed && toolGrantStore.isAlwaysAllowed(call.function.name)) return null
        val tool = toolRegistry.get(call.function.name)
        val arguments = parseToolArguments(call.function.arguments)
        val actionSummary = if (call.function.name == ConversationDispatchTool.NAME) {
            val target = arguments[ConversationDispatchTool.ARG_TARGET].orEmpty().take(80)
            val summary = arguments[ConversationDispatchTool.ARG_SUMMARY].orEmpty().replace("\n", " ").take(160)
            "发送到「$target」：$summary"
        } else {
            tool?.approvalDescription.orEmpty()
        }
        return ToolApprovalRequest(
            toolName = call.function.name,
            displayName = tool?.displayName ?: call.function.name,
            actionSummary = actionSummary,
            integrationProtocol = when {
                call.function.name.startsWith("a2a__") -> "A2A"
                call.function.name.startsWith("mcp__") -> "MCP"
                else -> null
            },
            allowPersistentGrant = profile.persistentGrantAllowed,
            riskLevel = profile.risk,
            reason = ToolPolicy.approvalReason(profile),
            call = call,
        )
    }

    private fun isToolCallConfirmed(call: ToolCall, approvedCallIds: List<String>): Boolean {
        if (call.id in approvedCallIds) return true
        val profile = toolSecurityProfile(call.function.name)
        if (profile.risk != ToolRiskLevel.High) return true
        return !apiConfig.value.requireToolConfirmation &&
            !profile.mandatoryApproval
    }

    private fun toolSecurityProfile(toolName: String) = ToolPolicy.profileFor(
        toolName,
        toolRegistry.get(toolName)?.securityHints,
    )

    private suspend fun persistToolMessages(messages: List<ChatMessage>) {
        currentConversationId?.let { convId ->
            messages.forEach { message ->
                syncManager.saveMessage(
                    convId,
                    role = message.role,
                    content = message.content,
                    toolCallId = message.tool_call_id,
                    toolCallName = message.name,
                )
            }
        }
    }

    private suspend fun updateAgentCheckpoint(checkpoint: AgentExecutionCheckpoint?) {
        val run = activeTaskRun ?: return
        val snapshot = run.copy(agentExecution = checkpoint).withSteps(_uiState.value.taskSteps)
        activeTaskRun = snapshot
        agentRuntime.persist(snapshot)
        _uiState.value = _uiState.value.copy(taskRun = snapshot)
    }

    private suspend fun finalizeAgentResponse(
        startedAt: Long,
        artifacts: List<ArtifactMetadata>,
        usageSeen: Boolean,
        modelAnswered: Boolean,
    ) {
        val content = _uiState.value.streamingContent
        if (content.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                toolCallStatus = null,
                requestStartedAt = null,
                lastProcessingMs = System.currentTimeMillis() - startedAt,
            )
            return
        }
        val runBeforeReview = activeTaskRun?.withSteps(_uiState.value.taskSteps)
        val review = runBeforeReview?.let(agentRuntime::review)
        activeTaskRun = runBeforeReview?.let { agentRuntime.reviewed(it, review ?: return@let it) }
        val waitingForInput = _uiState.value.taskSteps.any { it.status == TaskStepStatus.WaitingForUser }
        val waitingForRetry = review?.decision == com.denggl2.mason.agent.AgentReviewDecision.WaitForUser &&
            _uiState.value.taskSteps.none { it.status == TaskStepStatus.WaitingForUser }
        val waiting = waitingForInput || waitingForRetry
        val finalSteps = if (waiting) {
            _uiState.value.taskSteps
                .updateStep(
                    "review",
                    if (waitingForRetry) TaskStepStatus.WaitingForUser else TaskStepStatus.Completed,
                    review?.detail ?: "等待用户继续",
                )
                .updateStep(
                    "summary",
                    TaskStepStatus.Completed,
                    if (waitingForRetry) "已保留失败步骤，等待用户重试或取消" else "已说明需要补充的信息",
                )
        } else {
            completeOpenTaskSteps(
                completeTaskStepUnlessFailed(
                    _uiState.value.taskSteps.updateStep(
                        "review",
                        TaskStepStatus.Completed,
                        review?.detail ?: "已检查执行结果",
                    ),
                    "summary",
                    "已生成最终回复",
                ),
            )
        }
        val finalContent = prepareAssistantContent(content, artifacts, finalSteps)
        val assistantMessage = ChatMessage(
            role = "assistant",
            content = finalContent,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + assistantMessage,
            isStreaming = false,
            streamingContent = "",
            toolCallStatus = null,
            requestStartedAt = null,
            lastProcessingMs = System.currentTimeMillis() - startedAt,
            lastUsageMissing = modelAnswered && !usageSeen,
            taskSteps = finalSteps,
            taskRun = activeTaskRun,
        )
        currentConversationId?.let { convId ->
            syncManager.saveMessage(convId, role = "assistant", content = finalContent)
        }
        if (!waiting) updateAgentCheckpoint(null)
        notifyTaskCompletedIfNeeded()
    }

    private fun formatGuidedError(message: String): String {
        if (!message.contains("API Key", ignoreCase = true)) {
            return """
            可以先检查网络、API 地址、模型名称和服务商额度；如果刚切换过模型，建议在设置里测试连接。
            ${summarizeModelError(message)}
            """.trimIndent()
        }
        return if (message.contains("API Key", ignoreCase = true)) {
            """
            请先进入左侧菜单的设置，选择服务商并配置 API Key；如果使用远端模型，也要确认 API 地址和模型名称。
            当前请求还没有发送给模型，所以 Mason 暂时无法继续处理。
            """.trimIndent()
        } else {
            """
            可以先检查网络、API 地址、模型名称和服务商额度；如果刚切换过模型，建议在设置里测试连接。
            请求失败：$message
            """.trimIndent()
        }
    }

    private fun selectedLocalModelId(config: ApiConfig): String =
        config.localModel.ifBlank { LocalModelCatalog.models.firstOrNull()?.id.orEmpty() }

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
        val stoppedRun = activeTaskRun?.withSteps(stoppedSteps)
        activeTaskRun = stoppedRun
        val stoppedContent = annotateCurrentTaskRun("已停止生成。", stoppedSteps)
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
            taskRun = stoppedRun,
        )
        currentConversationId?.let { convId ->
            viewModelScope.launch {
                syncManager.saveMessage(convId, role = "assistant", content = stoppedContent)
            }
        }
        viewModelScope.launch {
            stoppedRun?.let { agentRuntime.persist(it) }
            notifyTaskEvent(TaskNotificationEvent.Stopped)
        }
    }

    private suspend fun markGenerationInterrupted() {
        val state = _uiState.value
        if (!state.isStreaming) return
        val interruptedRun = activeTaskRun?.let { run ->
            agentRuntime.cancel(
                run.withSteps(state.taskSteps),
                reason = "已被新消息打断",
            )
        }
        activeTaskRun = interruptedRun
        val interruptedSteps = interruptedRun?.steps ?: state.taskSteps.map { step ->
            if (step.status == TaskStepStatus.Running || step.status == TaskStepStatus.Pending) {
                step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = "已被新消息打断",
                    finishedAt = System.currentTimeMillis(),
                )
            } else {
                step
            }
        }
        val visibleContent = state.streamingContent.trim()
        val interruptedContent = if (visibleContent.isBlank()) {
            "已被新消息打断。"
        } else {
            "$visibleContent\n\n已被新消息打断。"
        }
        val persistedContent = annotateCurrentTaskRun(interruptedContent, interruptedSteps)
        val message = ChatMessage(
            role = "assistant",
            content = persistedContent,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.value = state.copy(
            messages = state.messages + message,
            isStreaming = false,
            streamingContent = "",
            toolCallStatus = null,
            requestStartedAt = null,
            lastProcessingMs = state.requestStartedAt?.let { System.currentTimeMillis() - it },
            taskSteps = interruptedSteps,
            taskRun = interruptedRun,
        )
        currentConversationId?.let { conversationId ->
            syncManager.saveMessage(conversationId, role = "assistant", content = persistedContent)
        }
        interruptedRun?.let { agentRuntime.persist(it) }
        notifyTaskEvent(TaskNotificationEvent.Stopped)
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
        val failure = steps.firstOrNull { it.status == TaskStepStatus.Failed }?.error
        val reason = failure?.let(::interruptionReasonFor)
        val snapshot = activeTaskRun?.withSteps(steps)?.copy(
            lastError = failure ?: activeTaskRun?.lastError,
            interruptionReason = reason ?: activeTaskRun?.interruptionReason,
        )
        activeTaskRun = snapshot
        return annotateTaskRun(
            annotateModelParticipation(content, _uiState.value.modelContributions),
            snapshot,
        )
    }

    private fun interruptionReasonFor(error: String): TaskInterruptionReason =
        if (listOf("network", "timeout", "连接", "网络").any(error.lowercase()::contains)) {
            TaskInterruptionReason.NetworkInterrupted
        } else {
            TaskInterruptionReason.ExecutionFailed
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

    private fun failOpenTaskSteps(steps: List<TaskStep>, error: String): List<TaskStep> = steps.map { step ->
        when {
            step.status !in setOf(TaskStepStatus.Pending, TaskStepStatus.Running) -> step
            step.id == "execute" || step.id == "summary" -> step.copy(
                status = TaskStepStatus.Failed,
                detail = error,
                error = error,
                retryable = isRetryableModelError(error),
                finishedAt = System.currentTimeMillis(),
            )
            else -> step.copy(
                status = TaskStepStatus.Cancelled,
                detail = "前序步骤失败，未继续执行",
                finishedAt = System.currentTimeMillis(),
            )
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

    private suspend fun presentCapabilityRequirement(
        requirement: CapabilityRequirement,
        startedAt: Long,
    ) {
        val finalSteps = _uiState.value.taskSteps
            .updateStep("plan", TaskStepStatus.Completed, "已识别需要 ${requirement.displayName} 协作")
            .updateStep("prepare-inputs", TaskStepStatus.Completed, "已保留原任务和输入材料")
            .updateStep("execute", TaskStepStatus.WaitingForUser, requirement.detail)
            .updateStep("summary", TaskStepStatus.Completed, "已给出能力连接入口")
        val summary = "需要先连接 ${requirement.displayName} 才能继续。"
        val content = annotateCurrentTaskRun(
            summary + "\n\n" + requirement.toMarker(),
            finalSteps,
        )
        activeTaskRun?.let { agentRuntime.persist(it) }
        val message = ChatMessage(
            role = "assistant",
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message,
            isStreaming = false,
            streamingContent = "",
            requestStartedAt = null,
            lastProcessingMs = System.currentTimeMillis() - startedAt,
            taskSteps = finalSteps,
            taskRun = activeTaskRun,
        )
        currentConversationId?.let { conversationId ->
            syncManager.saveMessage(conversationId, role = "assistant", content = content)
        }
    }

    private suspend fun notifyTaskCompletedIfNeeded() {
        if (_uiState.value.taskSteps.any { it.status == TaskStepStatus.WaitingForUser }) return
        notifyTaskEvent(
            if (_uiState.value.taskSteps.any { it.status == TaskStepStatus.Failed }) {
                TaskNotificationEvent.Failed
            } else {
                TaskNotificationEvent.Completed
            },
        )
    }

    private suspend fun notifyTaskEvent(event: TaskNotificationEvent) {
        val preferences = uiPreferencesDataStore.preferences.first()
        if (!preferences.regularNotificationsEnabled && !preferences.islandNotificationsEnabled) return

        if (event == TaskNotificationEvent.Started) {
            notificationTool.execute(
                mapOf(
                    "title" to "Mason 正在处理任务",
                    "text" to "任务正在执行，可点按返回 Mason 查看进度",
                    NotificationTool.EXTRA_LIVE_UPDATE to "true",
                    NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "5",
                    NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to "执行中",
                    NotificationTool.EXTRA_CONVERSATION_ID to (activeTaskRun?.conversationId ?: currentConversationId)
                        ?.toString()
                        .orEmpty(),
                ),
            )
            return
        }

        val (title, text) = when (event) {
            TaskNotificationEvent.Started -> return
            TaskNotificationEvent.Completed -> "Mason 已完成" to
                taskCompletionNotificationText(activeTaskRun?.artifactPaths.orEmpty())
            TaskNotificationEvent.Failed -> "Mason 任务失败" to
                (activeTaskRun?.lastError ?: "任务未能完成，可返回 Mason 查看原因")
            TaskNotificationEvent.Paused -> "Mason 已暂停任务" to "任务已暂停，可返回 Mason 继续或取消"
            TaskNotificationEvent.Stopped -> "Mason 已停止任务" to "生成已停止，任务不会继续执行"
            TaskNotificationEvent.Cancelled -> "Mason 已取消任务" to "任务已取消，不会继续执行"
        }

        notificationTool.execute(
            mapOf(
                "title" to title,
                "text" to text,
                NotificationTool.EXTRA_LIVE_UPDATE to (
                    event in setOf(
                        TaskNotificationEvent.Paused,
                        TaskNotificationEvent.Completed,
                        TaskNotificationEvent.Failed,
                    )
                ).toString(),
                NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to if (event == TaskNotificationEvent.Paused) "50" else "100",
                NotificationTool.EXTRA_LIVE_UPDATE_FINAL to
                    (event == TaskNotificationEvent.Completed || event == TaskNotificationEvent.Failed).toString(),
                NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to when (event) {
                    TaskNotificationEvent.Paused -> "已暂停"
                    TaskNotificationEvent.Completed -> "已完成"
                    TaskNotificationEvent.Failed -> "失败"
                    else -> ""
                },
                "task_action" to if (event == TaskNotificationEvent.Paused) {
                    NotificationTool.TASK_ACTION_PAUSED
                } else {
                    ""
                },
                NotificationTool.EXTRA_ARTIFACT_PATH to if (event == TaskNotificationEvent.Completed) {
                    activeTaskRun?.artifactPaths?.lastOrNull().orEmpty()
                } else {
                    ""
                },
                NotificationTool.EXTRA_CONVERSATION_ID to (activeTaskRun?.conversationId ?: currentConversationId)
                    ?.toString()
                    .orEmpty(),
            ),
        )
    }

    private suspend fun refreshAutomaticSkillTool(content: String) {
        val refreshed = runCatching { skillRuntime.refresh() }.isSuccess
        val manuallySelected = content.contains("<mason-skill-instructions>")
        val tools = if (refreshed && !manuallySelected && skillRuntime.hasAvailableSkills()) {
            listOf(skillActivationTool)
        } else {
            emptyList()
        }
        toolRegistry.replaceNamespace(SkillActivationTool.TOOL_PREFIX, tools)
    }

    private fun parseToolArguments(raw: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) ->
            runCatching { value.jsonPrimitive.content }.getOrElse { value.toString() }
        }
    }.getOrDefault(emptyMap())
}

internal fun summarizeConversationTitle(content: String): String {
    val normalized = content
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('。', '！', '？', '!', '?', '.', '；', ';')
    if (normalized.isBlank()) return "新对话"

    val sentence = normalized.split(Regex("[。！？!?；;\\n]"))
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    val withoutPoliteness = sentence.replace(
        Regex("^(请|帮我|麻烦你|麻烦|能不能|可以帮我|请问|我想要|我需要)\\s*"),
        "",
    ).trim()
    val title = when {
        Regex("^(分析|解析|总结|梳理)").containsMatchIn(withoutPoliteness) ->
            "分析${withoutPoliteness.removePrefix("分析").trimStart()}"
        Regex("^(生成|制作|创建|写|设计)").containsMatchIn(withoutPoliteness) ->
            "${withoutPoliteness.substringBefore(' ').ifBlank { "处理" }}${withoutPoliteness.substringAfter(' ', "").trim()}"
        Regex("^(修复|解决|排查|检查)").containsMatchIn(withoutPoliteness) ->
            "${withoutPoliteness.substringBefore(' ').ifBlank { "处理" }}${withoutPoliteness.substringAfter(' ', "").trim()}"
        else -> withoutPoliteness.ifBlank { sentence }
    }
    return title.replace(Regex("\\s+"), " ").trim().removeSuffix("的问题").let {
        if (it.length <= 28) it else it.take(27).trimEnd() + "…"
    }
}

internal fun summarizeModelError(message: String): String {
    return summarizeModelErrorV2(message)
}

internal fun isRetryableModelError(message: String): Boolean {
    return isRetryableModelErrorV2(message)
}

internal fun isTaskContinuationCommand(content: String): Boolean {
    val normalized = content.trim().lowercase().replace(Regex("[，。！？,.!?]"), "")
    return normalized in setOf(
        "继续",
        "继续任务",
        "继续执行",
        "接着做",
        "接着继续",
        "continue",
        "resume",
        "keep going",
    )
}
