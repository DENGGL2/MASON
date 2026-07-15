package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.toApiChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalMessageTest {
    @Test
    fun serializesTextImagesAndExtractedFilesAsContentParts() {
        val message = ChatMessage(role = "user", content = "分析附件").toApiChatMessage(
            listOf(
                ModelAttachment("screen.png", "data:image/png;base64,AAAA", "image/png"),
                ModelAttachment("notes.md", "content://notes", "text/markdown", inlineText = "正文"),
            ),
        )
        val encoded = Json.encodeToString(message)
        assertTrue(encoded.contains("image_url"))
        assertTrue(encoded.contains("data:image/png;base64,AAAA"))
        assertTrue(encoded.contains("附件 notes.md"))
    }
}
