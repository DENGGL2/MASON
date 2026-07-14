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
    val expectedSizeBytes: Long,
    val recommendedRamGb: Int,
    val capabilities: Set<ModelCapability>,
    val runtime: String,
    val downloadUrl: String,
    val sha256: String,
    val sourcePageUrl: String,
)

object LocalModelCatalog {
    const val PROVIDER_ID = "local_gemma"

    val gemmaModels = listOf(
        LocalModelPreset(
            id = "gemma-4-e2b-it-litert",
            name = "Gemma 4 E2B",
            description = "轻量端侧文本模型，适合离线问答、草稿和简单任务拆解。",
            estimatedSizeGb = 2.4f,
            expectedSizeBytes = 2_588_147_712L,
            recommendedRamGb = 6,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = "LiteRT-LM",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            sourcePageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        ),
        LocalModelPreset(
            id = "gemma-4-e4b-it-litert",
            name = "Gemma 4 E4B",
            description = "更强的端侧文本模型，适合性能更好的手机，回答质量更高。",
            estimatedSizeGb = 3.4f,
            expectedSizeBytes = 3_659_530_240L,
            recommendedRamGb = 8,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = "LiteRT-LM",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            sourcePageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
        ),
    )

    fun get(id: String): LocalModelPreset? =
        gemmaModels.firstOrNull { it.id == id }
}
