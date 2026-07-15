package com.denggl2.mason.integration

import com.denggl2.mason.tool.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class McpToolDescriptor(
    val remoteName: String,
    val title: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class McpDiscoveryResult(
    val serverName: String,
    val protocolVersion: String,
    val tools: List<McpToolDescriptor>,
)

@Singleton
class McpClient @Inject internal constructor(
    private val transport: JsonRpcHttpTransport,
) {
    private data class Session(val id: String?, val protocolVersion: String, val serverName: String)

    private val requestIds = AtomicLong(1)
    private val sessions = ConcurrentHashMap<String, Session>()

    suspend fun discoverTools(server: McpServerConfig): McpDiscoveryResult {
        val session = initialize(server)
        val tools = mutableListOf<McpToolDescriptor>()
        var cursor: String? = null
        var pages = 0
        do {
            val params = buildJsonObject { cursor?.let { put("cursor", it) } }
            val response = request(server, session, "tools/list", params)
            val result = response["result"]?.jsonObject
                ?: throw RemoteProtocolException("MCP tools/list 没有返回 result")
            result["tools"]?.jsonArray.orEmpty().mapNotNullTo(tools, ::parseTool)
            cursor = result["nextCursor"]?.jsonPrimitive?.contentOrNull
            pages += 1
        } while (!cursor.isNullOrBlank() && pages < MAX_TOOL_PAGES)
        return McpDiscoveryResult(session.serverName, session.protocolVersion, tools.distinctBy { it.remoteName })
    }

    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        arguments: JsonObject,
    ): ToolResult {
        var session = sessions[server.id] ?: initialize(server)
        val response = try {
            request(server, session, "tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
        } catch (error: RemoteProtocolException) {
            if (error.statusCode != 404) throw error
            sessions.remove(server.id)
            session = initialize(server)
            request(server, session, "tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
        }
        val error = response["error"]?.jsonObject
        if (error != null) {
            return ToolResult.error(error["message"]?.jsonPrimitive?.contentOrNull ?: "MCP 调用失败")
        }
        val result = response["result"]?.jsonObject
            ?: return ToolResult.error("MCP 工具没有返回结果")
        val text = result["content"]?.jsonArray.orEmpty().mapNotNull(::contentSummary)
        val structured = result["structuredContent"]
        val data = buildMap {
            if (text.isNotEmpty()) put("content", text.joinToString("\n"))
            if (structured != null && structured !is JsonNull) put("structured", structured.toString())
            put("server", server.name)
            put("tool", toolName)
        }
        return if (result["isError"]?.jsonPrimitive?.contentOrNull == "true") {
            ToolResult.error(text.joinToString("\n").ifBlank { "MCP 工具执行失败" })
        } else {
            ToolResult.success(data)
        }
    }

    private suspend fun initialize(server: McpServerConfig): Session {
        val id = requestIds.getAndIncrement()
        val response = transport.post(
            url = server.endpoint,
            bearerToken = server.bearerToken,
            protocolVersion = PROTOCOL_VERSION,
            payload = rpcRequest(id, "initialize", buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "Mason")
                    put("version", "0.1.0")
                })
            }),
        )
        val payload = response.payload ?: throw RemoteProtocolException("MCP 初始化没有返回内容")
        throwIfRpcError(payload)
        val result = payload["result"]?.jsonObject
            ?: throw RemoteProtocolException("MCP 初始化响应缺少 result")
        val negotiatedVersion = result["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: PROTOCOL_VERSION
        val serverName = result["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?: server.name
        val session = Session(response.sessionId, negotiatedVersion, serverName)
        sessions[server.id] = session
        transport.post(
            url = server.endpoint,
            bearerToken = server.bearerToken,
            sessionId = session.id,
            protocolVersion = session.protocolVersion,
            payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            },
        )
        return session
    }

    private suspend fun request(
        server: McpServerConfig,
        session: Session,
        method: String,
        params: JsonObject,
    ): JsonObject {
        val response = transport.post(
            url = server.endpoint,
            bearerToken = server.bearerToken,
            sessionId = session.id,
            protocolVersion = session.protocolVersion,
            payload = rpcRequest(requestIds.getAndIncrement(), method, params),
        ).payload ?: throw RemoteProtocolException("MCP $method 没有返回内容")
        throwIfRpcError(response)
        return response
    }

    private fun parseTool(element: JsonElement): McpToolDescriptor? {
        val value = element as? JsonObject ?: return null
        val name = value["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return McpToolDescriptor(
            remoteName = name,
            title = value["title"]?.jsonPrimitive?.contentOrNull ?: name,
            description = value["description"]?.jsonPrimitive?.contentOrNull ?: "MCP tool $name",
            inputSchema = value["inputSchema"] as? JsonObject ?: buildJsonObject { put("type", "object") },
        )
    }

    private fun contentSummary(element: JsonElement): String? {
        val value = element as? JsonObject ?: return null
        return when (value["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> value["text"]?.jsonPrimitive?.contentOrNull
            "resource_link" -> value["uri"]?.jsonPrimitive?.contentOrNull
            "image", "audio" -> "[${value["type"]?.jsonPrimitive?.contentOrNull}: ${value["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()}]"
            "resource" -> value["resource"]?.toString()
            else -> value.toString()
        }
    }

    private fun rpcRequest(id: Long, method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }

    private fun throwIfRpcError(payload: JsonObject) {
        val error = payload["error"] as? JsonObject ?: return
        val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "远程协议调用失败"
        throw RemoteProtocolException(message)
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        const val MAX_TOOL_PAGES = 20
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
