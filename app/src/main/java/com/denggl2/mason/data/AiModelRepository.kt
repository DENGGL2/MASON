package com.denggl2.mason.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiModelRepository @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchOpenRouterFreeModels(apiKey: String): Result<List<AiModelPreset>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("OpenRouter 返回 ${response.code}: ${body.take(160)}")
                    }

                    val root = json.parseToJsonElement(body).jsonObject
                    root["data"]
                        ?.jsonArray
                        .orEmpty()
                        .mapNotNull { element ->
                            val item = element.jsonObject
                            val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            if (!id.endsWith(":free")) return@mapNotNull null

                            val name = item["name"]?.jsonPrimitive?.contentOrNull
                                ?.removeSuffix(" (free)")
                                ?: id.substringAfter("/")

                            AiModelPreset(
                                id = id,
                                name = name,
                                description = "OpenRouter 当前可用免费模型",
                                isFree = true,
                                supportsTools = false,
                            )
                        }
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase() }
                }
            }
        }
}
