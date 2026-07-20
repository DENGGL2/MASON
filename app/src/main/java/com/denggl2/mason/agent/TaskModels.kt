package com.denggl2.mason.agent

import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.llm.model.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class TaskStepStatus {
    Pending,
    Running,
    WaitingForUser,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
enum class TaskStepKind {
    Understand,
    PrepareInputs,
    Model,
    Skill,
    Tool,
    Review,
    Deliver,
}

@Serializable
data class TaskStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: TaskStepStatus,
    val kind: TaskStepKind = TaskStepKind.Model,
    val attempt: Int = 0,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val error: String? = null,
    val retryable: Boolean = false,
    val toolCall: ToolCall? = null,
)

@Serializable
enum class TaskRunStatus {
    Running,
    WaitingForUser,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
data class TaskRun(
    val id: String,
    val goal: String,
    val status: TaskRunStatus,
    val steps: List<TaskStep>,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val conversationId: Long? = null,
    val updatedAt: Long = createdAt,
    val activeAgent: MasonAgentRole = MasonAgentRole.Planner,
    val summary: String? = null,
    val artifactPaths: List<String> = emptyList(),
    val lastError: String? = null,
    val agentExecution: AgentExecutionCheckpoint? = null,
    val agentPlan: AgentPlanState? = null,
    val schemaVersion: Int = 4,
)

/** Persistent orchestration state. It intentionally contains no hidden model reasoning. */
@Serializable
data class AgentPlanState(
    val goal: String,
    val plannedStepIds: List<String>,
    val source: String = "deterministic",
    val reviewCount: Int = 0,
    val lastReviewDecision: AgentReviewDecision? = null,
    val lastReviewDetail: String? = null,
)

@Serializable
data class AgentExecutionCheckpoint(
    val messages: List<ChatMessage>,
    val round: Int = 0,
    val callFingerprints: List<String> = emptyList(),
    val pendingAssistantMessage: ChatMessage? = null,
    val pendingCalls: List<ToolCall> = emptyList(),
    val pendingApprovalCallId: String? = null,
    val approvedCallIds: List<String> = emptyList(),
    val waitingForInput: Boolean = false,
)

enum class ToolRiskLevel {
    Low,
    Medium,
    High,
}

data class ToolApprovalRequest(
    val toolName: String,
    val displayName: String = toolName,
    val actionSummary: String = "",
    val integrationProtocol: String? = null,
    val allowPersistentGrant: Boolean = true,
    val riskLevel: ToolRiskLevel,
    val reason: String,
    val call: ToolCall,
)

object ToolPolicy {
    private val highRiskTools = setOf(
        "sms",
        "file_delete",
        "file_write",
        "run_shell",
        "system_setting",
        "app_launcher",
        "app_manager",
        "alarm",
        "calendar",
        "camera",
        "audio_record",
        "screenshot",
        "notification",
        "memory_save_sensitive",
    )

    private val mediumRiskTools = setOf(
        "contacts",
        "call_log",
        "location",
        "clipboard",
        "http_request",
    )

    fun riskFor(toolName: String): ToolRiskLevel = when {
        toolName.startsWith("mcp__") || toolName.startsWith("a2a__") -> ToolRiskLevel.High
        toolName in highRiskTools -> ToolRiskLevel.High
        toolName in mediumRiskTools -> ToolRiskLevel.Medium
        else -> ToolRiskLevel.Low
    }

    fun requiresUserApproval(toolName: String): Boolean =
        riskFor(toolName) != ToolRiskLevel.Low

    fun requiresMandatoryApproval(toolName: String): Boolean =
        toolName.startsWith("a2a__") || toolName == "memory_save_sensitive"

    fun canRememberApproval(toolName: String): Boolean = !requiresMandatoryApproval(toolName)

