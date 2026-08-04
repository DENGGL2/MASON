package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationMessage
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationRole
import com.denggl2.mason.protocol.RemoteConversationSummary
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

interface RemoteConversationProvider {
    suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage
    suspend fun readConversation(threadId: String): RemoteConversationDetail
}

class RemoteConversationService(
    private val api: CodexThreadHistoryApi,
    private val store: ConnectorStateStore,
    private val messageLimit: Int = DEFAULT_MESSAGE_LIMIT,
) : RemoteConversationProvider {
    init {
        require(messageLimit > 0) { "Remote conversation message limit must be positive" }
    }

    override suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage {
        require(limit in 1..MAX_PAGE_SIZE) { "Conversation page size must be between 1 and $MAX_PAGE_SIZE" }
        val response = api.listThreads(limit = limit, cursor = cursor).asObject()
        val conversations = response["data"]
            .asArrayOrEmpty()
            .mapNotNull { element -> element.asObjectOrNull()?.toSummary() }
        return RemoteConversationPage(
            conversations = conversations,
            nextCursor = response.string("nextCursor") ?: response.string("next_cursor"),
        )
    }

    override suspend fun readConversation(threadId: String): RemoteConversationDetail {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val response = api.readThread(threadId = threadId, includeTurns = true).asObject()
        val thread = response["thread"].asObjectOrNull() ?: response
        val summary = thread.toSummary()
            ?: throw RemoteConversationNotFoundException(threadId)
        val allMessages = thread["turns"]
            .asArrayOrEmpty()
            .flatMap { turn ->
                turn.asObjectOrNull()
                    ?.get("items")
                    .asArrayOrEmpty()
                    .mapNotNull(::projectMessage)
            }
        return RemoteConversationDetail(
            conversation = summary,
            messages = allMessages.takeLast(messageLimit),
            hasEarlierMessages = allMessages.size > messageLimit,
        )
    }

    private fun JsonObject.toSummary(): RemoteConversationSummary? {
        val threadId = string("id") ?: string("threadId") ?: return null
        val preview = string("preview").orEmpty().cleanPreview()
        val explicitTitle = string("name").orEmpty().trim()
        return RemoteConversationSummary(
            threadId = threadId,
            title = explicitTitle.ifBlank { preview.lineSequence().firstOrNull().orEmpty() }
                .ifBlank { "未命名对话" }
                .take(MAX_TITLE_LENGTH),
            preview = preview.take(MAX_PREVIEW_LENGTH),
            updatedAt = epochMillis("updatedAt") ?: epochMillis("updated_at") ?: 0,
            projectPath = string("cwd")?.takeIf(String::isNotBlank),
            ownership = store.sessionForThread(threadId)?.binding?.ownership
                ?: CodexOwnership.EXTERNAL_HISTORY_ONLY,
        )
    }

    private fun projectMessage(element: JsonElement): RemoteConversationMessage? {
        val item = element.asObjectOrNull() ?: return null
        val type = item.string("type").orEmpty().lowercase()
        val role = when {
            type.contains("user") -> RemoteConversationRole.USER
            type.contains("agentmessage") || type.contains("assistant") -> RemoteConversationRole.ASSISTANT
            item.string("role").equals("user", ignoreCase = true) -> RemoteConversationRole.USER
            item.string("role").equals("assistant", ignoreCase = true) -> RemoteConversationRole.ASSISTANT
            else -> return null
        }
        val text = extractText(item).trim()
        if (text.isBlank()) return null
        return RemoteConversationMessage(role = role, text = text)
    }

    private fun extractText(item: JsonObject): String {
        item.string("text")?.let { return it }
        val content = item["content"] ?: return ""
        content.asPrimitiveString()?.let { return it }
        return content.asArrayOrEmpty()
            .mapNotNull { part ->
                part.asPrimitiveString()
                    ?: part.asObjectOrNull()?.string("text")
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    private fun JsonObject.epochMillis(key: String): Long? {
        val value = this[key]?.jsonPrimitive ?: return null
        val numeric = value.longOrNull ?: value.contentOrNull?.toLongOrNull()
        if (numeric != null) return if (numeric in 1..999_999_999_999L) numeric * 1_000 else numeric
        return value.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }

    private fun String.cleanPreview(): String = replace(Regex("\\s+"), " ").trim()

    companion object {
        const val MAX_PAGE_SIZE = 30
        const val DEFAULT_MESSAGE_LIMIT = 20
        private const val MAX_TITLE_LENGTH = 80
        private const val MAX_PREVIEW_LENGTH = 160
    }
}

class RemoteConversationNotFoundException(threadId: String) :
    IllegalStateException("Codex thread was not found: $threadId")

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement.asObject(): JsonObject = this as? JsonObject
    ?: throw IllegalArgumentException("Codex response must be a JSON object")

private fun JsonElement?.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

private fun JsonElement.asPrimitiveString(): String? =
    runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
