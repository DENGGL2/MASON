package com.denggl2.mason.agent

import com.denggl2.mason.llm.model.ToolCall

enum class TaskStepStatus {
    Pending,
    Running,
    WaitingForUser,
    Completed,
    Failed,
    Cancelled,
}

data class TaskStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: TaskStepStatus,
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
    fun initial(): List<TaskStep> = listOf(
        TaskStep(
            id = "plan",
            title = "规划",
            detail = "理解目标，判断需要的模型、技能、工具和权限",
            status = TaskStepStatus.Running,
        ),
        TaskStep(
            id = "execute",
            title = "执行",
            detail = "按计划调用模型、技能、手机能力或外部接口",
            status = TaskStepStatus.Pending,
        ),
        TaskStep(
            id = "review",
            title = "检查",
            detail = "核对执行结果，判断是否需要补充确认或重试",
            status = TaskStepStatus.Pending,
        ),
        TaskStep(
            id = "summary",
            title = "总结",
            detail = "整理最终回复、产出物和下一步建议",
            status = TaskStepStatus.Pending,
        ),
    )
}

fun List<TaskStep>.updateStep(
    id: String,
    status: TaskStepStatus,
    detail: String? = null,
): List<TaskStep> = map { step ->
    if (step.id == id) {
        step.copy(
            status = status,
            detail = detail ?: step.detail,
        )
    } else {
        step
    }
}
