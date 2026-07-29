package com.denggl2.mason.connector

import com.denggl2.mason.protocol.ConversationEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CodexEventMapperTest {
    private var sequence = 0L
    private val mapper = CodexEventMapper(
        sourceDeviceId = "device-1",
        eventId = { "event-${sequence + 1}" },
        nextSequence = { ++sequence },
        now = { 1_000 },
    )

    @Test
    fun mapsAgentDelta() {
        val event = mapper.mapNotification(
            "conversation-1",
            CodexNotification(
                "item/agentMessage/delta",
                buildJsonObject { put("delta", "hello") },
            ),
        )

        assertEquals(ConversationEventType.ASSISTANT_MESSAGE_DELTA, event?.type)
        assertEquals(1, event?.sequence)
    }

    @Test
    fun doesNotMapReasoningEvents() {
        val event = mapper.mapNotification(
            "conversation-1",
            CodexNotification(
                "item/reasoning/textDelta",
                buildJsonObject { put("delta", "hidden") },
            ),
        )

        assertNull(event)
    }

    @Test
    fun mapsApprovalServerRequest() {
        val event = mapper.mapServerRequest(
            "conversation-1",
            CodexServerRequest(
                id = JsonPrimitive(7),
                method = "item/fileChange/requestApproval",
                params = buildJsonObject { put("reason", "outside workspace") },
            ),
        )

        assertEquals(ConversationEventType.APPROVAL_REQUESTED, event?.type)
    }
}
