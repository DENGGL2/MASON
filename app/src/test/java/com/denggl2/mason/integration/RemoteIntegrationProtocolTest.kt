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

    @Test
    fun mcpOAuthDiscoversMetadataAndExchangesCodeWithPkceResource() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                "/mcp" -> MockResponse()
                    .setResponseCode(401)
                    .addHeader(
                        "WWW-Authenticate",
                        "Bearer resource_metadata=\"$baseUrl/.well-known/oauth-protected-resource/mcp\"",
                    )
                "/.well-known/oauth-protected-resource/mcp" -> MockResponse().setResponseCode(200).setBody(
                    """{"resource":"$baseUrl/mcp","authorization_servers":["$baseUrl/login/oauth"],"scopes_supported":["repo","read:user"]}""",
                )
                "/.well-known/oauth-authorization-server/login/oauth" -> MockResponse().setResponseCode(200).setBody(
                    """{"issuer":"$baseUrl/login/oauth","authorization_endpoint":"$baseUrl/authorize","token_endpoint":"$baseUrl/token","code_challenge_methods_supported":["S256"]}""",
                )
                "/token" -> {
                    val body = request.body.readUtf8()
                    assertTrue(body.contains("grant_type=authorization_code"))
                    assertTrue(body.contains("code_verifier=test-verifier"))
                    assertTrue(body.contains("resource="))
                    MockResponse().setResponseCode(200).setBody("""{"access_token":"secure-token","token_type":"bearer"}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val protocol = McpOAuthProtocol(JsonRpcHttpTransport())

        val metadata = protocol.discover("$baseUrl/mcp")
        val token = protocol.exchangeCode(
            tokenEndpoint = metadata.tokenEndpoint,
            code = "test-code",
            clientId = "client-id",
            redirectUri = "mason://oauth/mcp",
            verifier = "test-verifier",
            resource = metadata.resource,
        )

        assertEquals("$baseUrl/authorize", metadata.authorizationEndpoint)
        assertEquals(listOf("repo", "read:user"), metadata.scopes)
        assertEquals("secure-token", token)
    }

    @Test
    fun mcpOAuthParsesGitHubStyleMetadataLocations() {
        val protocol = McpOAuthProtocol(JsonRpcHttpTransport())

        assertEquals(
            "https://github.com/.well-known/oauth-authorization-server/login/oauth",
            protocol.authorizationMetadataUrl("https://github.com/login/oauth"),
        )
        assertEquals(
            "https://example.com/resource",
            protocol.parseResourceMetadataUrl(
                "Bearer realm=\"mcp\", resource_metadata=\"https://example.com/resource\"",
            ),
        )
    }

    @Test
    fun legacyIntegrationTokensMigrateToCredentialReferences() {
        val legacy = IntegrationConfigSnapshot(
            mcpServers = listOf(McpServerConfig(name = "Legacy MCP", endpoint = "https://example.com/mcp", bearerToken = "mcp-secret")),
            a2aAgents = listOf(A2aAgentConfig(name = "Legacy A2A", cardUrl = "https://example.com/a2a", bearerToken = "a2a-secret")),
            schemaVersion = 1,
        )
        val saved = mutableMapOf<String, String>()

        val migrated = migrateIntegrationSnapshot(legacy) { secret, existingRef ->
            (existingRef ?: "ref-${saved.size + 1}").also { saved[it] = secret }
        }

        assertEquals(2, migrated.schemaVersion)
        assertEquals(McpAuthType.BEARER_TOKEN, migrated.mcpServers.single().authType)
        assertEquals("", migrated.mcpServers.single().bearerToken)
        assertEquals("", migrated.a2aAgents.single().bearerToken)
        assertEquals(setOf("mcp-secret", "a2a-secret"), saved.values.toSet())
    }
}
