package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.CommandEnvelope
import com.denggl2.mason.protocol.CommandResult
import com.denggl2.mason.protocol.ConversationEvent
import com.denggl2.mason.protocol.MasonProtocolJson
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement

const val CONNECTOR_STATE_SCHEMA_VERSION: Int = 1

@Serializable
data class ConnectorStateSnapshot(
    val schemaVersion: Int = CONNECTOR_STATE_SCHEMA_VERSION,
    val deviceId: String,
    val sessions: Map<String, ManagedSessionSnapshot> = emptyMap(),
    val commands: Map<String, StoredCommandExecution> = emptyMap(),
)

@Serializable
data class ManagedSessionSnapshot(
    val binding: CodexThreadBinding,
    val lastSequence: Long = 0,
    val events: List<StoredConversationEvent> = emptyList(),
    val approvals: Map<String, ManagedApproval> = emptyMap(),
    val lastRecoveredAt: Long? = null,
)

@Serializable
data class StoredConversationEvent(
    val sourceEventKey: String,
    val event: ConversationEvent,
)

@Serializable
data class StoredCommandExecution(
    val commandId: String,
    val requestFingerprint: String,
    val startedAt: Long,
    val result: CommandResult? = null,
)

@Serializable
enum class ManagedApprovalStatus {
    PENDING,
    RESOLVED,
}

@Serializable
data class ManagedApproval(
    val conversationId: String,
    val codexThreadId: String,
    val sourceRequestId: String,
    val method: String,
    val connectionId: String,
    val createdAt: Long,
    val status: ManagedApprovalStatus = ManagedApprovalStatus.PENDING,
    val resolvedAt: Long? = null,
)

sealed interface CommandClaim {
    data object Acquired : CommandClaim

    data class InProgress(val startedAt: Long) : CommandClaim

    data class Completed(val result: CommandResult) : CommandClaim

    data object Conflict : CommandClaim
}