    fun approvalReason(toolName: String): String = when (riskFor(toolName)) {
        ToolRiskLevel.High -> "这个操作可能修改手机状态、写入数据、发送消息、打开应用，或捕获敏感信息。"
        ToolRiskLevel.Medium -> "这个操作可能读取隐私数据、位置、剪贴板、联系人，或访问外部网络内容。"
        ToolRiskLevel.Low -> "这个操作主要是只读或低风险。"
    }
}

object TaskStepFactory {
    fun initial(goal: String, now: Long = System.currentTimeMillis()): List<TaskStep> = buildList {
        add(
            TaskStep(
                id = "plan",
                title = "理解请求",
                detail = "识别目标、约束和需要的能力",
                status = TaskStepStatus.Running,
                kind = TaskStepKind.Understand,
                attempt = 1,
                startedAt = now,
            ),
        )
        if (goal.contains("<mason-skill-instructions>")) {
            add(
                TaskStep(
                    id = "skill",
                    title = "运行 Skill",
                    detail = "按已启用 Skill 的受控说明处理任务",
                    status = TaskStepStatus.Pending,
                    kind = TaskStepKind.Skill,
                ),
            )
        }
        if (goal.needsInputPreparation()) {
            add(
                TaskStep(
                    id = "prepare-inputs",
                    title = "整理材料",
                    detail = "检查消息中的文件、图片、链接或附加上下文",
                    status = TaskStepStatus.Pending,
                    kind = TaskStepKind.PrepareInputs,
                ),
            )
        }
        add(
            TaskStep(
                id = "execute",
                title = "生成方案",
                detail = "调用当前模型生成回答或工具计划",
                status = TaskStepStatus.Pending,
                kind = TaskStepKind.Model,
            ),
        )
        add(
            TaskStep(
                id = "review",
                title = "核对结果",
                detail = "检查执行结果和回答是否完整",
                status = TaskStepStatus.Pending,
                kind = TaskStepKind.Review,
            ),
        )
        add(
            TaskStep(
                id = "summary",
                title = "整理回复",
                detail = "保存产出并生成最终回复",
                status = TaskStepStatus.Pending,
                kind = TaskStepKind.Deliver,
            ),
        )
    }

    fun toolSteps(calls: List<ToolCall>): List<TaskStep> = calls.map { call ->
        TaskStep(
            id = call.taskStepId(),
            title = toolTitle(call.function.name),
            detail = if (call.function.name == "skill__activate") {
                "正在从已启用能力中选择"
            } else {
                "准备调用 ${call.function.name}"
            },
            status = TaskStepStatus.Pending,
            kind = if (call.function.name == "skill__activate") TaskStepKind.Skill else TaskStepKind.Tool,
            retryable = call.function.name != "skill__activate",
            toolCall = call,
        )
    }

    private fun String.needsInputPreparation(): Boolean {
        val normalized = lowercase()
        return listOf(
            "文件", "图片", "照片", "附件", "材料", "链接", "网页", "文档",
            "file", "image", "photo", "attachment", "document", "url", "http",
            "mason 附加上下文",
        ).any(normalized::contains)
    }

