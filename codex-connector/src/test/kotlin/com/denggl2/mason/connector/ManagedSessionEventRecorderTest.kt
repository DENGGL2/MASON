package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.ConversationEventType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ManagedSessionEventRecorderTest {
    @Test
    fun terminalNotificationIsDeduplicatedAcrossRecorderRestart() {
        val path = Files.createTempFile("mason-event-recorder", ".json")
        Files.deleteIfExists(path)
        try {
            val store = ConnectorStateStore(path) { "device-1" }
            store.register(binding())
            val notification = CodexNotification(
                method = "item/completed",
                params = buildJsonObject {
                    put("threadId", "thread-1")
                    put("item", buildJsonObject {
                        put("id", "item-1")
                        put("type", "agentMessage")
                    })
                },
            )

            val first = ManagedSessionEventRecorder(store, connectionId = "connection-1")
            assertEquals(ConversationEventType.ASSISTANT_MESSAGE_COMPLETED, first.record(notification)?.type)

            val restarted = ManagedSessionEventRecorder(
                ConnectorStateStore(path),
                connectionId = "connection-2",
            )
            val reorderedNotification = CodexNotification(
                method = "item/completed",
                params = buildJsonObject {
                    put("item", buildJsonObject {
                        put("type", "agentMessage")
                        put("id", "item-1")
                    })
                    put("threadId", "thread-1")
                },
            )

            assertNull(restarted.record(reorderedNotification))
            assertEquals(1, ConnectorStateStore(path).eventsAfter("conversation-1", 0).size)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun approvalIsRecordedAgainstItsBoundThreadAndConnection() {
        val path = Files.createTempFile("mason-approval-recorder", ".json")
        Files.deleteIfExists(path)
        try {
            val store = ConnectorStateStore(path) { "device-1" }
            store.register(binding())
            val recorder = ManagedSessionEventRecorder(store, connectionId = "connection-1", now = { 100 })
            val request = CodexServerRequest(
                id = JsonPrimitive(7),
                method = "item/commandExecution/requestApproval",
                params = buildJsonObject {
                    put("threadId", "thread-1")
                    put("turnId", "turn-1")
                    put("itemId", "item-1")
                },
            )

            val event = recorder.record(request)

            assertEquals(ConversationEventType.APPROVAL_REQUESTED, event?.type)
            assertNotNull(store.approvalForResolution("conversation-1", "thread-1", "7", "connection-1"))
            assertNull(store.approvalForResolution("conversation-1", "thread-1", "7", "other-connection"))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun binding() = CodexThreadBinding(
        conversationId = "conversation-1",
        deviceId = "device-1",
        codexThreadId = "thread-1",
        cwd = "D:\\workspace\\MASON",
        ownership = CodexOwnership.MASON_MANAGED,
        protocolVersion = "test",
    )
}
