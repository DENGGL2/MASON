package com.denggl2.mason.llm

import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.toApiChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
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

    @Test
    fun readsArrayStyleMultimodalResponseText() {
        val content = buildJsonArray {
            add(buildJsonObject { put("type", "output_text"); put("text", "第一段") })
            add(buildJsonObject { put("type", "text"); put("text", "第二段") })
        }

        assertEquals("第一段\n第二段", content.displayText())
    }
}
