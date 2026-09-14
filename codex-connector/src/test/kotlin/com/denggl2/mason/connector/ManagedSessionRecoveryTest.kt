package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.ConversationEvent
import com.denggl2.mason.protocol.ConversationEventType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ManagedSessionRecoveryTest {
    @Test
    fun repeatedRecoveryRestoresBindingWithoutDuplicatingEvents() = runBlocking {
        val path = Files.createTempFile("mason-recovery-state", ".json")
        Files.deleteIfExists(path)
        try {
            val initial = ConnectorStateStore(path) { "device-1" }
            initial.register(
                CodexThreadBinding(
                    conversationId = "conversation-1",
                    deviceId = "device-1",
                    codexThreadId = "thread-1",
                    cwd = "D:\\workspace\\MASON",
                    ownership = CodexOwnership.MASON_MANAGED,
                    protocolVersion = "0.146.0-alpha.3.1",
                ),
            )
            initial.appendEvent("conversation-1", "item-1") { sequence ->
                ConversationEvent(
                    eventId = "event-1",
                    conversationId = "conversation-1",
                    sourceDeviceId = "device-1",
                    sequence = sequence,
                    occurredAt = 50,
                    type = ConversationEventType.ASSISTANT_MESSAGE_COMPLETED,
                )
            }

            val restarted = ConnectorStateStore(path)
            var reads = 0
            val recovery = ManagedSessionRecovery(
                store = restarted,
                readThread = { threadId ->
                    reads++
                    buildJsonObject {
                        put("thread", buildJsonObject { put("id", threadId) })
                    }
                },
                now = { 200 },
            )

            assertEquals(SessionRecoveryStatus.RECOVERED, recovery.recoverAll().single().status)
            assertEquals(SessionRecoveryStatus.RECOVERED, recovery.recoverAll().single().status)
            assertEquals(2, reads)
            assertEquals(200, restarted.session("conversation-1")?.lastRecoveredAt)
            assertEquals(1, restarted.eventsAfter("conversation-1", 0).size)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun mismatchedThreadResponseFailsClosed() = runBlocking {
        val path = Files.createTempFile("mason-recovery-mismatch", ".json")
        Files.deleteIfExists(path)
        try {
            val store = ConnectorStateStore(path) { "device-1" }
            store.register(
                CodexThreadBinding(
                    conversationId = "conversation-1",
                    deviceId = "device-1",
                    codexThreadId = "thread-1",
                    ownership = CodexOwnership.MASON_MANAGED,
                    protocolVersion = "test",
                ),
            )
            val recovery = ManagedSessionRecovery(
                store = store,
                readThread = {
                    buildJsonObject {
                        put("thread", buildJsonObject { put("id", "other-thread") })
                    }
                },
            )

            val result = recovery.recoverAll().single()

            assertEquals(SessionRecoveryStatus.THREAD_MISMATCH, result.status)
            assertEquals(null, store.session("conversation-1")?.lastRecoveredAt)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
