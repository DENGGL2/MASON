package com.denggl2.mason.connector

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class CodexNotification(
    val method: String,
    val params: JsonObject,
)

data class CodexServerRequest(
    val id: JsonElement,
    val method: String,
    val params: JsonObject,
)

class CodexRpcException(
    val code: Int?,
    override val message: String,
) : IllegalStateException(message)

class CodexAppServerClient(
    private val transport: CodexTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    private val nextRequestId = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val notificationFlow = MutableSharedFlow<CodexNotification>(extraBufferCapacity = 128)
    private val serverRequestChannel = Channel<CodexServerRequest>(Channel.UNLIMITED)
    private val readerJob = scope.launch {
        transport.messages.collect { message -> route(message) }
    }

    val notifications: Flow<CodexNotification> = notificationFlow.asSharedFlow()
    val serverRequests: Flow<CodexServerRequest> = serverRequestChannel.receiveAsFlow()

    suspend fun initialize(
        clientName: String,
        clientTitle: String,
        clientVersion: String,
    ): JsonObject {
        val result = request(
            method = "initialize",
            params = buildJsonObject {
                put("clientInfo", buildJsonObject {
                    put("name", clientName)
                    put("title", clientTitle)
                    put("version", clientVersion)
                })
                put("capabilities", buildJsonObject {
                    put("experimentalApi", true)
                })
            },
        ).jsonObject
        notify("initialized")
        return result
    }

    suspend fun request(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): JsonElement {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            transport.send(buildJsonObject {
                put("method", method)
                put("id", id)
                put("params", params)
            })
            val response = deferred.await()
            response["error"]?.jsonObject?.let { error ->
                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Codex RPC failed"
                println("MASON RPC failed method=$method id=$id code=${error["code"]} message=$message")
                throw CodexRpcException(
                    code = error["code"]?.jsonPrimitive?.intOrNull,
                    message = message,
                )
            }
            return response["result"] ?: JsonObject(emptyMap())
        } finally {
            pending.remove(id)
        }
    }

    suspend fun notify(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ) {
        transport.send(buildJsonObject {
            put("method", method)
            put("params", params)
        })
    }

    suspend fun resolveServerRequest(
        request: CodexServerRequest,
        result: JsonElement,
    ) {
        transport.send(buildJsonObject {
            put("id", request.id)
            put("result", result)
        })
    }

    private suspend fun route(message: JsonObject) {
        when (val routed = CodexWireMessageRouter.classify(message)) {
            is CodexWireMessage.ServerRequest -> serverRequestChannel.send(
                CodexServerRequest(routed.id, routed.method, routed.params).also {
                    println("MASON server request method=${routed.method} id=${routed.id}")
                },
            )
            is CodexWireMessage.Notification -> notificationFlow.emit(
                CodexNotification(routed.method, routed.params),
            )
            is CodexWireMessage.Response -> pending.remove(routed.id)?.complete(routed.raw)
            is CodexWireMessage.Invalid -> Unit
        }
    }

    override fun close() {
        val error = IllegalStateException("Codex App Server client closed")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        readerJob.cancel()
        serverRequestChannel.close()
        transport.close()
        scope.cancel()
    }
}
