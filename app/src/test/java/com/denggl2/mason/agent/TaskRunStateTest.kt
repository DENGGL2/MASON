package com.denggl2.mason.agent

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
    fun taskRunSchemaKeepsRecoveryMetadata() {
        val run = createTaskRun("生成报告").copy(
            conversationId = 42L,
            artifactPaths = listOf("/files/report.md"),
            summary = "报告已生成",
        )
        assertEquals(2, run.schemaVersion)
        assertEquals(42L, run.conversationId)
        assertTrue(run.artifactPaths.single().endsWith("report.md"))
    }
}
