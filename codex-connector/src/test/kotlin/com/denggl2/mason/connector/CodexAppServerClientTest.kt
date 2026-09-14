package com.denggl2.mason.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun threadMutationsUseOfficialMethodsAndParameters() {
        runBlocking {
            val transport = FakeTransport()
            val client = CodexAppServerClient(transport)
            val api = CodexAppServerApi(client)
            try {
                val pin = async { api.updateThreadMetadata("thread-1", true) }
                val pinRequest = transport.sent.receive()
                assertEquals("thread/metadata/update", pinRequest["method"]?.jsonPrimitive?.content)
                val pinParams = pinRequest["params"]!!.jsonObject
                assertEquals("thread-1", pinParams["threadId"]?.jsonPrimitive?.content)
                assertTrue(pinParams["isPinned"]?.jsonPrimitive?.content?.toBoolean() == true)
                transport.reply(pinRequest)
                pin.await()

                val archive = async { api.archiveThread("thread-1") }
                val archiveRequest = transport.sent.receive()
                assertEquals("thread/archive", archiveRequest["method"]?.jsonPrimitive?.content)
                assertEquals(
                    "thread-1",
                    archiveRequest["params"]?.jsonObject?.get("threadId")?.jsonPrimitive?.content,
                )
                transport.reply(archiveRequest)
                archive.await()
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun initializeAdvertisesExperimentalApi() = runBlocking {
        val transport = FakeTransport()
        val client = CodexAppServerClient(transport)
        try {
            val initialization = async {
                client.initialize(
                    clientName = "mason_connector",
                    clientTitle = "MASON Connector",
                    clientVersion = "0.2.0",
                )
            }
            val request = transport.sent.receive()
            val id = request["id"]?.jsonPrimitive?.long ?: error("Missing request id")

            assertEquals("initialize", request["method"]?.jsonPrimitive?.content)
            assertEquals(
                "true",
                request["params"]
                    ?.jsonObject
                    ?.get("capabilities")
                    ?.jsonObject
                    ?.get("experimentalApi")
                    ?.jsonPrimitive
                    ?.content,
            )

            transport.incoming.send(buildJsonObject {
                put("id", id)
                put("result", JsonObject(emptyMap()))
            })

            withTimeout(1_000) { initialization.await() }
            assertEquals(
                "initialized",
                withTimeout(1_000) { transport.sent.receive() }["method"]?.jsonPrimitive?.content,
            )
        } finally {
            client.close()
        }
    }

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

    suspend fun reply(request: JsonObject, result: JsonObject = JsonObject(emptyMap())) {
        incoming.send(buildJsonObject {
            put("id", request["id"]!!)
            put("result", result)
        })
    }

    override fun close() {
        incoming.close()
        sent.close()
    }
}
