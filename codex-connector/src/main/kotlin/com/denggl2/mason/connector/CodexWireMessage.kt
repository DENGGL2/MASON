package com.denggl2.mason.connector

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface CodexWireMessage {
    val raw: JsonObject

    data class Response(
        val id: Long,
        override val raw: JsonObject,
    ) : CodexWireMessage

    data class ServerRequest(
        val id: JsonElement,
        val method: String,
        val params: JsonObject,
        override val raw: JsonObject,
    ) : CodexWireMessage

    data class Notification(
        val method: String,
        val params: JsonObject,
        override val raw: JsonObject,
    ) : CodexWireMessage

    data class Invalid(override val raw: JsonObject) : CodexWireMessage
}

object CodexWireMessageRouter {
    fun classify(message: JsonObject): CodexWireMessage {
        val method = message["method"]?.jsonPrimitive?.contentOrNull
        val id = message["id"]
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())

        // Server requests carry both method and id. This check must precede response handling.
        if (method != null && id != null) {
            return CodexWireMessage.ServerRequest(id, method, params, message)
        }
        if (method != null) return CodexWireMessage.Notification(method, params, message)

        val numericId = id?.jsonPrimitive?.longOrNull
        return if (numericId != null) {
            CodexWireMessage.Response(numericId, message)
        } else {
            CodexWireMessage.Invalid(message)
        }
    }
}
