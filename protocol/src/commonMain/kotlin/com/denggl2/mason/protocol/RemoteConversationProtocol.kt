package com.denggl2.mason.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RemoteConversationSummary(
    val threadId: String,
    val title: String,
    val preview: String = "",
    val updatedAt: Long = 0,
    val projectPath: String? = null,
    val ownership: CodexOwnership = CodexOwnership.EXTERNAL_HISTORY_ONLY,
    val executionStatus: RemoteExecutionStatus = RemoteExecutionStatus.IDLE,
    val latestCompletionId: String? = null,
    val isPinned: Boolean = false,
)

@Serializable
data class RemoteConversationPage(
    val conversations: List<RemoteConversationSummary>,
    val nextCursor: String? = null,
    /** Monotonic connector-side execution cursor used by foreground clients. */
    val revision: Long = 0,
)

@Serializable
data class RemoteConversationEventPage(
    /** Latest connector-side execution revision. */
    val revision: Long = 0,
    val changes: List<RemoteConversationExecutionChange> = emptyList(),
)

@Serializable
data class RemoteConversationExecutionChange(
    val revision: Long,
    val threadId: String,
    val turnId: String? = null,
    val status: RemoteExecutionStatus,
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
    val attachments: List<RemoteConversationAttachment> = emptyList(),
)

@Serializable
data class RemoteConversationAttachment(
    val attachmentId: String,
    val kind: RemoteAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
)

@Serializable
enum class RemoteExecutionStatus {
    IDLE,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

@Serializable
enum class RemoteConversationActivityKind {
    THINKING,
    COMMAND,
    WEB_SEARCH,
    TOOL,
    FILE_CHANGE,
    COMMENTARY,
    PLAN,
    IMAGE,
    OTHER,
}

@Serializable
enum class RemoteConversationActivityStatus {
    RUNNING,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

@Serializable
data class RemoteConversationActivity(
    val id: String,
    val kind: RemoteConversationActivityKind,
    val title: String,
    val text: String = "",
    val status: RemoteConversationActivityStatus = RemoteConversationActivityStatus.COMPLETED,
)

@Serializable
data class RemoteConversationDetail(
    val conversation: RemoteConversationSummary,
    val messages: List<RemoteConversationMessage>,
    val hasEarlierMessages: Boolean = false,
    val executionStatus: RemoteExecutionStatus = RemoteExecutionStatus.IDLE,
    val activeTurnId: String? = null,
    val activeActivityTitle: String? = null,
    val activeActivityText: String? = null,
    val activities: List<RemoteConversationActivity> = emptyList(),
)

@Serializable
enum class RemoteAttachmentKind {
    IMAGE,
    FILE,
}

@Serializable
data class RemoteAttachmentDescriptor(
    val attachmentId: String,
    val kind: RemoteAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
)

@Serializable
data class RemoteSkillOption(
    val name: String,
    val displayName: String = name,
    val description: String,
    val path: String,
    val scope: String,
)

@Serializable
data class RemoteReasoningEffortOption(
    val id: String,
    val description: String,
)

@Serializable
data class RemoteModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean = false,
    val defaultReasoningEffort: String,
    val supportedReasoningEfforts: List<RemoteReasoningEffortOption> = emptyList(),
)

@Serializable
data class RemotePermissionProfileOption(
    val id: String,
    val description: String? = null,
    val allowed: Boolean,
)

@Serializable
data class RemoteProjectOption(
    val path: String,
    val displayName: String,
)

@Serializable
data class RemoteComposerOptions(
    val projects: List<RemoteProjectOption> = emptyList(),
    val models: List<RemoteModelOption> = emptyList(),
    val skills: List<RemoteSkillOption> = emptyList(),
    val permissionProfiles: List<RemotePermissionProfileOption> = emptyList(),
    val currentModelId: String? = null,
    val currentReasoningEffort: String? = null,
    val currentPermissionProfileId: String? = null,
    val cwd: String? = null,
)

@Serializable
data class RemoteConversationCreateRequest(
    val text: String,
    val projectPath: String,
    val modelId: String,
    val reasoningEffort: String? = null,
    val permissionProfileId: String,
)

@Serializable
data class RemoteSkillSelection(
    val name: String,
    val path: String,
)

@Serializable
data class RemoteMessageRequest(
    val text: String = "",
    val attachmentIds: List<String> = emptyList(),
    val skill: RemoteSkillSelection? = null,
    val modelId: String? = null,
    val reasoningEffort: String? = null,
    val permissionProfileId: String? = null,
)

@Serializable
data class RemoteExecutionResult(
    val threadId: String,
    val turnId: String? = null,
    val status: RemoteExecutionStatus,
)

@Serializable
data class RemoteApprovalRequest(
    val threadId: String,
    val requestId: String,
    val method: String,
    val title: String,
    val detail: String = "",
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RemoteApprovalResolutionRequest(
    val decision: String,
)
