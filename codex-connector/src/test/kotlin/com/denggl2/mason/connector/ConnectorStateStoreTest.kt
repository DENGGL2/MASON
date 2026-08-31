package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.CommandEnvelope
import com.denggl2.mason.protocol.CommandResult
import com.denggl2.mason.protocol.CommandType
import com.denggl2.mason.protocol.ConversationEvent
import com.denggl2.mason.protocol.ConversationEventType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConnectorStateStoreTest {
    @Test
    fun webRtcResumeOfferIdSurvivesConnectorRestart() = withStatePath { path ->
        val first = ConnectorStateStore(path) { "device-1" }
        val offerId = first.webRtcResumeOfferId()

        val restarted = ConnectorStateStore(path) { "unexpected-device" }

        assertEquals(offerId, restarted.webRtcResumeOfferId())
    }

    @Test
    fun restartRestoresBindingCursorAndDeduplicatesStoredEvent() = withStatePath { path ->
        val first = ConnectorStateStore(path) { "device-1" }
        first.register(binding())
        first.appendEvent("conversation-1", "codex:item-1") { sequence -> event(sequence) }

        val restarted = ConnectorStateStore(path) { "unexpected-device" }
        val duplicate = restarted.appendEvent("conversation-1", "codex:item-1") { sequence -> event(sequence) }

        assertEquals("device-1", restarted.deviceId)
        assertNull(duplicate)
        assertEquals(listOf(1L), restarted.eventsAfter("conversation-1", 0).map { it.sequence })
        assertEquals(emptyList(), restarted.eventsAfter("conversation-1", 1))
    }

    @Test
    fun inProgressCommandIsNotReacquiredAfterRestart() = withStatePath { path ->
        val first = ConnectorStateStore(path) { "device-1" }

        val command = command()
        assertIs<CommandClaim.Acquired>(first.claimCommand(command, startedAt = 100))
        val restarted = ConnectorStateStore(path)
        assertEquals(CommandClaim.InProgress(100), restarted.claimCommand(command, startedAt = 200))
        assertEquals(
            CommandClaim.Conflict,
            restarted.claimCommand(
                command.copy(type = CommandType.EXECUTION_INTERRUPT),
                startedAt = 200,
            ),
        )

        val result = CommandResult(
            commandId = "command-1",
            accepted = true,
            completedAt = 300,
        )
        assertEquals(result, restarted.completeCommand(result))
        val completed = ConnectorStateStore(path).claimCommand(command, startedAt = 400)
        assertEquals(CommandClaim.Completed(result), completed)
    }

    @Test
    fun approvalResolutionRequiresConversationThreadAndLiveConnection() = withStatePath { path ->
        val store = ConnectorStateStore(path) { "device-1" }
        store.register(binding())
        store.recordApproval(
            ManagedApproval(
                conversationId = "conversation-1",
                codexThreadId = "thread-1",
                sourceRequestId = "7",
                method = "item/commandExecution/requestApproval",
                connectionId = "connection-1",
                createdAt = 100,
            ),
        )

        assertNull(store.approvalForResolution("other", "thread-1", "7", "connection-1"))
        assertNull(store.approvalForResolution("conversation-1", "other", "7", "connection-1"))
        assertNull(store.approvalForResolution("conversation-1", "thread-1", "7", "connection-2"))
        assertEquals(
            "connection-1",
            store.approvalForResolution("conversation-1", "thread-1", "7", "connection-1")?.connectionId,
        )

        val restarted = ConnectorStateStore(path)
        assertNull(restarted.approvalForResolution("conversation-1", "thread-1", "7", "connection-2"))

        val reissued = restarted.recordApproval(
            ManagedApproval(
                conversationId = "conversation-1",
                codexThreadId = "thread-1",
                sourceRequestId = "7",
                method = "item/commandExecution/requestApproval",
                connectionId = "connection-2",
                createdAt = 200,
            ),
        )
        assertEquals("connection-2", reissued.connectionId)
        assertEquals(
            "connection-2",
            restarted.approvalForResolution("conversation-1", "thread-1", "7", "connection-2")?.connectionId,
        )
    }

    @Test
    fun oneCodexThreadCannotBeBoundToTwoConversations() = withStatePath { path ->
        val store = ConnectorStateStore(path) { "device-1" }
        store.register(binding())

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            store.register(binding().copy(conversationId = "conversation-2"))
        }
    }

    @Test
    fun remoteComposerSelectionSurvivesRestart() = withStatePath { path ->
        val selection = StoredRemoteComposerSelection(
            model = "gpt-5.6-sol",
            reasoningEffort = "high",
            permissionProfileId = ":workspace",
            cwd = "D:\\workspace\\MASON",
        )
        ConnectorStateStore(path) { "device-1" }
            .recordRemoteComposerSelection("thread-1", selection)

        assertEquals(selection, ConnectorStateStore(path).remoteComposerSelection("thread-1"))
    }

    @Test
    fun versionOneStateMigratesWithoutLosingConnectorIdentity() = withStatePath { path ->
        Files.writeString(
            path,
            """{"schemaVersion":1,"deviceId":"device-1","sessions":{},"commands":{}}""",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val migrated = ConnectorStateStore(
            statePath = path,
            newDeviceId = { "unexpected-device" },
            newOwnerId = { "owner-1" },
        )

        assertEquals("device-1", migrated.deviceId)
        assertEquals("owner-1", migrated.ownerId)
        assertEquals(CONNECTOR_STATE_SCHEMA_VERSION, migrated.snapshot().schemaVersion)
        assertEquals(emptyMap(), migrated.snapshot().pairedDevices)
        assertEquals("owner-1", ConnectorStateStore(path).ownerId)
    }

    private fun binding() = CodexThreadBinding(
        conversationId = "conversation-1",
        deviceId = "device-1",
        codexThreadId = "thread-1",
        cwd = "D:\\workspace\\MASON",
        ownership = CodexOwnership.MASON_MANAGED,
        protocolVersion = "0.146.0-alpha.3.1",
    )

    private fun event(sequence: Long) = ConversationEvent(
        eventId = "event-$sequence",
        conversationId = "conversation-1",
        sourceDeviceId = "device-1",
        sequence = sequence,
        occurredAt = 100,
        type = ConversationEventType.ASSISTANT_MESSAGE_COMPLETED,
    )

    private fun command() = CommandEnvelope(
        commandId = "command-1",
        deviceId = "phone-1",
        issuedAt = 10,
        expiresAt = 1_000,
        type = CommandType.EXECUTION_START,
    )
}

private fun withStatePath(block: (Path) -> Unit) {
    val path = Files.createTempFile("mason-connector-state", ".json")
    Files.deleteIfExists(path)
    try {
        block(path)
    } finally {
        Files.deleteIfExists(path)
    }
}
