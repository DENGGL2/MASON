package com.denggl2.mason.phoneagent

import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.ToolRiskLevel
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.ui.phoneagent.PhoneAgentPermissionState
import com.denggl2.mason.ui.phoneagent.canEnableScreenAssistant
import com.denggl2.mason.ui.phoneagent.shouldDisableScreenAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentSafetyTest {
    @Test
    fun nodeIdsOnlyResolveAgainstTheSnapshotThatCreatedThem() {
        val index = PhoneAgentNodeIndex(
            version = 7L,
            paths = mapOf("v7_n0" to listOf(0, 2)),
        )

        assertEquals(listOf(0, 2), index.pathFor("v7_n0", currentVersion = 7L))
        assertNull(index.pathFor("v7_n0", currentVersion = 8L))
        assertNull(index.pathFor("v6_n0", currentVersion = 7L))
    }

    @Test
    fun screenReadIsAuditedAndMutatingActionsRequireHighRiskApproval() {
        assertEquals(ToolRiskLevel.Medium, ToolPolicy.riskFor(PhoneAgentToolNames.OBSERVE))
        assertFalse(ToolPolicy.allowsBackgroundExecution(PhoneAgentToolNames.OBSERVE))

        val mutatingTools = PhoneAgentToolNames.all - PhoneAgentToolNames.OBSERVE
        mutatingTools.forEach { toolName ->
            assertEquals(toolName, ToolRiskLevel.High, ToolPolicy.riskFor(toolName))
            assertTrue(toolName, ToolPolicy.requiresUserApproval(toolName))
            assertFalse(toolName, ToolPolicy.allowsBackgroundExecution(toolName))
        }
    }

    @Test
    fun screenshotsAlwaysRequireOneTimeConfirmation() {
        assertTrue(ToolPolicy.requiresMandatoryApproval(PhoneAgentToolNames.SCREENSHOT))
        assertFalse(ToolPolicy.canRememberApproval(PhoneAgentToolNames.SCREENSHOT))
    }

    @Test
    fun screenAssistantStaysOffUntilBothPermissionsAreGranted() {
        val none = PhoneAgentPermissionState()
        val accessibilityOnly = PhoneAgentPermissionState(accessibilityEnabled = true)
        val all = PhoneAgentPermissionState(accessibilityEnabled = true, overlayEnabled = true)

        assertFalse(ApiConfig().phoneToolsEnabled)
        assertFalse(canEnableScreenAssistant(none))
        assertFalse(canEnableScreenAssistant(accessibilityOnly))
        assertTrue(canEnableScreenAssistant(all))
        assertTrue(shouldDisableScreenAssistant(enabled = true, permissions = accessibilityOnly))
        assertFalse(shouldDisableScreenAssistant(enabled = true, permissions = all))
    }
}
