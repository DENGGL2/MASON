package com.denggl2.mason.data

data class ApiConfig(
    val providerId: String = AiProviderCatalog.DEFAULT_PROVIDER_ID,
    val apiUrl: String = AiProviderCatalog.defaultProvider.apiUrl,
    val apiKey: String = "",
    val model: String = AiProviderCatalog.defaultProvider.defaultModel,
    val visionModel: String = "",
    val imageModel: String = "",
    val localModel: String = "",
    val offlineFallbackEnabled: Boolean = false,
    val toolsEnabled: Boolean = AiProviderCatalog.defaultProvider.toolsEnabledByDefault,
    val requireToolConfirmation: Boolean = true,
    val verifiedSignature: String = "",
)
