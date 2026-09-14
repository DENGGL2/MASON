package com.denggl2.mason.integration

import com.denggl2.mason.agent.ToolPolicy
import com.denggl2.mason.agent.ToolRiskLevel
import com.denggl2.mason.tool.ParameterDef
import com.denggl2.mason.tool.Tool
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.tool.ToolApprovalHint
import com.denggl2.mason.tool.ToolSecurityHints
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicToolRegistryTest {
    @Test
    fun remoteSchemaIsPreservedAndNamespaceCanBeReplaced() {
        val registry = ToolRegistry()
        val first = FakeRemoteTool("mcp__one__first")
        val second = FakeRemoteTool("mcp__two__second")
        registry.register(first)

        val schema = registry.getDefinitions().single().function.parameters
        assertEquals("integer", schema["properties"]?.jsonObject?.get("count")?.jsonObject?.get("type")?.jsonPrimitive?.content)

        registry.replaceNamespace("mcp__", listOf(second))
        assertNull(registry.get(first.name))
        assertSame(second, registry.get(second.name))
    }

    @Test
    fun remoteToolRiskUsesMcpAnnotationsConservatively() {
        assertEquals(ToolRiskLevel.High, ToolPolicy.riskFor("mcp__server__write"))
        assertEquals(ToolRiskLevel.High, ToolPolicy.riskFor("a2a__agent__delegate"))
        assertTrue(ToolPolicy.requiresMandatoryApproval("a2a__agent__delegate"))
        assertFalse(ToolPolicy.canRememberApproval("a2a__agent__delegate"))
        assertTrue(ToolPolicy.canRememberApproval("mcp__server__write"))

        val readOnly = ToolPolicy.profileFor(
            "mcp__server__read",
            ToolSecurityHints(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
        )
        val webRead = ToolPolicy.profileFor(
            "mcp__server__search",
            ToolSecurityHints(readOnlyHint = true, destructiveHint = false, openWorldHint = true),
        )
        val alwaysAsk = ToolPolicy.profileFor(
            "mcp__server__read",
            ToolSecurityHints(
                readOnlyHint = true,
                destructiveHint = false,
                approvalHint = ToolApprovalHint.AlwaysAsk,
            ),
        )

        assertEquals(ToolRiskLevel.Low, readOnly.risk)
        assertEquals(ToolRiskLevel.Medium, webRead.risk)
        assertEquals(ToolRiskLevel.High, alwaysAsk.risk)
        assertTrue(alwaysAsk.mandatoryApproval)
        assertFalse(alwaysAsk.persistentGrantAllowed)
    }

    @Test
    fun appCollaborationCatalogDoesNotAdvertiseUnpublishedAuthorizationLinks() {
        val providers = CapabilityProviderCatalog.appCollaborations

        assertEquals(listOf("微信", "支付宝", "美团"), providers.map(CapabilityProvider::displayName))
        assertTrue(providers.all { it.protocol == CapabilityProtocol.A2A })
        assertTrue(providers.all { !it.officialAccessAvailable && it.authorization == null })
        assertTrue(providers.first().capabilities.contains("发送消息"))
    }
}

private class FakeRemoteTool(override val name: String) : Tool {
    override val description: String = "test"
    override val parameters: Map<String, ParameterDef> = emptyMap()
    override val inputSchema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("count", buildJsonObject { put("type", "integer") })
        })
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = ToolResult.success()
}
