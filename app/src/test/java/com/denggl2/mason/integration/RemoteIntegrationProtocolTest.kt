package com.denggl2.mason.integration

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class RemoteIntegrationProtocolTest {
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mcpInitializesDiscoversAndCallsToolOverJsonAndSse() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val payload = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                return when (payload["method"]?.jsonPrimitive?.content) {
                "initialize" -> MockResponse().setResponseCode(200).setBody(
                    """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","serverInfo":{"name":"Loopback MCP","version":"1"},"capabilities":{"tools":{}}}}""",
                ).addHeader("Content-Type", "application/json").addHeader("Mcp-Session-Id", "test-session")
                "notifications/initialized" -> MockResponse().setResponseCode(202)
                "tools/list" -> MockResponse().setResponseCode(200).setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","title":"Echo","description":"Returns input","inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}]}}""",
                ).addHeader("Content-Type", "application/json")
                "tools/call" -> MockResponse().setResponseCode(200).setBody(
                    "data: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"pong\"}],\"isError\":false}}\n\n",
                ).addHeader("Content-Type", "text/event-stream")
                else -> MockResponse().setResponseCode(400).setBody("unknown")
                }
            }
        }
        val config = McpServerConfig(name = "Test", endpoint = "$baseUrl/mcp")
        val client = McpClient(JsonRpcHttpTransport())

        val discovery = client.discoverTools(config)
        val result = client.callTool(config, "echo", buildJsonObject { put("text", "ping") })

        assertEquals("Loopback MCP", discovery.serverName)
        assertEquals(listOf("echo"), discovery.tools.map(McpToolDescriptor::remoteName))
        assertTrue(result.success)
        assertEquals("pong", result.data["content"])
    }

    @Test
    fun a2aDiscoversAgentAndParsesTaskArtifacts() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/.well-known/agent-card.json" -> MockResponse().setResponseCode(200).setBody(
                    """{"name":"Research Agent","description":"Finds facts","version":"1","supportedInterfaces":[{"url":"$baseUrl/a2a","protocolBinding":"JSONRPC","protocolVersion":"1.0"}],"capabilities":{},"defaultInputModes":["text/plain"],"defaultOutputModes":["text/plain"],"skills":[{"id":"research","name":"Research"}]}""",
                ).addHeader("Content-Type", "application/json")
                "/a2a" -> {
                    val payload = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    assertEquals("SendMessage", payload["method"]?.jsonPrimitive?.content)
                    MockResponse().setResponseCode(200).setBody(
                        """{"jsonrpc":"2.0","id":"1","result":{"task":{"id":"remote-1","contextId":"ctx-1","status":{"state":"TASK_STATE_COMPLETED","message":{"messageId":"m2","role":"ROLE_AGENT","parts":[{"text":"Done"}]}},"artifacts":[{"artifactId":"a1","name":"result","parts":[{"text":"artifact body","mediaType":"text/markdown"}]}]}}}""",
                    ).addHeader("Content-Type", "application/json")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val config = A2aAgentConfig(name = "Test Agent", cardUrl = baseUrl)
        val client = A2aClient(JsonRpcHttpTransport())

        val descriptor = client.discover(config)
        val result = client.sendTask(config, descriptor, "research this")

        assertEquals("Research Agent", descriptor.name)
        assertEquals(listOf("Research"), descriptor.skills)
        assertEquals("remote-1", result.externalTaskId)
        assertEquals("TASK_STATE_COMPLETED", result.state)
        assertEquals("Done", result.summary)
        assertEquals("artifact body", result.artifacts.single().text)
    }

    @Test
    fun endpointValidationRejectsNonHttpAndNamespaceIsStable() {
        assertFalse(validateRemoteEndpoint("ftp://example.com") == null)
        assertEquals("my_server", "My Server!".integrationNamespace())
    }
}
