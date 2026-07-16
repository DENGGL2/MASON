package com.denggl2.mason.model

import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.ConversationContextManager
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.LiteRtModelEngine
import com.denggl2.mason.llm.ModelEngineStatus
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

data class ModelRouteDecision(
    val engineId: String,
    val modelId: String,
    val modality: ModelModality,
    val reason: String,
    val fallbackModelId: String? = null,
)

data class RoutedModelResponse(
    val decision: ModelRouteDecision,
    val responses: Flow<ChatResponse>,
)

@Singleton
class MasonModelRouter @Inject constructor(
    private val configStore: ApiConfigDataStore,
    private val chatClient: ChatClient,
    private val localStore: LocalModelStore,
    private val localEngine: LiteRtModelEngine,
    private val attachmentResolver: ChatAttachmentResolver,
    private val contextManager: ConversationContextManager,
) {
    private val _statuses = MutableStateFlow<Map<ModelModality, ModelEngineStatus>>(emptyMap())
    val statuses = _statuses.asStateFlow()

    suspend fun route(
        messages: List<ChatMessage>,
        toolsEnabled: Boolean = true,
        includeMemory: Boolean = true,
    ): RoutedModelResponse {
        val config = configStore.config.first()
        val context = ChatContextParser.parse(messages.lastOrNull { it.role == "user" }?.content.orEmpty())
        val attachmentResult = runCatching { attachmentResolver.resolve(context.attachments) }
        val attachments = attachmentResult.getOrDefault(emptyList())
        val modality = detectModelModality(context.userText, context.attachments, attachments)
        val sanitizedMessages = sanitizeAttachmentMetadata(messages, context)
        val preparedMessages = if (includeMemory) {
            contextManager.prepare(sanitizedMessages, context.userText)
        } else {
            contextManager.compact(sanitizedMessages)
        }
        val localModelId = config.localModel.ifBlank { LocalModelCatalog.gemmaModels.firstOrNull()?.id.orEmpty() }
        val localReady = LocalModelCatalog.get(localModelId)?.let(localStore::stateFor)?.state in setOf(
            LocalModelInstallState.Installed,
            LocalModelInstallState.DeviceMayBeUnsupported,
        )
        val useLocal = modality == ModelModality.Text && context.attachments.isEmpty() && context.skillId == null &&
            config.localModelDirectEnabled && localReady
        val selectedModel = when (modality) {
            ModelModality.Text -> if (useLocal) localModelId else config.model
            ModelModality.Vision -> resolveVisionModel(config)
            ModelModality.ImageGeneration -> config.imageModel
        }
        val decision = ModelRouteDecision(
            engineId = if (useLocal) localEngine.id else "remote-openai-compatible",
            modelId = selectedModel,
            modality = modality,
            reason = routeReason(modality, useLocal, context.attachments.isNotEmpty()),
            fallbackModelId = localModelId.takeIf {
                !useLocal && modality == ModelModality.Text && config.offlineFallbackEnabled && localReady
            },
        )
        recordStatus(decision, selectedModel.isNotBlank(), if (selectedModel.isBlank()) "未配置对应模型" else "已路由")
        val invocation = ModelInvocation(
            modality = modality,
            messages = preparedMessages,
            modelId = selectedModel,
            attachments = attachments,
            toolsEnabled = toolsEnabled && modality == ModelModality.Text,
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

    suspend fun cancelActive() = localEngine.cancelActiveInvocation()

    suspend fun releaseLocal() = localEngine.release()

    private fun invokeWithFallback(
        invocation: ModelInvocation,
        decision: ModelRouteDecision,
    ): Flow<ChatResponse> = flow {
        var remoteFailed = false
        var remoteError: String? = null
        val primary = when {
            decision.engineId == localEngine.id -> localEngine.invoke(invocation)
            invocation.modality == ModelModality.ImageGeneration ->
                chatClient.generateImage(invocation.messages.lastOrNull()?.content.orEmpty(), invocation.modelId)
            else -> chatClient.chat(
                messages = invocation.messages,
                toolsEnabled = invocation.toolsEnabled,
                modelOverride = invocation.modelId,
                attachments = invocation.attachments,
            )
        }
        try {
            withTimeout(invocation.timeoutMillis) {
                primary.collect { response ->
                    if (response is ChatResponse.Error && decision.fallbackModelId != null) {
                        remoteFailed = true
                        remoteError = response.message
                    } else {
                        emit(response)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            emit(ChatResponse.Error("模型响应超过 ${invocation.timeoutMillis / 1000} 秒，已停止"))
            return@flow
        }
        if (remoteFailed) {
            val fallback = invocation.copy(
                modelId = decision.fallbackModelId.orEmpty(),
                attachments = emptyList(),
                toolsEnabled = false,
            )
            emit(ChatResponse.TextChunk("进行中：远程模型失败，已切换到本地模型。\n"))
            localEngine.invoke(fallback).collect(::emit)
            recordStatus(
                decision.copy(engineId = localEngine.id, modelId = fallback.modelId),
                true,
                "远程失败后已启用本地兜底：${remoteError.orEmpty()}",
            )
        }
    }

    private fun routeReason(modality: ModelModality, local: Boolean, hasAttachments: Boolean): String = when {
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
