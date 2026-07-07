package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ChatRequest
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    data class ToolCallsRequested(val calls: List<FunctionCall>) : ChatResponse()
    data class Error(val message: String) : ChatResponse()
}

@Singleton
class ChatClient @Inject constructor(
    private val streamProcessor: StreamProcessor,
    private val configProvider: ApiConfigProvider,
    private val toolRegistry: ToolRegistry,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun chat(messages: List<ChatMessage>): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = configProvider.getModel()

        if (apiKey.isBlank()) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val systemPrompt = ChatMessage(
            role = "system",
            content = buildString {
                append("你是 Mason，一个智能 Android 系统助手。")
                append("你可以调用工具来获取设备硬件信息，然后基于数据进行分析和回答。")
                append("当用户询问设备配置、硬件状态、性能等问题时，请先调用相应工具获取数据。")
                append("获取数据后，用通俗易懂的语言向用户解释结果。")
            },
        )

        val toolDefinitions = toolRegistry.getDefinitions()

        val request = ChatRequest(
            model = model,
            messages = listOf(systemPrompt) + messages,
            stream = false,
            tools = toolDefinitions.ifEmpty { null },
        )

        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

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
            val toolCalls = message["tool_calls"]?.jsonArray
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                val calls = toolCalls.map { tc ->
                    val obj = tc.jsonObject
                    val func = obj["function"]!!.jsonObject
                    FunctionCall(
                        name = func["name"]!!.jsonPrimitive.content,
                        arguments = func["arguments"]!!.jsonPrimitive.content,
                    )
                }
                emit(ChatResponse.ToolCallsRequested(calls))
                return@flow
            }

            val content = message["content"]?.jsonPrimitive?.content
            if (!content.isNullOrBlank()) {
                emit(ChatResponse.TextChunk(content))
            }
        }
    }

    fun streamChat(messages: List<ChatMessage>): Flow<ChatResponse> = flow {
        val apiUrl = configProvider.getApiUrl()
        val apiKey = configProvider.getApiKey()
        val model = configProvider.getModel()

        if (apiKey.isBlank()) {
            emit(ChatResponse.Error("请先在设置中配置 API Key"))
            return@flow
        }

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = true,
        )

        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (!response.isSuccessful) {
            emit(ChatResponse.Error("API 错误 ${response.code}"))
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit(ChatResponse.Error("空响应"))
            return@flow
        }

        streamProcessor.parseSseStream(source).collect { chunk ->
            emit(ChatResponse.TextChunk(chunk))
        }
        source.close()
    }
}
