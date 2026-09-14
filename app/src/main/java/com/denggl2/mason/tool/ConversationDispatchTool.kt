package com.denggl2.mason.tool

import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.createTaskRun
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.agent.withSteps
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.stripModelParticipationMarkers
import com.denggl2.mason.integration.stripCapabilityRequirementMarkers
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.model.MasonModelRouter
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.Message
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConversationDispatchTool @Inject constructor(
    private val syncManager: SyncManager,
    private val taskRunStore: TaskRunStore,
    private val modelRouter: Provider<MasonModelRouter>,
) : Tool {
    override val name: String = NAME
    override val displayName: String = "发送到其他对话"
    override val approvalDescription: String = "把当前对话的总结发送到另一个已有对话，并触发该对话继续处理。"
    override val description: String =
        "仅当用户明确要求把总结或消息发送到另一个已有 Mason 对话并让其继续处理时使用。" +
            "目标不存在或不唯一时会失败，不会创建新对话。summary 必须是目标对话可独立理解的任务交接文本。"
    override val parameters: Map<String, ParameterDef> = mapOf(
        ARG_TARGET to ParameterDef("string", "目标对话的标题或数字 ID", required = true),
        ARG_SUMMARY to ParameterDef("string", "发送给目标对话的完整总结和下一步要求", required = true),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val targetQuery = args[ARG_TARGET].orEmpty().trim()
        val summary = args[ARG_SUMMARY].orEmpty().trim()
        if (targetQuery.isBlank()) return ToolResult.error("目标对话不能为空")
        if (summary.isBlank()) return ToolResult.error("发送内容不能为空")
        if (summary.length > MAX_HANDOFF_CHARS) return ToolResult.error("发送内容过长，请压缩到 $MAX_HANDOFF_CHARS 字以内")

        val sourceConversationId = args[INTERNAL_SOURCE_CONVERSATION_ID]?.toLongOrNull()
        val conversations = syncManager.getConversationsSnapshot()
        val target = when (val resolution = resolveConversationTarget(targetQuery, sourceConversationId, conversations)) {
            is ConversationTargetResolution.Found -> resolution.conversation
            ConversationTargetResolution.CurrentConversation -> return ToolResult.error("不能把消息发送到当前对话")
            is ConversationTargetResolution.Ambiguous -> return ToolResult.error(
                "找到多个匹配对话，请使用数字 ID 指定：" +
                    resolution.matches.joinToString("；") { "${it.title}（${it.id}）" },
            )
            is ConversationTargetResolution.NotFound -> return ToolResult.error(
                buildString {
                    append("没有找到目标对话“$targetQuery”")
                    if (resolution.available.isNotEmpty()) {
                        append("。最近对话：")
                        append(resolution.available.joinToString("；") { "${it.title}（${it.id}）" })
                    }
                },
            )
        }

        val sourceTitle = sourceConversationId?.let { id -> conversations.firstOrNull { it.id == id }?.title }
            ?: "当前对话"
        val handoff = buildHandoffMessage(sourceTitle, summary)
        val dispatchId = conversationDispatchId(
            taskRunId = args[INTERNAL_TASK_RUN_ID].orEmpty().ifBlank { UUID.randomUUID().toString() },
            sourceConversationId = sourceConversationId,
            targetConversationId = target.id,
            summary = summary,
        )
        val targetRunId = "$TARGET_RUN_PREFIX$dispatchId"
        val existingMessages = syncManager.getMessagesSnapshot(target.id)
        val existingProgress = inspectConversationDispatch(existingMessages, dispatchId, handoff)
        if (existingProgress.completed) {
            taskRunStore.get(targetRunId)
                ?.takeIf { it.status in setOf(TaskRunStatus.Running, TaskRunStatus.WaitingForUser) }
                ?.let { taskRunStore.save(it.completedDispatch(existingProgress.response.orEmpty())) }
            taskRunStore.markConversationInactive(target.id)
            return dispatchSuccess(target, existingProgress.response.orEmpty(), deduplicated = true)
        }

        val conflictingRun = taskRunStore.runs.value.values.any { run ->
            run.id != targetRunId && run.conversationId == target.id &&
                run.status in setOf(TaskRunStatus.Running, TaskRunStatus.WaitingForUser)
        }
        if (target.id in taskRunStore.activeConversationIds.value || conflictingRun) {
            return ToolResult.error("目标对话“${target.title}”正在处理其他任务，请稍后再发送")
        }

        val run = taskRunStore.get(targetRunId)
            ?: createTaskRun(handoff).copy(id = targetRunId, conversationId = target.id)
        taskRunStore.markConversationActive(target.id)
        taskRunStore.save(run.runningForDispatch())

        var messageWritten = existingProgress.messageRecorded
        return try {
            if (!existingProgress.requestRecorded) {
                saveDispatchMarker(target.id, requestMarkerId(dispatchId), "跨对话投递请求")
            }
            if (!existingProgress.messageRecorded) {
                syncManager.saveMessage(target.id, role = "user", content = handoff)
                messageWritten = true
            }

            val refreshed = syncManager.getMessagesSnapshot(target.id)
            val progressAfterWrite = inspectConversationDispatch(refreshed, dispatchId, handoff)
            val response = progressAfterWrite.response ?: generateTargetResponse(target.id, refreshed)
            if (progressAfterWrite.response == null) {
                syncManager.saveMessage(target.id, role = "assistant", content = response)
            }
            if (!progressAfterWrite.completed) {
                saveDispatchMarker(target.id, completionMarkerId(dispatchId), "跨对话投递完成")
            }
            taskRunStore.save(run.completedDispatch(response))
            dispatchSuccess(target, response, deduplicated = progressAfterWrite.messageRecorded)
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            taskRunStore.save(run.failedDispatch(message))
            ToolResult.error(if (messageWritten) {
                "已向“${target.title}”写入交接消息，但自动继续失败：$message"
            } else {
                "向“${target.title}”发送交接消息失败：$message"
            })
        } finally {
            taskRunStore.markConversationInactive(target.id)
        }
    }

    private suspend fun generateTargetResponse(conversationId: Long, messages: List<Message>): String {
        val chatMessages = messages
            .filterNot { it.role == "tool" && it.toolCallName == NAME }
            .map { message ->
                ChatMessage(
                    role = message.role,
                    content = message.content?.let(::visibleConversationContent),
                    tool_call_id = message.toolCallId,
                    name = message.toolCallName,
                    timestamp = message.timestamp,
                )
            }
        val content = StringBuilder()
        var failure: String? = null
        modelRouter.get().route(
            messages = chatMessages,
            toolsEnabled = false,
            memoryScopeId = conversationId.toString(),
        ).responses.collect { response ->
            when (response) {
                is ChatResponse.TextChunk -> content.append(response.text)
                is ChatResponse.Error -> failure = response.message
                is ChatResponse.ToolCallsRequested -> failure = "目标对话需要调用工具，请打开该对话确认后继续"
                else -> Unit
            }
        }
        if (content.isNotBlank()) return content.toString().trim()
        throw IllegalStateException(failure ?: "目标对话没有生成可显示的回复")
    }

    private suspend fun saveDispatchMarker(conversationId: Long, markerId: String, content: String) {
        syncManager.saveMessage(
            conversationId = conversationId,
            role = "tool",
            content = content,
            toolCallId = markerId,
            toolCallName = NAME,
        )
    }

    private fun dispatchSuccess(
        target: Conversation,
        response: String,
        deduplicated: Boolean,
    ) = ToolResult.success(
        mapOf(
            "targetConversationId" to target.id.toString(),
            "targetConversationTitle" to target.title,
            "status" to "completed",
            "responsePreview" to response.take(300),
            "deduplicated" to deduplicated.toString(),
        ),
    )

    companion object {
        const val NAME = "conversation_dispatch"
        const val ARG_TARGET = "target_conversation"
        const val ARG_SUMMARY = "summary"
        const val INTERNAL_TASK_RUN_ID = "_mason_task_run_id"
        const val INTERNAL_SOURCE_CONVERSATION_ID = "_mason_source_conversation_id"
        private const val TARGET_RUN_PREFIX = "dispatch-"
        private const val MAX_HANDOFF_CHARS = 12_000
    }
}

