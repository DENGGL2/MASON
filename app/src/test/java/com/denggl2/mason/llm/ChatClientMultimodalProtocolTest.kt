package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.tool.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
