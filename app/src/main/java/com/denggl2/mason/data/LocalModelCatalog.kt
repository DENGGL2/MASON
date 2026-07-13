package com.denggl2.mason.data

enum class ModelCapability {
    TextChat,
    Vision,
    ImageGeneration,
    ToolCalling,
    LocalInference,
}

data class LocalModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val estimatedSizeGb: Float,
    val recommendedRamGb: Int,
    val capabilities: Set<ModelCapability>,
    val runtime: String,
)

object LocalModelCatalog {
    const val PROVIDER_ID = "local_gemma"

    val gemmaModels = listOf(
        LocalModelPreset(
            id = "gemma-4-e2b-it-litert",
            name = "Gemma 4 E2B",
            description = "轻量端侧文本模型，适合离线问答、草稿和简单任务拆解。",
            estimatedSizeGb = 2.0f,
            recommendedRamGb = 6,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = "LiteRT-LM",
        ),
        LocalModelPreset(
            id = "gemma-4-e4b-it-litert",
            name = "Gemma 4 E4B",
            description = "更强的端侧文本模型，适合性能更好的手机，回答质量更高。",
            estimatedSizeGb = 4.0f,
            recommendedRamGb = 8,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = "LiteRT-LM",
        ),
    )

    fun get(id: String): LocalModelPreset? =
        gemmaModels.firstOrNull { it.id == id }
}
