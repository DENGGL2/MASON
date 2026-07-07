package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatClient @Inject constructor(
    private val streamProcessor: StreamProcessor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // Change this to your LLM API endpoint
    private val apiUrl = "https://api.deepseek.com/v1/chat/completions"
    private val apiKey = "sk-your-api-key"
    private val model = "deepseek-chat"

    fun chat(messages: List<ChatMessage>): Flow<String> = kotlinx.coroutines.flow.flow {
        val systemPrompt = ChatMessage(
            role = "system",
            content = "你是 Mason，一个智能 Android 系统助手。"
        )

        val request = ChatRequest(
            model = model,
            messages = listOf(systemPrompt) + messages,
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
            throw Exception("API error: ${response.code} ${response.message}")
        }

        val source = response.body?.source() ?: throw Exception("Empty response body")
        streamProcessor.parseSseStream(source).collect { chunk ->
            emit(chunk)
        }
        source.close()
    }
}
