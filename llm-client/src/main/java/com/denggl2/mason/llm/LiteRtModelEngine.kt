package com.denggl2.mason.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow as CoroutineFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.reflect.Method

data class LiteRtRuntimeStatus(
    val available: Boolean,
    val message: String,
)

class LiteRtModelEngine(
    private val modelPathProvider: suspend (String) -> String?,
    private val cacheDirProvider: () -> File,
) : ModelEngine {
    override val id: String = "litert-lm"
    override val supportsStreaming: Boolean = false
    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var engine: AutoCloseable? = null

    override fun canHandle(invocation: ModelInvocation): Boolean =
        invocation.modality == ModelModality.Text && invocation.modelId.isNotBlank()

    override fun invoke(invocation: ModelInvocation): Flow<ChatResponse> = flow {
        val modelPath = modelPathProvider(invocation.modelId)
        if (modelPath.isNullOrBlank()) {
            emit(ChatResponse.Error("本地模型文件未安装或不可用"))
            return@flow
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            emit(ChatResponse.Error("本地模型文件不存在：$modelPath"))
            return@flow
        }

        val runtimeStatus = runtimeStatus()
        if (!runtimeStatus.available) {
            emit(ChatResponse.Error(runtimeStatus.message))
            return@flow
        }

        val prompt = invocation.messages
            .filter { it.role == "user" || it.role == "assistant" }
            .joinToString("\n") { message ->
                val speaker = if (message.role == "user") "User" else "Mason"
                "$speaker: ${message.content.orEmpty()}"
            }
            .ifBlank {
                invocation.messages.lastOrNull()?.content.orEmpty()
            }

        if (prompt.isBlank()) {
            emit(ChatResponse.Error("本地模型没有可处理的输入"))
            return@flow
        }

        val text = runCatching {
            val loadedEngine = loadEngine(invocation.modelId, modelPath)
            val conversation = createConversation(loadedEngine)
            val builder = StringBuilder()
            try {
                sendMessageAsync(conversation, prompt).collect { partial ->
                    val chunk = extractText(partial)
                    if (chunk.isNotBlank()) builder.append(chunk)
                }
            } finally {
                closeQuietly(conversation)
            }
            builder.toString().trim()
        }.getOrElse { throwable ->
            emit(ChatResponse.Error(throwable.toLocalRuntimeMessage()))
            return@flow
        }

        if (text.isBlank()) {
            emit(ChatResponse.Error("本地模型没有返回内容"))
        } else {
            emit(ChatResponse.TextChunk(text))
        }
    }.flowOn(Dispatchers.IO)

    fun runtimeStatus(): LiteRtRuntimeStatus {
        return runCatching {
            val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
            createCpuBackend(backendClass)

            val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            checkEngineConfigConstructor(engineConfigClass, backendClass)

            val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
            val conversationConfigClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
            engineClass.getConstructor(engineConfigClass)
            engineClass.getMethod(
                "createConversation\$default",
                engineClass,
                conversationConfigClass,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )

            val messageClass = Class.forName("com.google.ai.edge.litertlm.Message")
            messageClass.getField("Companion").get(null)
                .javaClass
                .getMethod("of", String::class.java)
            Class.forName("com.google.ai.edge.litertlm.Conversation")
                .getMethod("sendMessageAsync", messageClass)

            LiteRtRuntimeStatus(
                available = true,
                message = "LiteRT-LM 运行时可用",
            )
        }.getOrElse { throwable ->
            LiteRtRuntimeStatus(
                available = false,
                message = throwable.toLocalRuntimeMessage(),
            )
        }
    }

    private suspend fun loadEngine(modelId: String, modelPath: String): AutoCloseable =
        mutex.withLock {
            engine?.takeIf { loadedModelId == modelId }?.let { return@withLock it }
            engine?.close()
            val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
            val backend = createCpuBackend(backendClass)
            val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val config = createEngineConfig(engineConfigClass, backendClass, modelPath, backend)
            val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
            val loaded = engineClass.getConstructor(engineConfigClass).newInstance(config) as AutoCloseable
            engineClass.getMethod("initialize").invoke(loaded)
            loaded.also {
                engine = it
                loadedModelId = modelId
            }
        }

    private fun createCpuBackend(backendClass: Class<*>): Any {
        if (backendClass.isEnum) {
            val values = requireNotNull(backendClass.enumConstants) {
                "LiteRT-LM Backend enum has no values."
            }
            return values.first { (it as Enum<*>).name == "CPU" }
        }

        val cpuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$CPU")
        return runCatching { cpuClass.getField("INSTANCE").get(null) }
            .getOrElse { cpuClass.getConstructor().newInstance() }
    }

    private fun createEngineConfig(
        engineConfigClass: Class<*>,
        backendClass: Class<*>,
        modelPath: String,
        backend: Any,
    ): Any {
        val cacheDir = cacheDirProvider().absolutePath
        val integerClass = Integer::class.java
        return runCatching {
            engineConfigClass
                .getConstructor(
                    String::class.java,
                    backendClass,
                    backendClass,
                    backendClass,
                    integerClass,
                    String::class.java,
                )
                .newInstance(modelPath, backend, null, null, null, cacheDir)
        }.getOrElse {
            engineConfigClass
                .getConstructor(
                    String::class.java,
                    backendClass,
                    backendClass,
                    backendClass,
                    integerClass,
                    integerClass,
                    String::class.java,
                )
                .newInstance(modelPath, backend, null, null, null, null, cacheDir)
        }
    }

    private fun checkEngineConfigConstructor(engineConfigClass: Class<*>, backendClass: Class<*>) {
        val integerClass = Integer::class.java
        val hasSupportedConstructor = engineConfigClass.constructors.any { constructor ->
            constructor.parameterTypes.toList() == listOf(
                String::class.java,
                backendClass,
                backendClass,
                backendClass,
                integerClass,
                String::class.java,
            ) || constructor.parameterTypes.toList() == listOf(
                String::class.java,
                backendClass,
                backendClass,
                backendClass,
                integerClass,
                integerClass,
                String::class.java,
            )
        }
        check(hasSupportedConstructor) {
            "LiteRT-LM EngineConfig API is not compatible."
        }
    }

    private fun createConversation(engine: AutoCloseable): Any {
        val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
        val conversationConfigClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
        val defaultMethod = engineClass.getMethod(
            "createConversation\$default",
            engineClass,
            conversationConfigClass,
            Int::class.javaPrimitiveType,
            Any::class.java,
        )
        return defaultMethod.invoke(null, engine, null, 1, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendMessageAsync(conversation: Any, prompt: String): CoroutineFlow<Any> {
        val conversationClass = Class.forName("com.google.ai.edge.litertlm.Conversation")
        val message = createMessage(prompt)
        val messageClass = Class.forName("com.google.ai.edge.litertlm.Message")
        val method = conversationClass.getMethod("sendMessageAsync", messageClass)
        return method.invoke(conversation, message) as CoroutineFlow<Any>
    }

    private fun createMessage(prompt: String): Any {
        val messageClass = Class.forName("com.google.ai.edge.litertlm.Message")
        val companion = messageClass.getField("Companion").get(null)
        return companion.javaClass.getMethod("of", String::class.java).invoke(companion, prompt)
    }

    private fun extractText(message: Any): String {
        val contents = runCatching {
            message.javaClass.getMethod("getContents").invoke(message) as? List<*>
        }.getOrNull().orEmpty()

        return contents.joinToString(separator = "") { content ->
            content?.textFromContent().orEmpty()
        }.ifBlank {
            message.toString()
                .removePrefix("Message(")
                .removeSuffix(")")
                .trim()
        }
    }

    private fun Any.textFromContent(): String {
        val getter: Method = javaClass.methods.firstOrNull { it.name == "getText" && it.parameterCount == 0 }
            ?: return toString()
        return getter.invoke(this)?.toString().orEmpty()
    }

    private fun closeQuietly(value: Any) {
        runCatching {
            (value as? AutoCloseable)?.close()
        }
    }

    private fun Throwable.rootMessage(): String {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause ?: break
        }
        return current.message ?: current.javaClass.simpleName
    }

    private fun Throwable.toLocalRuntimeMessage(): String {
        val root = rootCause()
        val detail = root.message ?: root.javaClass.simpleName
        return when (root) {
            is ClassNotFoundException,
            is NoClassDefFoundError -> {
                "本地模型运行失败：LiteRT-LM 运行时没有正确打进 APK，或当前依赖版本不兼容。"
            }
            is NoSuchMethodException,
            is NoSuchFieldException -> {
                "本地模型运行失败：LiteRT-LM API 版本和 Mason 当前适配层不匹配。"
            }
            is UnsatisfiedLinkError -> {
                "本地模型运行失败：LiteRT-LM 原生库加载失败，当前设备架构或 APK 打包可能不兼容。"
            }
            is IllegalStateException -> {
                "本地模型运行失败：$detail"
            }
            else -> {
                "本地模型运行失败：$detail"
            }
        }
    }

    private fun Throwable.rootCause(): Throwable {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause ?: break
        }
        return current
    }
}
