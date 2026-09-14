package com.denggl2.mason.llm

data class ResolvedApiConfig(
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val toolsEnabled: Boolean,
    val requiresApiKey: Boolean,
    val additionalHeaders: Map<String, String> = emptyMap(),
)

interface ApiConfigProvider {
    suspend fun getApiUrl(): String
    suspend fun getApiKey(): String
    suspend fun getModel(): String
    suspend fun getToolsEnabled(): Boolean
    suspend fun requiresApiKey(): Boolean

    suspend fun resolve(connectionId: String? = null): ResolvedApiConfig = ResolvedApiConfig(
        apiUrl = getApiUrl(),
        apiKey = getApiKey(),
        model = getModel(),
        toolsEnabled = getToolsEnabled(),
        requiresApiKey = requiresApiKey(),
    )
}
