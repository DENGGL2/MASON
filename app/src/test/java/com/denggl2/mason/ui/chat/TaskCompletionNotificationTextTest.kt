package com.denggl2.mason.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCompletionNotificationTextTest {
    @Test
    fun completionNotificationNamesTheLatestArtifact() {
        assertEquals(
            "已生成：日报.md",
            taskCompletionNotificationText(
                listOf("/data/user/0/com.denggl2.mason/files/artifacts/日报.md"),
            ),
        )
    }

    @Test
    fun completionNotificationUsesGenericTextWithoutArtifacts() {
        assertEquals("对话任务已处理完成", taskCompletionNotificationText(emptyList()))
    }
}
