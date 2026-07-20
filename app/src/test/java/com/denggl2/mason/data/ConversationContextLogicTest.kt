package com.denggl2.mason.data

import com.denggl2.mason.agent.annotateTaskRun
import com.denggl2.mason.agent.createTaskRun
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.llm.model.FunctionCall
import com.denggl2.mason.llm.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextLogicTest {
    @Test
    fun compactedTailNeverStartsWithOrphanToolResult() {
        val messages = listOf(
            ChatMessage(role = "user", content = "old"),
            ChatMessage(
                role = "assistant",
                tool_calls = listOf(ToolCall("call-1", function = FunctionCall("file_read", "{}"))),
            ),
            ChatMessage(role = "tool", content = "result", tool_call_id = "call-1"),
            ChatMessage(role = "user", content = "new question"),
            ChatMessage(role = "assistant", content = "new answer"),
        )

        val compacted = compactConversationMessages(
            messages,
            maxMessages = 1,
            keepRecentMessages = 3,
        )
        val conversation = compacted.filterNot { it.role == "system" }

        assertFalse(conversation.first().role == "tool")
        assertEquals(listOf("user", "assistant"), conversation.map(ChatMessage::role))
        assertTrue(compacted.any { it.content.orEmpty().contains("较早对话的压缩摘要") })
    }

    @Test
    fun compactedTailPreservesCompleteAssistantToolPair() {
        val call = ToolCall("call-2", function = FunctionCall("file_read", "{}"))
        val messages = listOf(
            ChatMessage(role = "user", content = "old"),
            ChatMessage(role = "assistant", tool_calls = listOf(call)),
            ChatMessage(role = "tool", content = "result", tool_call_id = call.id),
            ChatMessage(role = "user", content = "continue"),
        )

        val compacted = compactConversationMessages(messages, maxMessages = 1, keepRecentMessages = 3)
        val conversation = compacted.filterNot { it.role == "system" }

        assertEquals(listOf("assistant", "tool", "user"), conversation.map(ChatMessage::role))
        assertEquals("call-2", conversation[1].tool_call_id)
    }

    @Test
    fun truncatesOversizedRecentContent() {
        val compacted = compactConversationMessages(
            listOf(ChatMessage(role = "user", content = "x".repeat(20_000))),
            maxChars = 10,
        )

        assertTrue(compacted.last().content.orEmpty().length <= 4_000)
    }

    @Test
    fun persistedSummaryIsReusedWhenCoveredHistoryIsUnchanged() {
        val older = listOf(
            ChatMessage(role = "user", content = "Use Chinese by default", timestamp = 1L),
            ChatMessage(role = "assistant", content = "Understood", timestamp = 2L),
        )
        val first = updateConversationSummary("42", older, existing = null, now = 10L)

        val reused = updateConversationSummary("42", older, existing = first, now = 20L)

        assertTrue(reused === first)
    }

    @Test
    fun persistedSummaryAppendsOnlyNewlyCoveredMessages() {
        val initialMessages = listOf(ChatMessage(role = "user", content = "Project Mason", timestamp = 1L))
        val initial = updateConversationSummary("42", initialMessages, existing = null, now = 10L)
        val extended = initialMessages + ChatMessage(role = "assistant", content = "Android assistant", timestamp = 2L)

        val updated = updateConversationSummary("42", extended, existing = initial, now = 20L)

        assertTrue(updated.content.contains("Project Mason"))
        assertTrue(updated.content.contains("Android assistant"))
        assertEquals(2, updated.coveredMessageCount)
    }

    @Test
    fun persistedSummaryRebuildsWhenCoveredHistoryChanges() {
        val original = listOf(ChatMessage(role = "user", content = "Old requirement", timestamp = 1L))
        val initial = updateConversationSummary("42", original, existing = null, now = 10L)
        val edited = listOf(ChatMessage(role = "user", content = "Corrected requirement", timestamp = 1L))

        val rebuilt = updateConversationSummary("42", edited, existing = initial, now = 20L)

        assertTrue(rebuilt.content.contains("Corrected requirement"))
        assertFalse(rebuilt.content.contains("Old requirement"))
    }

    @Test
    fun persistedSummaryNeverContainsTaskRunRecoveryPayload() {
        val annotated = annotateTaskRun("Visible answer", createTaskRun("internal task"))

        val summary = updateConversationSummary(
            scopeId = "42",
            olderMessages = listOf(ChatMessage(role = "assistant", content = annotated)),
            existing = null,
        )

        assertTrue(summary.content.contains("Visible answer"))
        assertFalse(summary.content.contains("mason-task-run"))
        assertFalse(summary.content.contains("internal task"))
    }
}
