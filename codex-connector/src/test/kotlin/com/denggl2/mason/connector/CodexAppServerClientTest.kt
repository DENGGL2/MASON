package com.denggl2.mason.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class CodexAppServerClientTest {
    @Test
    fun serverRequestIdCollisionDoesNotCompletePendingClientRequest() = runBlocking {
        val transport = FakeTransport()
        val client = CodexAppServerClient(transport)
        try {
            val response = async { client.request("thread/list") }
            val outgoing = transport.sent.receive()
            val id = outgoing["id"]?.jsonPrimitive?.long ?: error("Missing request id")
            val approval = async { client.serverRequests.first() }

            transport.incoming.send(buildJsonObject {
                put("id", id)
                put("method", "item/commandExecution/requestApproval")
                put("params", JsonObject(emptyMap()))
            })

            assertEquals("item/commandExecution/requestApproval", withTimeout(1_000) { approval.await() }.method)
            assertFalse(response.isCompleted)

            transport.incoming.send(buildJsonObject {
                put("id", id)
                put("result", buildJsonObject { put("ok", true) })
            })

            assertEquals(true, withTimeout(1_000) { response.await() }.jsonObject["ok"]?.jsonPrimitive?.content?.toBoolean())
        } finally {
            client.close()
        }
    }
}

private class FakeTransport : CodexTransport {
    val incoming = Channel<JsonObject>(Channel.UNLIMITED)
    val sent = Channel<JsonObject>(Channel.UNLIMITED)

    override val messages: Flow<JsonObject> = incoming.receiveAsFlow()

    override suspend fun send(message: JsonObject) {
        sent.send(message)
    }

    override fun close() {
        incoming.close()
        sent.close()
    }
}
