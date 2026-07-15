package com.denggl2.mason.integration

import android.util.Base64
import com.denggl2.mason.agent.MasonAgentRole
import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepKind
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.withSteps
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.tool.ToolResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class A2aTaskBridge @Inject constructor(
    private val client: A2aClient,
    private val taskRunStore: TaskRunStore,
    private val artifactStore: ArtifactStore,
) {
    suspend fun delegate(
        config: A2aAgentConfig,
        agent: A2aAgentDescriptor,
        goal: String,
        parentTaskRunId: String?,
    ): ToolResult {
        val stepId = "a2a:${config.id.integrationNamespace()}"
        var run = parentTaskRunId?.let { taskRunStore.get(it) }
            ?: standaloneRun(goal, agent.name, stepId)
        run = run.withExternalStep(stepId, agent.name, TaskStepStatus.Running, "已交给 ${agent.name} 处理")
        taskRunStore.save(run)
        return runCatching { client.sendTask(config, agent, goal) }
            .fold(
                onSuccess = { result ->
                    val artifactPaths = saveArtifacts(result)
                    val stepStatus = result.state.toStepStatus()
                    val next = run.withExternalStep(stepId, agent.name, stepStatus, result.summary).copy(
                        summary = result.summary,
                        artifactPaths = (run.artifactPaths + artifactPaths).distinct(),
                        lastError = if (stepStatus == TaskStepStatus.Failed) result.summary else null,
                    )
                    taskRunStore.save(next)
                    if (stepStatus == TaskStepStatus.Failed) {
                        ToolResult.error(result.summary)
                    } else {
                        ToolResult.success(buildMap {
                            put("agent", agent.name)
                            put("state", result.state)
                            put("summary", result.summary)
                            result.externalTaskId?.let { put("externalTaskId", it) }
                            if (artifactPaths.isNotEmpty()) put("artifactPaths", artifactPaths.joinToString("\n"))
                        })
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: error.javaClass.simpleName
                    val failed = run.withExternalStep(stepId, agent.name, TaskStepStatus.Failed, message).copy(lastError = message)
                    taskRunStore.save(failed)
                    ToolResult.error(message)
                },
            )
    }

    private suspend fun saveArtifacts(result: A2aTaskResult): List<String> = result.artifacts.mapNotNull { artifact ->
        runCatching {
            when {
                !artifact.text.isNullOrBlank() -> artifactStore.saveTextArtifact(
                    artifact.name.withExtensionFor(artifact.mimeType),
                    artifact.text,
                )
                !artifact.base64.isNullOrBlank() -> artifactStore.saveBinaryArtifact(
                    artifact.name.withExtensionFor(artifact.mimeType),
                    Base64.decode(artifact.base64, Base64.DEFAULT),
                    artifact.mimeType,
                )
                !artifact.url.isNullOrBlank() -> artifactStore.saveTextArtifact(
                    artifact.name.withExtensionFor("text/uri-list"),
                    artifact.url,
                )
                else -> null
            }?.path
        }.getOrNull()
    }

    private fun standaloneRun(goal: String, agentName: String, stepId: String): TaskRun {
        val now = System.currentTimeMillis()
        return TaskRun(
            id = UUID.randomUUID().toString(),
            goal = goal.take(500),
            status = TaskRunStatus.Running,
            steps = listOf(externalStep(stepId, agentName, TaskStepStatus.Running, "正在发送任务")),
            createdAt = now,
            updatedAt = now,
            activeAgent = MasonAgentRole.Executor,
        )
    }

    private fun TaskRun.withExternalStep(
        stepId: String,
        agentName: String,
        status: TaskStepStatus,
        detail: String,
    ): TaskRun {
        val existing = steps.any { it.id == stepId }
        val nextSteps = if (existing) {
            steps.map { step ->
                if (step.id == stepId) step.copy(
                    status = status,
                    detail = detail,
                    error = detail.takeIf { status == TaskStepStatus.Failed },
                    finishedAt = System.currentTimeMillis().takeIf { status in terminalStepStates },
                ) else step
            }
        } else {
            steps + externalStep(stepId, agentName, status, detail)
        }
        return withSteps(nextSteps)
    }

    private fun externalStep(
        stepId: String,
        agentName: String,
        status: TaskStepStatus,
        detail: String,
    ) = TaskStep(
        id = stepId,
        title = "委派给 $agentName",
        detail = detail,
        status = status,
        kind = TaskStepKind.Tool,
        attempt = 1,
        startedAt = System.currentTimeMillis(),
        finishedAt = System.currentTimeMillis().takeIf { status in terminalStepStates },
        retryable = true,
    )

    private companion object {
        val terminalStepStates = setOf(TaskStepStatus.Completed, TaskStepStatus.Failed, TaskStepStatus.Cancelled)
    }
}

private fun String.toStepStatus(): TaskStepStatus = when (uppercase()) {
    "TASK_STATE_COMPLETED", "COMPLETED" -> TaskStepStatus.Completed
    "TASK_STATE_FAILED", "TASK_STATE_REJECTED", "FAILED", "REJECTED" -> TaskStepStatus.Failed
    "TASK_STATE_CANCELED", "CANCELED", "CANCELLED" -> TaskStepStatus.Cancelled
    "TASK_STATE_INPUT_REQUIRED", "TASK_STATE_AUTH_REQUIRED", "INPUT_REQUIRED", "AUTH_REQUIRED" -> TaskStepStatus.WaitingForUser
    else -> TaskStepStatus.Completed
}

private fun String.withExtensionFor(mimeType: String): String {
    if (substringAfterLast('.', "").isNotBlank()) return this
    val extension = when (mimeType.substringBefore(';').lowercase()) {
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "text/uri-list" -> "url.txt"
        "application/json" -> "json"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "application/pdf" -> "pdf"
        else -> "bin"
    }
    return "$this.$extension"
}
