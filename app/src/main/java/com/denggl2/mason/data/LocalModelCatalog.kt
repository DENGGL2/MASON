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
    val fileExtension: String,
    val downloadUrl: String,
    val sha256: String,
    val sourcePageUrl: String,
)

object LocalModelCatalog {
    const val PROVIDER_ID = "local_gemma"
    const val RUNTIME_LITERT = "LiteRT-LM"
    const val RUNTIME_LLAMA_CPP = "llama.cpp"

    val gemmaModels = listOf(
        LocalModelPreset(
            id = "gemma-4-e2b-it-litert",
            name = "Gemma 4 E2B",
            description = "Google 开源轻量小模型，兼顾速度和内存占用，适合离线问答、草稿与简单任务。",
            estimatedSizeGb = 2.4f,
            expectedSizeBytes = 2_588_147_712L,
            recommendedRamGb = 6,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = RUNTIME_LITERT,
            fileExtension = "litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            sourcePageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        ),
        LocalModelPreset(
            id = "gemma-4-e4b-it-litert",
            name = "Gemma 4 E4B",
            description = "Google 开源端侧模型，能力强于 E2B，适合内存更充足的手机和更复杂的离线问答。",
            estimatedSizeGb = 3.4f,
            expectedSizeBytes = 3_659_530_240L,
            recommendedRamGb = 8,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = RUNTIME_LITERT,
            fileExtension = "litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            sourcePageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
        ),
    )

    val miniCpmModels = listOf(
        LocalModelPreset(
            id = "minicpm5-1b-q4-k-m-gguf",
            name = "MiniCPM5 1B",
            description = "OpenBMB 开源中文小模型，体积较小，适合离线中文问答、草稿与简单任务。",
            estimatedSizeGb = 0.64f,
            expectedSizeBytes = 688_065_920L,
            recommendedRamGb = 4,
            capabilities = setOf(
                ModelCapability.TextChat,
                ModelCapability.LocalInference,
            ),
            runtime = RUNTIME_LLAMA_CPP,
            fileExtension = "gguf",
            downloadUrl = "https://huggingface.co/openbmb/MiniCPM5-1B-GGUF/resolve/main/MiniCPM5-1B-Q4_K_M.gguf",
            sha256 = "81b64d05a23b17b34c475f42b3e72fbde62d4b92cc34541f7a8031d0752deafa",
            sourcePageUrl = "https://huggingface.co/openbmb/MiniCPM5-1B-GGUF",
        ),
    )

    val models = gemmaModels + miniCpmModels

    fun get(id: String): LocalModelPreset? =
        models.firstOrNull { it.id == id }
}
