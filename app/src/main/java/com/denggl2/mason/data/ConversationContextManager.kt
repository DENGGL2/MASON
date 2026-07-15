package com.denggl2.mason.data

import com.denggl2.mason.llm.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationContextManager @Inject constructor(
    private val memoryStore: UserMemoryStore,
) {
    suspend fun prepare(
        messages: List<ChatMessage>,
        query: String,
        scopeId: String? = null,
    ): List<ChatMessage> {
        val explicitSensitiveUse = listOf("使用记忆", "根据我的", "我的地址", "我的身份", "我的账号")
            .any(query::contains)
        val memories = memoryStore.relevant(query, scopeId, allowSensitive = explicitSensitiveUse)
        val memoryMessage = memories.takeIf(List<UserMemoryItem>::isNotEmpty)?.let { items ->
            ChatMessage(
                role = "system",
                content = buildString {
                    append("以下是与本轮相关的本机记忆，只在确有帮助时使用，不要主动泄露：\n")
                    items.forEach { append("- ${it.label}：${it.value}\n") }
                }.trimEnd(),
            )
        }
        val compacted = compact(messages)
        return if (memoryMessage == null) compacted else listOf(memoryMessage) + compacted
    }

    fun compact(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.size <= MAX_MESSAGES && messages.sumOf { it.content.orEmpty().length } <= MAX_CHARS) {
            return messages
        }
        val system = messages.filter { it.role == "system" }
        val tail = messages.filterNot { it.role == "system" }.takeLast(KEEP_RECENT_MESSAGES)
        val old = messages.filterNot { it.role == "system" }.dropLast(tail.size)
        val summary = old.takeLast(SUMMARY_SOURCE_MESSAGES).joinToString("\n") { message ->
            "${message.role}：${message.content.orEmpty().take(220)}"
        }.take(MAX_SUMMARY_CHARS)
        return system + ChatMessage(
            role = "system",
            content = "较早对话的压缩摘要：\n$summary",
        ) + tail
    }

    private companion object {
        const val MAX_MESSAGES = 24
        const val MAX_CHARS = 16_000
        const val KEEP_RECENT_MESSAGES = 12
        const val SUMMARY_SOURCE_MESSAGES = 12
        const val MAX_SUMMARY_CHARS = 2_400
    }
}
