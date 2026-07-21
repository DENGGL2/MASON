package com.denggl2.mason.agent

import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunStateTest {
    @Test
    fun derivesWaitingAndCompletedRunStatesFromSteps() {
        val run = createTaskRun("处理文件")
        val waiting = run.withSteps(
            run.steps.updateStep("execute", TaskStepStatus.WaitingForUser, "等待确认"),
        )
        assertEquals(TaskRunStatus.WaitingForUser, waiting.status)
        assertEquals(MasonAgentRole.Executor, waiting.activeAgent)

        val completed = waiting.withSteps(waiting.steps.map { it.copy(status = TaskStepStatus.Completed) })
        assertEquals(TaskRunStatus.Completed, completed.status)
        assertNotNull(completed.finishedAt)
    }

    @Test
    fun pausedAndCancelledStepsKeepTheirDistinctRecoverableStates() {
        val run = createTaskRun("生成报告")
        val paused = run.withSteps(run.steps.updateStep("execute", TaskStepStatus.WaitingForUser, "已暂停"))
        val cancelled = paused.withSteps(
            paused.steps.map { step ->
                if (step.status in setOf(TaskStepStatus.Running, TaskStepStatus.WaitingForUser, TaskStepStatus.Pending)) {
                    step.copy(status = TaskStepStatus.Cancelled)
                } else {
                    step
                }
            },
        )

        assertEquals(TaskRunStatus.WaitingForUser, paused.status)
        assertEquals(TaskRunStatus.Cancelled, cancelled.status)
    }

    @Test
    fun restartRecoveryKeepsCheckpointAndMakesRunningStepsResumable() {
        val checkpoint = AgentExecutionCheckpoint(
            messages = listOf(ChatMessage(role = "user", content = "生成报告")),
            round = 1,
        )
        val run = createTaskRun("生成报告", now = 100L).copy(agentExecution = checkpoint)

        val recovered = run.recoverAfterProcessRestart(now = 200L)

        assertEquals(TaskRunStatus.WaitingForUser, recovered.status)
        assertEquals(TaskStepStatus.WaitingForUser, recovered.steps.first().status)
        assertEquals("应用已重新启动，可继续或取消此任务", recovered.steps.first().detail)
        assertEquals(checkpoint, recovered.agentExecution)
        assertEquals(200L, recovered.updatedAt)
        assertEquals(null, recovered.finishedAt)
    }

    @Test
    fun taskRunSchemaKeepsRecoveryMetadata() {
        val run = createTaskRun("生成报告").copy(
            conversationId = 42L,
            artifactPaths = listOf("/files/report.md"),
            summary = "报告已生成",
        )
        assertEquals(4, run.schemaVersion)
        assertEquals(42L, run.conversationId)
        assertTrue(run.artifactPaths.single().endsWith("report.md"))
    }

    @Test
    fun automaticSkillActivationCreatesSkillStep() {
        val call = ToolCall("skill-call", function = FunctionCall("skill__activate", "{}"))

        val steps = TaskStepFactory.initial("创建一个报告").withToolSteps(listOf(call))
        val skillStep = steps.single { it.toolCall?.id == "skill-call" }

        assertEquals(TaskStepKind.Skill, skillStep.kind)
        assertEquals("选择 Skill", skillStep.title)
    }

    @Test
    fun toolStepsAccumulateAcrossAgentRounds() {
        val first = ToolCall("skill-call", function = FunctionCall("skill__activate", "{}"))
        val second = ToolCall("read-call", function = FunctionCall("file_read", "{\"path\":\"a.txt\"}"))

        val steps = TaskStepFactory.initial("处理文件")
            .withToolSteps(listOf(first))
            .withToolSteps(listOf(second))

        assertTrue(steps.any { it.toolCall?.id == "skill-call" })
        assertTrue(steps.any { it.toolCall?.id == "read-call" })
    }

    @Test
    fun agentCheckpointStopsRoundsAndRepeatedCalls() {
        val repeated = ToolCall("call", function = FunctionCall("file_read", "{\"path\":\"a.txt\"}"))
        val fingerprint = repeated.fingerprint()
        val repeatedState = AgentExecutionCheckpoint(
            messages = emptyList(),
            callFingerprints = listOf(fingerprint, fingerprint),
        )
        val exhaustedState = AgentExecutionCheckpoint(messages = emptyList(), round = MAX_AGENT_TOOL_ROUNDS)

        assertTrue(!repeatedState.canExecute(listOf(repeated)))
        assertTrue(!exhaustedState.canExecute(listOf(repeated)))
        assertTrue(AgentExecutionCheckpoint(messages = emptyList()).canExecute(listOf(repeated)))
    }

    @Test
    fun taskRunMarkerRoundTripsPendingAgentApproval() {
        val call = ToolCall("write-call", function = FunctionCall("file_write", "{\"path\":\"report.md\"}"))
        val assistant = ChatMessage(role = "assistant", tool_calls = listOf(call))
        val checkpoint = AgentExecutionCheckpoint(
            messages = listOf(ChatMessage(role = "user", content = "生成报告")),
            round = 1,
            pendingAssistantMessage = assistant,
            pendingCalls = listOf(call),
            pendingApprovalCallId = call.id,
        )
        val run = createTaskRun("生成报告").copy(agentExecution = checkpoint)

        val restored = extractTaskRunMarker(annotateTaskRun("等待确认", run))

        assertNotNull(restored)
        assertEquals(1, restored?.agentExecution?.round)
        assertEquals("write-call", restored?.agentExecution?.pendingApprovalCallId)
        assertEquals("file_write", restored?.agentExecution?.pendingCalls?.single()?.function?.name)
    }

    @Test
    fun nestedTaskRunMarkersDoNotLeakCheckpointJsonIntoVisibleContent() {
        val innerContent = annotateTaskRun("previous answer", createTaskRun("previous task"))
        val checkpoint = AgentExecutionCheckpoint(
            messages = listOf(ChatMessage(role = "assistant", content = innerContent)),
        )
        val outerRun = createTaskRun("current task").copy(agentExecution = checkpoint)
        val annotated = annotateTaskRun("cancelled safely", outerRun)

        assertEquals("cancelled safely", stripTaskRunMarkers(annotated))
        val restored = extractTaskRunMarker(annotated)
        assertNotNull(restored)
        assertEquals("previous answer", restored?.agentExecution?.messages?.single()?.content)
    }

    @Test
    fun legacyRejectedRunCannotRestoreACompletedApproval() {
        val call = ToolCall("sensitive-call", function = FunctionCall("memory_save_sensitive", "{}"))
        val base = createTaskRun("remember address")
        val legacySteps = base.steps
            .updateStep("plan", TaskStepStatus.Running)
            .updateStep("execute", TaskStepStatus.Cancelled)
            .updateStep("summary", TaskStepStatus.Completed)
        val legacyRun = base.copy(
            status = TaskRunStatus.Running,
            steps = legacySteps,
            agentExecution = AgentExecutionCheckpoint(
                messages = emptyList(),
                pendingAssistantMessage = ChatMessage(role = "assistant", tool_calls = listOf(call)),
                pendingCalls = listOf(call),
                pendingApprovalCallId = call.id,
            ),
        )

        val restored = extractTaskRunMarker(annotateTaskRun("cancelled", legacyRun))

        assertEquals(TaskRunStatus.Cancelled, restored?.status)
        assertTrue(restored?.steps.orEmpty().none { it.status == TaskStepStatus.Running })
        assertEquals(null, restored?.agentExecution?.pendingApprovalCallId)
        assertTrue(restored?.agentExecution?.pendingCalls.orEmpty().isEmpty())
    }

    @Test
    fun sensitiveMemoryWritesAlwaysRequireOneTimeConfirmation() {
        assertTrue(ToolPolicy.requiresMandatoryApproval("memory_save_sensitive"))
        assertTrue(ToolPolicy.requiresUserApproval("memory_save_sensitive"))
        assertTrue(!ToolPolicy.canRememberApproval("memory_save_sensitive"))
        assertTrue(!ToolPolicy.requiresUserApproval("memory_save"))
    }

    @Test
    fun toolPolicyUsesOneProfileForRiskPermissionsAndBackgroundRules() {
        val location = ToolPolicy.profileFor("location")
        val remoteAgent = ToolPolicy.profileFor("a2a__wechat__delegate")

        assertEquals(ToolRiskLevel.Medium, location.risk)
        assertTrue(location.permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(location.backgroundAllowed)
        assertTrue(remoteAgent.mandatoryApproval)
        assertTrue(!remoteAgent.persistentGrantAllowed)
        assertTrue(!remoteAgent.backgroundAllowed)
    }

    @Test
    fun taskRunMarkerPreservesAgentPlanAndReviewCheckpoint() {
        val planned = createTaskRun("整理项目资料").copy(
            agentPlan = AgentPlanState(
                goal = "整理项目资料",
                plannedStepIds = listOf("plan", "execute", "review", "summary"),
                reviewCount = 1,
                lastReviewDecision = AgentReviewDecision.WaitForUser,
                lastReviewDetail = "等待用户补充文件",
            ),
        )

        val restored = extractTaskRunMarker(annotateTaskRun("等待补充", planned))

        assertEquals(4, restored?.schemaVersion)
        assertEquals(1, restored?.agentPlan?.reviewCount)
        assertEquals(AgentReviewDecision.WaitForUser, restored?.agentPlan?.lastReviewDecision)
        assertEquals("等待用户补充文件", restored?.agentPlan?.lastReviewDetail)
    }

    @Test
    fun lowRiskRetryNeverAppliesToSensitiveTools() {
        val safeCall = ToolCall("read", function = FunctionCall("file_read", "{}"))
        val riskyCall = ToolCall("write", function = FunctionCall("file_write", "{}"))

        assertEquals(ToolRiskLevel.Low, ToolPolicy.riskFor(safeCall.function.name))
        assertEquals(ToolRiskLevel.High, ToolPolicy.riskFor(riskyCall.function.name))
        assertTrue(TaskStepFactory.toolSteps(listOf(safeCall)).single().retryable)
        assertTrue(TaskStepFactory.toolSteps(listOf(riskyCall)).single().retryable)
    }

    @Test
    fun failedRetryableStepWaitsForUserAfterAutomaticRetriesAreExhausted() {
        val call = ToolCall("read", function = FunctionCall("file_read", "{}"))
        val failedRun = createTaskRun("读取文件").withSteps(
            TaskStepFactory.initial("读取文件")
                .withToolSteps(listOf(call))
                .updateStep(call.taskStepId(), TaskStepStatus.Failed, "文件暂时不可读")
                .map { step ->
                    if (step.id == call.taskStepId()) step.copy(attempt = 2, error = "文件暂时不可读") else step
                },
        )

        val review = reviewTaskRun(failedRun)

        assertEquals(AgentReviewDecision.WaitForUser, review.decision)
        assertEquals(call.taskStepId(), review.retryStepId)
        assertTrue(review.detail.contains("可重试"))
    }
}
