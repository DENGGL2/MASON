package com.denggl2.mason.automation

import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.model.MasonModelRouter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList

data class AutomationGenerationResult(
    val content: String,
    val modelLabel: String,
)

@Singleton
class AutomationModelGenerator @Inject constructor(
    private val modelRouter: MasonModelRouter,
) {
    suspend fun generate(prompt: String): AutomationGenerationResult = generateWithSystem(
        prompt = prompt,
        systemInstruction = "这是后台自动化任务。只生成最终文本，不调用工具，不执行手机操作。",
    )

    suspend fun generateStructured(prompt: String): AutomationGenerationResult = generateWithSystem(
        prompt = prompt,
        systemInstruction = "你是 Mason 的自动化规划器。只返回一个有效 JSON 对象，不要 Markdown、解释、前后缀或工具调用。",
    )

    private suspend fun generateWithSystem(
        prompt: String,
        systemInstruction: String,
    ): AutomationGenerationResult {
        require(prompt.isNotBlank()) { "AI 任务内容不能为空" }
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = systemInstruction,
            ),
            ChatMessage(role = "user", content = prompt.trim()),
        )

        val routed = modelRouter.route(messages, toolsEnabled = false, includeMemory = false)
        return try {
            val responses = routed.responses.toList()
            val result = collectText(responses)
            AutomationGenerationResult(
                content = result.content ?: error(result.error ?: "本地模型没有返回内容"),
                modelLabel = routed.decision.modelId,
            )
        } finally {
            if (routed.decision.engineId == "litert-lm") modelRouter.releaseLocal()
        }
    }

    private fun collectText(responses: List<ChatResponse>): CollectedText {
        val content = buildString {
            responses.forEach { response ->
                if (response is ChatResponse.TextChunk) append(response.text)
            }
        }.trim()
        val error = responses.filterIsInstance<ChatResponse.Error>()
            .lastOrNull()
            ?.message
        val requestedTools = responses.any { it is ChatResponse.ToolCallsRequested }
        return CollectedText(
            content = content.takeIf { it.isNotBlank() && !requestedTools },
            error = when {
                requestedTools -> "后台模型请求了工具调用，已拒绝执行"
                error != null -> error
                else -> null
            },
        )
    }

    private data class CollectedText(
        val content: String?,
        val error: String?,
    )

}
