package com.denggl2.mason.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMarkdownTest {

    @Test
    fun `parses markdown table between paragraphs`() {
        val blocks = parseMessageBlocks(
            """
            结论先说。

            | 方案 | 价格 |
            | --- | ---: |
            | A | 12 |
            | B | 9 |

            选择 B。
            """.trimIndent(),
        )

        assertEquals(
            listOf(MessageBlockKind.Paragraph, MessageBlockKind.Table, MessageBlockKind.Paragraph),
            blocks.map { it.kind },
        )
        assertEquals(listOf("方案", "价格"), blocks[1].table?.headers)
        assertEquals(listOf(listOf("A", "12"), listOf("B", "9")), blocks[1].table?.rows)
    }

    @Test
    fun `keeps pipes inside code blocks`() {
        val blocks = parseMessageBlocks(
            """
            ```js
            const either = left || right;
            ```
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        assertEquals(MessageBlockKind.Code, blocks.single().kind)
        assertTrue(blocks.single().text.contains("left || right"))
    }

    @Test
    fun `copies tsv and escapes csv`() {
        val table = MessageTable(
            headers = listOf("名称", "说明"),
            rows = listOf(listOf("A, B", "他说\"可以\"")),
        )

        assertEquals("名称\t说明\nA, B\t他说\"可以\"", messageTableToTsv(table))
        assertEquals("名称,说明\r\n\"A, B\",\"他说\"\"可以\"\"\"", messageTableToCsv(table))
    }
}
