package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.ToolCall
import com.denggl2.mason.tool.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatClientMultimodalProtocolTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ChatClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ChatClient(
            StreamProcessor(),
            FakeApiConfigProvider(server.url("/v1").toString().trimEnd('/')),
            ToolRegistry(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun imageGenerationAcceptsBase64WithoutForcingResponseFormat() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"b64_json":"iVBORw0KGgo=","revised_prompt":"clean prompt"}]}""",
        ))

        val responses = client.generateImage("画一座城市", "gpt-image-1").toList()
        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()

        assertEquals("/v1/images/generations", request.path)
        assertTrue(requestBody.contains("gpt-image-1"))
        assertFalse(requestBody.contains("response_format"))
        assertTrue(responses.single() is ChatResponse.ImageGenerated)
        assertTrue((responses.single() as ChatResponse.ImageGenerated).isBase64)
    }

    @Test
    fun chatAcceptsArrayStyleResponseContent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":[{"type":"output_text","text":"识别完成"}]}}]}""",
        ))

        val responses = client.chat(listOf(ChatMessage("user", "看图")), toolsEnabled = false).toList()

        assertEquals("识别完成", (responses.single() as ChatResponse.TextChunk).text)
    }

    @Test
    fun connectionTestAcceptsAnImageOnlyModel() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"chat unsupported"}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"vision unsupported"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"url":"https://example.invalid/image.png"}]}""",
        ))

        val result = client.testConnection(
            apiUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "",
            model = "image-only-model",
            visionModel = "image-only-model",
            imageModel = "image-only-model",
            requiresApiKey = false,
            testTools = false,
        )

        assertTrue(result.success)
        assertFalse(result.capabilities.first { it.label == "聊天" }.success)
        assertTrue(result.capabilities.first { it.label == "生图" }.success)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
        assertEquals("/v1/images/generations", server.takeRequest().path)
    }

    @Test
    fun connectionTestExplainsHttpFailuresBeforeShowingCodeAndBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request body"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model not found"}}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"quota exceeded"}"""))

        val result = client.testConnection(
            apiUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "",
            model = "unavailable-model",
            visionModel = "unavailable-model",
            imageModel = "unavailable-model",
            requiresApiKey = false,
            testTools = false,
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("可能原因：请求参数、Model ID、接口协议或当前模型能力不匹配"))
        assertTrue(result.message.contains("HTTP 400"))
        assertTrue(result.message.contains("接口返回：bad request body"))
        assertTrue(result.message.contains("HTTP 404"))
        assertTrue(result.message.contains("接口返回：model not found"))
        assertTrue(result.message.contains("HTTP 429"))
        assertTrue(result.message.contains("接口返回：quota exceeded"))
    }
    @Test
    fun connectionTestVerifiesToolResultRoundTrip() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""",
        ))
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":{"message":"vision unsupported"}}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_probe","type":"function","function":{"name":"mason_connection_probe","arguments":"{}"}}]}}]}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""",
        ))

        val result = client.testConnection(
            apiUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "",
            model = "tool-model",
            visionModel = "tool-model",
            requiresApiKey = false,
            testTools = true,
        )

        assertTrue(result.capabilities.last().success)
        server.takeRequest()
        server.takeRequest()
        val firstToolRequest = server.takeRequest()
        val followUpRequest = server.takeRequest()
        val firstToolJson = Json.parseToJsonElement(firstToolRequest.body.readUtf8()).jsonObject
        assertTrue(firstToolJson["tools"]!!.jsonArray.any { tool ->
            tool.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content ==
                "mason_connection_probe"
        })
        val followUpJson = Json.parseToJsonElement(followUpRequest.body.readUtf8()).jsonObject
        val followUpMessages = followUpJson["messages"]!!.jsonArray
        assertEquals("mason-msg-0", followUpMessages[0].jsonObject["id"]!!.jsonPrimitive.content)
        val assistantMessage = followUpMessages[1].jsonObject
        val assistantToolCall = assistantMessage["tool_calls"]!!.jsonArray.single().jsonObject
        assertFalse(assistantMessage.containsKey("content"))
        assertEquals("mason-msg-1", assistantMessage["id"]!!.jsonPrimitive.content)
        assertEquals("call_probe", assistantToolCall["id"]!!.jsonPrimitive.content)
        assertEquals("function", assistantToolCall["type"]!!.jsonPrimitive.content)
        val toolMessage = followUpMessages[2].jsonObject
        assertEquals("mason-msg-2", toolMessage["id"]!!.jsonPrimitive.content)
        assertEquals("call_probe", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun chatToolResultRoundTripIncludesRelayToolResultId() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_main","type":"function","function":{"name":"mason_connection_probe","arguments":"{}"}}]}}]}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":"done"}}]}""",
        ))

        val call = ToolCall(
            id = "call_main",
            function = FunctionCall("mason_connection_probe", "{}"),
        )
        val user = ChatMessage(role = "user", content = "check")
        val first = client.chat(listOf(user), toolsEnabled = true).toList()
        assertTrue(first.single() is ChatResponse.ToolCallsRequested)
        server.takeRequest()

        client.chat(
            listOf(
                user,
                ChatMessage(role = "assistant", tool_calls = listOf(call)),
                ChatMessage(role = "tool", content = "ok", tool_call_id = call.id),
            ),
            toolsEnabled = true,
        ).toList()
        val followUpJson = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val followUpMessages = followUpJson["messages"]!!.jsonArray
        assertEquals("mason-msg-0", followUpMessages[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("mason-msg-1", followUpMessages[1].jsonObject["id"]!!.jsonPrimitive.content)
        val assistantMessage = followUpMessages[2].jsonObject
        val assistantToolCall = assistantMessage["tool_calls"]!!.jsonArray.single().jsonObject
        assertFalse(assistantMessage.containsKey("content"))
        assertEquals("mason-msg-2", assistantMessage["id"]!!.jsonPrimitive.content)
        assertEquals("call_main", assistantToolCall["id"]!!.jsonPrimitive.content)
        assertEquals("function", assistantToolCall["type"]!!.jsonPrimitive.content)
        val toolMessage = followUpMessages[3].jsonObject
        assertEquals("mason-msg-3", toolMessage["id"]!!.jsonPrimitive.content)
        assertEquals("call_main", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun chatRetriesMissingMessageIdWithOriginalToolCallIds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_relay","type":"function","function":{"name":"mason_connection_probe","arguments":"{}"}}]}}]}""",
        ))
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":"messages[2]: missing field id"}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":"done"}}]}""",
        ))

        val call = ToolCall(
            id = "call_relay",
            function = FunctionCall("mason_connection_probe", "{}"),
        )
        val user = ChatMessage(role = "user", content = "check")
        client.chat(listOf(user), toolsEnabled = true).toList()
        server.takeRequest()

        val responses = client.chat(
            listOf(
                user,
                ChatMessage(role = "assistant", tool_calls = listOf(call)),
                ChatMessage(role = "tool", content = "ok", tool_call_id = call.id),
            ),
            toolsEnabled = true,
        ).toList()

        assertTrue("responses=$responses" , responses.any { it is ChatResponse.TextChunk && it.text == "done" })
        server.takeRequest()
        val retryJson = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val retryMessages = retryJson["messages"]!!.jsonArray
        assertFalse(retryMessages[0].jsonObject.containsKey("id"))
        assertFalse(retryMessages[1].jsonObject.containsKey("id"))
        val assistant = retryMessages.first { it.jsonObject["role"]!!.jsonPrimitive.content == "assistant" }.jsonObject
        val tool = retryMessages.first { it.jsonObject["role"]!!.jsonPrimitive.content == "tool" }.jsonObject
        assertTrue(assistant["content"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("call_relay", assistant["id"]!!.jsonPrimitive.content)
        assertEquals("call_relay", tool["id"]!!.jsonPrimitive.content)
        assertEquals("call_relay", tool["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun chatRetriesInvalidAssistantToolRoundWithRelayContent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_content","type":"function","function":{"name":"mason_connection_probe","arguments":"{}"}}]}}]}""",
        ))
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":"Invalid assistant message: content or tool_calls must be set"}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"role":"assistant","content":"done"}}]}""",
        ))

        val call = ToolCall(
            id = "call_content",
            function = FunctionCall("mason_connection_probe", "{}"),
        )
        val user = ChatMessage(role = "user", content = "check")
        client.chat(listOf(user), toolsEnabled = true).toList()
        server.takeRequest()

        val responses = client.chat(
            listOf(
                user,
                ChatMessage(role = "assistant", tool_calls = listOf(call)),
                ChatMessage(role = "tool", content = "ok", tool_call_id = call.id),
            ),
            toolsEnabled = true,
        ).toList()

        assertTrue(responses.any { it is ChatResponse.TextChunk && it.text == "done" })
        server.takeRequest()
        val retryJson = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val assistant = retryJson["messages"]!!.jsonArray.first {
            it.jsonObject["role"]!!.jsonPrimitive.content == "assistant"
        }.jsonObject
        assertTrue(assistant["content"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("call_content", assistant["id"]!!.jsonPrimitive.content)
    }
}

private class FakeApiConfigProvider(
    private val apiUrl: String,
) : ApiConfigProvider {
    override suspend fun getApiUrl(): String = apiUrl
    override suspend fun getApiKey(): String = ""
    override suspend fun getModel(): String = "test-model"
    override suspend fun getToolsEnabled(): Boolean = false
    override suspend fun requiresApiKey(): Boolean = false
}
