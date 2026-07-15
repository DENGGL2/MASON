package com.denggl2.mason.automation

import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationWorkflowLogicTest {
    @Test
    fun interpolatesKnownVariablesAndClearsMissingVariables() {
        assertEquals(
            "已生成 report.md，路径 ",
            AutomationWorkflowLogic.interpolate(
                "已生成 {{artifact_name}}，路径 {{missing}}",
                mapOf("artifact_name" to "report.md"),
            ),
        )
    }

    @Test
    fun evaluatesConditions() {
        val values = mapOf("result" to "AUTOMATION_OK")
        assertTrue(AutomationWorkflowLogic.conditionMatches(MasonAutomationCondition("result"), values))
        assertTrue(
            AutomationWorkflowLogic.conditionMatches(
                MasonAutomationCondition("result", "contains", "automation"),
                values,
            ),
        )
        assertFalse(
            AutomationWorkflowLogic.conditionMatches(
                MasonAutomationCondition("result", "equals", "failed"),
                values,
            ),
        )
        assertTrue(
            AutomationWorkflowLogic.conditionMatches(
                MasonAutomationCondition("result", "not_contains", "failed"),
                values,
            ),
        )
    }

    @Test
    fun normalizesLegacyActions() {
        val normalized = AutomationWorkflowLogic.normalizedActions(
            listOf(MasonAutomationAction(type = "notification")),
        ).single()
        assertEquals("step-1", normalized.id)
        assertEquals("发送通知", normalized.title)
    }

    @Test
    fun routesConditionalRequestsToModelPlanner() {
        assertTrue(AutomationWorkflowLogic.requiresModelPlanner("如果有日历事件，然后生成日报"))
        assertFalse(AutomationWorkflowLogic.requiresModelPlanner("每天十点生成日报并提醒我"))
    }

    @Test
    fun normalizesFencedStructuredOutputAndStringifiesArguments() {
        val normalized = AutomationWorkflowLogic.normalizeStructuredOutput(
            """```json
                {"name":"测试","actions":[{"type":"tool","arguments":{"limit":10,"enabled":true},"continue_on_failure":"true"}]}
                ```""".trimIndent(),
        )

        assertTrue(normalized.orEmpty().startsWith("{"))
        assertTrue(normalized.orEmpty().contains("\"limit\":\"10\""))
        assertTrue(normalized.orEmpty().contains("\"enabled\":\"true\""))
        assertTrue(normalized.orEmpty().contains("\"continue_on_failure\":true"))
    }

    @Test
    fun rejectsIncompleteStructuredOutput() {
        assertEquals(null, AutomationWorkflowLogic.normalizeStructuredOutput("{\"name\":\"未完成\""))
    }

    @Test
    fun recognizesManualConditionalAutomationRequest() {
        val request = "创建自动化：如果日历有事件然后生成日报并提醒我"
        assertTrue(AutomationWorkflowLogic.requiresModelPlanner(request))
        assertTrue(AutomationDraftService.looksLikeAutomationRequest(request))
    }
}
