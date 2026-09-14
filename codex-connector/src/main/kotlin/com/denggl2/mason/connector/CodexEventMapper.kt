package com.denggl2.mason.connector

import com.denggl2.mason.protocol.ConversationEvent
import com.denggl2.mason.protocol.ConversationEventType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CodexEventMapper(
    private val sourceDeviceId: String,
    private val eventId: () -> String,
    private val nextSequence: () -> Long,
    private val now: () -> Long,
) {
    fun mapNotification(
        conversationId: String,
        notification: CodexNotification,
    ): ConversationEvent? {
        val type = when (notification.method) {
            "item/agentMessage/delta" -> ConversationEventType.ASSISTANT_MESSAGE_DELTA
            "turn/plan/updated" -> ConversationEventType.PLAN_UPDATED
            "item/commandExecution/outputDelta" -> ConversationEventType.COMMAND_OUTPUT_DELTA
            "turn/diff/updated" -> ConversationEventType.FILE_CHANGE_UPDATED
            "item/started" -> startedItemType(notification.params)
            "item/completed" -> completedItemType(notification.params)
            "turn/completed" -> completedExecutionType(notification.params)
            else -> null
        } ?: return null

        return event(conversationId, type, notification.params)
    }

    fun mapServerRequest(
        conversationId: String,
        request: CodexServerRequest,
    ): ConversationEvent? {
        if (request.method !in approvalMethods) return null
        val payload = buildJsonObject {
            put("sourceRequestId", request.id.toString())
            put("method", request.method)
            request.params.forEach { (key, value) -> put(key, value) }
        }
        return event(conversationId, ConversationEventType.APPROVAL_REQUESTED, payload)
    }

    private fun event(
        conversationId: String,
        type: ConversationEventType,
        payload: JsonObject,
    ) = ConversationEvent(
        eventId = eventId(),
        conversationId = conversationId,
        sourceDeviceId = sourceDeviceId,
        sequence = nextSequence(),
        occurredAt = now(),
        type = type,
        payload = payload,
    )

    private fun startedItemType(params: JsonObject): ConversationEventType? = when (
        params["item"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
    ) {
        "commandExecution" -> ConversationEventType.COMMAND_STARTED
        "fileChange" -> ConversationEventType.FILE_CHANGE_UPDATED
        else -> null
    }

    private fun completedItemType(params: JsonObject): ConversationEventType? = when (
        params["item"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
    ) {
        "agentMessage" -> ConversationEventType.ASSISTANT_MESSAGE_COMPLETED
        "commandExecution" -> ConversationEventType.COMMAND_COMPLETED
        "fileChange" -> ConversationEventType.FILE_CHANGE_UPDATED
        else -> null
    }

    private fun completedExecutionType(params: JsonObject): ConversationEventType = when (
        params["turn"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
    ) {
        "interrupted" -> ConversationEventType.EXECUTION_INTERRUPTED
        "failed" -> ConversationEventType.EXECUTION_FAILED
        else -> ConversationEventType.EXECUTION_COMPLETED
    }

    private companion object {
        val approvalMethods = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "item/tool/requestUserInput",
        )
    }
}