internal sealed interface ConversationTargetResolution {
    data class Found(val conversation: Conversation) : ConversationTargetResolution
    data class Ambiguous(val matches: List<Conversation>) : ConversationTargetResolution
    data class NotFound(val available: List<Conversation>) : ConversationTargetResolution
    data object CurrentConversation : ConversationTargetResolution
}

internal fun resolveConversationTarget(
    query: String,
    sourceConversationId: Long?,
    conversations: List<Conversation>,
): ConversationTargetResolution {
    val cleaned = cleanConversationQuery(query)
    if (cleaned.isBlank()) return ConversationTargetResolution.NotFound(conversations.take(5))
    val numericId = cleaned.removePrefix("#").toLongOrNull()
    if (numericId != null) {
        val match = conversations.firstOrNull { it.id == numericId }
            ?: return ConversationTargetResolution.NotFound(conversations.take(5))
        return if (match.id == sourceConversationId) {
            ConversationTargetResolution.CurrentConversation
        } else {
            ConversationTargetResolution.Found(match)
        }
    }

    fun resolveMatches(matches: List<Conversation>): ConversationTargetResolution? = when {
        matches.isEmpty() -> null
        matches.size > 1 -> ConversationTargetResolution.Ambiguous(matches.take(5))
        matches.single().id == sourceConversationId -> ConversationTargetResolution.CurrentConversation
        else -> ConversationTargetResolution.Found(matches.single())
    }

    val exact = conversations.filter { cleanConversationQuery(it.title).equals(cleaned, ignoreCase = true) }
    resolveMatches(exact)?.let { return it }
    val partial = conversations.filter { conversation ->
        val title = cleanConversationQuery(conversation.title)
        title.isNotBlank() &&
            (title.contains(cleaned, ignoreCase = true) || cleaned.contains(title, ignoreCase = true))
    }
    return resolveMatches(partial) ?: ConversationTargetResolution.NotFound(conversations.take(5))
}

