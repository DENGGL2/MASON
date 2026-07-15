package com.denggl2.mason.automation

import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutomationOrchestratorTest {
    private val orchestrator = AutomationOrchestrator()

    @Test
    fun acceptsOrderedOutputDependencies() {
        val spec = spec(
            listOf(
                MasonAutomationAction(type = "tool", outputKey = "calendar_data"),
                MasonAutomationAction(type = "model_artifact", inputKey = "calendar_data"),
            ),
        )
        assertEquals(4, orchestrator.plan(spec).phases.size)
    }

    @Test
    fun rejectsUnknownInputDependency() {
        val spec = spec(listOf(MasonAutomationAction(type = "model_artifact", inputKey = "unknown")))
        assertThrows(IllegalArgumentException::class.java) { orchestrator.plan(spec) }
    }

    private fun spec(actions: List<MasonAutomationAction>) = MasonAutomationSpec(
        id = "test",
        name = "test",
        trigger = MasonAutomationTrigger("manual"),
        actions = actions,
    )
}
