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
}
