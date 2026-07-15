package com.denggl2.mason.automation

import com.denggl2.mason.agent.MasonAgentRole
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationStepLog
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationExecutionPlan(
    val phases: List<MasonAgentRole>,
    val declaredOutputs: Set<String>,
)

@Singleton
class AutomationOrchestrator @Inject constructor() {
    fun plan(spec: MasonAutomationSpec): AutomationExecutionPlan {
        require(spec.actions.isNotEmpty()) { "自动化没有执行步骤" }
        val ids = spec.actions.mapIndexed { index, action -> action.id.ifBlank { "step-${index + 1}" } }
        require(ids.distinct().size == ids.size) { "自动化步骤 ID 不能重复" }
        val available = mutableSetOf(
            "artifact_name", "artifact_path", "model_output",
            "latitude", "longitude", "ssid", "device", "package", "title", "text",
        )
        spec.actions.forEach { action ->
            if (action.inputKey.isNotBlank()) {
                require(action.inputKey in available) { "步骤 ${action.title} 引用了尚未生成的输入 ${action.inputKey}" }
            }
            action.condition?.key?.takeIf(String::isNotBlank)?.let { key ->
                require(key in available) { "步骤 ${action.title} 的条件引用了未知变量 $key" }
            }
            action.outputKey.takeIf(String::isNotBlank)?.let(available::add)
        }
        return AutomationExecutionPlan(
            phases = listOf(
                MasonAgentRole.Planner,
                MasonAgentRole.Executor,
                MasonAgentRole.Reviewer,
                MasonAgentRole.Summarizer,
            ),
            declaredOutputs = available,
        )
    }

    fun review(stepLogs: List<MasonAutomationStepLog>, artifactPath: String?): String {
        val failed = stepLogs.filter { it.status == "failed" }
        if (failed.isNotEmpty()) return "${failed.size} 个步骤失败：${failed.first().message}"
        val completed = stepLogs.count { it.status == "success" }
        val skipped = stepLogs.count { it.status == "skipped" }
        return buildString {
            append("$completed 个步骤执行成功")
            if (skipped > 0) append("，$skipped 个步骤因条件未满足而跳过")
            if (artifactPath != null) append("，产出已保存")
        }
    }

    fun summarize(spec: MasonAutomationSpec, review: String, failed: Boolean): String =
        if (failed) "${spec.name} 未完成：$review" else "${spec.name} 已完成：$review"
}
