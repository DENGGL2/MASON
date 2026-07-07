package com.denggl2.mason.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue

            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val obj = json.parseToJsonElement(data).jsonObject
                    val choices = obj["choices"]?.jsonArray
                    val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                    val content = delta?.get("content")?.jsonPrimitive?.content

                    if (!content.isNullOrEmpty()) {
                        emit(content)
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        }
    }
}
