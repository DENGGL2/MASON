package com.denggl2.mason.llm

interface ApiConfigProvider {
    suspend fun getApiUrl(): String
    suspend fun getApiKey(): String
    suspend fun getModel(): String
    suspend fun getToolsEnabled(): Boolean
    suspend fun requiresApiKey(): Boolean
}
