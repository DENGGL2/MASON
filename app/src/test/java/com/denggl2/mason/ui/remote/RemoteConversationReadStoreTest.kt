package com.denggl2.mason.ui.remote

import com.denggl2.mason.protocol.RemoteExecutionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConversationReadStoreTest {
    @Test
    fun completedConversationIsUnreadUntilCurrentVersionIsSeen() {
        val completionId = "turn-123"

        assertTrue(isRemoteCompletionUnread(RemoteExecutionStatus.COMPLETED, completionId, null))
        assertFalse(isRemoteCompletionUnread(RemoteExecutionStatus.COMPLETED, completionId, completionId))
    }

    @Test
    fun newCompletionVersionBecomesUnreadAgain() {
        val previous = "turn-123"
        val next = "turn-456"

        assertTrue(isRemoteCompletionUnread(RemoteExecutionStatus.COMPLETED, next, previous))
    }

    @Test
    fun nonCompletedConversationNeverShowsUnreadDot() {
        assertFalse(isRemoteCompletionUnread(RemoteExecutionStatus.RUNNING, "current", null))
        assertFalse(isRemoteCompletionUnread(RemoteExecutionStatus.IDLE, "current", null))
    }

    @Test
    fun completedConversationWithoutCompletionIdDoesNotShowPermanentDot() {
        assertFalse(isRemoteCompletionUnread(RemoteExecutionStatus.COMPLETED, null, null))
    }
}
