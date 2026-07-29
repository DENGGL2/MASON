package com.denggl2.mason.model

import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.resolvedChatModelRef
import com.denggl2.mason.data.resolvedImageModelRef
import com.denggl2.mason.data.resolvedVisionModelRef
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.ConversationContextManager
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.ModelEngineStatus
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.OpenAiCompatibleModelEngine
import com.denggl2.mason.llm.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

data class ModelRouteDecision(
    val engineId: String,
    val modelId: String,
    val modality: ModelModality,
    val reason: String,
    val connectionId: String? = null,
    val fallbackModelId: String? = null,
)

data class RoutedModelResponse(
    val decision: ModelRouteDecision,
    val responses: Flow<ChatResponse>,
)

@Singleton
class MasonModelRouter @Inject constructor(
    private val configStore: ApiConfigDataStore,
    private val remoteEngine: OpenAiCompatibleModelEngine,
    private val localStore: LocalModelStore,
    private val localEngines: LocalModelEngineRegistry,
    private val attachmentResolver: ChatAttachmentResolver,
    private val contextManager: ConversationContextManager,
) {
    private val _statuses = MutableStateFlow<Map<ModelModality, ModelEngineStatus>>(emptyMap())
    val statuses = _statuses.asStateFlow()

    suspend fun route(
        messages: List<ChatMessage>,
        toolsEnabled: Boolean = true,
        includeMemory: Boolean = true,
        memoryScopeId: String? = null,
    ): RoutedModelResponse {
        val config = configStore.config.first()
        val context = ChatContextParser.parse(messages.lastOrNull { it.role == "user" }?.content.orEmpty())
        val attachmentResult = runCatching { attachmentResolver.resolve(context.attachments) }
        val attachments = attachmentResult.getOrDefault(emptyList())
        val modality = detectModelModality(context.userText, context.attachments, attachments)
        val sanitizedMessages = sanitizeAttachmentMetadata(messages, context)
        val preparedMessages = contextManager.prepare(
            messages = sanitizedMessages,
            query = context.userText,
            scopeId = memoryScopeId,
            includeMemory = includeMemory,
        )
        val localModelId = resolveSelectedLocalModelId(config)
        val selectedLocalEngine = localEngines.engineFor(localModelId)
        val localReady = LocalModelCatalog.get(localModelId)?.let(localStore::stateFor)?.state in setOf(
            LocalModelInstallState.Installed,
            LocalModelInstallState.DeviceMayBeUnsupported,
        )
        val useLocal = shouldUseLocalModel(
            config = config,
            modality = modality,
            userText = context.userText,
            hasAttachments = context.attachments.isNotEmpty(),
            hasSkill = context.skillId != null,
            localReady = localReady,
            localEngineAvailable = selectedLocalEngine != null,
        )
        val remoteRef = when (modality) {
            ModelModality.Text -> config.resolvedChatModelRef()
            ModelModality.Vision -> config.resolvedVisionModelRef()
            ModelModality.ImageGeneration -> config.resolvedImageModelRef()
        }
        val selectedModel = if (useLocal) localModelId else remoteRef?.modelId.orEmpty()
        val selectedConnection = remoteRef?.let { config.connection(it.connectionId) }
        val selectedPreset = selectedConnection?.let { connection ->
            AiProviderCatalog.getModel(connection.providerId, selectedModel)
        }
        val modelSupportsTools = selectedConnection?.toolsSupported == true &&
            (selectedPreset?.supportsTools != false)
        val decision = ModelRouteDecision(
            engineId = if (useLocal) selectedLocalEngine?.id.orEmpty() else remoteEngine.id,
            modelId = selectedModel,
            modality = modality,
            reason = routeReason(modality, useLocal, context.attachments.isNotEmpty(), config.dynamicLocalRoutingEnabled),
            connectionId = if (useLocal) null else remoteRef?.connectionId,
            fallbackModelId = resolveLocalFallbackModelId(config, modality, useLocal, localReady),
        )
        recordStatus(decision, selectedModel.isNotBlank(), if (selectedModel.isBlank()) "未配置对应模型" else "已路由")
        val invocation = ModelInvocation(
            modality = modality,
            messages = preparedMessages,
            modelId = selectedModel,
            connectionId = decision.connectionId,
            attachments = attachments,
            toolsEnabled = toolsEnabled && config.phoneToolsEnabled && modelSupportsTools &&
                modality == ModelModality.Text && !useLocal,
        )
        val responses = when {
            attachmentResult.isFailure -> {
                val error = attachmentResult.exceptionOrNull()
                flowOf(ChatResponse.Error("附件读取失败：${error?.message ?: error?.javaClass?.simpleName}"))
            }
            selectedModel.isBlank() -> flowOf(ChatResponse.Error(when (modality) {
                ModelModality.Vision -> "当前模型不支持识图，请先在设置中选择识图模型"
                ModelModality.ImageGeneration -> "请先在设置中选择生图模型"
                ModelModality.Text -> "请先在设置中选择聊天模型"
            }))
            else -> invokeWithFallback(invocation, decision)
        }
        return RoutedModelResponse(decision, responses)
    }

    suspend fun cancelActive() = localEngines.cancelActive()

    suspend fun releaseLocal() = localEngines.releaseAll()

    private fun invokeWithFallback(
        invocation: ModelInvocation,
        decision: ModelRouteDecision,
    ): Flow<ChatResponse> = flow {
        var remoteFailed = false
        var remoteError: String? = null
        val engine = if (decision.engineId == remoteEngine.id) {
            remoteEngine
        } else {
            localEngines.engineFor(decision.modelId)
        }
        if (engine == null) {
            emit(ChatResponse.Error("所选本地模型运行时不可用"))
            return@flow
        }
        if (!engine.canHandle(invocation)) {
            emit(ChatResponse.Error("所选模型无法处理当前请求"))
            return@flow
        }
        val primary = engine.invoke(invocation)
        try {
            withTimeout(invocation.timeoutMillis) {
                primary.collect { response ->
                    if (
                        response is ChatResponse.Error &&
                        decision.fallbackModelId != null &&
                        isOfflineFailure(response.message)
                    ) {
                        remoteFailed = true
                        remoteError = response.message
                    } else {
                        if (response is ChatResponse.Error) {
                            recordStatus(decision, available = false, message = response.message)
                        }
                        emit(response)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            val message = remoteTimeoutMessage(invocation.timeoutMillis)
            recordStatus(decision, available = false, message = message)
            if (decision.fallbackModelId == null) {
                emit(ChatResponse.Error(message))
                return@flow
            }
            remoteFailed = true
            remoteError = message
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = "模型请求失败：${error.message ?: error.javaClass.simpleName}"
            recordStatus(decision, available = false, message = message)
            if (decision.fallbackModelId != null && isOfflineFailure(message)) {
                remoteFailed = true
                remoteError = message
            } else {
                emit(ChatResponse.Error(message))
                return@flow
            }
        }
        if (remoteFailed) {
            val fallback = invocation.copy(
                modelId = decision.fallbackModelId.orEmpty(),
                attachments = emptyList(),
                toolsEnabled = false,
            )
            recordStatus(decision, available = false, message = remoteError.orEmpty())
            emit(ChatResponse.TextChunk("进行中：远程模型失败，已切换到本地模型。\n"))
            var fallbackFailed = false
            var fallbackError = ""
            val fallbackEngine = localEngines.engineFor(fallback.modelId)
            if (fallbackEngine == null) {
                emit(ChatResponse.Error("本地兜底模型运行时不可用"))
                return@flow
            }
            fallbackEngine.invoke(fallback).collect { response ->
                if (response is ChatResponse.Error) {
                    fallbackFailed = true
                    fallbackError = response.message
                }
                emit(response)
            }
            recordStatus(
                decision.copy(engineId = fallbackEngine.id, modelId = fallback.modelId),
                !fallbackFailed,
                if (fallbackFailed) {
                    "远程模型失败，本地兜底也不可用：$fallbackError"
                } else {
                    "远程失败后已启用本地兜底：${remoteError.orEmpty()}"
                },
            )
        }
    }

    private fun routeReason(
        modality: ModelModality,
        local: Boolean,
        hasAttachments: Boolean,
        dynamicLocalRoutingEnabled: Boolean,
    ): String = when {
        local && dynamicLocalRoutingEnabled -> "简单文字请求，按难度动态使用本地模型"
        local -> "用户选择本地直连，且请求仅包含文字"
        modality == ModelModality.Vision -> "检测到图片附件，使用识图模型"
        modality == ModelModality.ImageGeneration -> "检测到生图请求，使用生图模型"
        hasAttachments -> "检测到文件附件，使用远程模型处理抽取文本"
        else -> "使用当前远程主对话模型"
    }

    private fun recordStatus(decision: ModelRouteDecision, available: Boolean, message: String) {
        _statuses.value = _statuses.value + (decision.modality to ModelEngineStatus(
            engineId = decision.engineId,
            available = available,
            modelId = decision.modelId,
            modality = decision.modality,
            message = message,
        ))
    }
}

internal fun resolveSelectedLocalModelId(config: ApiConfig): String =
    config.localModel.ifBlank { LocalModelCatalog.models.firstOrNull()?.id.orEmpty() }

internal fun resolveLocalFallbackModelId(
    config: ApiConfig,
    modality: ModelModality,
    useLocalDirect: Boolean,
    localReady: Boolean,
): String? = resolveSelectedLocalModelId(config).takeIf {
    !useLocalDirect && modality == ModelModality.Text && config.offlineFallbackEnabled && localReady
}

internal fun shouldUseLocalModel(
    config: ApiConfig,
    modality: ModelModality,
    userText: String,
    hasAttachments: Boolean,
    hasSkill: Boolean,
    localReady: Boolean,
    localEngineAvailable: Boolean,
): Boolean {
    if (
        modality != ModelModality.Text || hasAttachments || hasSkill ||
        !localReady || !localEngineAvailable
    ) return false
    return config.localModelDirectEnabled ||
        (config.dynamicLocalRoutingEnabled && isSimpleLocalRequest(userText))
}

internal fun isSimpleLocalRequest(userText: String): Boolean {
    val text = userText.trim().lowercase()
    if (text.isBlank() || text.length > 240 || text.lines().size > 4) return false
    if (isConversationDispatchRequest(text)) return false
    val remoteOnlyTerms = listOf(
        "图片", "照片", "截图", "附件", "pdf", "文档", "文件",
        "手机", "短信", "电话", "联系人", "日历", "闹钟", "相机", "定位", "位置",
        "蓝牙", "wifi", "wi-fi", "剪贴板", "通知", "应用", "系统设置",
        "生成图片", "画一张", "生图", "image", "photo", "attachment",
        "phone", "sms", "call", "contact", "calendar", "alarm", "camera",
        "location", "bluetooth", "clipboard", "notification",
    )
    if (remoteOnlyTerms.any(text::contains)) return false
    val complexTerms = listOf(
        "详细分析", "深入分析", "全面比较", "制定方案", "分步骤", "多个步骤",
        "调研", "检索", "联网", "最新", "实时", "长文", "报告", "代码",
        "analyze", "research", "compare", "plan", "step by step", "latest", "code",
    )
    return complexTerms.none(text::contains)
}

internal fun isConversationDispatchRequest(text: String): Boolean {
    val normalized = text.lowercase()
    val targetTerms = listOf("对话", "会话", "conversation", "chat")
    val actionTerms = listOf("发到", "发送到", "转到", "转发到", "send to", "forward to")
    return targetTerms.any(normalized::contains) && actionTerms.any(normalized::contains)
}

internal fun isOfflineFailure(message: String): Boolean {
    val normalized = message.lowercase()
    return listOf(
        "timeout", "timed out", "unable to resolve host", "failed to connect",
        "unknownhostexception", "sockettimeoutexception", "connectexception",
        "connection reset", "connection refused", "network is unreachable",
        "网络", "超时", "无法连接", "连接失败", "api 错误 502", "api 错误 503", "api 错误 504",
    ).any(normalized::contains)
}

internal fun remoteTimeoutMessage(timeoutMillis: Long): String =
    "模型响应超过 ${timeoutMillis / 1000} 秒，已停止"

internal fun detectModelModality(
    userText: String,
    references: List<ChatAttachmentReference>,
    attachments: List<com.denggl2.mason.llm.ModelAttachment>,
): ModelModality {
    val text = userText.lowercase()
    val requestsImage = listOf("生成图片", "画一张", "生图", "create an image", "generate an image")
        .any(text::contains)
    return when {
        requestsImage -> ModelModality.ImageGeneration
        references.any(ChatAttachmentReference::image) ||
            attachments.any { it.mimeType?.startsWith("image/") == true } -> ModelModality.Vision
        else -> ModelModality.Text
    }
}

internal fun resolveVisionModel(config: ApiConfig): String = config.visionModel.ifBlank {
    config.model.takeIf {
        config.providerId == AiProviderCatalog.CUSTOM_PROVIDER_ID ||
            AiProviderCatalog.getModel(config.providerId, config.model)?.supportsVision == true
    }.orEmpty()
}

internal fun sanitizeAttachmentMetadata(
    messages: List<ChatMessage>,
    context: ChatRequestContext,
): List<ChatMessage> {
    if (context.attachments.isEmpty()) return messages
    val lastUserIndex = messages.indexOfLast { it.role == "user" }
    if (lastUserIndex < 0) return messages
    val original = messages[lastUserIndex]
    val sanitized = if (context.skillId == null) {
        context.userText
    } else {
        original.content.orEmpty().lineSequence()
            .filterNot { raw ->
                val line = raw.trim().removePrefix("-").trim()
                line.startsWith("图片：") || line.startsWith("文件：")
            }
            .joinToString("\n")
    }
    return messages.toMutableList().apply {
        set(lastUserIndex, original.copy(content = sanitized))
    }
}
