package com.denggl2.mason.model

import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.llm.ModelAttachment
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRoutingLogicTest {
    @Test
    fun timeoutMessageUsesTheConfiguredTimeout() {
        assertEquals("模型响应超过 15 秒，已停止", remoteTimeoutMessage(15_000L))
    }

    @Test
    fun imageRequestDoesNotFallBackToTextWhenImageModelIsMissing() {
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("请生成图片：一座未来城市", emptyList(), emptyList()),
        )
    }

    @Test
    fun renderedPdfPagesUseVisionRouting() {
        assertEquals(
            ModelModality.Vision,
            detectModelModality(
                "总结这个 PDF",
                listOf(ChatAttachmentReference("report.pdf", "content://report", image = false)),
                listOf(ModelAttachment("report-page-1.jpg", "data:image/jpeg;base64,AA", "image/jpeg")),
            ),
        )
    }

    @Test
    fun visionFallsBackOnlyToKnownCapableOrCustomModel() {
        assertEquals("", resolveVisionModel(ApiConfig(providerId = "deepseek", model = "deepseek-v4-flash")))
        assertEquals("gemini-3.5-flash", resolveVisionModel(ApiConfig(providerId = "gemini", model = "gemini-3.5-flash")))
        assertEquals("my-vision", resolveVisionModel(ApiConfig(visionModel = "my-vision")))
    }

    @Test
    fun attachmentUrisAreNotSentToModelButSkillInstructionsRemain() {
        val content = """分析图片

---
Mason 附加上下文
- Skill：视觉检查 | /skill
<mason-skill-instructions>
检查画面中的风险
</mason-skill-instructions>
- 图片：screen.png | content://private/screen
请结合以上材料处理
""".trimIndent()
        val context = ChatContextParser.parse(content)

        val sanitized = sanitizeAttachmentMetadata(listOf(ChatMessage("user", content)), context).single().content.orEmpty()

        assertFalse(sanitized.contains("content://private/screen"))
        assertTrue(sanitized.contains("检查画面中的风险"))
    }

    @Test
    fun largeImagesUsePowerOfTwoDecodeSampling() {
        assertEquals(4, calculateImageSampleSize(8_000, 4_000, 2_048))
        assertEquals(1, calculateImageSampleSize(1_920, 1_080, 2_048))
    }

    @Test
    fun explicitLocalSelectionNeverSwitchesBetweenLocalModels() {
        val miniCpm = ApiConfig(
            localModel = "minicpm5-1b-q4-k-m-gguf",
            localModelDirectEnabled = true,
        )
        val gemma = miniCpm.copy(localModel = "gemma-4-e2b-it-litert")

        assertEquals("minicpm5-1b-q4-k-m-gguf", resolveSelectedLocalModelId(miniCpm))
        assertEquals("gemma-4-e2b-it-litert", resolveSelectedLocalModelId(gemma))
    }

    @Test
    fun remoteFallbackUsesOnlyTheConfiguredLocalModel() {
        val config = ApiConfig(
            localModel = "minicpm5-1b-q4-k-m-gguf",
            offlineFallbackEnabled = true,
        )

        assertEquals(
            "minicpm5-1b-q4-k-m-gguf",
            resolveLocalFallbackModelId(config, ModelModality.Text, useLocalDirect = false, localReady = true),
        )
        assertEquals(
            null,
            resolveLocalFallbackModelId(config, ModelModality.Vision, useLocalDirect = false, localReady = true),
        )
    }

    @Test
    fun dynamicRoutingUsesLocalOnlyForSimpleText() {
        val config = ApiConfig(dynamicLocalRoutingEnabled = true)

        assertTrue(
            shouldUseLocalModel(
                config = config,
                modality = ModelModality.Text,
                userText = "帮我把这句话改得简洁一点",
                hasAttachments = false,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
        assertFalse(isSimpleLocalRequest("请读取手机联系人并发送短信"))
        assertFalse(isSimpleLocalRequest("请详细分析并制定一个分步骤的完整方案"))
        assertTrue(isConversationDispatchRequest("把总结发送到 MiniCPM 调试对话"))
        assertFalse(isSimpleLocalRequest("把总结发送到 MiniCPM 调试对话"))
        assertFalse(
            shouldUseLocalModel(
                config = config,
                modality = ModelModality.Vision,
                userText = "看看这张图",
                hasAttachments = true,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
    }

    @Test
    fun offlineFallbackDoesNotHideAuthenticationErrors() {
        assertTrue(isOfflineFailure("failed to connect to host"))
        assertTrue(isOfflineFailure("模型请求失败：UnknownHostException"))
        assertTrue(isOfflineFailure("API 错误 503: unavailable"))
        assertFalse(isOfflineFailure("API 错误 401: invalid key"))
    }
}
