package com.denggl2.mason.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamProcessor @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseSseStream(source: BufferedSource): Flow<String> = flow {
        parseSseEvents(source).collect { event ->
            if (event is StreamEvent.TextChunk) emit(event.text)
        }
    }

    fun parseSseEvents(source: BufferedSource): Flow<StreamEvent> = flow {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue

            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val obj = json.parseToJsonElement(data).jsonObject
                    parseUsage(obj["usage"])?.let { usage ->
                        emit(StreamEvent.UsageReceived(usage))
                    }
                    val choices = obj["choices"]?.jsonArray
                    val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull

                    if (!content.isNullOrEmpty()) {
                        emit(StreamEvent.TextChunk(content))
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
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
}

sealed class StreamEvent {
    data class TextChunk(val text: String) : StreamEvent()
    data class UsageReceived(val usage: TokenUsage) : StreamEvent()
}
