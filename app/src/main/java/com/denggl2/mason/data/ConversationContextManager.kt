package com.denggl2.mason.data

import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.integration.stripCapabilityRequirementMarkers
import com.denggl2.mason.llm.model.ChatMessage
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationContextManager @Inject constructor(
    private val memoryStore: UserMemoryStore,
    private val summaryStore: ConversationSummaryStore,
    private val projectContextStore: ProjectContextStore,
) {
    suspend fun prepare(
        messages: List<ChatMessage>,
        query: String,
        scopeId: String? = null,
        includeMemory: Boolean = true,
    ): List<ChatMessage> {
        val projectContext = scopeId?.let { conversationId ->
            projectContextStore.resolve(conversationId, query)
        }
        val explicitSensitiveUse = includeMemory && allowsSensitiveMemory(query)
        val memories = if (includeMemory) {
            memoryStore.relevant(
                query = query,
                conversationScopeId = scopeId,
                projectScopeId = projectContext?.id,
                allowSensitive = explicitSensitiveUse,
            )
        } else {
            emptyList()
        }
        val memoryMessage = memories.takeIf(List<UserMemoryItem>::isNotEmpty)?.let { items ->
            ChatMessage(
                role = "system",
                content = buildString {
                    append("以下是与本轮相关的本机记忆，只在确有帮助时使用，不要主动泄露：\n")
                    items.forEach { append("- ${it.label}：${it.value}\n") }
                }.trimEnd(),
            )
        }
        val projectMessage = projectContext?.let { project ->
            ChatMessage(
                role = "system",
                content = buildString {
                    append("当前会话属于项目：${project.displayName}（project_id=${project.id}）。")
                    append("项目技术栈、约定和项目事实应使用 PROJECT 记忆；用户个人偏好仍使用 GLOBAL。")
                },
            )
        }
        val compacted = compactPersisted(messages, scopeId)
        return listOfNotNull(projectMessage, memoryMessage) + compacted
    }

    fun compact(messages: List<ChatMessage>): List<ChatMessage> {
        return compactConversationMessages(messages)
    }

    private suspend fun compactPersisted(
        messages: List<ChatMessage>,
        scopeId: String?,
    ): List<ChatMessage> {
        val compaction = conversationCompaction(messages) ?: return messages
        val summaryContent = if (scopeId.isNullOrBlank()) {
            buildConversationSummary(compaction.olderMessages)
        } else {
            val existing = summaryStore.get(scopeId)
            val next = updateConversationSummary(scopeId, compaction.olderMessages, existing)
            (if (next != existing) summaryStore.save(next) else next).content
        }
        val summaryMessage = summaryContent.takeIf(String::isNotBlank)?.let { content ->
            ChatMessage(role = "system", content = "较早对话的持久摘要：\n$content")
        }
        return compaction.systemMessages + listOfNotNull(summaryMessage) + compaction.recentMessages
    }

}

internal fun allowsSensitiveMemory(query: String): Boolean =
    listOf(
        "使用记忆", "根据我的", "我的地址", "我的住址", "我的身份", "我的账号",
        "我的姓名", "我的名字", "怎么称呼我", "我叫什么", "我的车牌", "我的收款",
    ).any(query::contains)

internal fun compactConversationMessages(
    messages: List<ChatMessage>,
    maxMessages: Int = 24,
    maxChars: Int = 16_000,
    keepRecentMessages: Int = 12,
): List<ChatMessage> {
    val compaction = conversationCompaction(messages, maxMessages, maxChars, keepRecentMessages)
        ?: return messages
    val summaryContent = buildConversationSummary(compaction.olderMessages)
    val summaryMessage = summaryContent.takeIf(String::isNotBlank)?.let { content ->
        ChatMessage(role = "system", content = "较早对话的压缩摘要：\n$content")
    }
    return compaction.systemMessages + listOfNotNull(summaryMessage) + compaction.recentMessages
}

