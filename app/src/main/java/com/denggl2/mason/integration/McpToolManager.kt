package com.denggl2.mason.integration

import com.denggl2.mason.tool.ParameterDef
import com.denggl2.mason.tool.Tool
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.tool.ToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolManager @Inject constructor(
    private val store: IntegrationStore,
    private val client: McpClient,
    private val toolRegistry: ToolRegistry,
) {
    private val _states = MutableStateFlow<Map<String, IntegrationConnectionState>>(emptyMap())
    val states = _states.asStateFlow()

    suspend fun refreshAll() = coroutineScope {
        val servers = store.snapshot.value.mcpServers
        _states.value = servers.associate { server ->
            server.id to if (server.enabled) connecting(server.name) else disabled(server.name)
        }
        val discovered = servers.filter(McpServerConfig::enabled).map { server ->
            async { discover(server) }
        }.awaitAll().flatten()
        toolRegistry.replaceNamespace(MCP_TOOL_PREFIX, discovered)
    }

    suspend fun refresh(serverId: String): IntegrationConnectionState {
        val server = store.snapshot.value.mcpServers.firstOrNull { it.id == serverId }
            ?: return IntegrationConnectionState(IntegrationConnectionPhase.Error, detail = "MCP 配置不存在")
        val tools = if (server.enabled) discover(server) else emptyList()
        val otherTools = toolRegistry.getAll().filterNot { it.name.startsWith(MCP_TOOL_PREFIX + server.id.integrationNamespace()) }
        toolRegistry.replaceNamespace(MCP_TOOL_PREFIX, otherTools.filter { it.name.startsWith(MCP_TOOL_PREFIX) } + tools)
        return _states.value[server.id] ?: disabled(server.name)
    }

    private suspend fun discover(server: McpServerConfig): List<Tool> {
        updateState(server.id, connecting(server.name))
        return runCatching {
            val result = client.discoverTools(server)
            val tools = result.tools.map { McpRemoteTool(server, it, client) }
            updateState(
                server.id,
                IntegrationConnectionState(
                    phase = IntegrationConnectionPhase.Online,
                    displayName = result.serverName,
                    detail = "${result.protocolVersion} · ${tools.size} 个工具",
                    capabilityCount = tools.size,
                    checkedAt = System.currentTimeMillis(),
                ),
            )
            tools
        }.getOrElse { error ->
            updateState(
                server.id,
                IntegrationConnectionState(
                    phase = IntegrationConnectionPhase.Error,
                    displayName = server.name,
                    detail = error.message ?: error.javaClass.simpleName,
                    checkedAt = System.currentTimeMillis(),
                ),
            )
            emptyList()
        }
    }

    private fun updateState(id: String, state: IntegrationConnectionState) {
        _states.value = _states.value + (id to state)
    }

    private fun connecting(name: String) = IntegrationConnectionState(
        IntegrationConnectionPhase.Connecting,
        displayName = name,
        detail = "正在连接",
    )

    private fun disabled(name: String) = IntegrationConnectionState(
        IntegrationConnectionPhase.Disabled,
        displayName = name,
        detail = "已停用",
    )

    companion object {
        const val MCP_TOOL_PREFIX = "mcp__"
    }
}

private class McpRemoteTool(
    private val server: McpServerConfig,
    private val descriptor: McpToolDescriptor,
    private val client: McpClient,
) : Tool {
    override val name: String = McpToolManager.MCP_TOOL_PREFIX +
        server.id.integrationNamespace() + "__" + descriptor.remoteName.integrationNamespace()
    override val displayName: String = "${server.name} · ${descriptor.title}"
    override val approvalDescription: String = descriptor.description.ifBlank {
        "允许 ${server.name} 的 ${descriptor.title} 工具处理本轮任务"
    }
    override val description: String = "${server.name} / ${descriptor.title}: ${descriptor.description}"
    override val inputSchema: JsonObject = descriptor.inputSchema
    override val parameters: Map<String, ParameterDef> = descriptor.inputSchema.toParameterDefs()

    override suspend fun execute(args: Map<String, String>): ToolResult = client.callTool(
        server = server,
        toolName = descriptor.remoteName,
        arguments = descriptor.inputSchema.coerceArguments(args),
    )
}

private fun JsonObject.toParameterDefs(): Map<String, ParameterDef> {
    val properties = this["properties"] as? JsonObject ?: return emptyMap()
    val required = (this["required"]?.jsonArray ?: return properties.mapValues { ParameterDef("string", it.key) })
        .mapNotNull { it.jsonPrimitive.contentOrNull }
        .toSet()
    return properties.mapValues { (name, element) ->
        val schema = element as? JsonObject
        ParameterDef(
            type = schema?.get("type")?.jsonPrimitive?.contentOrNull ?: "string",
            description = schema?.get("description")?.jsonPrimitive?.contentOrNull ?: name,
            required = name in required,
            enum = schema?.get("enum")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
        )
    }
}

private fun JsonObject.coerceArguments(args: Map<String, String>): JsonObject {
    val properties = this["properties"] as? JsonObject
    return JsonObject(args.mapValues { (name, raw) ->
        val type = (properties?.get(name) as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        raw.toJsonValue(type)
    })
}

private fun String.toJsonValue(type: String?): JsonElement = when (type) {
    "integer" -> toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(this)
    "number" -> toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(this)
    "boolean" -> toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(this)
    "object", "array" -> runCatching { Json.parseToJsonElement(this) }.getOrElse { JsonPrimitive(this) }
    else -> JsonPrimitive(this)
}
