package com.denggl2.mason.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwnerProfile(
    val id: String,
    val displayName: String,
    val createdAt: Long,
)

@Serializable
enum class Platform {
    ANDROID,
    WINDOWS,
    MACOS,
    IOS,
    WEB,
    UNKNOWN,
}

@Serializable
enum class DeviceCapability {
    CODEX_EXECUTION,
    ANDROID_TOOLS,
    FILE_SEND,
    FILE_RECEIVE,
}

@Serializable
data class Device(
    val id: String,
    val ownerId: String,
    val displayName: String,
    val platform: Platform,
    val keyAlgorithm: DeviceKeyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
    val publicKey: String,
    val capabilities: Set<DeviceCapability>,
    val lastSeenAt: Long? = null,
    val revokedAt: Long? = null,
)

@Serializable
data class LogicalProject(
    val id: String,
    val ownerId: String,
    val displayName: String,
    val repositoryIdentity: String? = null,
    val createdAt: Long,
)

@Serializable
enum class ReplicaAvailability {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

@Serializable
data class ProjectReplica(
    val id: String,
    val projectId: String,
    val deviceId: String,
    val rootPath: String,
    val gitRemote: String? = null,
    val branch: String? = null,
    val commitSha: String? = null,
    val dirty: Boolean? = null,
    val availability: ReplicaAvailability = ReplicaAvailability.UNKNOWN,
    val lastObservedAt: Long,
)

@Serializable
enum class ConversationSyncMode {
    SHARED,
    LOCAL_ONLY,
    REMOTE_MIRROR,
}

@Serializable
data class Conversation(
    val id: String,
    val ownerId: String,
    val title: String,
    val syncMode: ConversationSyncMode,
    val projectId: String? = null,
    val selectedReplicaId: String? = null,
    val authorityDeviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
enum class CodexOwnership {
    MASON_MANAGED,
    EXTERNAL_HISTORY_ONLY,
}

@Serializable
data class CodexThreadBinding(
    val conversationId: String,
    val deviceId: String,
    val codexThreadId: String,
    val cwd: String? = null,
    val ownership: CodexOwnership,
    val protocolVersion: String,
)

@Serializable
enum class ExecutionStatus {
    STARTING,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    FAILED,
    INTERRUPTED,
}

@Serializable
data class ExecutionSession(
    val id: String,
    val conversationId: String,
    val executorDeviceId: String,
    val replicaId: String? = null,
    val externalThreadId: String? = null,
    val status: ExecutionStatus,
    val leaseExpiresAt: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Serializable
enum class ApprovalKind {
    COMMAND_EXECUTION,
    FILE_CHANGE,
    PERMISSIONS,
    USER_INPUT,
}

@Serializable
enum class ApprovalStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED,
}

@Serializable
data class ApprovalRequest(
    val id: String,
    val conversationId: String,
    val executionId: String,
    val sourceRequestId: String,
    val kind: ApprovalKind,
    val summary: String,
    val command: String? = null,
    val cwd: String? = null,
    val expiresAt: Long,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
)

@Serializable
enum class FileTransferStatus {
    OFFERED,
    ACCEPTED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED,
}

@Serializable
data class FileTransfer(
    val id: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val sourcePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val transferredBytes: Long = 0,
    val expiresAt: Long,
    val status: FileTransferStatus = FileTransferStatus.OFFERED,
)

@Serializable
enum class ConversationEventType {
    @SerialName("conversation.created") CONVERSATION_CREATED,
    @SerialName("conversation.updated") CONVERSATION_UPDATED,
    @SerialName("user_message.completed") USER_MESSAGE_COMPLETED,
    @SerialName("assistant_message.delta") ASSISTANT_MESSAGE_DELTA,
    @SerialName("assistant_message.completed") ASSISTANT_MESSAGE_COMPLETED,
    @SerialName("plan.updated") PLAN_UPDATED,
    @SerialName("command.started") COMMAND_STARTED,
    @SerialName("command.output.delta") COMMAND_OUTPUT_DELTA,
    @SerialName("command.completed") COMMAND_COMPLETED,
    @SerialName("file_change.updated") FILE_CHANGE_UPDATED,
    @SerialName("approval.requested") APPROVAL_REQUESTED,
    @SerialName("approval.resolved") APPROVAL_RESOLVED,
    @SerialName("artifact.available") ARTIFACT_AVAILABLE,
    @SerialName("execution.completed") EXECUTION_COMPLETED,
    @SerialName("execution.failed") EXECUTION_FAILED,
    @SerialName("execution.interrupted") EXECUTION_INTERRUPTED,
    @SerialName("device.presence.changed") DEVICE_PRESENCE_CHANGED,
}
