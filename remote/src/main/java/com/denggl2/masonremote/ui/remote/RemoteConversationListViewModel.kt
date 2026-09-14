package com.denggl2.masonremote.ui.remote

import android.content.Context
import androidx.lifecycle.ViewModel
import com.denggl2.masonremote.data.AndroidDeviceIdentityStore
import com.denggl2.masonremote.data.RemotePreferences
import com.denggl2.masonremote.diagnostics.DiagnosticLog
import com.denggl2.masonremote.transport.ConnectorConversationChangePayload
import com.denggl2.masonremote.transport.ConnectorConversationCreateRequest
import com.denggl2.masonremote.transport.ConnectorConversationSummaryPayload
import com.denggl2.masonremote.transport.ConnectorComposerOptions
import com.denggl2.masonremote.transport.ConnectorExecutionStatus
import com.denggl2.masonremote.transport.PairedConnector
import com.denggl2.masonremote.transport.RemoteConnectorClient
import com.denggl2.masonremote.transport.TransportMode
import com.denggl2.masonremote.transport.isCloudXVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val EVENT_OBSERVATION_RETRY_DELAY_MILLIS = 1_000L
private const val ACTION_RETRY_DELAY_MILLIS = 350L

data class RemoteConversationSummary(
    val threadId: String,
    val title: String,
    val preview: String = "",
    val latestAttachmentNames: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val projectPath: String? = null,
    val executionStatus: RemoteExecutionStatus = RemoteExecutionStatus.IDLE,
    val latestCompletionId: String? = null,
    val isPinned: Boolean = false,
)

enum class RemoteExecutionStatus {
    IDLE,
    RUNNING,
    WAITING_FOR_PERMISSION,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

data class RemoteTaskNotificationEvent(
    val threadId: String,
    val conversationTitle: String,
    val kind: Kind,
    val detail: String? = null,
) {
    enum class Kind {
        RUNNING,
        WAITING_FOR_PERMISSION,
        COMPLETED,
        FAILED,
    }
}

data class RemoteReasoningEffortOption(
    val id: String,
    val description: String,
)

data class RemoteModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean = false,
    val defaultReasoningEffort: String,
    val supportedReasoningEfforts: List<RemoteReasoningEffortOption> = emptyList(),
)

data class RemotePermissionProfileOption(
    val id: String,
    val description: String? = null,
    val allowed: Boolean,
)

data class RemoteProjectOption(
    val path: String,
    val displayName: String,
)

data class RemoteComposerOptions(
    val projects: List<RemoteProjectOption> = emptyList(),
    val models: List<RemoteModelOption> = emptyList(),
    val permissionProfiles: List<RemotePermissionProfileOption> = emptyList(),
    val currentModelId: String? = null,
    val currentReasoningEffort: String? = null,
    val currentPermissionProfileId: String? = null,
)

data class RemoteConversationListUiState(
    val connector: TransportMode? = null,
    val connectionStatus: RemoteConnectionStatus = RemoteConnectionStatus.DISCONNECTED,
    val conversations: List<RemoteConversationSummary> = emptyList(),
    val nextCursor: String? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val mutatingThreadIds: Set<String> = emptySet(),
    val unreadCompletionThreadIds: Set<String> = emptySet(),
    val newConversationOptions: RemoteComposerOptions = RemoteComposerOptions(),
    val newConversationDraft: String = "",
    val selectedNewProjectPath: String? = null,
    val selectedNewModelId: String? = null,
    val selectedNewReasoningEffort: String? = null,
    val selectedNewPermissionProfileId: String? = null,
    val isNewConversationOptionsLoading: Boolean = false,
    val isCreatingConversation: Boolean = false,
    val createdConversationThreadId: String? = null,
    val newConversationError: String? = null,
    val isDisconnecting: Boolean = false,
    val disconnectError: String? = null,
    val operationErrorMessage: String? = null,
    val errorMessage: String? = null,
)

