package com.denggl2.mason.model

import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.configuredChatModelRefs
import com.denggl2.mason.data.configuredImageModelRef
import com.denggl2.mason.data.configuredVisionModelRef
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
        contribution: String? = null,
    ): RoutedModelResponse {
        val config = configStore.config.first()
        val context = ChatContextParser.parse(messages.lastOrNull { it.role == "user" }?.content.orEmpty())
        val attachmentResult = runCatching { attachmentResolver.resolve(context.attachments) }
        val resolvedAttachments = attachmentResult.getOrDefault(emptyList())
        val attachments = resolvedAttachments.filter { it.inlineText.isNullOrBlank() }
        val modality = detectModelModality(context.userText, context.attachments, resolvedAttachments)
        val sanitizedMessages = appendInlineAttachmentText(
            sanitizeAttachmentMetadata(messages, context),
            resolvedAttachments,
        )
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
            hasAttachments = attachments.isNotEmpty(),
            hasSkill = context.skillId != null,
            localReady = localReady,
            localEngineAvailable = selectedLocalEngine != null,
        )
        val remoteSelection = if (modality == ModelModality.Text && !useLocal) {
            selectConfiguredRemoteChatModel(
                config = config,
                userText = context.userText,
                toolsRequested = toolsEnabled && config.phoneToolsEnabled,
            )
        } else {
            null
        }
        val remoteRef = when (modality) {
            ModelModality.Text -> remoteSelection?.reference ?: config.configuredChatModelRef()
            ModelModality.Vision -> config.configuredVisionModelRef()
            ModelModality.ImageGeneration -> config.configuredImageModelRef()
        }
        val selectedModel = if (useLocal) localModelId else remoteRef?.modelId.orEmpty()
        val selectedConnection = remoteRef?.let { config.connection(it.connectionId) }
        val modelSupportsTools = selectedConnection
            ?.modelCapabilities
            ?.get(selectedModel)
            ?.supportsTools == true
        val decision = ModelRouteDecision(
            engineId = if (useLocal) selectedLocalEngine?.id.orEmpty() else remoteEngine.id,
            modelId = selectedModel,
            modality = modality,
            reason = routeReason(
                modality = modality,
                local = useLocal,
                hasAttachments = context.attachments.isNotEmpty(),
                dynamicLocalRoutingEnabled = config.dynamicLocalRoutingEnabled,
                hasRemoteChatModel = remoteRef != null,
                remoteReason = remoteSelection?.reason,
            ),
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
            toolsEnabled = shouldEnableRemoteTools(
                requested = toolsEnabled,
                phoneToolsEnabled = config.phoneToolsEnabled,
                modelSupportsTools = modelSupportsTools,
                modality = modality,
                useLocal = useLocal,
            ),
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
            else -> invokeWithFallback(
                invocation = invocation,
                decision = decision,
                contribution = contribution ?: when (modality) {
                    ModelModality.Text -> "理解问题并生成回答"
                    ModelModality.Vision -> "识别图片并生成回答"
                    ModelModality.ImageGeneration -> "生成图片"
                },
            )
        }
        return RoutedModelResponse(decision, responses)
    }

    suspend fun cancelActive() = localEngines.cancelActive()

    suspend fun releaseLocal() = localEngines.releaseAll()

    private fun invokeWithFallback(
        invocation: ModelInvocation,
        decision: ModelRouteDecision,
        contribution: String,
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
        emit(
            ChatResponse.ModelExecutionStarted(
                engineId = engine.id,
                modelId = invocation.modelId,
                contribution = contribution,
            ),
        )
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
            emit(
                ChatResponse.ModelExecutionStarted(
                    engineId = fallbackEngine.id,
                    modelId = fallback.modelId,
                    contribution = "远端失败后生成兜底回答",
                ),
            )
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
        hasRemoteChatModel: Boolean,
        remoteReason: String?,
    ): String = when {
        local && !hasRemoteChatModel -> "未配置远程聊天模型，使用已安装的本地模型"
        local && dynamicLocalRoutingEnabled -> "简单文字请求，按难度动态使用本地模型"
        local -> "用户选择本地直连，且请求仅包含文字"
        modality == ModelModality.Vision -> "检测到图片附件，使用识图模型"
        modality == ModelModality.ImageGeneration -> "检测到生图请求，使用生图模型"
        hasAttachments -> "检测到文件附件，使用远程模型处理抽取文本"
        remoteReason != null -> remoteReason
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

internal enum class ModelTaskDifficulty {
    Simple,
    Standard,
    Complex,
}

internal fun shouldEnableRemoteTools(
    requested: Boolean,
    phoneToolsEnabled: Boolean,
    modelSupportsTools: Boolean,
    modality: ModelModality,
    useLocal: Boolean,
): Boolean = requested && phoneToolsEnabled && modelSupportsTools &&
    modality == ModelModality.Text && !useLocal

internal data class RemoteChatModelSelection(
    val reference: com.denggl2.mason.data.ModelReference,
    val reason: String,
)

internal fun selectConfiguredRemoteChatModel(
    config: ApiConfig,
    userText: String,
    toolsRequested: Boolean,
): RemoteChatModelSelection? {
    val selected = config.configuredChatModelRef()
    val allCandidates = config.configuredChatModelRefs()
    if (allCandidates.isEmpty()) return null
    if (!config.dynamicLocalRoutingEnabled) {
        return selected?.let { RemoteChatModelSelection(it, "动态选择已关闭，使用当前聊天模型") }
    }

    val toolIntent = toolsRequested && isLikelyToolRequest(userText)
    val capableCandidates = if (toolIntent) {
        allCandidates.filter { reference ->
            config.connection(reference.connectionId)
                ?.modelCapabilities
                ?.get(reference.modelId)
                ?.supportsTools == true
        }
    } else {
        allCandidates
    }
    val candidates = capableCandidates.ifEmpty { allCandidates }
    val difficulty = classifyModelTaskDifficulty(userText)
    val chosen = when (difficulty) {
        ModelTaskDifficulty.Simple -> candidates.minWithOrNull(
            compareBy<com.denggl2.mason.data.ModelReference> { modelRoutingStrength(config, it) }
                .thenBy { if (it == selected) 0 else 1 },
        )
        ModelTaskDifficulty.Standard -> selected?.takeIf(candidates::contains) ?: candidates.first()
        ModelTaskDifficulty.Complex -> candidates.maxWithOrNull(
            compareBy<com.denggl2.mason.data.ModelReference> { modelRoutingStrength(config, it) }
                .thenBy { if (it == selected) 1 else 0 },
        )
    } ?: return null
    val reason = when {
        toolIntent && chosen != selected -> "任务需要工具，自动选择支持工具的已配置模型"
        difficulty == ModelTaskDifficulty.Simple && chosen != selected -> "任务较简单，自动选择轻量的已配置模型"
        difficulty == ModelTaskDifficulty.Complex && chosen != selected -> "任务较复杂，自动选择能力更强的已配置模型"
        else -> "根据任务难度，优先使用当前聊天模型"
    }
    return RemoteChatModelSelection(chosen, reason)
}

internal fun classifyModelTaskDifficulty(userText: String): ModelTaskDifficulty {
    val text = userText.trim().lowercase()
    if (text.length > 1_200 || text.lines().size > 16) return ModelTaskDifficulty.Complex
    val complexTerms = listOf(
        "详细分析", "深入分析", "全面比较", "制定方案", "分步骤", "多个步骤",
        "调研", "研究报告", "长文", "完整代码", "架构设计", "审查代码", "推理",
        "analyze", "research", "compare", "step by step", "architecture", "review code",
    )
    if (complexTerms.any(text::contains)) return ModelTaskDifficulty.Complex
    val simpleTerms = listOf(
        "翻译", "改写", "润色", "总结一下", "一句话", "是什么", "什么意思",
        "translate", "rewrite", "summarize", "what is", "define",
    )
    return if (
        text.length <= 240 && text.lines().size <= 4 &&
        (simpleTerms.any(text::contains) || text.length <= 48)
    ) {
        ModelTaskDifficulty.Simple
    } else {
        ModelTaskDifficulty.Standard
    }
}

internal fun isLikelyToolRequest(userText: String): Boolean {
    val text = userText.lowercase()
    return listOf(
        "联网", "搜索", "查询最新", "打开", "发送", "写入", "删除", "创建日程",
        "短信", "电话", "联系人", "日历", "闹钟", "定位", "文件", "github", "mcp",
        "search", "browse", "open", "send", "write", "delete", "calendar", "contact",
    ).any(text::contains)
}

private fun modelRoutingStrength(
    config: ApiConfig,
    reference: com.denggl2.mason.data.ModelReference,
): Int {
    val connection = config.connection(reference.connectionId)
    val preset = connection?.let { AiProviderCatalog.getModel(it.providerId, reference.modelId) }
    val searchable = listOf(
        reference.modelId,
        preset?.name.orEmpty(),
        preset?.description.orEmpty(),
        preset?.modeLabel.orEmpty(),
    ).joinToString(" ").lowercase()
    val advanced = listOf(
        "pro", "max", "opus", "reason", "thinking", "旗舰", "复杂", "推理",
        "32b", "70b", "72b", "80b", "gpt-5", "k2.5",
    )
    if (advanced.any(searchable::contains)) return 2
    val lightweight = listOf(
        "flash", "turbo", "mini", "nano", "air", "lite", "轻量", "速度", "低成本",
        "8b", "free", "免费",
    )
    return if (lightweight.any(searchable::contains) || preset?.isFree == true) 0 else 1
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
    return config.configuredChatModelRef() == null ||
        config.localModelDirectEnabled ||
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

internal fun appendInlineAttachmentText(
    messages: List<ChatMessage>,
    attachments: List<com.denggl2.mason.llm.ModelAttachment>,
): List<ChatMessage> {
    val documents = attachments.mapNotNull { attachment ->
        attachment.inlineText?.takeIf(String::isNotBlank)?.let { text ->
            "<attachment name=\"${attachment.name.replace('"', '_')}\">\n$text\n</attachment>"
        }
    }
    if (documents.isEmpty()) return messages
    val lastUserIndex = messages.indexOfLast { it.role == "user" }
    if (lastUserIndex < 0) return messages
    val original = messages[lastUserIndex]
    val documentContext = documents.joinToString("\n\n").take(MAX_INLINE_DOCUMENT_CHARACTERS)
    return messages.toMutableList().apply {
        set(
            lastUserIndex,
            original.copy(
                content = listOfNotNull(
                    original.content?.takeIf(String::isNotBlank),
                    "以下内容由 Mason 在本地从附件中提取：\n$documentContext",
                ).joinToString("\n\n"),
            ),
        )
    }
}

private const val MAX_INLINE_DOCUMENT_CHARACTERS = 240_000