    private fun toolTitle(toolName: String): String = when (toolName) {
        "skill__activate" -> "选择 Skill"
        "file_write" -> "生成文件"
        "file_delete" -> "删除文件"
        "http_request" -> "访问网络"
        "app_launcher" -> "打开应用"
        "system_setting" -> "修改系统设置"
        "sms" -> "发送短信"
        "calendar" -> "处理日历"
        "alarm" -> "处理闹钟"
        "camera" -> "使用相机"
        "location" -> "读取位置"
        else -> "执行 $toolName"
    }
}

fun ToolCall.taskStepId(): String = "tool:${id.ifBlank { function.name }}"

fun List<TaskStep>.updateStep(
    id: String,
    status: TaskStepStatus,
    detail: String? = null,
): List<TaskStep> = map { step ->
    if (step.id == id) {
        step.copy(
            status = status,
            detail = detail ?: step.detail,
            attempt = if (status == TaskStepStatus.Running && step.status != TaskStepStatus.Running) {
                step.attempt + 1
            } else {
                step.attempt
            },
            startedAt = if (status == TaskStepStatus.Running && step.status != TaskStepStatus.Running) {
                System.currentTimeMillis()
            } else {
                step.startedAt
            },
            finishedAt = if (status in terminalStepStatuses) System.currentTimeMillis() else null,
            error = if (status == TaskStepStatus.Failed) detail ?: step.error else null,
        )
    } else {
        step
    }
}

fun List<TaskStep>.withToolSteps(calls: List<ToolCall>): List<TaskStep> {
    val existingIds = map(TaskStep::id).toSet()
    val newSteps = TaskStepFactory.toolSteps(calls).filterNot { it.id in existingIds }
    if (newSteps.isEmpty()) return this
    val reviewIndex = indexOfFirst { it.kind == TaskStepKind.Review }
        .takeIf { it >= 0 }
        ?: size
    return toMutableList().apply {
        addAll(reviewIndex, newSteps)
    }
}

fun ToolCall.fingerprint(): String = "${function.name}:${function.arguments.trim()}"

fun AgentExecutionCheckpoint.canExecute(calls: List<ToolCall>): Boolean {
    if (round >= MAX_AGENT_TOOL_ROUNDS) return false
    val previous = callFingerprints.groupingBy { it }.eachCount()
    return calls.none { (previous[it.fingerprint()] ?: 0) >= MAX_IDENTICAL_TOOL_CALLS }
}

const val MAX_AGENT_TOOL_ROUNDS = 8
const val MAX_IDENTICAL_TOOL_CALLS = 2

fun TaskRun.withSteps(nextSteps: List<TaskStep>): TaskRun {
    val nextStatus = when {
        nextSteps.any { it.status == TaskStepStatus.WaitingForUser } -> TaskRunStatus.WaitingForUser
        nextSteps.any { it.status == TaskStepStatus.Running } -> TaskRunStatus.Running
        nextSteps.any { it.status == TaskStepStatus.Failed } -> TaskRunStatus.Failed
        nextSteps.any { it.status == TaskStepStatus.Cancelled } -> TaskRunStatus.Cancelled
        nextSteps.isNotEmpty() && nextSteps.all { it.status == TaskStepStatus.Completed } -> TaskRunStatus.Completed
        else -> TaskRunStatus.Running
    }
    return copy(
        status = nextStatus,
        steps = nextSteps,
        finishedAt = if (nextStatus in terminalRunStatuses) System.currentTimeMillis() else null,
        updatedAt = System.currentTimeMillis(),
        activeAgent = when {
            nextSteps.any { it.status == TaskStepStatus.WaitingForUser } -> MasonAgentRole.Executor
            nextSteps.any { it.kind == TaskStepKind.Review && it.status == TaskStepStatus.Running } -> MasonAgentRole.Reviewer
            nextSteps.any { it.kind == TaskStepKind.Deliver && it.status == TaskStepStatus.Running } -> MasonAgentRole.Summarizer
            nextSteps.any {
                it.kind == TaskStepKind.Tool || it.kind == TaskStepKind.Model || it.kind == TaskStepKind.Skill
            } -> MasonAgentRole.Executor
            else -> MasonAgentRole.Planner
        },
    )
}

private val terminalStepStatuses = setOf(
    TaskStepStatus.Completed,
    TaskStepStatus.Failed,
    TaskStepStatus.Cancelled,
)

private val terminalRunStatuses = setOf(
    TaskRunStatus.Completed,
    TaskRunStatus.Failed,
    TaskRunStatus.Cancelled,
)

private const val TASK_RUN_MARKER_PREFIX = "<!-- mason-task-run"
private const val TASK_RUN_MARKER_SUFFIX = "-->"
private const val TASK_GOAL_MAX_CHARS = 500
private const val USER_CONTEXT_SEPARATOR = "\n---\nMason 附加上下文"
private val taskRunJson = Json { ignoreUnknownKeys = true }

fun createTaskRun(goal: String, now: Long = System.currentTimeMillis()): TaskRun = TaskRun(
    id = UUID.randomUUID().toString(),
    goal = goal.substringBefore(USER_CONTEXT_SEPARATOR).trim().take(TASK_GOAL_MAX_CHARS),
    status = TaskRunStatus.Running,
    steps = TaskStepFactory.initial(goal, now),
    createdAt = now,
)

fun annotateTaskRun(content: String, taskRun: TaskRun?): String {
    if (taskRun == null) return content
    val sanitizedTaskRun = taskRun.withoutEmbeddedTaskRunMarkers()
    return content.trimEnd() + "\n\n$TASK_RUN_MARKER_PREFIX ${taskRunJson.encodeToString(sanitizedTaskRun)} $TASK_RUN_MARKER_SUFFIX"
}

fun extractTaskRunMarker(content: String): TaskRun? = findLastTaskRunMarker(content)?.taskRun

fun stripTaskRunMarkers(content: String): String {
    var stripped = content
    while (true) {
        val marker = findLastTaskRunMarker(stripped) ?: break
        stripped = stripped.removeRange(marker.startIndex, marker.endExclusive)
    }
    return stripped.trimEnd()
}

private fun TaskRun.withoutEmbeddedTaskRunMarkers(): TaskRun = copy(
    agentExecution = agentExecution?.copy(
        messages = agentExecution.messages.map(ChatMessage::withoutTaskRunMarkers),
        pendingAssistantMessage = agentExecution.pendingAssistantMessage?.withoutTaskRunMarkers(),
    ),
)

private fun ChatMessage.withoutTaskRunMarkers(): ChatMessage = copy(
    content = content?.let(::stripTaskRunMarkers),
)

private fun findLastTaskRunMarker(content: String): ParsedTaskRunMarker? {
    val prefixIndexes = content.allIndexesOf(TASK_RUN_MARKER_PREFIX).asReversed()
    val suffixIndexes = content.allIndexesOf(TASK_RUN_MARKER_SUFFIX).asReversed()
    for (startIndex in prefixIndexes) {
        val payloadStart = startIndex + TASK_RUN_MARKER_PREFIX.length
        for (suffixIndex in suffixIndexes) {
            if (suffixIndex <= payloadStart) continue
            val taskRun = runCatching {
                taskRunJson.decodeFromString<TaskRun>(content.substring(payloadStart, suffixIndex).trim())
            }.getOrNull()?.normalizeLegacyCancelledRun() ?: continue
            return ParsedTaskRunMarker(
                taskRun = taskRun,
                startIndex = startIndex,
                endExclusive = suffixIndex + TASK_RUN_MARKER_SUFFIX.length,
            )
        }
    }
    return null
}

private fun TaskRun.normalizeLegacyCancelledRun(): TaskRun {
    val summaryCompleted = steps.any { step ->
        step.kind == TaskStepKind.Deliver && step.status == TaskStepStatus.Completed
    }
    if (!summaryCompleted || steps.none { it.status == TaskStepStatus.Cancelled }) return this
    val normalizedSteps = steps.map { step ->
        if (step.status == TaskStepStatus.Pending ||
            step.status == TaskStepStatus.Running ||
            step.status == TaskStepStatus.WaitingForUser
        ) {
            step.copy(status = TaskStepStatus.Cancelled, finishedAt = finishedAt ?: updatedAt)
        } else step
    }
    return copy(
        agentExecution = agentExecution?.copy(
            pendingAssistantMessage = null,
            pendingCalls = emptyList(),
            pendingApprovalCallId = null,
        ),
    ).withSteps(normalizedSteps)
}

private fun String.allIndexesOf(value: String): List<Int> = buildList {
    var startIndex = 0
    while (startIndex < this@allIndexesOf.length) {
        val index = this@allIndexesOf.indexOf(value, startIndex)
        if (index < 0) break
        add(index)
        startIndex = index + value.length
    }
}

private data class ParsedTaskRunMarker(
    val taskRun: TaskRun,
    val startIndex: Int,
    val endExclusive: Int,
)
