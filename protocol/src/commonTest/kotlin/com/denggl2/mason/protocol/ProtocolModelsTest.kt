package com.denggl2.mason.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolModelsTest {
    @Test
    fun commandRoundTripsWithStableWireType() {
        val command = CommandEnvelope(
            commandId = "command-1",
            deviceId = "device-1",
            issuedAt = 1_000,
            expiresAt = 2_000,
            type = CommandType.EXECUTION_START,
            payload = buildJsonObject { put("conversationId", "conversation-1") },
        )

        val encoded = MasonProtocolJson.encode(command)
        val decoded = MasonProtocolJson.decode<CommandEnvelope>(encoded)

        assertEquals(command, decoded)
        assertTrue(encoded.contains("\"execution.start\""))
    }

    @Test
    fun expiredCommandFailsValidation() {
        val command = CommandEnvelope(
            commandId = "command-1",
            deviceId = "device-1",
            issuedAt = 1_000,
            expiresAt = 2_000,
            type = CommandType.CONVERSATION_RENAME,
        )

        assertEquals(listOf("expiresAt"), command.validate(now = 3_000).map(ProtocolViolation::field))
    }

    @Test
    fun timeSensitiveCommandsCannotQueueOffline() {
        assertFalse(CommandType.APPROVAL_RESOLVE.canQueueWhileOffline())
        assertFalse(CommandType.EXECUTION_INTERRUPT.canQueueWhileOffline())
        assertFalse(CommandType.FILE_REQUEST.canQueueWhileOffline())
        assertTrue(CommandType.CONVERSATION_RENAME.canQueueWhileOffline())
    }

    @Test
    fun eventRejectsNegativeSequence() {
        val event = ConversationEvent(
            eventId = "event-1",
            conversationId = "conversation-1",
            sourceDeviceId = "device-1",
            sequence = -1,
            occurredAt = 1_000,
            type = ConversationEventType.EXECUTION_FAILED,
        )

        assertEquals(listOf("sequence"), event.validate().map(ProtocolViolation::field))
    }
}
