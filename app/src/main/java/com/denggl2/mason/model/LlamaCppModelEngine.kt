package com.denggl2.mason.model

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.ModelEngine
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LlamaCppRuntimeStatus(
    val available: Boolean,
    val message: String,
)

class LlamaCppModelEngine(
    private val context: Context,
    private val modelPathProvider: suspend (String) -> String?,
) : ModelEngine {
    override val id: String = "llama-cpp"
    override val supportsStreaming: Boolean = true

    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var nativeEngine: InferenceEngine? = null

    @Volatile
    private var activeJob: Job? = null

    override fun canHandle(invocation: ModelInvocation): Boolean =
        invocation.modality == ModelModality.Text && invocation.modelId.isNotBlank()

    override fun invoke(invocation: ModelInvocation): Flow<ChatResponse> = flow {
        val metrics = LlamaCppPerformanceMetrics(SystemClock.elapsedRealtime())
        var outcome = "completed"
        val modelPath = modelPathProvider(invocation.modelId)
        if (modelPath.isNullOrBlank()) {
            emit(ChatResponse.Error("本地 GGUF 模型未安装或不可用"))
            return@flow
        }

        val modelFile = File(modelPath)
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            emit(ChatResponse.Error("本地 GGUF 模型文件不存在：$modelPath"))
            return@flow
        }

        val status = runtimeStatus()
        if (!status.available) {
            emit(ChatResponse.Error(status.message))
            return@flow
        }

        val prompt = buildPrompt(invocation)
        if (prompt.isBlank()) {
            emit(ChatResponse.Error("本地模型没有可处理的输入"))
            return@flow
        }

        try {
            mutex.withLock {
                val engine = loadEngineLocked(invocation.modelId, modelPath)
                metrics.markModelReady(SystemClock.elapsedRealtime())
                activeJob = currentCoroutineContext()[Job]
                val responseFilter = MiniCpmResponseFilter()
                engine.sendUserPrompt(prompt, MAX_OUTPUT_TOKENS).collect { chunk ->
                    responseFilter.consume(chunk)?.let { visibleText ->
                        metrics.recordVisibleText(visibleText, SystemClock.elapsedRealtime())
                        emit(ChatResponse.TextChunk(visibleText))
                    }
                }
                responseFilter.finish()?.let { visibleText ->
                    metrics.recordVisibleText(visibleText, SystemClock.elapsedRealtime())
                    emit(ChatResponse.TextChunk(visibleText))
                }
            }
        } catch (cancelled: CancellationException) {
            outcome = "cancelled"
            throw cancelled
        } catch (throwable: Throwable) {
            outcome = "failed"
            emit(ChatResponse.Error(throwable.toRuntimeMessage()))
        } finally {
            val snapshot = metrics.snapshot(SystemClock.elapsedRealtime(), outcome)
            Log.i(
                LOG_TAG,
                "local_inference model=${invocation.modelId} outcome=${snapshot.outcome} " +
                    "load_ms=${snapshot.loadMs} ttfv_ms=${snapshot.ttfvMs ?: -1} " +
                    "total_ms=${snapshot.totalMs} visible_chars=${snapshot.visibleChars} " +
                    "visible_chars_per_s=${"%.2f".format(java.util.Locale.US, snapshot.visibleCharsPerSecond)}",
            )
            activeJob = null
        }
    }.flowOn(Dispatchers.IO)

    suspend fun cancelActiveInvocation() {
        activeJob?.cancel()
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        cancelActiveInvocation()
        mutex.withLock {
            nativeEngine?.let { engine ->
                if (engine.state.value.isModelLoaded || engine.state.value is InferenceEngine.State.Error) {
                    runCatching { engine.cleanUp() }
                }
            }
            loadedModelId = null
        }
    }

    fun runtimeStatus(): LlamaCppRuntimeStatus {
        val supportedAbi = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }
        if (!supportedAbi) {
            return LlamaCppRuntimeStatus(false, "当前设备架构不支持 llama.cpp 本地推理")
        }
        return runCatching {
            Class.forName("com.arm.aichat.AiChat")
            LlamaCppRuntimeStatus(true, "llama.cpp 运行时可用")
        }.getOrElse { throwable ->
            LlamaCppRuntimeStatus(false, "llama.cpp 运行时不可用：${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private suspend fun loadEngineLocked(modelId: String, modelPath: String): InferenceEngine {
        val engine = nativeEngine ?: AiChat.getInferenceEngine(context.applicationContext).also {
            nativeEngine = it
        }
        engine.state.first { state ->
            state is InferenceEngine.State.Initialized ||
                state is InferenceEngine.State.ModelReady ||
                state is InferenceEngine.State.Error
        }
        if (engine.state.value.isModelLoaded || engine.state.value is InferenceEngine.State.Error) {
            engine.cleanUp()
        }
        engine.loadModel(modelPath)
        loadedModelId = modelId
        return engine
    }

    private fun buildPrompt(invocation: ModelInvocation): String {
        val latestUserIndex = invocation.messages.indexOfLast { it.role == "user" }
        if (latestUserIndex < 0) return ""

        val latestUserMessage = invocation.messages[latestUserIndex].content.orEmpty().trim()
        if (latestUserMessage.isBlank()) return ""

        // The embedded MiniCPM5 Jinja template owns role framing. Passing only the
        // latest user content matches the validated llama.cpp integration path.
        return latestUserMessage
    }

    private fun Throwable.toRuntimeMessage(): String {
        val root = generateSequence(this) { it.cause }.last()
        val detail = root.message ?: root.javaClass.simpleName
        return when (root) {
            is UnsatisfiedLinkError -> "本地模型运行失败：llama.cpp 原生库与当前设备不兼容。"
            else -> "本地模型运行失败：$detail"
        }
    }

    private companion object {
        const val LOG_TAG = "MasonLlamaCpp"
        // MiniCPM5 emits its hidden reasoning before the user-visible answer.
        // MiniCPM5 can spend more than 256 tokens in hidden reasoning before its visible answer.
        const val MAX_OUTPUT_TOKENS = 512
    }
}

internal class MiniCpmResponseFilter {
    private enum class State { Detecting, Answer }

    private var state = State.Detecting
    private val pending = StringBuilder()

    fun consume(chunk: String): String? {
        if (chunk.isEmpty()) return null
        pending.append(chunk)
        return when (state) {
            State.Detecting -> consumeUntilReasoningEnds()
            State.Answer -> pending.takeString()
        }
    }

    fun finish(): String? = when (state) {
        State.Detecting, State.Answer -> pending.takeString()
    }

    private fun consumeUntilReasoningEnds(): String? {
        val endIndex = pending.indexOf(THINK_CLOSE)
        if (endIndex < 0) return null
        pending.delete(0, endIndex + THINK_CLOSE.length)
        state = State.Answer
        return pending.takeString()
    }

    private fun StringBuilder.takeString(): String? =
        toString().takeIf(String::isNotEmpty).also { clear() }

    private companion object {
        const val THINK_CLOSE = "</think>"
    }
}
