package com.denggl2.mason.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextParserTest {
    @Test
    fun extractsSkillImagesAndFilesFromOutgoingMessage() {
        val context = ChatContextParser.parse(
            """分析这些材料

                ---
                Mason 附加上下文
                - Skill：报告助手 | /skills/report | 生成报告
                - 图片：screen.png | content://mason/screen
                - 文件：notes.md | content://mason/notes
            """.trimIndent(),
        )

        assertEquals("分析这些材料", context.userText)
        assertEquals("报告助手", context.skillId)
        assertEquals(2, context.attachments.size)
        assertTrue(context.attachments.first().image)
        assertFalse(context.attachments.last().image)
    }

    @Test
    fun leavesPlainMessagesUntouched() {
        val context = ChatContextParser.parse("普通文字问题")
        assertEquals("普通文字问题", context.userText)
        assertTrue(context.attachments.isEmpty())
    }
}
