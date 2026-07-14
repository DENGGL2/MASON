package com.denggl2.mason.agent

enum class MasonAgentRole {
    Planner,
    Executor,
    Reviewer,
    Summarizer,
}

data class MasonAgent(
    val role: MasonAgentRole,
    val title: String,
    val goal: String,
    val allowedToolRisk: ToolRiskLevel,
)

data class MasonTaskPlan(
    val userGoal: String,
    val agents: List<MasonAgent>,
    val steps: List<TaskStep>,
)

object MasonAgentCatalog {
    val defaultAgents = listOf(
        MasonAgent(
            role = MasonAgentRole.Planner,
            title = "规划",
            goal = "拆解用户目标，判断需要哪些模型、工具和权限",
            allowedToolRisk = ToolRiskLevel.Low,
        ),
        MasonAgent(
            role = MasonAgentRole.Executor,
            title = "执行",
            goal = "按计划调用模型、工具、技能和自动化能力",
            allowedToolRisk = ToolRiskLevel.Medium,
        ),
        MasonAgent(
            role = MasonAgentRole.Reviewer,
            title = "检查",
            goal = "检查执行结果是否完整、是否需要补充确认或重试",
            allowedToolRisk = ToolRiskLevel.Low,
        ),
        MasonAgent(
            role = MasonAgentRole.Summarizer,
            title = "总结",
            goal = "把过程、产出物和下一步行动整理给用户",
            allowedToolRisk = ToolRiskLevel.Low,
        ),
    )
}

object MasonTaskPlanner {
    fun createInitialPlan(userGoal: String): MasonTaskPlan =
        MasonTaskPlan(
            userGoal = userGoal,
            agents = MasonAgentCatalog.defaultAgents,
            steps = TaskStepFactory.initial(userGoal),
        )
}
