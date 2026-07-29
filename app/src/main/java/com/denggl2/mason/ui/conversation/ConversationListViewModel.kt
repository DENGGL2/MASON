package com.denggl2.mason.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Environment
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.automation.AutomationDraftService
import com.denggl2.mason.integration.stripCapabilityRequirementMarkers
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.tool.ConversationDispatchTool
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationListItem(
    val conversation: Conversation,
    val lastMessage: String?,
    val searchableText: String,
    val isRunning: Boolean = false,
    val hasUnreadCompletion: Boolean = false,
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val taskRunStore: TaskRunStore,
    configDataStore: ApiConfigDataStore,
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationListItem>>(emptyList())
    val conversations: StateFlow<List<ConversationListItem>> = _conversations.asStateFlow()
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()
    val apiConfig: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    init {
        viewModelScope.launch {
            taskRunStore.refresh()
            combine(
                syncManager.getConversationsFlow(),
                taskRunStore.runs,
                taskRunStore.activeConversationIds,
                taskRunStore.unreadCompletionConversationIds,
            ) { convList, runs, activeConversationIds, unreadCompletionConversationIds ->
                Triple(
                    convList,
                    runs.values
                        .filter { it.status == TaskRunStatus.Running || it.status == TaskRunStatus.WaitingForUser }
                        .mapNotNull { it.conversationId }
                        .toSet() + activeConversationIds,
                    unreadCompletionConversationIds,
                )
            }.collect { (convList, runningConversationIds, unreadCompletionConversationIds) ->
                val items = convList.map { conv ->
                    val messages = syncManager.getMessagesSnapshot(conv.id)
                    val lastMsg = messages.lastOrNull { it.toolCallName != ConversationDispatchTool.NAME }
                        ?: syncManager.getLastMessage(conv.id)
                    val preview = lastMsg?.content?.let { content ->
                        buildConversationPreview(lastMsg.role, content)
                    }
                    ConversationListItem(
                        conversation = conv,
                        lastMessage = preview,
                        searchableText = messages
                            .filterNot { it.toolCallName == ConversationDispatchTool.NAME }
                            .joinToString("\n") { it.content.orEmpty() },
                        isRunning = conv.id in runningConversationIds,
                        hasUnreadCompletion = conv.id in unreadCompletionConversationIds,
                    )
                }
                _conversations.value = items
            }
        }
    }

    fun markConversationForegrounded(id: Long) {
        taskRunStore.markConversationForegrounded(id)
    }

    fun markConversationBackgrounded(id: Long) {
        taskRunStore.markConversationBackgrounded(id)
    }

    fun markConversationSeen(id: Long) {
        taskRunStore.markConversationSeen(id)
    }

    fun createConversation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = syncManager.createOrGetConversation("新对话")
            onCreated(id)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            syncManager.deleteConversation(id)
        }
    }

    fun deleteConversations(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            syncManager.deleteConversations(ids)
            _toastEvent.emit("已删除 ${ids.size} 个对话")
        }
    }

    fun exportConversations(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "mason",
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val file = File(downloadDir, "mason_selected_${System.currentTimeMillis()}.md")
                val success = syncManager.exportMarkdownToFile(file, ids)
                if (success) {
                    _toastEvent.emit("已导出 ${ids.size} 个对话：${file.absolutePath}")
                } else {
                    _toastEvent.emit("导出失败")
                }
            } catch (e: Exception) {
                _toastEvent.emit("导出失败：${e.message}")
            }
        }
    }
}

private fun buildConversationPreview(role: String, content: String): String {
    AutomationDraftService.extractAutomationDraftMarker(content)?.let { draft ->
        return "自动化草稿 · ${draft.name}"
    }
    AutomationDraftService.extractAutomationApplyMarker(content)?.let { result ->
        return if (result.status == "success") "自动化已保存" else "自动化状态已更新"
    }
    if (role == "tool") return "工具步骤已完成"

    val body = stripCapabilityRequirementMarkers(
        stripTaskRunMarkers(stripArtifactMarkers(content)),
    ).replace("\n", " ")
        .trim()
        .replace(
            Regex("^#{0,6}\\s*\\*{0,2}(最终总结|最后总结)[：:]\\*{0,2}\\s*"),
            "",
        )
    val roleLabel = if (role == "user") "你：" else ""
    val visible = if (body.length > 40) body.take(40) + "..." else body
    return roleLabel + visible
}
