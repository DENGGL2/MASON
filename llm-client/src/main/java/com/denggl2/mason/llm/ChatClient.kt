package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ChatRequest
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.llm.model.ApiChatMessage
import com.denggl2.mason.llm.model.toApiChatMessage
import com.denggl2.mason.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatResponse {
    data class TextChunk(val text: String) : ChatResponse()
    data class ToolCallsRequested(
        val assistantMessage: ChatMessage,
        val calls: List<ToolCall>,
    ) : ChatResponse()
    data class Error(val message: String) : ChatResponse()
}

data class ApiTestResult(
    val success: Boolean,
    val message: String,
)

@Singleton
class ChatClient @Inject constructor(
    private val streamProcessor: StreamProcessor,
    private val configProvider: ApiConfigProvider,
    private val toolRegistry: ToolRegistry,
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun chat(messages: List<ChatMessage>): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = configProvider.getModel()
        val toolsEnabled = configProvider.getToolsEnabled()

        if (apiKey.isBlank() && !allowsBlankApiKey(apiUrl)) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val toolDefinitions = if (toolsEnabled) toolRegistry.getDefinitions() else emptyList()

        val request = ChatRequest(
            model = model,
            messages = buildApiMessages(listOf(systemPrompt()) + messages),
            stream = false,
            tools = toolDefinitions.ifEmpty { null },
        )

        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = buildRequest(apiUrl, apiKey, body)

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "未知错误"
            emit(ChatResponse.Error("API 错误 ${response.code}: $errorBody"))
            return@flow
        }

        val responseBody = response.body?.string() ?: run {
            emit(ChatResponse.Error("空响应"))
            return@flow
        }

        val root = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
            emit(ChatResponse.Error("解析响应失败: ${e.message}"))
            return@flow
        }

        val choices = root["choices"]?.jsonArray
        val choice = choices?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject

        if (message != null) {
            val calls = parseToolCalls(message)
            if (calls.isNotEmpty()) {
                emit(
                    ChatResponse.ToolCallsRequested(
                        assistantMessage = ChatMessage(
                            role = "assistant",
                            content = message["content"]?.jsonPrimitive?.contentOrNull,
                            tool_calls = calls,
                            timestamp = System.currentTimeMillis(),
                        ),
                        calls = calls,
                    ),
                )
                return@flow
            }

            val content = message["content"]?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrBlank()) {
                emit(ChatResponse.TextChunk(content))
            } else {
                emit(ChatResponse.Error("模型没有返回可显示内容"))
            }
        }
    }

    fun streamChat(messages: List<ChatMessage>): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = configProvider.getModel()

        if (apiKey.isBlank() && !allowsBlankApiKey(apiUrl)) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val request = ChatRequest(
            model = model,
            messages = buildApiMessages(listOf(systemPrompt()) + messages),
            stream = true,
        )

        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = buildRequest(apiUrl, apiKey, body)

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            emit(ChatResponse.Error("API 错误 ${response.code}: $errorBody"))
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit(ChatResponse.Error("空响应"))
            return@flow
        }

        try {
            streamProcessor.parseSseStream(source).collect { chunk ->
                emit(ChatResponse.TextChunk(chunk))
            }
        } finally {
            source.close()
        }
    }

    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        model: String,
    ): ApiTestResult {
        if (apiUrl.isBlank()) return ApiTestResult(false, "请填写 API 地址")
        if (model.isBlank()) return ApiTestResult(false, "请填写模型名称")
        if (apiKey.isBlank() && !allowsBlankApiKey(apiUrl)) {
            return ApiTestResult(false, "请填写 API Key")
        }

        val request = ChatRequest(
            model = model,
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = "Reply with OK.",
                ),
            ),
            stream = false,
        )

        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        val httpRequest = buildRequest(apiUrl, apiKey, body)

        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use ApiTestResult(
                            success = false,
                            message = "API 错误 ${response.code}: ${responseBody.take(240)}",
                        )
                    }

                    val content = runCatching {
                        val root = json.parseToJsonElement(responseBody).jsonObject
                        root["choices"]
                            ?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("message")
                            ?.jsonObject
                            ?.get("content")
                            ?.jsonPrimitive
                            ?.contentOrNull
                    }.getOrNull()

                    ApiTestResult(
                        success = true,
                        message = if (content.isNullOrBlank()) "连接成功" else "连接成功：${content.take(80)}",
                    )
                }
            }.getOrElse { error ->
                ApiTestResult(false, "连接失败: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun systemPrompt(): ChatMessage = ChatMessage(
        role = "system",
        content = buildString {
            append("你是 Mason，一个运行在 Android 手机上的智能系统助手。")
            append("你可以在用户授权范围内调用手机工具获取设备、系统、网络和应用信息。")
            append("当用户询问设备配置、硬件状态、系统设置或性能问题时，优先调用合适工具获取真实数据。")
            append("拿到工具结果后，用简洁、通俗、可执行的中文回答。")
        },
    )

    private fun buildApiMessages(messages: List<ChatMessage>) =
        buildList {
            val pendingToolIds = mutableSetOf<String>()

            messages.forEach { message ->
                when (message.role) {
                    "assistant" -> {
                        if (message.content.isNullOrBlank() && message.tool_calls.isNullOrEmpty()) {
                            return@forEach
                        }
                        add(message.toApiChatMessage())
                        pendingToolIds.clear()
                        message.tool_calls.orEmpty().forEach { pendingToolIds.add(it.id) }
                    }

                    "tool" -> {
                        val toolCallId = message.tool_call_id
                        if (toolCallId.isNullOrBlank() || toolCallId !in pendingToolIds) {
                            return@forEach
                        }
                        add(message.toApiChatMessage())
                        pendingToolIds.remove(toolCallId)
                    }

                    else -> {
                        add(message.toApiChatMessage())
                        pendingToolIds.clear()
                    }
                }
            }
        }

    private fun parseToolCalls(message: JsonObject): List<ToolCall> {
        val toolCalls = message["tool_calls"]?.jsonArray ?: return emptyList()
        return toolCalls.mapIndexedNotNull { index, element ->
            runCatching {
                val obj = element.jsonObject
                val func = obj["function"]!!.jsonObject
                ToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull
                        ?: "call_${System.currentTimeMillis()}_$index",
                    type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "function",
                    function = FunctionCall(
                        name = func["name"]!!.jsonPrimitive.content,
                        arguments = argumentContent(func["arguments"]),
                    ),
                )
            }.getOrNull()
        }
    }

    private fun argumentContent(element: JsonElement?): String {
        if (element == null) return "{}"
        return runCatching { element.jsonPrimitive.content }.getOrElse { element.toString() }
    }

    private fun buildRequest(
        apiUrl: String,
        apiKey: String,
        body: okhttp3.RequestBody,
    ): Request {
        val normalizedUrl = normalizeChatCompletionsUrl(apiUrl)
        return Request.Builder()
            .url(normalizedUrl)
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
                if ("openrouter.ai" in normalizedUrl.lowercase()) {
                    addHeader("HTTP-Referer", "https://github.com/DENGGL2/MASON")
                    addHeader("X-Title", "Mason")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun normalizeChatCompletionsUrl(apiUrl: String): String {
        val trimmed = apiUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            else -> "$trimmed/chat/completions"
        }
    }

    private fun allowsBlankApiKey(apiUrl: String): Boolean {
        val normalized = apiUrl.lowercase()
        return listOf("localhost", "127.0.0.1", "10.0.2.2").any { it in normalized }
    }
}
