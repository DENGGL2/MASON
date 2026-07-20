package com.denggl2.mason.llm

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Bridges the existing OpenAI-compatible client into the common engine contract. */
@Singleton
class OpenAiCompatibleModelEngine @Inject constructor(
    private val chatClient: ChatClient,
) : ModelEngine {
    override val id: String = "remote-openai-compatible"
    override val supportsStreaming: Boolean = true

    override fun canHandle(invocation: ModelInvocation): Boolean = invocation.modelId.isNotBlank()

    override fun invoke(invocation: ModelInvocation): Flow<ChatResponse> = when (invocation.modality) {
        ModelModality.ImageGeneration -> chatClient.generateImage(
            prompt = invocation.messages.lastOrNull { it.role == "user" }?.content.orEmpty(),
            modelOverride = invocation.modelId,
        )
        ModelModality.Text,
        ModelModality.Vision -> chatClient.chat(
            messages = invocation.messages,
            toolsEnabled = invocation.toolsEnabled,
            modelOverride = invocation.modelId,
            attachments = invocation.attachments,
        )
    }
}
