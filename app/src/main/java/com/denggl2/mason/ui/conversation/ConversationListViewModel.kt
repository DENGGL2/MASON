package com.denggl2.mason.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Environment
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.stripModelParticipationMarkers
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.automation.AutomationDraftService
import com.denggl2.mason.integration.stripCapabilityRequirementMarkers
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.sync.remote.PairedConnector
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedConnectorClient
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
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

data class RemoteConversationListUiState(
    val connector: PairedConnector? = null,
    val expanded: Boolean = false,
    val conversations: List<RemoteConversationSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isReachable: Boolean? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val taskRunStore: TaskRunStore,
    private val connectorStore: PairedConnectorStore,
    configDataStore: ApiConfigDataStore,
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationListItem>>(emptyList())
    val conversations: StateFlow<List<ConversationListItem>> = _conversations.asStateFlow()
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()
    private val _remoteConversations = MutableStateFlow(RemoteConversationListUiState())
    val remoteConversations: StateFlow<RemoteConversationListUiState> = _remoteConversations.asStateFlow()
    val apiConfig: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    init {
        viewModelScope.launch {
            connectorStore.connector.collect { connector ->
                _remoteConversations.value = RemoteConversationListUiState(connector = connector)
            }
        }
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
                        .filter { isConversationProgressActive(it.status) }
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

    fun toggleRemoteConversations() {
        val current = _remoteConversations.value
        if (current.connector == null) return
        if (current.expanded) {
            _remoteConversations.value = current.copy(expanded = false)
            return
        }
        _remoteConversations.value = current.copy(expanded = true)
        if (current.conversations.isEmpty()) loadRemoteConversationPage(reset = true)
    }

    fun loadMoreRemoteConversations() {
        val current = _remoteConversations.value
        if (!current.expanded || current.isLoading || current.nextCursor == null) return
        loadRemoteConversationPage(reset = false)
    }

    fun retryRemoteConversations() {
        val current = _remoteConversations.value
        if (current.isLoading) return
        loadRemoteConversationPage(reset = current.conversations.isEmpty())
    }

    private fun loadRemoteConversationPage(reset: Boolean) {
        val current = _remoteConversations.value
        val connector = current.connector ?: return
        val cursor = if (reset) null else current.nextCursor ?: return
        _remoteConversations.value = current.copy(
            conversations = if (reset) emptyList() else current.conversations,
            nextCursor = if (reset) null else current.nextCursor,
            isLoading = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                val deviceId = syncManager.getLocalDeviceId()
                PinnedConnectorClient(connector, AndroidDeviceIdentityStore())
                    .listConversations(deviceId = deviceId, limit = REMOTE_PAGE_SIZE, cursor = cursor)
            }.onSuccess { page ->
                val latest = _remoteConversations.value
                if (latest.connector != connector) return@onSuccess
                val combined = if (reset) page.conversations else latest.conversations + page.conversations
                _remoteConversations.value = latest.copy(
                    conversations = combined.distinctBy(RemoteConversationSummary::threadId),
                    nextCursor = page.nextCursor,
                    isLoading = false,
                    isReachable = true,
                    errorMessage = null,
                )
            }.onFailure {
                val latest = _remoteConversations.value
                if (latest.connector != connector) return@onFailure
                _remoteConversations.value = latest.copy(
                    isLoading = false,
                    isReachable = false,
                    errorMessage = "电脑当前不可连接，点击重试",
                )
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

internal fun isConversationProgressActive(status: TaskRunStatus): Boolean =
    status == TaskRunStatus.Running

private const val REMOTE_PAGE_SIZE = 3

private fun buildConversationPreview(role: String, content: String): String {
    AutomationDraftService.extractAutomationDraftMarker(content)?.let { draft ->
        return "自动化草稿 · ${draft.name}"
    }
    AutomationDraftService.extractAutomationApplyMarker(content)?.let { result ->
        return if (result.status == "success") "自动化已保存" else "自动化状态已更新"
    }
    if (role == "tool") return "工具步骤已完成"

    val body = stripCapabilityRequirementMarkers(
        stripTaskRunMarkers(stripArtifactMarkers(stripModelParticipationMarkers(content))),
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
