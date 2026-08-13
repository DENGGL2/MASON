package com.denggl2.mason.ui.chat

import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.navigation.shouldApplyFreshConversation
import com.denggl2.mason.ui.conversation.isConversationProgressActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDrawerLogicTest {
    @Test
    fun conversationTitle_usesIntentInsteadOfRawPrefix() {
        assertEquals("分析这份日志里的错误", summarizeConversationTitle("请分析这份日志里的错误。再给我修复建议"))
        assertEquals("修复登录页按钮无法点击", summarizeConversationTitle("帮我修复登录页按钮无法点击的问题"))
        assertEquals("新对话", summarizeConversationTitle("   "))
    }

    @Test
    fun naturalLanguageContinueCommandsResumeButNormalMessagesDoNot() {
        assertTrue(isTaskContinuationCommand("继续"))
        assertTrue(isTaskContinuationCommand("继续任务。"))
        assertTrue(isTaskContinuationCommand("continue"))
        assertFalse(isTaskContinuationCommand("继续分析这份新文件"))
    }

    @Test
    fun drawerGesture_requiresRightwardHorizontalDragPastSlop() {
        assertTrue(shouldOpenDrawerFromGesture(25f, 4f, 12f))
        assertFalse(shouldOpenDrawerFromGesture(8f, 1f, 12f))
        assertFalse(shouldOpenDrawerFromGesture(18f, 22f, 12f))
        assertFalse(shouldOpenDrawerFromGesture(-25f, 1f, 12f))
    }

    @Test
    fun drawerGesture_onlyStartsInsideTheNarrowLeadingEdge() {
        assertTrue(isDrawerGestureStartWithinEdge(startX = 0f, edgeWidth = 32f))
        assertTrue(isDrawerGestureStartWithinEdge(startX = 32f, edgeWidth = 32f))
        assertFalse(isDrawerGestureStartWithinEdge(startX = 33f, edgeWidth = 32f))
        assertFalse(isDrawerGestureStartWithinEdge(startX = -1f, edgeWidth = 32f))
    }

    @Test
    fun waitingAndTerminalTasks_doNotShowRunningSpinner() {
        assertTrue(isConversationProgressActive(TaskRunStatus.Running))
        assertFalse(isConversationProgressActive(TaskRunStatus.WaitingForUser))
        assertFalse(isConversationProgressActive(TaskRunStatus.Failed))
        assertFalse(isConversationProgressActive(TaskRunStatus.Completed))
    }

    @Test
    fun modelMenuSummary_prefersTheActiveRoutingMode() {
        val remote = ApiConnection(
            id = "remote-1",
            providerId = "custom",
            name = "工作模型",
            apiUrl = "https://example.invalid/v1",
            apiKey = "key",
            modelIds = listOf("remote-model", "vision-model", "image-model"),
            modelCapabilities = mapOf(
                "remote-model" to ApiModelCapabilities(supportsChat = true),
                "vision-model" to ApiModelCapabilities(supportsChat = true, supportsVision = true),
                "image-model" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
        )
        val base = ApiConfig(
            connections = listOf(remote),
            chatModelRef = ModelReference(remote.id, "remote-model"),
            visionModelRef = ModelReference(remote.id, "vision-model"),
            imageModelRef = ModelReference(remote.id, "image-model"),
            localModel = "minicpm5-1b-q4-k-m-gguf",
        )

        assertEquals("remote-model", chatModelMenuSummary(base).chatModelName)
        assertEquals("vision-model", chatModelMenuSummary(base).visionModelName)
        assertEquals("image-model", chatModelMenuSummary(base).imageModelName)

        val local = chatModelMenuSummary(base.copy(localModelDirectEnabled = true))
        assertEquals("MiniCPM5 1B", local.chatModelName)
        assertEquals("vision-model", local.visionModelName)

        val automatic = chatModelMenuSummary(base.copy(dynamicLocalRoutingEnabled = true))
        assertTrue(automatic.chatModelName.contains("remote-model"))
        assertTrue(automatic.chatModelName.contains("MiniCPM5 1B"))
        assertEquals("vision-model", automatic.visionModelName)
    }

    @Test
    fun modelMenuSummary_onlyShowsEntryWhenAModelIsConfigured() {
        assertFalse(chatModelMenuSummary(ApiConfig()).hasConfiguredModel)

        val imageConnection = ApiConnection(
            id = "image-1",
            providerId = "custom",
            name = "图片模型",
            apiUrl = "https://example.invalid/v1",
            apiKey = "test-key",
            modelIds = listOf("image-model"),
            modelCapabilities = mapOf(
                "image-model" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
        )
        assertTrue(
            chatModelMenuSummary(
                ApiConfig(
                    connections = listOf(imageConnection),
                    imageModelRef = ModelReference(imageConnection.id, "image-model"),
                ),
            ).hasConfiguredModel,
        )
    }

    @Test
    fun composerAction_sendsInputBeforeOfferingStop() {
        assertEquals(
            ComposerPrimaryAction.Send,
            composerPrimaryAction(isGenerating = true, hasSendableInput = true),
        )
        assertEquals(
            ComposerPrimaryAction.Stop,
            composerPrimaryAction(isGenerating = true, hasSendableInput = false),
        )
        assertEquals(
            ComposerPrimaryAction.Send,
            composerPrimaryAction(isGenerating = false, hasSendableInput = true),
        )
        assertEquals(
            ComposerPrimaryAction.Disabled,
            composerPrimaryAction(isGenerating = false, hasSendableInput = false),
        )
    }

    @Test
    fun backgroundAction_keepsRunningTaskAliveAndPersistsIt() {
        assertEquals(
            AppBackgroundAction.PersistRunningTask,
            appBackgroundAction(generationActive = true, hasActiveTask = true),
        )
        assertEquals(
            AppBackgroundAction.ReleaseLocalRuntime,
            appBackgroundAction(generationActive = false, hasActiveTask = true),
        )
        assertEquals(
            AppBackgroundAction.ReleaseLocalRuntime,
            appBackgroundAction(generationActive = true, hasActiveTask = false),
        )
    }

    @Test
    fun scrollToBottomButton_onlyShowsAwayFromLatestContent() {
        assertTrue(
            shouldShowScrollToBottom(
                hasMessages = true,
                canScrollForward = true,
                scrollInProgress = false,
            ),
        )
        assertFalse(
            shouldShowScrollToBottom(
                hasMessages = true,
                canScrollForward = false,
                scrollInProgress = false,
            ),
        )
        assertFalse(
            shouldShowScrollToBottom(
                hasMessages = true,
                canScrollForward = true,
                scrollInProgress = true,
            ),
        )
        assertFalse(
            shouldShowScrollToBottom(
                hasMessages = false,
                canScrollForward = true,
                scrollInProgress = false,
            ),
        )
    }

    @Test
    fun freshConversation_isConsumedOncePerNavigationEntry() {
        assertTrue(shouldApplyFreshConversation(freshRequested = true, freshApplied = false))
        assertFalse(shouldApplyFreshConversation(freshRequested = true, freshApplied = true))
        assertFalse(shouldApplyFreshConversation(freshRequested = false, freshApplied = false))

        // A newly-created entry has its own unapplied state.
        assertTrue(shouldApplyFreshConversation(freshRequested = true, freshApplied = false))
    }

    @Test
    fun resumedTask_reusesGoalWithoutDuplicatingVisibleOrPersistedUserMessage() {
        val existing = listOf(ChatMessage(role = "user", content = "继续原任务", timestamp = 1L))

        assertFalse(shouldRecordTaskGoal(isResumingTask = true))
        assertEquals(existing, taskExecutionMessages(existing, "继续原任务", timestamp = 2L))

        val missingGoal = taskExecutionMessages(emptyList(), "继续原任务", timestamp = 2L)
        assertEquals(1, missingGoal.size)
        assertEquals("继续原任务", missingGoal.single().content)
    }
}
