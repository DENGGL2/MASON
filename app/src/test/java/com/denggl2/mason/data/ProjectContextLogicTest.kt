package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectContextLogicTest {
    @Test
    fun detectsChineseAndEnglishProjectReferences() {
        assertEquals("mason", detectProjectContext("继续 Mason 项目")?.id)
        assertEquals("mason", detectProjectContext("Continue project Mason")?.id)
        assertEquals("消息中枢", detectProjectContext("继续消息中枢项目")?.id)
    }

    @Test
    fun ignoresOrdinaryConversationWithoutProjectReference() {
        assertNull(detectProjectContext("继续处理刚才的文件"))
        assertNull(detectProjectContext("What project are we working on?"))
        assertNull(detectProjectContext("Show the project status"))
        assertNull(detectProjectContext("Open project settings"))
        assertNull(detectProjectContext("继续当前项目"))
        assertNull(detectProjectContext("这个项目下一步做什么"))
    }

    @Test
    fun normalizesStableProjectIds() {
        assertEquals("mason-android", normalizeProjectId(" Mason Android "))
        assertEquals("消息中枢", normalizeProjectId("消息中枢"))
    }
}