internal data class ConversationDispatchProgress(
    val requestRecorded: Boolean,
    val messageRecorded: Boolean,
    val response: String?,
    val completed: Boolean,
)

internal fun inspectConversationDispatch(
    messages: List<Message>,
    dispatchId: String,
    handoff: String,
): ConversationDispatchProgress {
    val requestIndex = messages.indexOfFirst { message ->
        message.role == "tool" && message.toolCallName == ConversationDispatchTool.NAME &&
            message.toolCallId == requestMarkerId(dispatchId)
    }
    val messageIndex = if (requestIndex >= 0) {
        messages.withIndex().firstOrNull { (index, message) ->
            index > requestIndex && message.role == "user" && message.content == handoff
        }?.index ?: -1
    } else {
        -1
    }
    val response = messages.drop((messageIndex + 1).coerceAtLeast(0))
        .takeWhile { it.role != "user" }
        .firstOrNull { it.role == "assistant" && !it.content.isNullOrBlank() }
        ?.content
        ?.let(::visibleConversationContent)
    val completed = messages.any { message ->
        message.role == "tool" && message.toolCallName == ConversationDispatchTool.NAME &&
            message.toolCallId == completionMarkerId(dispatchId)
    }
    return ConversationDispatchProgress(
        requestRecorded = requestIndex >= 0,
        messageRecorded = messageIndex >= 0,
        response = response,
        completed = completed,
    )
}

internal fun conversationDispatchId(
    taskRunId: String,
    sourceConversationId: Long?,
    targetConversationId: Long,
    summary: String,
): String {
    val input = listOf(taskRunId, sourceConversationId.orEmptyId(), targetConversationId.toString(), summary.trim())
        .joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun buildHandoffMessage(sourceTitle: String, summary: String): String =
    "来自对话「${sourceTitle.take(80)}」的任务交接：\n\n${summary.trim()}\n\n请基于以上信息继续处理，并直接给出下一步结果。"

private fun cleanConversationQuery(value: String): String = value.trim()
    .trim('"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』')
    .removeSuffix("对话")
    .removeSuffix("会话")
    .trim()

private fun visibleConversationContent(content: String): String = stripCapabilityRequirementMarkers(
    stripTaskRunMarkers(stripArtifactMarkers(stripModelParticipationMarkers(content))),
)

private fun TaskRun.runningForDispatch(): TaskRun = withSteps(steps.map { step ->
    when (step.id) {
        "plan" -> step.copy(status = TaskStepStatus.Completed, detail = "已接收跨对话任务")
        "execute" -> step.copy(status = TaskStepStatus.Running, detail = "正在根据交接内容继续处理")
        else -> step
    }
})

private fun TaskRun.completedDispatch(response: String): TaskRun = withSteps(steps.map { step ->
    step.copy(status = TaskStepStatus.Completed, detail = when (step.id) {
        "execute" -> "目标对话已生成回复"
        "summary" -> "跨对话任务已完成"
        else -> step.detail
    })
}).copy(summary = response.take(500), lastError = null)

private fun TaskRun.failedDispatch(error: String): TaskRun = withSteps(steps.map { step ->
    when (step.id) {
        "plan" -> step.copy(status = TaskStepStatus.Completed)
        "execute" -> step.copy(status = TaskStepStatus.Failed, detail = error, error = error)
        else -> step.copy(status = TaskStepStatus.Cancelled, detail = "自动继续未完成")
    }
}).copy(lastError = error)

private fun requestMarkerId(dispatchId: String) = "${ConversationDispatchTool.NAME}:$dispatchId:request"
private fun completionMarkerId(dispatchId: String) = "${ConversationDispatchTool.NAME}:$dispatchId:complete"
private fun Long?.orEmptyId(): String = this?.toString().orEmpty()
