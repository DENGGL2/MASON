package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ChatRequest
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.StreamOptions
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
import kotlinx.serialization.json.longOrNull
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
    data class UsageReceived(val usage: TokenUsage) : ChatResponse()
    data class ToolCallsRequested(
        val assistantMessage: ChatMessage,
        val calls: List<ToolCall>,
    ) : ChatResponse()
    data class Error(val message: String) : ChatResponse()
    data class ImageGenerated(
        val data: String,
        val mimeType: String = "image/png",
        val revisedPrompt: String? = null,
        val isBase64: Boolean = false,
    ) : ChatResponse()
}

data class TokenUsage(
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val totalTokens: Long = 0L,
) {
    operator fun plus(other: TokenUsage): TokenUsage =
        TokenUsage(
            promptTokens = promptTokens + other.promptTokens,
            completionTokens = completionTokens + other.completionTokens,
            totalTokens = totalTokens + other.totalTokens,
        )

    val isEmpty: Boolean
        get() = promptTokens == 0L && completionTokens == 0L && totalTokens == 0L
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

    fun chat(
        messages: List<ChatMessage>,
        toolsEnabled: Boolean? = null,
        modelOverride: String? = null,
        attachments: List<ModelAttachment> = emptyList(),
    ): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = modelOverride?.takeIf(String::isNotBlank) ?: configProvider.getModel()
        val resolvedToolsEnabled = toolsEnabled ?: configProvider.getToolsEnabled()
        val requiresApiKey = configProvider.requiresApiKey()

        if (apiKey.isBlank() && requiresApiKey) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val toolDefinitions = if (resolvedToolsEnabled) toolRegistry.getDefinitions() else emptyList()

        val request = ChatRequest(
            model = model,
            messages = buildApiMessages(listOf(systemPrompt()) + messages, attachments),
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
        val usage = parseUsage(root["usage"])

        if (message != null) {
            val calls = parseToolCalls(message)
            if (calls.isNotEmpty()) {
                usage?.let { emit(ChatResponse.UsageReceived(it)) }
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
                usage?.let { emit(ChatResponse.UsageReceived(it)) }
            } else {
                emit(ChatResponse.Error("模型没有返回可显示内容"))
            }
        }
    }

    fun generateImage(prompt: String, modelOverride: String): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        if (prompt.isBlank()) {
            emit(ChatResponse.Error("生图提示词不能为空"))
            return@flow
        }
        if (modelOverride.isBlank()) {
            emit(ChatResponse.Error("没有配置生图模型"))
            return@flow
        }
        if (apiKey.isBlank() && configProvider.requiresApiKey()) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }
        val endpoint = normalizeImagesUrl(apiUrl)
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive(modelOverride))
            put("prompt", kotlinx.serialization.json.JsonPrimitive(prompt))
            put("size", kotlinx.serialization.json.JsonPrimitive("1024x1024"))
            put("response_format", kotlinx.serialization.json.JsonPrimitive("b64_json"))
        }
        val request = buildAuthorizedRequest(endpoint, apiKey, json.encodeToString(JsonObject.serializer(), payload))
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                emit(ChatResponse.Error("生图 API 错误 ${it.code}: $body"))
                return@flow
            }
            val item = runCatching {
                json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.first().jsonObject
            }.getOrNull()
            val base64 = item?.get("b64_json")?.jsonPrimitive?.contentOrNull
            val url = item?.get("url")?.jsonPrimitive?.contentOrNull
            val revisedPrompt = item?.get("revised_prompt")?.jsonPrimitive?.contentOrNull
            when {
                !base64.isNullOrBlank() -> emit(ChatResponse.ImageGenerated(base64, revisedPrompt = revisedPrompt, isBase64 = true))
                !url.isNullOrBlank() -> emit(ChatResponse.ImageGenerated(url, revisedPrompt = revisedPrompt))
                else -> emit(ChatResponse.Error("生图模型没有返回图片"))
            }
        }
    }

    fun streamChat(messages: List<ChatMessage>): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = configProvider.getModel()
        val requiresApiKey = configProvider.requiresApiKey()

        if (apiKey.isBlank() && requiresApiKey) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val apiMessages = buildApiMessages(listOf(systemPrompt()) + messages)
        fun streamRequest(includeUsage: Boolean): Request {
            val request = ChatRequest(
                model = model,
                messages = apiMessages,
                stream = true,
                stream_options = if (includeUsage) StreamOptions(include_usage = true) else null,
            )
            val body = json.encodeToString(ChatRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())
            return buildRequest(apiUrl, apiKey, body)
        }

        var response = withContext(Dispatchers.IO) {
            client.newCall(streamRequest(includeUsage = true)).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            val shouldRetryWithoutUsage = response.code in 400..499 &&
                errorBody.contains("stream_options", ignoreCase = true)
            response.close()
            if (shouldRetryWithoutUsage) {
                response = withContext(Dispatchers.IO) {
                    client.newCall(streamRequest(includeUsage = false)).execute()
                }
                if (!response.isSuccessful) {
                    val fallbackError = response.body?.string().orEmpty()
                    emit(ChatResponse.Error("API 错误 ${response.code}: $fallbackError"))
                    response.close()
                    return@flow
                }
            } else {
                emit(ChatResponse.Error("API 错误 ${response.code}: $errorBody"))
                return@flow
            }
        }

        val source = response.body?.source() ?: run {
            emit(ChatResponse.Error("空响应"))
            return@flow
        }

        try {
            streamProcessor.parseSseEvents(source).collect { event ->
                when (event) {
                    is StreamEvent.TextChunk -> emit(ChatResponse.TextChunk(event.text))
                    is StreamEvent.UsageReceived -> emit(ChatResponse.UsageReceived(event.usage))
                }
            }
        } finally {
            source.close()
        }
    }

    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        model: String,
        requiresApiKey: Boolean = true,
    ): ApiTestResult {
        if (apiUrl.isBlank()) return ApiTestResult(false, "请填写 API 地址")
        if (model.isBlank()) return ApiTestResult(false, "请填写模型名称")
        if (apiKey.isBlank() && requiresApiKey) {
            return ApiTestResult(false, "请填写 API Key")
        }

        val request = ChatRequest(
            model = model,
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive("Reply with OK."),
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
            append("回答需要适配 Mason 的工作流界面：")
            append("可以使用“思考：”“进行中：”“引导：”“最终总结：”这些可见小节。")
            append("“思考”只写一句可见判断或计划，不输出隐藏推理过程。")
            append("需要用户选择、授权或确认风险时，用“引导”给出 2 到 3 个清晰选项。")
            append("任务完成后用“最终总结”收束，优先说明结果、文件、下一步。")
            append("当用户要求生成文档、代码、报告、配置或其他文件时，必须输出一个带 filename=\"相对路径/文件名.扩展名\" 的 fenced code block，便于 Mason 自动保存为产出。")
            append("文件代码块示例：```markdown filename=\"notes/summary.md\"。不要把普通解释性回答伪装成文件。")
        },
    )

    private fun buildApiMessages(
        messages: List<ChatMessage>,
        attachments: List<ModelAttachment> = emptyList(),
    ) =
        buildList {
            val pendingToolIds = mutableSetOf<String>()

            messages.forEachIndexed { index, message ->
                when (message.role) {
                    "assistant" -> {
                        if (message.content.isNullOrBlank() && message.tool_calls.isNullOrEmpty()) {
                            return@forEachIndexed
                        }
                        add(message.toApiChatMessage())
                        pendingToolIds.clear()
                        message.tool_calls.orEmpty().forEach { pendingToolIds.add(it.id) }
                    }

                    "tool" -> {
                        val toolCallId = message.tool_call_id
                        if (toolCallId.isNullOrBlank() || toolCallId !in pendingToolIds) {
                            return@forEachIndexed
                        }
                        add(message.toApiChatMessage())
                        pendingToolIds.remove(toolCallId)
                    }

                    else -> {
                        val isLastUser = index == messages.lastIndex && message.role == "user"
                        add(if (isLastUser && attachments.isNotEmpty()) {
                            message.toApiChatMessage(attachments)
                        } else {
                            message.toApiChatMessage()
                        })
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

    private fun parseUsage(element: JsonElement?): TokenUsage? {
        val usage = element?.jsonObject ?: return null
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val completion = usage["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val total = usage["total_tokens"]?.jsonPrimitive?.longOrNull ?: (prompt + completion)
        if (prompt == 0L && completion == 0L && total == 0L) return null
        return TokenUsage(promptTokens = prompt, completionTokens = completion, totalTokens = total)
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
        val lowerUrl = normalizedUrl.lowercase()
        return Request.Builder()
            .url(normalizedUrl)
            .apply {
                if (apiKey.isNotBlank()) {
                    if ("xiaomimimo.com" in lowerUrl) {
                        addHeader("api-key", apiKey)
                    } else {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
                if ("openrouter.ai" in lowerUrl) {
                    addHeader("HTTP-Referer", "https://github.com/DENGGL2/MASON")
                    addHeader("X-Title", "Mason")
                }
                if ("xiaomimimo.com" in lowerUrl) {
                    addHeader("User-Agent", "Mason Android")
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

    private fun normalizeImagesUrl(apiUrl: String): String {
        val normalized = apiUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/chat/completions", ignoreCase = true) ->
                normalized.removeSuffix("/chat/completions") + "/images/generations"
            normalized.endsWith("/v1", ignoreCase = true) -> "$normalized/images/generations"
            else -> "$normalized/v1/images/generations"
        }
    }

    private fun buildAuthorizedRequest(url: String, apiKey: String, body: String): Request {
        val lowerUrl = url.lowercase()
        return Request.Builder()
            .url(url)
            .apply {
                if (apiKey.isNotBlank()) {
                    if ("xiaomimimo.com" in lowerUrl) addHeader("api-key", apiKey)
                    else addHeader("Authorization", "Bearer $apiKey")
                }
                if ("openrouter.ai" in lowerUrl) {
                    addHeader("HTTP-Referer", "https://github.com/DENGGL2/MASON")
                    addHeader("X-Title", "Mason")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

}