internal fun updateConversationSummary(
    scopeId: String,
    olderMessages: List<ChatMessage>,
    existing: ConversationSummary?,
    now: Long = System.currentTimeMillis(),
): ConversationSummary {
    val existingPrefixMatches = existing != null &&
        existing.coveredMessageCount <= olderMessages.size &&
        existing.sourceFingerprint == conversationFingerprint(olderMessages.take(existing.coveredMessageCount))
    val content = if (existingPrefixMatches) {
        mergeSummaryContent(
            existing.content,
            summaryLines(olderMessages.drop(existing.coveredMessageCount)),
        )
    } else {
        buildConversationSummary(olderMessages)
    }
    val next = ConversationSummary(
        scopeId = scopeId,
        content = content,
        coveredMessageCount = olderMessages.size,
        coveredThroughTimestamp = olderMessages.lastOrNull()?.timestamp,
        sourceFingerprint = conversationFingerprint(olderMessages),
        updatedAtMillis = now,
    )
    return if (existing != null &&
        existing.content == next.content &&
        existing.coveredMessageCount == next.coveredMessageCount &&
        existing.coveredThroughTimestamp == next.coveredThroughTimestamp &&
        existing.sourceFingerprint == next.sourceFingerprint
    ) existing else next
}

private fun conversationCompaction(
    messages: List<ChatMessage>,
    maxMessages: Int = 24,
    maxChars: Int = 16_000,
    keepRecentMessages: Int = 12,
): ConversationCompaction? {
    if (messages.size <= maxMessages && messages.sumOf { it.content.orEmpty().length } <= maxChars) return null
    val system = messages.filter { it.role == "system" }.takeLast(MAX_SYSTEM_MESSAGES)
    val conversation = messages.filterNot { it.role == "system" }
    val rawTail = conversation.takeLast(keepRecentMessages.coerceAtLeast(1)).toMutableList()
    while (rawTail.firstOrNull()?.role == "tool") rawTail.removeAt(0)
    val tail = rawTail.map { message ->
        message.copy(content = message.content?.take(MAX_RECENT_MESSAGE_CHARS))
    }
    return ConversationCompaction(
        systemMessages = system,
        olderMessages = conversation.dropLast(rawTail.size),
        recentMessages = tail,
    )
}

private fun buildConversationSummary(messages: List<ChatMessage>): String =
    mergeSummaryContent("", summaryLines(messages))

private fun summaryLines(messages: List<ChatMessage>): String = messages.asSequence()
    .filter { it.role == "user" || it.role == "assistant" }
    .map { message ->
        val content = stripTaskRunMarkers(
            stripArtifactMarkers(
                stripCapabilityRequirementMarkers(message.content.orEmpty()),
            ),
        ).trim().take(SUMMARY_MESSAGE_CHARS)
        "${message.role}: $content"
    }
    .filterNot { it.endsWith(": ") }
    .joinToString("\n")

private fun mergeSummaryContent(existing: String, appended: String): String {
    val merged = listOf(existing.trim(), appended.trim()).filter(String::isNotBlank).joinToString("\n")
    if (merged.length <= MAX_SUMMARY_CHARS) return merged
    val headLength = MAX_SUMMARY_CHARS / 2
    val tailLength = MAX_SUMMARY_CHARS - headLength - SUMMARY_GAP.length
    return merged.take(headLength).trimEnd() + SUMMARY_GAP + merged.takeLast(tailLength).trimStart()
}

private fun conversationFingerprint(messages: List<ChatMessage>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    messages.forEach { message ->
        digest.update(message.role.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(message.content.orEmpty().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(message.tool_call_id.orEmpty().toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private data class ConversationCompaction(
    val systemMessages: List<ChatMessage>,
    val olderMessages: List<ChatMessage>,
    val recentMessages: List<ChatMessage>,
)

private const val MAX_SYSTEM_MESSAGES = 4
private const val MAX_RECENT_MESSAGE_CHARS = 4_000
private const val MAX_SUMMARY_CHARS = 2_400
private const val SUMMARY_MESSAGE_CHARS = 220
private const val SUMMARY_GAP = "\n...\n"
