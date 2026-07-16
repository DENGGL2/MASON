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
