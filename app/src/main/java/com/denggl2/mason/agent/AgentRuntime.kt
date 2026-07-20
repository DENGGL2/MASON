package com.denggl2.mason.agent

import com.denggl2.mason.llm.model.ToolCall
import javax.inject.Inject
import javax.inject.Singleton

@kotlinx.serialization.Serializable
enum class AgentReviewDecision {
    Complete,
    Retry,
    WaitForUser,
}

data class AgentReview(
    val decision: AgentReviewDecision,
    val detail: String,
    val retryStepId: String? = null,
)

@Singleton
class AgentRuntime @Inject constructor(
    private val store: TaskRunStore,
) {
    fun begin(goal: String, conversationId: Long? = null): TaskRun {
        val initial = createTaskRun(goal)
        return initial.copy(
            conversationId = conversationId,
            agentPlan = plan(initial),
        )
    }

    suspend fun persist(run: TaskRun): TaskRun = store.save(run)

    suspend fun recover(conversationId: Long?): TaskRun? = store.recoverLatest(conversationId)

    suspend fun get(id: String): TaskRun? = store.get(id)

    fun resume(run: TaskRun): TaskRun {
        val steps = run.steps.map { step ->
            if (step.status == TaskStepStatus.WaitingForUser) {
                step.copy(
                    status = TaskStepStatus.Running,
                    detail = "任务已恢复，正在继续执行",
                    attempt = step.attempt + 1,
                    startedAt = System.currentTimeMillis(),
                )
            } else step
        }
        return run.copy(status = TaskRunStatus.Running, finishedAt = null).withSteps(steps)
    }

    fun pause(run: TaskRun): TaskRun = run.withSteps(
        run.steps.map { step ->
            if (step.status == TaskStepStatus.Running) {
                step.copy(
                    status = TaskStepStatus.WaitingForUser,
                    detail = "任务已暂停，可稍后继续",
                    finishedAt = null,
                )
            } else step
        },
    )

    fun cancel(run: TaskRun, reason: String = "用户已取消任务"): TaskRun = run.withSteps(
        run.steps.map { step ->
            if (step.status in openStepStatuses) {
                step.copy(
                    status = TaskStepStatus.Cancelled,
                    detail = reason,
                    finishedAt = System.currentTimeMillis(),
                )
            } else step
        },
    )

    fun retry(run: TaskRun, stepId: String): TaskRun = run.withSteps(
        run.steps.map { step ->
            if (step.id == stepId && step.retryable) {
                step.copy(
                    status = TaskStepStatus.Running,
                    detail = "正在重试 ${step.title}",
                    attempt = step.attempt + 1,
                    startedAt = System.currentTimeMillis(),
                    finishedAt = null,
                    error = null,
                )
            } else step
        },
    )

    fun review(run: TaskRun): AgentReview {
        val failed = run.steps.firstOrNull { it.status == TaskStepStatus.Failed }
        return when {
            run.steps.any { it.status == TaskStepStatus.WaitingForUser } -> AgentReview(
                AgentReviewDecision.WaitForUser,
                "任务正在等待用户确认或补充信息",
            )
            failed?.retryable == true &&
                failed.toolCall?.let { ToolPolicy.riskFor(it.function.name) == ToolRiskLevel.Low } == true &&
                failed.attempt < MAX_ATTEMPTS -> AgentReview(
                AgentReviewDecision.Retry,
                "${failed.title} 可以重试",
                failed.id,
            )
            failed != null -> AgentReview(AgentReviewDecision.WaitForUser, failed.error ?: failed.detail)
            else -> AgentReview(AgentReviewDecision.Complete, "任务步骤已完成并通过检查")
        }
    }

    fun reviewed(run: TaskRun, review: AgentReview): TaskRun = run.copy(
        agentPlan = (run.agentPlan ?: plan(run)).copy(
            reviewCount = (run.agentPlan?.reviewCount ?: 0) + 1,
            lastReviewDecision = review.decision,
            lastReviewDetail = review.detail,
        ),
    )

    fun shouldAutoRetry(run: TaskRun, stepId: String): Boolean {
        val step = run.steps.firstOrNull { it.id == stepId } ?: return false
        return step.retryable &&
            step.attempt < MAX_ATTEMPTS &&
            step.toolCall?.let { ToolPolicy.riskFor(it.function.name) == ToolRiskLevel.Low } == true
    }

    private fun plan(run: TaskRun): AgentPlanState = AgentPlanState(
        goal = run.goal,
        plannedStepIds = run.steps.map(TaskStep::id),
    )

    private companion object {
        // One initial execution plus one automatic retry keeps background work bounded.
        const val MAX_ATTEMPTS = 2
        val openStepStatuses = setOf(
            TaskStepStatus.Pending,
            TaskStepStatus.Running,
            TaskStepStatus.WaitingForUser,
        )
    }
}
