package com.denggl2.mason.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationIntentTest {
    @Test
    fun detectsExplicitAndRecurringAutomationRequests() {
        assertTrue(AutomationDraftService.looksLikeAutomationRequest("创建自动化：每天十点提醒我"))
        assertTrue(AutomationDraftService.looksLikeAutomationRequest("工作日九点生成日报"))
        assertFalse(AutomationDraftService.looksLikeAutomationRequest("每天吃什么比较健康"))
    }
}