class ConnectorStateStore(
    statePath: Path,
    private val newDeviceId: () -> String = { UUID.randomUUID().toString() },
) {
    private val path = statePath.toAbsolutePath().normalize()
    private val lock = ReentrantLock()
    private var state: ConnectorStateSnapshot = loadOrCreate()

    val deviceId: String
        get() = lock.withLock { state.deviceId }

    fun snapshot(): ConnectorStateSnapshot = lock.withLock { state }

    fun register(binding: CodexThreadBinding): ManagedSessionSnapshot = lock.withLock {
        require(binding.conversationId.isNotBlank()) { "Conversation ID is required" }
        require(binding.codexThreadId.isNotBlank()) { "Codex thread ID is required" }
        require(binding.deviceId == state.deviceId) {
            "Binding device ${binding.deviceId} does not match Connector device ${state.deviceId}"
        }

        val existingThreadOwner = state.sessions.values.firstOrNull {
            it.binding.codexThreadId == binding.codexThreadId &&
                it.binding.conversationId != binding.conversationId
        }
        require(existingThreadOwner == null) {
            "Codex thread ${binding.codexThreadId} is already bound to another conversation"
        }

        val existing = state.sessions[binding.conversationId]
        if (existing != null) {
            require(existing.binding == binding) {
                "Conversation ${binding.conversationId} already has a different Codex binding"
            }
            return existing
        }

        val session = ManagedSessionSnapshot(binding = binding)
        update(state.copy(sessions = state.sessions + (binding.conversationId to session)))
        session
    }

    fun session(conversationId: String): ManagedSessionSnapshot? = lock.withLock {
        state.sessions[conversationId]
    }

    fun sessionForThread(codexThreadId: String): ManagedSessionSnapshot? = lock.withLock {
        state.sessions.values.firstOrNull { it.binding.codexThreadId == codexThreadId }
    }

    fun managedBindings(): List<CodexThreadBinding> = lock.withLock {
        state.sessions.values
            .map(ManagedSessionSnapshot::binding)
            .filter { it.ownership == CodexOwnership.MASON_MANAGED }
    }

    fun appendEvent(
        conversationId: String,
        sourceEventKey: String,
        createEvent: (sequence: Long) -> ConversationEvent?,
    ): ConversationEvent? = lock.withLock {
        require(sourceEventKey.isNotBlank()) { "Source event key is required" }
        val session = state.sessions[conversationId]
            ?: error("Unknown conversation: $conversationId")
        if (session.events.any { it.sourceEventKey == sourceEventKey }) return null

        val sequence = session.lastSequence + 1
        val event = createEvent(sequence) ?: return null
        require(event.conversationId == conversationId) { "Event conversation does not match binding" }
        require(event.sequence == sequence) { "Event sequence must be allocated by the state store" }

        val updatedSession = session.copy(
            lastSequence = sequence,
            events = session.events + StoredConversationEvent(sourceEventKey, event),
        )
        update(state.copy(sessions = state.sessions + (conversationId to updatedSession)))
        event
    }

    fun eventsAfter(conversationId: String, sequence: Long): List<ConversationEvent> = lock.withLock {
        require(sequence >= 0) { "Cursor sequence cannot be negative" }
        state.sessions[conversationId]
            ?.events
            ?.asSequence()
            ?.map(StoredConversationEvent::event)
            ?.filter { it.sequence > sequence }
            ?.toList()
            .orEmpty()
    }

    fun claimCommand(command: CommandEnvelope, startedAt: Long): CommandClaim = lock.withLock {
        require(command.commandId.isNotBlank()) { "Command ID is required" }
        val fingerprint = connectorSha256(
            canonicalJson(MasonProtocolJson.format.encodeToJsonElement(command)),
        )
        val existing = state.commands[command.commandId]
        if (existing != null) {
            if (existing.requestFingerprint != fingerprint) return CommandClaim.Conflict
            return existing.result?.let(CommandClaim::Completed)
                ?: CommandClaim.InProgress(existing.startedAt)
        }

        update(
            state.copy(
                commands = state.commands + (
                    command.commandId to StoredCommandExecution(
                        commandId = command.commandId,
                        requestFingerprint = fingerprint,
                        startedAt = startedAt,
                    )
                ),
            ),
        )
        CommandClaim.Acquired
    }

    fun completeCommand(result: CommandResult): CommandResult = lock.withLock {
        val existing = state.commands[result.commandId]
            ?: error("Command ${result.commandId} was not claimed before completion")
        existing.result?.let { return it }

        val completed = existing.copy(result = result)
        update(state.copy(commands = state.commands + (result.commandId to completed)))
        result
    }

    fun recordApproval(approval: ManagedApproval): ManagedApproval = lock.withLock {
        val session = state.sessions[approval.conversationId]
            ?: error("Unknown conversation: ${approval.conversationId}")
        require(session.binding.codexThreadId == approval.codexThreadId) {
            "Approval thread does not match conversation binding"
        }

        val approvalKey = approvalKey(approval.connectionId, approval.sourceRequestId)
        val existing = session.approvals[approvalKey]
        if (existing != null) {
            require(existing == approval) { "Approval request ID collision in conversation" }
            return existing
        }

        val updatedSession = session.copy(
            approvals = session.approvals + (approvalKey to approval),
        )
        update(state.copy(sessions = state.sessions + (approval.conversationId to updatedSession)))
        approval
    }

    fun approvalForResolution(
        conversationId: String,
        codexThreadId: String,
        sourceRequestId: String,
        connectionId: String,
    ): ManagedApproval? = lock.withLock {
        state.sessions[conversationId]
            ?.approvals
            ?.get(approvalKey(connectionId, sourceRequestId))
            ?.takeIf { it.status == ManagedApprovalStatus.PENDING }
            ?.takeIf { it.codexThreadId == codexThreadId }
            ?.takeIf { it.connectionId == connectionId }
    }

    fun markApprovalResolved(
        conversationId: String,
        codexThreadId: String,
        sourceRequestId: String,
        connectionId: String,
        resolvedAt: Long,
    ): ManagedApproval? = lock.withLock {
        val session = state.sessions[conversationId] ?: return null
        val approvalKey = approvalKey(connectionId, sourceRequestId)
        val approval = session.approvals[approvalKey]
            ?.takeIf { it.status == ManagedApprovalStatus.PENDING }
            ?.takeIf { it.codexThreadId == codexThreadId }
            ?.takeIf { it.connectionId == connectionId }
            ?: return null
        val resolved = approval.copy(
            status = ManagedApprovalStatus.RESOLVED,
            resolvedAt = resolvedAt,
        )
        val updatedSession = session.copy(
            approvals = session.approvals + (approvalKey to resolved),
        )
        update(state.copy(sessions = state.sessions + (conversationId to updatedSession)))
        resolved
    }

    fun markRecovered(conversationId: String, recoveredAt: Long) = lock.withLock {
        val session = state.sessions[conversationId]
            ?: error("Unknown conversation: $conversationId")
        val updatedSession = session.copy(lastRecoveredAt = recoveredAt)
        update(state.copy(sessions = state.sessions + (conversationId to updatedSession)))
    }

    private fun loadOrCreate(): ConnectorStateSnapshot {
        if (!Files.exists(path)) {
            return ConnectorStateSnapshot(deviceId = newDeviceId()).also(::write)
        }
        val decoded = runCatching {
            MasonProtocolJson.decode<ConnectorStateSnapshot>(Files.readString(path, StandardCharsets.UTF_8))
        }.getOrElse { error ->
            throw IllegalStateException("Cannot read Connector state at $path", error)
        }
        require(decoded.schemaVersion == CONNECTOR_STATE_SCHEMA_VERSION) {
            "Unsupported Connector state schema: ${decoded.schemaVersion}"
        }
        return decoded
    }

    private fun update(next: ConnectorStateSnapshot) {
        write(next)
        state = next
    }

    private fun approvalKey(connectionId: String, sourceRequestId: String): String =
        "$connectionId:$sourceRequestId"

    private fun write(snapshot: ConnectorStateSnapshot) {
        val parent = requireNotNull(path.parent) { "State path must have a parent directory" }
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            Files.writeString(
                temporary,
                MasonProtocolJson.encode(snapshot),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
