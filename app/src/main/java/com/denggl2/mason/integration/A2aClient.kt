package com.denggl2.mason.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class A2aAgentDescriptor(
    val name: String,
    val description: String,
    val endpoint: String,
    val protocolBinding: String,
    val protocolVersion: String,
    val tenant: String?,
    val skills: List<String>,
)

data class A2aArtifactPayload(
    val name: String,
    val mimeType: String,
    val text: String? = null,
    val base64: String? = null,
    val url: String? = null,
)

data class A2aTaskResult(
    val externalTaskId: String?,
    val contextId: String?,
    val state: String,
    val summary: String,
    val artifacts: List<A2aArtifactPayload>,
)

@Singleton
class A2aClient @Inject internal constructor(
    private val transport: JsonRpcHttpTransport,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun discover(config: A2aAgentConfig): A2aAgentDescriptor = withContext(Dispatchers.IO) {
        val cardUrl = config.cardUrl.toAgentCardUrl()
        val request = Request.Builder().url(cardUrl).get().apply {
            if (config.bearerToken.isNotBlank()) header("Authorization", "Bearer ${config.bearerToken}")
        }.build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RemoteProtocolException("Agent Card HTTP ${response.code}: ${body.take(500)}", response.code)
            }
            parseAgentCard(json.parseToJsonElement(body).jsonObject, config)
        }
    }

    suspend fun sendTask(
        config: A2aAgentConfig,
        agent: A2aAgentDescriptor,
        goal: String,
    ): A2aTaskResult {
        val modern = agent.protocolVersion.substringBefore('.').toIntOrNull()?.let { it >= 1 } == true
        val message = buildJsonObject {
            put("messageId", UUID.randomUUID().toString())
            put("role", if (modern) "ROLE_USER" else "user")
            put("parts", buildJsonArray {
                add(buildJsonObject {
                    if (!modern) put("kind", "text")
                    put("text", goal)
                    if (modern) put("mediaType", "text/plain")
                })
            })
        }
        val params = buildJsonObject {
            agent.tenant?.let { put("tenant", it) }
            put("message", message)
            put("configuration", buildJsonObject { put("returnImmediately", false) })
        }
        val response = transport.post(
            url = agent.endpoint,
            bearerToken = config.bearerToken,
            payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", if (modern) "SendMessage" else "message/send")
                put("params", params)
            },
        ).payload ?: throw RemoteProtocolException("A2A 没有返回内容")
        response["error"]?.jsonObject?.let { error ->
            throw RemoteProtocolException(error["message"]?.jsonPrimitive?.contentOrNull ?: "A2A 任务失败")
        }
        return parseTaskResult(response["result"] ?: throw RemoteProtocolException("A2A 响应缺少 result"))
    }

    private fun parseAgentCard(card: JsonObject, config: A2aAgentConfig): A2aAgentDescriptor {
        val interfaces = card["supportedInterfaces"] as? JsonArray
        val selected = interfaces.orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { item ->
                item["protocolBinding"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    .contains("JSONRPC", ignoreCase = true)
            }
            ?: interfaces.orEmpty().firstOrNull()?.let { it as? JsonObject }
        val endpoint = selected?.get("url")?.jsonPrimitive?.contentOrNull
            ?: card["url"]?.jsonPrimitive?.contentOrNull
            ?: config.cardUrl.substringBefore("/.well-known/").trimEnd('/')
        val skills = (card["skills"] as? JsonArray).orEmpty().mapNotNull { skill ->
            (skill as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                ?: (skill as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
        }
        return A2aAgentDescriptor(
            name = card["name"]?.jsonPrimitive?.contentOrNull ?: config.name,
            description = card["description"]?.jsonPrimitive?.contentOrNull ?: "A2A agent",
            endpoint = endpoint,
            protocolBinding = selected?.get("protocolBinding")?.jsonPrimitive?.contentOrNull ?: "JSONRPC",
            protocolVersion = selected?.get("protocolVersion")?.jsonPrimitive?.contentOrNull
                ?: card["protocolVersion"]?.jsonPrimitive?.contentOrNull
                ?: "0.3",
            tenant = selected?.get("tenant")?.jsonPrimitive?.contentOrNull,
            skills = skills,
        )
    }

    private fun parseTaskResult(element: JsonElement): A2aTaskResult {
        val result = element.jsonObject
        val task = (result["task"] as? JsonObject)
            ?: result.takeIf { "status" in it || result["kind"]?.jsonPrimitive?.contentOrNull == "task" }
        val directMessage = result["message"] as? JsonObject
        if (task == null && directMessage != null) {
            return A2aTaskResult(
                externalTaskId = null,
                contextId = directMessage["contextId"]?.jsonPrimitive?.contentOrNull,
                state = "TASK_STATE_COMPLETED",
                summary = directMessage.extractText().ifBlank { "外部 Agent 已完成任务" },
                artifacts = emptyList(),
            )
        }
        task ?: throw RemoteProtocolException("A2A 返回了无法识别的任务格式")
        val status = task["status"] as? JsonObject
        val statusMessage = status?.get("message") as? JsonObject
        val historySummary = (task["history"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .lastOrNull { message ->
                message["role"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("agent", ignoreCase = true)
            }
            ?.extractText()
        return A2aTaskResult(
            externalTaskId = task["id"]?.jsonPrimitive?.contentOrNull,
            contextId = task["contextId"]?.jsonPrimitive?.contentOrNull,
            state = status?.get("state")?.jsonPrimitive?.contentOrNull ?: "TASK_STATE_UNSPECIFIED",
            summary = statusMessage?.extractText().orEmpty()
                .ifBlank { historySummary.orEmpty() }
                .ifBlank { "外部 Agent 已返回任务状态" },
            artifacts = (task["artifacts"] as? JsonArray).orEmpty().flatMap(::parseArtifact),
        )
    }

    private fun parseArtifact(element: JsonElement): List<A2aArtifactPayload> {
        val artifact = element as? JsonObject ?: return emptyList()
        val name = artifact["name"]?.jsonPrimitive?.contentOrNull
            ?: artifact["artifactId"]?.jsonPrimitive?.contentOrNull
            ?: "a2a-artifact"
        return (artifact["parts"] as? JsonArray).orEmpty().mapIndexedNotNull { index, partElement ->
            val part = partElement as? JsonObject ?: return@mapIndexedNotNull null
            val mimeType = part["mediaType"]?.jsonPrimitive?.contentOrNull ?: "text/plain"
            A2aArtifactPayload(
                name = if (index == 0) name else "$name-${index + 1}",
                mimeType = mimeType,
                text = part["text"]?.jsonPrimitive?.contentOrNull,
                base64 = part["raw"]?.jsonPrimitive?.contentOrNull,
                url = part["url"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}

private fun String.toAgentCardUrl(): String {
    val value = trim().trimEnd('/')
    return if (value.endsWith(".json", ignoreCase = true)) value else "$value/.well-known/agent-card.json"
}

private fun JsonObject.extractText(): String = (this["parts"] as? JsonArray).orEmpty()
    .mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
    .joinToString("\n")

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
