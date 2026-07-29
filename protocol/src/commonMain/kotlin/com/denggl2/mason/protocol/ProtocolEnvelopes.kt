package com.denggl2.mason.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val MASON_PROTOCOL_VERSION: Int = 1

@Serializable
enum class CommandType {
    @SerialName("conversation.create") CONVERSATION_CREATE,
    @SerialName("conversation.rename") CONVERSATION_RENAME,
    @SerialName("conversation.syncMode.set") CONVERSATION_SYNC_MODE_SET,
    @SerialName("execution.start") EXECUTION_START,
    @SerialName("execution.steer") EXECUTION_STEER,
    @SerialName("execution.interrupt") EXECUTION_INTERRUPT,
    @SerialName("approval.resolve") APPROVAL_RESOLVE,
    @SerialName("file.request") FILE_REQUEST,
    @SerialName("sync.pull") SYNC_PULL,
}

@Serializable
data class CommandEnvelope(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val commandId: String,
    val deviceId: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val type: CommandType,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class CommandResult(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val commandId: String,
    val accepted: Boolean,
    val completedAt: Long,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ConversationEvent(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val eventId: String,
    val conversationId: String,
    val sourceDeviceId: String,
    val sequence: Long,
    val occurredAt: Long,
    val type: ConversationEventType,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class SyncCursor(
    val conversationId: String,
    val sequence: Long,
)

@Serializable
data class SyncPullRequest(
    val cursors: List<SyncCursor>,
)

@Serializable
data class SyncPullResponse(
    val events: List<ConversationEvent>,
    val hasMore: Boolean,
)

data class ProtocolViolation(
    val field: String,
    val message: String,
)

fun CommandEnvelope.validate(now: Long): List<ProtocolViolation> = buildList {
    if (protocolVersion != MASON_PROTOCOL_VERSION) {
        add(ProtocolViolation("protocolVersion", "Unsupported protocol version: $protocolVersion"))
    }
    if (commandId.isBlank()) add(ProtocolViolation("commandId", "Command ID is required"))
    if (deviceId.isBlank()) add(ProtocolViolation("deviceId", "Device ID is required"))
    if (expiresAt <= issuedAt) add(ProtocolViolation("expiresAt", "Expiry must be after issue time"))
    if (expiresAt <= now) add(ProtocolViolation("expiresAt", "Command has expired"))
}

fun ConversationEvent.validate(): List<ProtocolViolation> = buildList {
    if (protocolVersion != MASON_PROTOCOL_VERSION) {
        add(ProtocolViolation("protocolVersion", "Unsupported protocol version: $protocolVersion"))
    }
    if (eventId.isBlank()) add(ProtocolViolation("eventId", "Event ID is required"))
    if (conversationId.isBlank()) add(ProtocolViolation("conversationId", "Conversation ID is required"))
    if (sourceDeviceId.isBlank()) add(ProtocolViolation("sourceDeviceId", "Source device ID is required"))
    if (sequence < 0) add(ProtocolViolation("sequence", "Sequence cannot be negative"))
}

fun CommandType.canQueueWhileOffline(): Boolean = when (this) {
    CommandType.CONVERSATION_CREATE,
    CommandType.CONVERSATION_RENAME,
    CommandType.CONVERSATION_SYNC_MODE_SET,
    CommandType.EXECUTION_START,
    CommandType.EXECUTION_STEER,
    CommandType.SYNC_PULL,
    -> true

    CommandType.EXECUTION_INTERRUPT,
    CommandType.APPROVAL_RESOLVE,
    CommandType.FILE_REQUEST,
    -> false
}
