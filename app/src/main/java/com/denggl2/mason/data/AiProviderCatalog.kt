package com.denggl2.mason.data

data class AiModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val isFree: Boolean = false,
    val supportsTools: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val modeGroup: String? = null,
    val modeLabel: String? = null,
)

data class AiProviderPreset(
    val id: String,
    val name: String,
    val description: String,
    val apiUrl: String,
    val modelOptions: List<AiModelPreset>,
    val defaultModel: String = modelOptions.firstOrNull()?.id.orEmpty(),
    val toolsEnabledByDefault: Boolean = true,
)

object AiProviderCatalog {
    const val DEFAULT_PROVIDER_ID = "deepseek"
    const val CUSTOM_PROVIDER_ID = "custom"

    val providers = listOf(
        AiProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            description = "一个 Key 连接很多模型；免费模型也通常要登录获取 Key",
            apiUrl = "https://openrouter.ai/api/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "qwen/qwen3-next-80b-a3b-instruct:free",
                    name = "Qwen3 Next 80B",
                    description = "免费文本对话模型，适合中文问答和日常试用；不负责识图或生图",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "google/gemma-4-31b-it:free",
                    name = "Gemma 4 31B",
                    description = "免费通用问答模型，适合轻量对话；通过 OpenRouter 调用",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "qwen/qwen3-coder:free",
                    name = "Qwen3 Coder",
                    description = "免费代码向模型，适合写代码和解释代码；不支持手机工具调用",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "~openai/gpt-latest",
                    name = "OpenAI 付费路由（OpenRouter）",
                    description = "通过 OpenRouter 调用 OpenAI 付费聊天模型；需要账户余额",
                    supportsVision = true,
                ),
            ),
            defaultModel = "qwen/qwen3-next-80b-a3b-instruct:free",
            toolsEnabledByDefault = false,
        ),
        AiProviderPreset(
            id = DEFAULT_PROVIDER_ID,
            name = "DeepSeek",
            description = "中文对话和推理稳定，适合作为默认聊天模型",
            apiUrl = "https://api.deepseek.com",
            modelOptions = listOf(
                AiModelPreset(
                    id = "deepseek-v4-flash",
                    name = "V4 Flash",
                    description = "速度优先，适合日常问答、总结和手机工具调用",
                    modeGroup = "deepseek-v4",
                    modeLabel = "Flash",
                ),
                AiModelPreset(
                    id = "deepseek-v4-pro",
                    name = "V4 Pro",
                    description = "质量优先，适合复杂分析、长文本和更慎重的回答",
                    modeGroup = "deepseek-v4",
                    modeLabel = "Pro",
                ),
            ),
            defaultModel = "deepseek-v4-flash",
        ),
        AiProviderPreset(
            id = "gemini",
            name = "Gemini",
            description = "Google 模型，适合多模态理解；需要 Google AI Studio Key",
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            modelOptions = listOf(
                AiModelPreset(
                    id = "gemini-3.5-flash",
                    name = "3.5 Flash",
                    description = "速度快，模型侧支持图文理解；Mason 视觉输入还需后续接入",
                    isFree = true,
                    supportsVision = true,
                ),
            ),
            defaultModel = "gemini-3.5-flash",
        ),
        AiProviderPreset(
            id = "mimo",
            name = "Xiaomi MiMo",
            description = "小米模型，适合手机助手场景；需要 MiMo Key",
            apiUrl = "https://api.xiaomimimo.com/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "mimo-v2.5",
                    name = "MiMo V2.5",
                    description = "标准模式，适合日常问答和手机助手任务",
                    supportsTools = false,
                    modeGroup = "mimo-v2.5",
                    modeLabel = "标准",
                ),
                AiModelPreset(
                    id = "mimo-v2.5-pro",
                    name = "MiMo V2.5 Pro",
                    description = "Pro 模式，适合更复杂的推理和长任务",
                    supportsTools = false,
                    modeGroup = "mimo-v2.5",
                    modeLabel = "Pro",
                ),
            ),
            defaultModel = "mimo-v2.5",
            toolsEnabledByDefault = false,
        ),
        AiProviderPreset(
            id = "siliconflow",
            name = "SiliconFlow",
            description = "国内模型平台，可选多种开源模型；按平台规则计费",
            apiUrl = "https://api.siliconflow.cn/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "Qwen/Qwen3-32B",
                    name = "Qwen3 32B",
                    description = "通用中文对话模型，适合问答、总结和写作",
                ),
                AiModelPreset(
                    id = "Qwen/Qwen3-8B",
                    name = "Qwen3 8B",
                    description = "小模型，响应轻快，适合低成本测试",
                    supportsTools = false,
                ),
            ),
            toolsEnabledByDefault = false,
        ),
        AiProviderPreset(
            id = "openai",
            name = "OpenAI",
            description = "OpenAI 官方模型；需要自备 API Key 和账户额度",
            apiUrl = "https://api.openai.com/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "gpt-5.5",
                    name = "GPT-5.5",
                    description = "高质量通用模型，适合复杂对话、写作和图文理解",
                    supportsVision = true,
                ),
            ),
            defaultModel = "gpt-5.5",
        ),
        AiProviderPreset(
            id = CUSTOM_PROVIDER_ID,
            name = "自定义中转站",
            description = "接入你自己的 OpenAI 兼容地址、Key 和模型名",
            apiUrl = "https://your-api-host/v1",
            modelOptions = emptyList(),
            defaultModel = "",
            toolsEnabledByDefault = true,
        ),
    )

    val defaultProvider: AiProviderPreset
        get() = getProvider(DEFAULT_PROVIDER_ID) ?: providers.first()

    fun getProvider(id: String): AiProviderPreset? =
        providers.firstOrNull { it.id == id }

    fun getModel(providerId: String, modelId: String): AiModelPreset? =
        getProvider(providerId)?.modelOptions?.firstOrNull { it.id == modelId }

    fun quickSwitchModels(providerId: String, modelId: String): List<AiModelPreset> {
        val provider = getProvider(providerId) ?: return emptyList()
        val current = provider.modelOptions.firstOrNull { it.id == modelId } ?: return emptyList()
        val group = current.modeGroup ?: return emptyList()
        return provider.modelOptions.filter { it.modeGroup == group }
    }

    fun isFreeModel(providerId: String, modelId: String): Boolean {
        return getModel(providerId, modelId)?.isFree == true ||
            modelId.endsWith(":free", ignoreCase = true)
    }

    fun allowsBlankApiKey(apiUrl: String): Boolean {
        val normalized = apiUrl.lowercase()
        return listOf("localhost", "127.0.0.1", "10.0.2.2").any { it in normalized }
    }

    fun requiresApiKey(config: ApiConfig): Boolean =
        requiresApiKey(
            providerId = config.providerId,
            apiUrl = config.apiUrl,
            modelId = config.model,
        )

    fun requiresApiKey(
        providerId: String,
        apiUrl: String,
        modelId: String,
    ): Boolean =
        !allowsBlankApiKey(apiUrl)

    fun verificationSignature(config: ApiConfig): String {
        val keyMarker = if (config.apiKey.isBlank()) "no-key" else config.apiKey.hashCode().toString()
        return listOf(
            config.providerId,
            config.apiUrl.trim().trimEnd('/'),
            config.model,
            keyMarker,
        ).joinToString("|")
    }

    fun isVerified(config: ApiConfig): Boolean =
        !requiresApiKey(config) || config.verifiedSignature == verificationSignature(config)

    fun inferProviderId(apiUrl: String): String {
        val normalized = apiUrl.lowercase()
        return when {
            "openrouter.ai" in normalized -> "openrouter"
            "deepseek.com" in normalized -> DEFAULT_PROVIDER_ID
            "generativelanguage.googleapis.com" in normalized -> "gemini"
            "xiaomimimo.com" in normalized || "mimo.mi.com" in normalized -> "mimo"
            "siliconflow" in normalized -> "siliconflow"
            "openai.com" in normalized -> "openai"
            else -> CUSTOM_PROVIDER_ID
        }
    }
}
