package com.denggl2.mason.model

import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.llm.LiteRtModelEngine
import com.denggl2.mason.llm.ModelEngine
import javax.inject.Inject
import javax.inject.Singleton

data class LocalRuntimeStatus(
    val available: Boolean,
    val message: String,
)

@Singleton
class LocalModelEngineRegistry @Inject constructor(
    private val liteRtEngine: LiteRtModelEngine,
    private val llamaCppEngine: LlamaCppModelEngine,
) {
    fun engineFor(modelId: String): ModelEngine? = when (LocalModelCatalog.get(modelId)?.runtime) {
        LocalModelCatalog.RUNTIME_LITERT -> liteRtEngine
        LocalModelCatalog.RUNTIME_LLAMA_CPP -> llamaCppEngine
        else -> null
    }

    fun runtimeStatus(modelId: String): LocalRuntimeStatus = when (LocalModelCatalog.get(modelId)?.runtime) {
        LocalModelCatalog.RUNTIME_LITERT -> liteRtEngine.runtimeStatus().let {
            LocalRuntimeStatus(it.available, it.message)
        }
        LocalModelCatalog.RUNTIME_LLAMA_CPP -> llamaCppEngine.runtimeStatus().let {
            LocalRuntimeStatus(it.available, it.message)
        }
        else -> LocalRuntimeStatus(false, "未找到本地模型运行时")
    }

    suspend fun release(modelId: String) {
        when (LocalModelCatalog.get(modelId)?.runtime) {
            LocalModelCatalog.RUNTIME_LITERT -> liteRtEngine.release()
            LocalModelCatalog.RUNTIME_LLAMA_CPP -> llamaCppEngine.release()
        }
    }

    suspend fun cancelActive() {
        liteRtEngine.cancelActiveInvocation()
        llamaCppEngine.cancelActiveInvocation()
    }

    suspend fun releaseAll() {
        liteRtEngine.release()
        llamaCppEngine.release()
    }
}
