package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ChatRequest
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.StreamOptions
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.llm.model.ApiChatMessage
import com.denggl2.mason.tool.FunctionDef
import com.denggl2.mason.tool.ToolDefinition
import com.denggl2.mason.llm.model.toApiChatMessage
import com.denggl2.mason.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

fun JsonElement?.displayText(): String? = when (this) {
    null -> null
    is JsonPrimitive -> contentOrNull
    is JsonArray -> mapNotNull { part ->
        when (part) {
            is JsonPrimitive -> part.contentOrNull
            is JsonObject -> when (part["type"]?.jsonPrimitive?.contentOrNull) {
                "text", "output_text" -> part["text"]?.jsonPrimitive?.contentOrNull
                else -> part["text"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }.joinToString("\n").takeIf(String::isNotBlank)
    else -> null
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
    val capabilityWarning: String? = null,
    val capabilities: List<ApiCapabilityCheck> = emptyList(),
)

data class ApiCapabilityCheck(
    val label: String,
    val success: Boolean,
    val detail: String? = null,
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
            tool_choice = "auto".takeIf { toolDefinitions.isNotEmpty() },
        )

        var response = withContext(Dispatchers.IO) {
            executeTestRequest(apiUrl, apiKey, request)
        }
        if (response.code == 400 && request.tool_choice != null) {
            response.close()
            response = withContext(Dispatchers.IO) {
                executeTestRequest(apiUrl, apiKey, request.copy(tool_choice = null))
            }
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
                            content = message["content"].displayText(),
                            tool_calls = calls,
                            timestamp = System.currentTimeMillis(),
                        ),
                        calls = calls,
                    ),
                )
                return@flow
            }

            val content = message["content"].displayText()
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
            put("n", kotlinx.serialization.json.JsonPrimitive(1))
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
                url?.startsWith("data:image/") == true -> {
                    val metadata = url.substringBefore(',')
                    val mimeType = metadata.substringAfter("data:").substringBefore(';').ifBlank { "image/png" }
                    emit(ChatResponse.ImageGenerated(url.substringAfter(','), mimeType, revisedPrompt, isBase64 = true))
                }
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
        visionModel: String = "",
        imageModel: String = "",
        requiresApiKey: Boolean = true,
        testTools: Boolean = true,
    ): ApiTestResult {
        if (apiUrl.isBlank()) return ApiTestResult(false, "请填写 API 地址")
        if (model.isBlank()) return ApiTestResult(false, "请填写模型名称")
        if (apiKey.isBlank() && requiresApiKey) {
            return ApiTestResult(false, "请填写 API Key")
        }

        val textRequest = ChatRequest(
            model = model,
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive("Reply with OK."),
                ),
            ),
            stream = false,
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                executeTestRequest(apiUrl, apiKey, textRequest).use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = "API 错误 ${response.code}: ${responseBody.take(240)}"
                        return@use ApiTestResult(
                            success = false,
                            message = message,
                            capabilities = listOf(ApiCapabilityCheck("聊天", success = false, detail = message)),
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

                    val textMessage = if (content.isNullOrBlank()) "连接成功" else "连接成功：${content.take(80)}"
                    val capabilities = mutableListOf(
                        ApiCapabilityCheck(label = "聊天", success = true),
                        probeVisionCapability(apiUrl, apiKey, visionModel.ifBlank { model }),
                        probeImageCapability(apiUrl, apiKey, imageModel),
                    )
                    if (!testTools) {
                        return@use ApiTestResult(
                            success = true,
                            message = textMessage,
                            capabilities = capabilities,
                        )
                    }

                    val probe = probeToolCalling(apiUrl, apiKey, model)
                    capabilities += ApiCapabilityCheck(
                        label = "工具调用",
                        success = probe.available,
                        detail = probe.warning,
                    )
                    ApiTestResult(
                        success = true,
                        message = if (probe.available) "$textMessage；工具调用可用" else textMessage,
                        capabilityWarning = probe.warning,
                        capabilities = capabilities,
                    )
                }
            }.getOrElse { error ->
                val message = "连接失败: ${error.message ?: error.javaClass.simpleName}"
                ApiTestResult(
                    success = false,
                    message = message,
                    capabilities = listOf(ApiCapabilityCheck("聊天", success = false, detail = message)),
                )
            }
        }
    }

    private fun probeVisionCapability(
        apiUrl: String,
        apiKey: String,
        model: String,
    ): ApiCapabilityCheck {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                            put("text", kotlinx.serialization.json.JsonPrimitive("Reply with OK."))
                        })
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                            put("image_url", kotlinx.serialization.json.buildJsonObject {
                                put("url", kotlinx.serialization.json.JsonPrimitive(VISION_PROBE_IMAGE_DATA_URL))
                            })
                        })
                    },
                ),
            ),
            stream = false,
        )
        return runCatching {
            executeTestRequest(apiUrl, apiKey, request).use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ApiCapabilityCheck(label = "识图", success = true)
                } else {
                    ApiCapabilityCheck(label = "识图", success = false, detail = responseErrorSummary(body))
                }
            }
        }.getOrElse { error ->
            ApiCapabilityCheck(label = "识图", success = false, detail = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun probeImageCapability(
        apiUrl: String,
        apiKey: String,
        model: String,
    ): ApiCapabilityCheck {
        if (model.isBlank()) return ApiCapabilityCheck(label = "生图", success = false, detail = "未配置模型")
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive(model))
            put("prompt", kotlinx.serialization.json.JsonPrimitive("A single blue dot on a white background."))
            put("n", kotlinx.serialization.json.JsonPrimitive(1))
        }
        return runCatching {
            buildAuthorizedRequest(
                normalizeImagesUrl(apiUrl),
                apiKey,
                json.encodeToString(JsonObject.serializer(), payload),
            ).let(client::newCall).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val hasImage = runCatching {
                    json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.isNotEmpty() == true
                }.getOrDefault(false)
                when {
                    response.isSuccessful && hasImage -> ApiCapabilityCheck(label = "生图", success = true)
                    response.isSuccessful -> ApiCapabilityCheck(label = "生图", success = false, detail = "未返回图片")
                    else -> ApiCapabilityCheck(label = "生图", success = false, detail = responseErrorSummary(body))
                }
            }
        }.getOrElse { error ->
            ApiCapabilityCheck(label = "生图", success = false, detail = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun executeTestRequest(
        apiUrl: String,
        apiKey: String,
        request: ChatRequest,
    ) = client.newCall(
        buildRequest(
            apiUrl,
            apiKey,
            json.encodeToString(ChatRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType()),
        ),
    ).execute()

    private fun probeToolCalling(apiUrl: String, apiKey: String, model: String): ToolProbeResult {
        val baseRequest = ChatRequest(
            model = model,
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive(
                        "Call the mason_connection_probe tool now. Do not answer with text.",
                    ),
                ),
            ),
            stream = false,
            tools = listOf(connectionProbeTool()),
        )
        var lastFailure = "中转站或模型未返回 function calling"
        for (toolChoice in listOf("auto", null)) {
            executeTestRequest(apiUrl, apiKey, baseRequest.copy(tool_choice = toolChoice)).use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && CONNECTION_PROBE_TOOL in responseToolNames(body)) {
                    return ToolProbeResult(available = true)
                }
                lastFailure = if (response.isSuccessful) {
                    "中转站或模型未返回 function calling"
                } else {
                    "检测请求返回 ${response.code}${responseErrorSummary(body)?.let { "：$it" }.orEmpty()}"
                }
            }
        }
        return ToolProbeResult(available = false, warning = "工具调用不可用：$lastFailure")
    }

    private fun responseErrorSummary(body: String): String? {
        val structured = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]
            when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
        }.getOrNull()
        return (structured ?: body)
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf(String::isNotBlank)
            ?.take(160)
    }

    private fun responseToolNames(responseBody: String): List<String> = runCatching {
        val message = json.parseToJsonElement(responseBody).jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return@runCatching emptyList()
        parseToolCalls(message).map { it.function.name }
    }.getOrDefault(emptyList())

    private fun connectionProbeTool(): ToolDefinition = ToolDefinition(
        type = "function",
        function = FunctionDef(
            name = CONNECTION_PROBE_TOOL,
            description = "Return a successful function call to verify tool calling compatibility.",
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {})
            },
        ),
    )

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
            append("如果工具列表中存在 skill__activate，且用户任务明确匹配某个 Skill，先调用它获取受控任务方法；普通问答不要调用。")
            append("Skill 返回 needs_input 时，只询问 missing_parameters，不要猜测；返回 activated 后按 instructions 完成任务，且不能绕过工具确认。")
            append("只有用户明确要求记住，或信息明显是可跨任务复用的长期偏好时，才调用 memory_save；当前状态、临时安排、推测和一次性内容不要保存。")
            append("用户个人偏好使用 GLOBAL 记忆；当前项目的技术栈、约定和项目事实使用 PROJECT 记忆并提供稳定 project_id。")
            append("姓名、身份、地址、账号、支付或收款信息必须调用 memory_save_sensitive，不能用普通记忆工具绕过确认。工具未成功时不得声称已经记住。")
            append("任务完成后用“最终总结”收束，优先说明结果、文件、下一步。")
            append("当用户要求生成文档、代码、报告、配置或其他文件时，必须输出一个带 filename=\"相对路径/文件名.扩展名\" 的 fenced code block，便于 Mason 自动保存为产出。")
            append("文件代码块示例：```markdown filename=\"notes/summary.md\"。不要把普通解释性回答伪装成文件。")
        },
    )

    private companion object {
        const val CONNECTION_PROBE_TOOL = "mason_connection_probe"
        const val VISION_PROBE_IMAGE_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }

    private data class ToolProbeResult(
        val available: Boolean,
        val warning: String? = null,
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
