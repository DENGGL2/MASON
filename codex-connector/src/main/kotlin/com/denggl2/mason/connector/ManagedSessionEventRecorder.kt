package com.denggl2.mason.connector

import com.denggl2.mason.protocol.ConversationEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ManagedSessionEventRecorder(
    private val store: ConnectorStateStore,
    private val connectionId: String,
    private val eventId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val transientEventSequence = AtomicLong(0)

    fun record(notification: CodexNotification): ConversationEvent? {
        val threadId = notification.params.threadId() ?: return null
        val session = store.sessionForThread(threadId) ?: return null
        val sourceEventKey = if (notification.method in replayStableMethods) {
            stableKey(notification.method, notification.params)
        } else {
            "$connectionId:${transientEventSequence.incrementAndGet()}"
        }

        return store.appendEvent(session.binding.conversationId, sourceEventKey) { sequence ->
            CodexEventMapper(
                sourceDeviceId = store.deviceId,
                eventId = eventId,
                nextSequence = { sequence },
                now = now,
            ).mapNotification(session.binding.conversationId, notification)
        }
    }

    fun record(request: CodexServerRequest): ConversationEvent? {
        if (request.method !in approvalMethods) return null
        val threadId = request.params.threadId() ?: return null
        val session = store.sessionForThread(threadId) ?: return null
        val sourceRequestId = request.id.toString()
        store.recordApproval(
            ManagedApproval(
                conversationId = session.binding.conversationId,
                codexThreadId = threadId,
                sourceRequestId = sourceRequestId,
                method = request.method,
                connectionId = connectionId,
                createdAt = now(),
            ),
        )

        return store.appendEvent(
            session.binding.conversationId,
            stableKey(request.method, request.params),
        ) { sequence ->
            CodexEventMapper(
                sourceDeviceId = store.deviceId,
                eventId = eventId,
                nextSequence = { sequence },
                now = now,
            ).mapServerRequest(session.binding.conversationId, request)
        }
    }

    private fun stableKey(method: String, params: JsonObject): String =
        "codex:${connectorSha256("$method\n${canonicalJson(params)}")}"

    private companion object {
        val replayStableMethods = setOf(
            "item/started",
            "item/completed",
            "turn/completed",
        )
        val approvalMethods = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "item/tool/requestUserInput",
        )
    }
}

private fun JsonObject.threadId(): String? =
    this["threadId"]?.jsonPrimitive?.contentOrNull
        ?: this["thread"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        ?: this["turn"]?.jsonObject?.get("threadId")?.jsonPrimitive?.contentOrNull
        ?: this["item"]?.jsonObject?.get("threadId")?.jsonPrimitive?.contentOrNull
