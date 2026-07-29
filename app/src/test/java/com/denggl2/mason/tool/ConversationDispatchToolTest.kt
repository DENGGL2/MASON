package com.denggl2.mason.tool

import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDispatchToolTest {
    private val conversations = listOf(
        Conversation(id = 1, title = "当前任务", updatedAt = 30),
        Conversation(id = 2, title = "MiniCPM 调试", updatedAt = 20),
        Conversation(id = 3, title = "设置页面", updatedAt = 10),
    )

    @Test
    fun resolvesExistingConversationByIdExactTitleAndFriendlySuffix() {
        assertFound(2, resolveConversationTarget("2", 1, conversations))
        assertFound(2, resolveConversationTarget("MiniCPM 调试", 1, conversations))
        assertFound(2, resolveConversationTarget("MiniCPM 调试对话", 1, conversations))
    }

    @Test
    fun rejectsCurrentMissingAndAmbiguousTargetsWithoutCreatingConversation() {
        assertTrue(resolveConversationTarget("当前任务", 1, conversations) is ConversationTargetResolution.CurrentConversation)
        assertTrue(resolveConversationTarget("不存在", 1, conversations) is ConversationTargetResolution.NotFound)
        val duplicated = conversations + Conversation(id = 4, title = "MiniCPM 调试")
        assertTrue(resolveConversationTarget("MiniCPM 调试", 1, duplicated) is ConversationTargetResolution.Ambiguous)
    }

    @Test
    fun dispatchIdentityIsStableForRecoveryAndChangesAcrossTasks() {
        val first = conversationDispatchId("task-a", 1, 2, "summary")
        assertEquals(first, conversationDispatchId("task-a", 1, 2, "summary"))
        assertFalse(first == conversationDispatchId("task-b", 1, 2, "summary"))
    }

    @Test
    fun detectsSavedMessageAndCompletedResponseForIdempotentRetry() {
        val handoff = buildHandoffMessage("当前任务", "继续处理")
        val dispatchId = conversationDispatchId("task-a", 1, 2, "继续处理")
        val messages = listOf(
            Message(
                id = 1,
                conversationId = 2,
                role = "tool",
                content = "跨对话投递请求",
                toolCallId = "conversation_dispatch:$dispatchId:request",
                toolCallName = ConversationDispatchTool.NAME,
                timestamp = 1,
            ),
            Message(id = 2, conversationId = 2, role = "user", content = handoff, timestamp = 2),
            Message(id = 3, conversationId = 2, role = "assistant", content = "已经继续处理", timestamp = 3),
            Message(
                id = 4,
                conversationId = 2,
                role = "tool",
                content = "跨对话投递完成",
                toolCallId = "conversation_dispatch:$dispatchId:complete",
                toolCallName = ConversationDispatchTool.NAME,
                timestamp = 4,
            ),
        )

        val progress = inspectConversationDispatch(messages, dispatchId, handoff)

        assertTrue(progress.requestRecorded)
        assertTrue(progress.messageRecorded)
        assertEquals("已经继续处理", progress.response)
        assertTrue(progress.completed)
    }

    @Test
    fun matchingHandoffWithoutRequestMarkerIsNotMistakenForCompletedDispatch() {
        val handoff = buildHandoffMessage("当前任务", "继续处理")
        val progress = inspectConversationDispatch(
            messages = listOf(Message(id = 1, conversationId = 2, role = "user", content = handoff)),
            dispatchId = "missing-marker",
            handoff = handoff,
        )

        assertFalse(progress.requestRecorded)
        assertFalse(progress.messageRecorded)
        assertEquals(null, progress.response)
        assertFalse(progress.completed)
    }

    private fun assertFound(expectedId: Long, resolution: ConversationTargetResolution) {
        assertTrue(resolution is ConversationTargetResolution.Found)
        assertEquals(expectedId, (resolution as ConversationTargetResolution.Found).conversation.id)
    }
}
