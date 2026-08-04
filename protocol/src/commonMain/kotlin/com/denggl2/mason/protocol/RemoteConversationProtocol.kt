package com.denggl2.mason.protocol

import kotlinx.serialization.Serializable

@Serializable
data class RemoteConversationSummary(
    val threadId: String,
    val title: String,
    val preview: String = "",
    val updatedAt: Long = 0,
    val projectPath: String? = null,
    val ownership: CodexOwnership = CodexOwnership.EXTERNAL_HISTORY_ONLY,
)

@Serializable
data class RemoteConversationPage(
    val conversations: List<RemoteConversationSummary>,
    val nextCursor: String? = null,
)

@Serializable
enum class RemoteConversationRole {
    USER,
    ASSISTANT,
}

@Serializable
data class RemoteConversationMessage(
    val role: RemoteConversationRole,
    val text: String,
)

@Serializable
data class RemoteConversationDetail(
    val conversation: RemoteConversationSummary,
    val messages: List<RemoteConversationMessage>,
    val hasEarlierMessages: Boolean = false,
)