enum class RemoteConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

internal class RemoteConversationListViewModel(
    private val pairedConnector: PairedConnector?,
    private val appContext: Context,
    private val debugDemoMode: Boolean = false,
) : ViewModel() {
    private val isPaired = pairedConnector != null
    private val remoteScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val remotePreferences = RemotePreferences(appContext)
    private val connectorClient = pairedConnector?.let {
        RemoteConnectorClient(it, AndroidDeviceIdentityStore(), appContext)
    }
    private val completionNotificationScopeId = pairedConnector?.connectorDeviceId
    private val completionNotificationBaselineAt = completionNotificationScopeId?.let { scopeId ->
        remotePreferences.completionNotificationBaselineAt(scopeId, appInstallOrUpdateTime())
    }
    private var eventJob: Job? = null
    private var eventRevision = 0L
    private var completionNotificationSessionBaselineRevision: Long? = null
    private var hasLoadedConversationPage = debugDemoMode
    private val archivedThreadIds = mutableSetOf<String>()
    private val pendingExecutionStatuses = mutableMapOf<String, RemoteExecutionStatus>()
    private val pendingExecutionCompletionIds = mutableMapOf<String, String>()
    private val pendingExecutionCompletionRevisions = mutableMapOf<String, Long>()
    private val project = RemoteProjectOption("C:/CodexWork/CloudX", "CloudX")
    private val _uiState = MutableStateFlow(
        RemoteConversationListUiState(
            connector = pairedConnector?.transportMode
                ?: if (debugDemoMode) TransportMode.CLOUDFLARE_TUNNEL else null,
            connectionStatus = if (pairedConnector != null || debugDemoMode) {
                RemoteConnectionStatus.CONNECTING
            } else {
                RemoteConnectionStatus.DISCONNECTED
            },
            conversations = if (debugDemoMode) demoConversations() else emptyList(),
        ),
    )
    val uiState = _uiState.asStateFlow()
    private val _pairingDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pairingDisconnected = _pairingDisconnected.asSharedFlow()
    private val _notificationEvents = MutableSharedFlow<RemoteTaskNotificationEvent>(replay = 1, extraBufferCapacity = 8)
    val notificationEvents = _notificationEvents.asSharedFlow()

    fun onResume() {
        if (connectorClient != null) refresh()
    }

    fun startExecutionEventObservation() {
        if (connectorClient == null || eventJob != null) return
        eventJob = remoteScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                try {
                    val page = connectorClient.awaitConversationEvents(
                        deviceId = checkNotNull(pairedConnector).deviceId,
                        afterRevision = eventRevision,
                    )
                    consecutiveFailures = 0
                    applyEventPage(page.revision, page.changes)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    consecutiveFailures += 1
                    // A long-poll can be interrupted by a short tunnel or Wi-Fi
                    // blip. Keep the conversation page alive while the loop
                    // reconnects; only an explicit list refresh can invalidate
                    // the page. This prevents a conversation mutation from
                    // being presented as a lost pairing.
                    if (consecutiveFailures == 4) {
                        DiagnosticLog.recordException(
                            "CONVERSATION_EVENT_OBSERVATION_DEGRADED",
                            error,
                        )
                    }
                    delay(EVENT_OBSERVATION_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    fun stopExecutionEventObservation() {
        eventJob?.cancel()
        eventJob = null
    }

    fun updateRemoteExecutionStatus(
        threadId: String,
        status: RemoteExecutionStatus,
        preview: String? = null,
        detail: String? = null,
        completionId: String? = null,
        completionRevision: Long? = null,
    ) {
        pendingExecutionStatuses[threadId] = status
        if (status == RemoteExecutionStatus.COMPLETED) {
            if (!completionId.isNullOrBlank()) {
                pendingExecutionCompletionIds[threadId] = completionId
            }
            if (completionRevision != null) {
                pendingExecutionCompletionRevisions[threadId] = completionRevision
            }
        } else {
            pendingExecutionCompletionIds.remove(threadId)
            pendingExecutionCompletionRevisions.remove(threadId)
        }
        val conversation = _uiState.value.conversations.firstOrNull { it.threadId == threadId } ?: return
        val next = conversation.copy(
            executionStatus = status,
            preview = preview ?: conversation.preview,
            updatedAt = System.currentTimeMillis(),
            latestCompletionId = if (status == RemoteExecutionStatus.COMPLETED) {
                completionId ?: conversation.latestCompletionId
            } else {
                conversation.latestCompletionId
            },
        )
        updateConversation(threadId) { next }
        if (
            status == RemoteExecutionStatus.COMPLETED &&
            hasLoadedConversationPage &&
            shouldShowCompletionUnread(next)
        ) {
            _uiState.value = _uiState.value.copy(
                unreadCompletionThreadIds = _uiState.value.unreadCompletionThreadIds + threadId,
            )
        }
        val kind = when (status) {
            RemoteExecutionStatus.RUNNING -> RemoteTaskNotificationEvent.Kind.RUNNING
            RemoteExecutionStatus.WAITING_FOR_PERMISSION -> RemoteTaskNotificationEvent.Kind.WAITING_FOR_PERMISSION
            RemoteExecutionStatus.COMPLETED -> RemoteTaskNotificationEvent.Kind.COMPLETED
            RemoteExecutionStatus.FAILED -> RemoteTaskNotificationEvent.Kind.FAILED
            else -> null
        }
        kind?.let {
            _notificationEvents.tryEmit(
                RemoteTaskNotificationEvent(
                    threadId = threadId,
                    conversationTitle = conversation.title,
                    kind = it,
                    detail = detail,
                ),
            )
        }
    }

    fun refresh() {
        val client = connectorClient ?: return
        DiagnosticLog.record("CONVERSATION_LIST_REFRESH_START")
        remoteScope.launch {
            _uiState.value = _uiState.value.copy(
                isInitialLoading = _uiState.value.conversations.isEmpty(),
                isRefreshing = true,
                connectionStatus = RemoteConnectionStatus.CONNECTING,
                operationErrorMessage = null,
                errorMessage = null,
            )
            runCatching {
                retryTransientAction {
                    client.listConversations(checkNotNull(pairedConnector).deviceId)
                }
            }.onSuccess { page ->
                DiagnosticLog.record("CONVERSATION_LIST_REFRESH_SUCCESS count=${page.conversations.size} revision=${page.revision}")
                applyConversationPage(page.conversations, page.revision)
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    connectionStatus = RemoteConnectionStatus.CONNECTED,
                )
            }.onFailure { error ->
                DiagnosticLog.recordException("CONVERSATION_LIST_REFRESH_FAILURE", error)
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    connectionStatus = RemoteConnectionStatus.DISCONNECTED,
                    errorMessage = error.message ?: "无法读取电脑端会话",
                )
            }
        }
    }

    fun loadMore() = Unit
    fun retry() = refresh()
    fun markConversationSeen(threadId: String) {
        val conversation = _uiState.value.conversations.firstOrNull { it.threadId == threadId }
        val completionMarker = conversation?.completionMarker()
        if (
            completionNotificationScopeId != null &&
            conversation?.executionStatus == RemoteExecutionStatus.COMPLETED &&
            completionMarker != null
        ) {
            remotePreferences.markCompletionSeen(
                scopeId = completionNotificationScopeId,
                threadId = threadId,
                completionId = completionMarker,
            )
        }
        _uiState.value = _uiState.value.copy(
            unreadCompletionThreadIds = _uiState.value.unreadCompletionThreadIds - threadId,
        )
    }

    fun setConversationPinned(threadId: String, isPinned: Boolean) {
        val client = connectorClient
        if (client == null) {
            updateConversation(threadId) { it.copy(isPinned = isPinned) }
            return
        }
        if (threadId in _uiState.value.mutatingThreadIds) return
        _uiState.value = _uiState.value.copy(
            operationErrorMessage = null,
            mutatingThreadIds = _uiState.value.mutatingThreadIds + threadId,
        )
        remoteScope.launch {
            runCatching {
                if (isPinned) {
                    client.pinConversation(checkNotNull(pairedConnector).deviceId, threadId)
                } else {
                    client.unpinConversation(checkNotNull(pairedConnector).deviceId, threadId)
                }
            }.onSuccess { summary ->
                DiagnosticLog.record("CONVERSATION_PIN_SUCCESS threadId=$threadId isPinned=${summary.isPinned}")
                _uiState.value = _uiState.value.copy(
                    operationErrorMessage = null,
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                )
                updateConversation(threadId) { it.copy(isPinned = summary.isPinned) }
            }.onFailure { error ->
                DiagnosticLog.recordException(
                    "CONVERSATION_PIN_FAILURE threadId=$threadId isPinned=$isPinned",
                    error,
                )
                _uiState.value = _uiState.value.copy(
                    operationErrorMessage = error.message ?: "置顶操作失败",
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                )
            }
        }
    }

    fun archiveConversation(threadId: String) {
        val client = connectorClient
        if (client == null) {
            _uiState.value = _uiState.value.copy(
                conversations = _uiState.value.conversations.filterNot { it.threadId == threadId },
            )
            return
        }
        if (threadId in _uiState.value.mutatingThreadIds) return
        _uiState.value = _uiState.value.copy(
            operationErrorMessage = null,
            mutatingThreadIds = _uiState.value.mutatingThreadIds + threadId,
        )
        remoteScope.launch {
            runCatching {
                // Archive is a state-changing POST. Retrying it after an I/O
                // failure can reopen the transport and may submit the action twice.
                client.archiveConversation(checkNotNull(pairedConnector).deviceId, threadId)
            }.onSuccess {
                DiagnosticLog.record("CONVERSATION_ARCHIVE_SUCCESS threadId=$threadId")
                archivedThreadIds += threadId
                _uiState.value = _uiState.value.copy(
                    conversations = _uiState.value.conversations.filterNot { it.threadId == threadId },
                    operationErrorMessage = null,
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                )
            }.onFailure { error ->
                DiagnosticLog.recordException("CONVERSATION_ARCHIVE_FAILURE threadId=$threadId", error)
                _uiState.value = _uiState.value.copy(
                    operationErrorMessage = error.message ?: "归档操作失败",
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                )
            }
        }
    }

    fun clearDisconnectError() {
        _uiState.value = _uiState.value.copy(disconnectError = null)
    }

    fun disconnectPairing() {
        val client = connectorClient
        val connector = pairedConnector
        if (client == null || connector == null) {
            clearLocalPairing()
            return
        }

        // The phone should leave the paired state immediately. Revoking the
        // desktop session is best-effort and must not keep the confirmation
        // dialog spinning when the tunnel is slow or already gone.
        DiagnosticLog.record("PAIRING_DISCONNECT_LOCAL_FIRST deviceId=${connector.deviceId}")
        clearLocalPairing()
        remoteScope.launch {
            runCatching {
                client.revoke(connector.deviceId)
            }.onSuccess { DiagnosticLog.record("PAIRING_DISCONNECT_REMOTE_REVOKED deviceId=${connector.deviceId}") }
                .onFailure { error -> DiagnosticLog.recordException("PAIRING_DISCONNECT_REMOTE_REVOKE_FAILURE", error) }
        }
    }

    private fun clearLocalPairing() {
        _uiState.value = _uiState.value.copy(
            connector = null,
            connectionStatus = RemoteConnectionStatus.DISCONNECTED,
            conversations = emptyList(),
            isDisconnecting = false,
        )
        _pairingDisconnected.tryEmit(Unit)
    }

    override fun onCleared() {
        connectorClient?.close()
        remoteScope.cancel()
        super.onCleared()
    }

    fun loadNewConversationOptions() {
        val client = connectorClient ?: return
        _uiState.value = _uiState.value.copy(
            isNewConversationOptionsLoading = true,
            newConversationError = null,
        )
        remoteScope.launch {
            runCatching {
                client.newConversationOptions(
                    deviceId = checkNotNull(pairedConnector).deviceId,
                )
            }.onSuccess { options ->
                val uiOptions = options.toUiOptions()
                val selectedModel = uiOptions.models.firstOrNull { it.id == options.currentModelId }
                    ?: uiOptions.models.firstOrNull { it.isDefault }
                    ?: uiOptions.models.firstOrNull()
                val selectedModelId = selectedModel?.id
                val currentModelIsSelected = selectedModelId != null && selectedModelId == options.currentModelId
                val selectedPermission = options.currentPermissionProfileId
                    ?: options.permissionProfiles.firstOrNull { it.allowed }?.id
                _uiState.value = _uiState.value.copy(
                    newConversationOptions = uiOptions.copy(currentModelId = selectedModelId),
                    selectedNewProjectPath = options.cwd
                        ?: options.projects.firstOrNull()?.path,
                    selectedNewModelId = selectedModelId,
                    selectedNewReasoningEffort = options.currentReasoningEffort
                        ?.takeIf { effort ->
                            currentModelIsSelected && selectedModel?.supportedReasoningEfforts?.any { it.id == effort } == true
                        }
                        ?: selectedModel?.defaultReasoningEffort,
                    selectedNewPermissionProfileId = selectedPermission,
                    isNewConversationOptionsLoading = false,
                    newConversationError = null,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isNewConversationOptionsLoading = false,
                    newConversationError = error.message ?: "无法读取电脑端新建对话选项",
                )
            }
        }
    }

    fun updateNewConversationDraft(value: String) {
        _uiState.value = _uiState.value.copy(newConversationDraft = value)
    }

    fun selectNewProject(value: String) {
        _uiState.value = _uiState.value.copy(selectedNewProjectPath = value)
    }

    fun selectNewModel(value: String) {
        val nextModel = _uiState.value.newConversationOptions.models.firstOrNull { it.id == value }
        _uiState.value = _uiState.value.copy(
            selectedNewModelId = value,
            selectedNewReasoningEffort = nextModel?.defaultReasoningEffort,
        )
    }

    fun selectNewReasoningEffort(value: String) {
        _uiState.value = _uiState.value.copy(selectedNewReasoningEffort = value)
    }

    fun selectNewPermissionProfile(value: String) {
        _uiState.value = _uiState.value.copy(selectedNewPermissionProfileId = value)
    }

    fun createConversation() {
        val state = _uiState.value
        val client = connectorClient ?: return
        val projectPath = state.selectedNewProjectPath ?: return
        val modelId = state.selectedNewModelId ?: return
        val permissionProfileId = state.selectedNewPermissionProfileId ?: return
        if (state.newConversationDraft.isBlank() || state.isCreatingConversation) return
        _uiState.value = state.copy(
            isCreatingConversation = true,
            newConversationError = null,
        )
        remoteScope.launch {
            runCatching {
                client.createConversation(
                    deviceId = checkNotNull(pairedConnector).deviceId,
                    request = ConnectorConversationCreateRequest(
                        text = state.newConversationDraft,
                        projectPath = projectPath,
                        modelId = modelId,
                        reasoningEffort = state.selectedNewReasoningEffort,
                        permissionProfileId = permissionProfileId,
                    ),
                )
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isCreatingConversation = false,
                    newConversationDraft = "",
                    createdConversationThreadId = result.threadId,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCreatingConversation = false,
                    newConversationError = error.message ?: "无法在电脑端新建对话",
                )
            }
        }
    }

    fun consumeCreatedConversation() {
        _uiState.value = _uiState.value.copy(createdConversationThreadId = null)
    }

    private fun updateConversation(threadId: String, transform: (RemoteConversationSummary) -> RemoteConversationSummary) {
        _uiState.value = _uiState.value.copy(
            conversations = sortConversations(_uiState.value.conversations.map {
                if (it.threadId == threadId) transform(it) else it
            }),
        )
    }

    private fun applyConversationPage(
        conversations: List<ConnectorConversationSummaryPayload>,
        revision: Long,
    ) {
        val previous = _uiState.value.conversations.associateBy(RemoteConversationSummary::threadId)
        val pendingCompletionRevisions = pendingExecutionCompletionRevisions.toMap()
        val sessionBaselineRevision = completionNotificationSessionBaselineRevision ?: revision
        val next = sortConversations(conversations.map { payload ->
            val incoming = payload.toUiSummary()
            val eventStatus = pendingExecutionStatuses[incoming.threadId]
            val pendingCompletionId = pendingExecutionCompletionIds[incoming.threadId]
            val previousStatus = previous[incoming.threadId]?.executionStatus
            val resolvedStatus = when {
                incoming.executionStatus != RemoteExecutionStatus.IDLE -> {
                    pendingExecutionStatuses.remove(incoming.threadId)
                    pendingExecutionCompletionIds.remove(incoming.threadId)
                    pendingExecutionCompletionRevisions.remove(incoming.threadId)
                    incoming.executionStatus
                }
                eventStatus != null -> eventStatus
                previousStatus == RemoteExecutionStatus.RUNNING ||
                    previousStatus == RemoteExecutionStatus.WAITING_FOR_PERMISSION -> previousStatus
                else -> incoming.executionStatus
            }
            incoming.copy(
                executionStatus = resolvedStatus,
                latestCompletionId = pendingCompletionId ?: incoming.latestCompletionId,
            )
        })
            .filterNot { it.threadId in archivedThreadIds }
        val newlyCompleted = completionUnreadThreads(
            next = next,
            previous = previous,
            baselineRevision = sessionBaselineRevision,
            pendingCompletionRevisions = pendingCompletionRevisions,
        )
        eventRevision = maxOf(eventRevision, revision)
        _uiState.value = _uiState.value.copy(
            conversations = next,
            nextCursor = null,
            unreadCompletionThreadIds = _uiState.value.unreadCompletionThreadIds + newlyCompleted,
        )
        completionNotificationSessionBaselineRevision = sessionBaselineRevision
        hasLoadedConversationPage = true
    }

    private fun applyEventPage(
        revision: Long,
        changes: List<ConnectorConversationChangePayload>,
    ) {
        val sessionBaselineRevision = completionNotificationSessionBaselineRevision
        val newChanges = changes.filter { change ->
            change.revision > eventRevision &&
                (sessionBaselineRevision == null || change.revision > sessionBaselineRevision)
        }
        eventRevision = maxOf(eventRevision, revision)
        newChanges.forEach { change ->
            updateRemoteExecutionStatus(
                threadId = change.threadId,
                status = change.status.toUiStatus(),
                completionId = change.turnId,
                completionRevision = change.revision,
            )
        }
    }

    private fun ConnectorConversationSummaryPayload.toUiSummary() = RemoteConversationSummary(
        threadId = threadId,
        title = title,
        preview = preview,
        latestAttachmentNames = latestAttachmentNames,
        updatedAt = updatedAt,
        projectPath = projectPath,
        executionStatus = executionStatus.toUiStatus(),
        latestCompletionId = latestCompletionId,
        isPinned = isPinned,
    )

    private fun completionUnreadThreads(
        next: List<RemoteConversationSummary>,
        previous: Map<String, RemoteConversationSummary>,
        baselineRevision: Long,
        pendingCompletionRevisions: Map<String, Long>,
    ): Set<String> {
        val scopeId = completionNotificationScopeId
        if (scopeId == null) {
            return next.asSequence()
                .filter { it.executionStatus == RemoteExecutionStatus.COMPLETED }
                .filter { previous[it.threadId]?.executionStatus != RemoteExecutionStatus.COMPLETED }
                .map(RemoteConversationSummary::threadId)
                .toSet()
        }

        // Seed existing completed threads once; only completions after the app
        // was installed or updated can enter the unread set on that first sync.
        val baselineSeeded = remotePreferences.isCompletionNotificationBaselineSeeded(scopeId)
        val baselineAt = completionNotificationBaselineAt ?: System.currentTimeMillis()
        val unread = mutableSetOf<String>()
        next.asSequence()
            .filter { it.executionStatus == RemoteExecutionStatus.COMPLETED }
            .forEach { conversation ->
                val marker = conversation.completionMarker()
                if (marker == null) {
                    if (
                        baselineSeeded &&
                        previous[conversation.threadId]?.let {
                            it.executionStatus != RemoteExecutionStatus.COMPLETED
                        } == true
                    ) {
                        unread += conversation.threadId
                    }
                    return@forEach
                }
                val seenMarker = remotePreferences.lastSeenCompletionId(scopeId, conversation.threadId)
                if (!baselineSeeded) {
                    if (
                        conversation.isCompletedAfter(baselineAt)
                    ) {
                        unread += conversation.threadId
                    } else {
                        remotePreferences.markCompletionSeen(scopeId, conversation.threadId, marker)
                    }
                } else if (marker != seenMarker) {
                    val completionArrivedAfterSessionStarted =
                        pendingCompletionRevisions[conversation.threadId]?.let { it > baselineRevision } == true
                    if (
                        seenMarker == null &&
                        !completionArrivedAfterSessionStarted &&
                        !conversation.isCompletedAfter(baselineAt)
                    ) {
                        remotePreferences.markCompletionSeen(scopeId, conversation.threadId, marker)
                    } else {
                        unread += conversation.threadId
                    }
                }
            }
        if (!baselineSeeded) {
            remotePreferences.markCompletionNotificationBaselineSeeded(scopeId)
        }
        return unread
    }

    private fun shouldShowCompletionUnread(conversation: RemoteConversationSummary): Boolean {
        if (conversation.executionStatus != RemoteExecutionStatus.COMPLETED) return false
        val scopeId = completionNotificationScopeId ?: return true
        val marker = conversation.completionMarker() ?: return true
        return remotePreferences.lastSeenCompletionId(scopeId, conversation.threadId) != marker
    }

    private fun RemoteConversationSummary.completionMarker(): String? =
        latestCompletionId?.takeIf(String::isNotBlank)
            ?: updatedAt.takeIf { it > 0 }?.let { "updatedAt:$it" }

    private fun RemoteConversationSummary.isCompletedAfter(baselineAt: Long): Boolean =
        updatedAt > 0 && updatedAt > baselineAt

    private fun appInstallOrUpdateTime(): Long = runCatching {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .lastUpdateTime
    }.getOrDefault(System.currentTimeMillis())

    private fun ConnectorComposerOptions.toUiOptions() = RemoteComposerOptions(
        projects = projects.map { RemoteProjectOption(it.path, it.displayName) },
        models = models.filter { it.isCloudXVisible() }.map { model ->
            RemoteModelOption(
                id = model.id,
                model = model.model,
                displayName = model.displayName,
                description = model.description,
                isDefault = model.isDefault,
                defaultReasoningEffort = model.defaultReasoningEffort,
                supportedReasoningEfforts = model.supportedReasoningEfforts.map {
                    RemoteReasoningEffortOption(it.id, it.description)
                },
            )
        },
        permissionProfiles = permissionProfiles.map {
            RemotePermissionProfileOption(it.id, it.description, it.allowed)
        },
        currentModelId = currentModelId,
        currentReasoningEffort = currentReasoningEffort,
        currentPermissionProfileId = currentPermissionProfileId,
    )

    private fun sortConversations(
        conversations: List<RemoteConversationSummary>,
    ): List<RemoteConversationSummary> = conversations
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<RemoteConversationSummary>> { it.value.updatedAt }
                // If an older Codex history entry has no timestamp, retain the
                // connector's already-recent-first order instead of sorting by ID.
                .thenBy { it.index },
        )
        .map(IndexedValue<RemoteConversationSummary>::value)

    private suspend fun <T> retryTransientAction(action: suspend () -> T): T {
        return try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!error.hasTransientTransportCause()) throw error
            delay(ACTION_RETRY_DELAY_MILLIS)
            action()
        }
    }

    private fun Throwable.hasTransientTransportCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is java.io.IOException }

    private fun ConnectorExecutionStatus.toUiStatus(): RemoteExecutionStatus = when (this) {
        ConnectorExecutionStatus.IDLE -> RemoteExecutionStatus.IDLE
        ConnectorExecutionStatus.RUNNING -> RemoteExecutionStatus.RUNNING
        ConnectorExecutionStatus.WAITING_FOR_APPROVAL -> RemoteExecutionStatus.WAITING_FOR_PERMISSION
        ConnectorExecutionStatus.COMPLETED -> RemoteExecutionStatus.COMPLETED
        ConnectorExecutionStatus.INTERRUPTED -> RemoteExecutionStatus.INTERRUPTED
        ConnectorExecutionStatus.FAILED -> RemoteExecutionStatus.FAILED
        ConnectorExecutionStatus.UNKNOWN -> RemoteExecutionStatus.IDLE
    }

    private fun demoConversations(): List<RemoteConversationSummary> {
        val now = System.currentTimeMillis()
        return listOf(
            RemoteConversationSummary(
                threadId = "demo-waiting",
                title = "等待确认远程命令",
                preview = "电脑端正在等待你的允许",
                updatedAt = now - 1 * 60_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.WAITING_FOR_PERMISSION,
            ),
            RemoteConversationSummary(
                threadId = "demo-running",
                title = "整理 CloudX 远程控制项目",
                preview = "正在扫描工作区，等待电脑端返回任务进度",
                updatedAt = now - 2 * 60_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.RUNNING,
            ),
            RemoteConversationSummary(
                threadId = "demo-completed-unread",
                title = "实现扫码配对流程",
                preview = "已完成：配对界面和连接状态展示",
                updatedAt = now - 18 * 60_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.COMPLETED,
            ),
            RemoteConversationSummary(
                threadId = "demo-idle",
                title = "检查 Windows Connector 状态",
                preview = "等待下一步指令",
                updatedAt = now - 52 * 60_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.IDLE,
            ),
            RemoteConversationSummary(
                threadId = "demo-completed",
                title = "同步设置页面",
                preview = "已完成：外观、通知和设备配对设置",
                updatedAt = now - 4 * 3_600_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.COMPLETED,
            ),
            RemoteConversationSummary(
                threadId = "demo-failed",
                title = "修复扫码连接超时",
                preview = "任务失败：未收到电脑端响应",
                updatedAt = now - 26 * 3_600_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.FAILED,
            ),
            RemoteConversationSummary(
                threadId = "demo-interrupted",
                title = "测试远程任务恢复",
                preview = "已中断：可以从最近消息继续",
                updatedAt = now - 2 * 86_400_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.INTERRUPTED,
            ),
            RemoteConversationSummary(
                threadId = "demo-archived",
                title = "整理项目文档",
                preview = "已完成项目结构检查",
                updatedAt = now - 6 * 86_400_000L,
                projectPath = project.path,
                executionStatus = RemoteExecutionStatus.COMPLETED,
            ),
        )
    }
}
