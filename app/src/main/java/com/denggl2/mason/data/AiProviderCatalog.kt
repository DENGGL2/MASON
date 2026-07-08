package com.denggl2.mason.data

data class AiModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val isFree: Boolean = false,
    val supportsTools: Boolean = true,
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
            description = "统一入口，适合免费模型和多家付费模型切换",
            apiUrl = "https://openrouter.ai/api/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "qwen/qwen3-next-80b-a3b-instruct:free",
                    name = "Qwen3 Next 80B",
                    description = "OpenRouter 免费模型，适合中文对话测试",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "google/gemma-4-31b-it:free",
                    name = "Gemma 4 31B",
                    description = "OpenRouter 免费模型，适合通用问答",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "qwen/qwen3-coder:free",
                    name = "Qwen3 Coder",
                    description = "OpenRouter 免费模型，偏代码能力",
                    isFree = true,
                    supportsTools = false,
                ),
                AiModelPreset(
                    id = "~openai/gpt-latest",
                    name = "OpenAI Latest",
                    description = "OpenRouter 的 OpenAI 最新模型别名",
                ),
            ),
            defaultModel = "qwen/qwen3-next-80b-a3b-instruct:free",
            toolsEnabledByDefault = false,
        ),
        AiProviderPreset(
            id = DEFAULT_PROVIDER_ID,
            name = "DeepSeek",
            description = "官方 OpenAI 兼容接口，适合作为 Mason 默认后端",
            apiUrl = "https://api.deepseek.com",
            modelOptions = listOf(
                AiModelPreset(
                    id = "deepseek-v4-flash",
                    name = "V4 Flash",
                    description = "速度优先，适合日常对话和工具调用",
                ),
                AiModelPreset(
                    id = "deepseek-v4-pro",
                    name = "V4 Pro",
                    description = "质量优先，适合复杂分析",
                ),
            ),
            defaultModel = "deepseek-v4-flash",
        ),
        AiProviderPreset(
            id = "gemini",
            name = "Gemini",
            description = "Google Gemini 的 OpenAI 兼容层",
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            modelOptions = listOf(
                AiModelPreset(
                    id = "gemini-3.5-flash",
                    name = "3.5 Flash",
                    description = "Google 示例默认模型，支持流式和函数调用",
                    isFree = true,
                ),
            ),
            defaultModel = "gemini-3.5-flash",
        ),
        AiProviderPreset(
            id = "siliconflow",
            name = "SiliconFlow",
            description = "硅基流动 OpenAI 兼容接口，可选免费和付费开源模型",
            apiUrl = "https://api.siliconflow.cn/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "Qwen/Qwen3-32B",
                    name = "Qwen3 32B",
                    description = "官方文档示例模型，适合中文对话",
                ),
                AiModelPreset(
                    id = "Qwen/Qwen3-8B",
                    name = "Qwen3 8B",
                    description = "小模型，适合低成本测试",
                    supportsTools = false,
                ),
            ),
            toolsEnabledByDefault = false,
        ),
        AiProviderPreset(
            id = "openai",
            name = "OpenAI",
            description = "OpenAI 官方接口，适合用户自备付费 Key",
            apiUrl = "https://api.openai.com/v1",
            modelOptions = listOf(
                AiModelPreset(
                    id = "gpt-5.5",
                    name = "GPT-5.5",
                    description = "OpenAI 当前旗舰入口",
                ),
            ),
            defaultModel = "gpt-5.5",
        ),
        AiProviderPreset(
            id = CUSTOM_PROVIDER_ID,
            name = "自定义中转站",
            description = "填写任意 OpenAI Chat Completions 兼容地址",
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

    fun inferProviderId(apiUrl: String): String {
        val normalized = apiUrl.lowercase()
        return when {
            "openrouter.ai" in normalized -> "openrouter"
            "deepseek.com" in normalized -> DEFAULT_PROVIDER_ID
            "generativelanguage.googleapis.com" in normalized -> "gemini"
            "siliconflow" in normalized -> "siliconflow"
            "openai.com" in normalized -> "openai"
            else -> CUSTOM_PROVIDER_ID
        }
    }
}
