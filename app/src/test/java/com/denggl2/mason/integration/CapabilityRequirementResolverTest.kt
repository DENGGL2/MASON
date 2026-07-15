package com.denggl2.mason.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRequirementResolverTest {
    @Test
    fun classifiesExplicitAppAndMcpActionsWithoutInterceptingQuestions() {
        assertEquals(
            CapabilityIntent(CapabilityProtocol.A2A, "wechat"),
            classifyCapabilityIntent("用微信告诉张三我晚点到"),
        )
        assertEquals(
            CapabilityIntent(CapabilityProtocol.MCP, "github"),
            classifyCapabilityIntent("在 GitHub 创建一个 issue"),
        )
        assertNull(classifyCapabilityIntent("微信是什么？"))
        assertNull(classifyCapabilityIntent("帮我写一段 Kotlin 代码"))
    }

    @Test
    fun capabilityRequestMarkerRoundTripsAndCanBeHiddenFromAnswerText() {
        val requirement = CapabilityRequirement(
            providerId = "wechat",
            protocol = CapabilityProtocol.A2A,
            displayName = "微信",
            capabilities = listOf("发送消息"),
            status = CapabilityRequirementStatus.WaitingForOfficialAccess,
            detail = "等待官方接入",
            originalGoal = "用微信发消息",
            taskRunId = "task-1",
        )
        val content = "最终总结：需要微信协作。\n\n${requirement.toMarker()}"

        assertEquals(requirement, extractCapabilityRequirementMarker(content))
        assertEquals("最终总结：需要微信协作。", stripCapabilityRequirementMarkers(content))
        assertTrue(content.contains("mason-capability-request"))
        assertFalse(stripCapabilityRequirementMarkers(content).contains("mason-capability-request"))
    }
}
