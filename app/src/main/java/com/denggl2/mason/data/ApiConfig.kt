package com.denggl2.mason.data

data class ApiConfig(
    val providerId: String = AiProviderCatalog.DEFAULT_PROVIDER_ID,
    val apiUrl: String = AiProviderCatalog.defaultProvider.apiUrl,
    val apiKey: String = "",
    val model: String = AiProviderCatalog.defaultProvider.defaultModel,
    val toolsEnabled: Boolean = AiProviderCatalog.defaultProvider.toolsEnabledByDefault,
)
