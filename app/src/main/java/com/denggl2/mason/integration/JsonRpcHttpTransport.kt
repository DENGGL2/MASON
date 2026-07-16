package com.denggl2.mason.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class JsonRpcHttpResponse(
    val payload: JsonObject?,
    val sessionId: String?,
)

internal class RemoteProtocolException(
    message: String,
    val statusCode: Int? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
) : IOException(message)

@Singleton
internal class JsonRpcHttpTransport @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun post(
        url: String,
        payload: JsonObject,
        bearerToken: String,
        sessionId: String? = null,
        protocolVersion: String? = null,
    ): JsonRpcHttpResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json, text/event-stream")
            .apply {
                if (bearerToken.isNotBlank()) header("Authorization", "Bearer $bearerToken")
                if (!sessionId.isNullOrBlank()) header("Mcp-Session-Id", sessionId)
                if (!protocolVersion.isNullOrBlank()) header("MCP-Protocol-Version", protocolVersion)
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RemoteProtocolException(
                    "HTTP ${response.code}: ${body.take(500).ifBlank { response.message }}",
                    response.code,
                    response.headers.toMultimap(),
                )
            }
            val contentType = response.header("Content-Type").orEmpty()
            val parsed = when {
                body.isBlank() -> null
                contentType.contains("text/event-stream", ignoreCase = true) -> parseSse(body)
                else -> parseObject(body)
            }
            JsonRpcHttpResponse(parsed, response.header("Mcp-Session-Id"))
        }
    }

    private fun parseSse(body: String): JsonObject? {
        val candidates = body.split(Regex("\\r?\\n\\r?\\n"))
            .asSequence()
            .map { event ->
                event.lineSequence()
                    .filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trimStart() }
            }
            .filter(String::isNotBlank)
            .mapNotNull(::parseObject)
            .toList()
        return candidates.lastOrNull { "result" in it || "error" in it }
            ?: candidates.lastOrNull()
    }

    private fun parseObject(value: String): JsonObject? = runCatching {
        json.parseToJsonElement(value).let { it as? JsonObject }
    }.getOrNull()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
