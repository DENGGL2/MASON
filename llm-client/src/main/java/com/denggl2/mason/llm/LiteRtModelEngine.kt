package com.denggl2.mason.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
    @Volatile
    private var activeConversation: Any? = null

    suspend fun cancelActiveInvocation() {
        withContext(Dispatchers.IO) {
            activeConversation?.let(::cancelQuietly)
        }
    }

    suspend fun release() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                engine?.close()
                engine = null
                loadedModelId = null
            }
        }
    }

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

        val prompt = buildLocalPrompt(invocation.messages)

        if (prompt.isBlank()) {
            emit(ChatResponse.Error("本地模型没有可处理的输入"))
            return@flow
        }

        val text = try {
            mutex.withLock {
                val loadedEngine = loadEngineLocked(invocation.modelId, modelPath)
                val conversation = createConversation(loadedEngine)
                activeConversation = conversation
                val builder = StringBuilder()
                try {
                    sendMessageAsync(conversation, prompt).collect { partial ->
                        val chunk = extractText(partial)
                        if (chunk.isNotBlank()) builder.append(chunk)
                    }
                } finally {
                    activeConversation = null
                    closeQuietly(conversation)
                }
                builder.toString().trim()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
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
            val conversationClass = Class.forName("com.google.ai.edge.litertlm.Conversation")
            findFlowSendMessageMethod(conversationClass, messageClass)

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

    private fun loadEngineLocked(modelId: String, modelPath: String): AutoCloseable {
        engine?.takeIf { loadedModelId == modelId }?.let { return it }
        engine?.close()
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val backend = createCpuBackend(backendClass)
        val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val config = createEngineConfig(engineConfigClass, backendClass, modelPath, backend)
        val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
        val loaded = engineClass.getConstructor(engineConfigClass).newInstance(config) as AutoCloseable
        engineClass.getMethod("initialize").invoke(loaded)
        return loaded.also {
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
        val method = findFlowSendMessageMethod(conversationClass, messageClass)
        val result = when (method.parameterCount) {
            1 -> method.invoke(conversation, message)
            2 -> method.invoke(conversation, message, emptyMap<String, Any>())
            else -> error("Unsupported LiteRT-LM sendMessageAsync signature.")
        }
        return result as CoroutineFlow<Any>
    }

    private fun findFlowSendMessageMethod(
        conversationClass: Class<*>,
        messageClass: Class<*>,
    ): Method = conversationClass.methods.firstOrNull { method ->
        method.name == "sendMessageAsync" &&
            method.parameterTypes.firstOrNull() == messageClass &&
            method.returnType.name == "kotlinx.coroutines.flow.Flow" &&
            method.parameterCount in 1..2
    } ?: throw NoSuchMethodException("Compatible LiteRT-LM sendMessageAsync method not found.")

    private fun createMessage(prompt: String): Any {
        val messageClass = Class.forName("com.google.ai.edge.litertlm.Message")
        val companion = messageClass.getField("Companion").get(null)
        return companion.javaClass.getMethod("of", String::class.java).invoke(companion, prompt)
    }

    private fun extractText(message: Any): String {
        val rawContents = runCatching {
            message.javaClass.getMethod("getContents").invoke(message)
        }.getOrNull()
        val contents = when (rawContents) {
            is List<*> -> rawContents
            null -> emptyList<Any>()
            else -> runCatching {
                rawContents.javaClass.getMethod("getContents").invoke(rawContents) as? List<*>
            }.getOrNull().orEmpty()
        }

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

    private fun cancelQuietly(conversation: Any) {
        runCatching {
            conversation.javaClass.getMethod("cancelProcess").invoke(conversation)
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

internal const val LOCAL_CONTEXT_MAX_MESSAGES = 12
internal const val LOCAL_CONTEXT_MAX_CHARS = 8_000
private const val LOCAL_CONTEXT_OMITTED_MARKER = "[Earlier conversation omitted]\n"

internal fun buildLocalPrompt(messages: List<com.denggl2.mason.llm.model.ChatMessage>): String {
    val eligible = messages.filter { message ->
        (message.role == "user" || message.role == "assistant") && !message.content.isNullOrBlank()
    }
    if (eligible.isEmpty()) return messages.lastOrNull()?.content.orEmpty().take(LOCAL_CONTEXT_MAX_CHARS)

    val selected = ArrayDeque<String>()
    var remaining = LOCAL_CONTEXT_MAX_CHARS - LOCAL_CONTEXT_OMITTED_MARKER.length
    var omitted = eligible.size > LOCAL_CONTEXT_MAX_MESSAGES

    for (message in eligible.takeLast(LOCAL_CONTEXT_MAX_MESSAGES).asReversed()) {
        val speaker = if (message.role == "user") "User" else "Mason"
        val formatted = "$speaker: ${message.content.orEmpty().trim()}"
        if (formatted.length > remaining) {
            if (selected.isEmpty()) {
                selected.addFirst(formatted.take(remaining))
            }
            omitted = true
            break
        }
        selected.addFirst(formatted)
        remaining -= formatted.length + 1
        if (remaining <= 0) break
    }

    return buildString {
        if (omitted) append(LOCAL_CONTEXT_OMITTED_MARKER)
        append(selected.joinToString("\n"))
    }
}
