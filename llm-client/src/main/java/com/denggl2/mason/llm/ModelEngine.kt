package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import kotlinx.coroutines.flow.Flow

enum class ModelModality {
    Text,
    Vision,
    ImageGeneration,
}

data class ModelInvocation(
    val modality: ModelModality,
    val messages: List<ChatMessage>,
    val modelId: String,
    val attachments: List<ModelAttachment> = emptyList(),
    val toolsEnabled: Boolean = false,
    val timeoutMillis: Long = 120_000L,
    val maxOutputTokens: Int? = null,
)

data class ModelAttachment(
    val name: String,
    val uri: String,
    val mimeType: String? = null,
    val inlineText: String? = null,
)

data class ModelEngineStatus(
    val engineId: String,
    val available: Boolean,
    val modelId: String,
    val modality: ModelModality,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis(),
)

interface ModelEngine {
    val id: String
    val supportsStreaming: Boolean

    fun canHandle(invocation: ModelInvocation): Boolean
    fun invoke(invocation: ModelInvocation): Flow<ChatResponse>
}

class LocalModelNotReadyException(
    message: String = "Local model runtime is not ready.",
) : IllegalStateException(message)
