package com.denggl2.mason.agent

import com.denggl2.mason.llm.model.ToolCall
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
    val schemaVersion: Int = 1,
)

enum class ToolRiskLevel {
    Low,
    Medium,
    High,
}

data class ToolApprovalRequest(
    val toolName: String,
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
    )

    private val mediumRiskTools = setOf(
        "contacts",
        "call_log",
        "location",
        "clipboard",
        "http_request",
    )

    fun riskFor(toolName: String): ToolRiskLevel = when (toolName) {
        in highRiskTools -> ToolRiskLevel.High
        in mediumRiskTools -> ToolRiskLevel.Medium
        else -> ToolRiskLevel.Low
    }

    fun requiresUserApproval(toolName: String): Boolean =
        riskFor(toolName) != ToolRiskLevel.Low

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
            detail = "准备调用 ${call.function.name}",
            status = TaskStepStatus.Pending,
            kind = TaskStepKind.Tool,
            retryable = true,
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
    val withoutOldTools = filterNot { it.kind == TaskStepKind.Tool }
    val reviewIndex = withoutOldTools.indexOfFirst { it.kind == TaskStepKind.Review }
        .takeIf { it >= 0 }
        ?: withoutOldTools.size
    return withoutOldTools.toMutableList().apply {
        addAll(reviewIndex, TaskStepFactory.toolSteps(calls))
    }
}

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
private val taskRunJson = Json { ignoreUnknownKeys = true }

fun createTaskRun(goal: String, now: Long = System.currentTimeMillis()): TaskRun = TaskRun(
    id = UUID.randomUUID().toString(),
    goal = goal,
    status = TaskRunStatus.Running,
    steps = TaskStepFactory.initial(goal, now),
    createdAt = now,
)

fun annotateTaskRun(content: String, taskRun: TaskRun?): String {
    if (taskRun == null) return content
    return content.trimEnd() + "\n\n$TASK_RUN_MARKER_PREFIX ${taskRunJson.encodeToString(taskRun)} $TASK_RUN_MARKER_SUFFIX"
}

fun extractTaskRunMarker(content: String): TaskRun? = taskRunMarkerRegex()
    .findAll(content)
    .lastOrNull()
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.let { encoded -> runCatching { taskRunJson.decodeFromString<TaskRun>(encoded) }.getOrNull() }

fun stripTaskRunMarkers(content: String): String =
    content.replace(taskRunMarkerRegex(), "").trimEnd()

private fun taskRunMarkerRegex(): Regex = Regex(
    Regex.escape(TASK_RUN_MARKER_PREFIX) + """\s+([\s\S]*?)\s+""" + Regex.escape(TASK_RUN_MARKER_SUFFIX),
)
