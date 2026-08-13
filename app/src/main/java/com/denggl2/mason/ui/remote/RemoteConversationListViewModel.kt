package com.denggl2.mason.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.remote.PairedConnector
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedConnectorClient
import com.denggl2.mason.sync.remote.RemotePairingException
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RemoteConversationListUiState(
    val connector: PairedConnector? = null,
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
    val errorMessage: String? = null,
)

@HiltViewModel
class RemoteConversationListViewModel @Inject constructor(
    private val connectorStore: PairedConnectorStore,
    private val syncManager: SyncManager,
    private val readStore: RemoteConversationReadStore,
    private val draftStore: RemoteConversationDraftStore,
    private val composerOptionsStore: RemoteComposerOptionsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteConversationListUiState())
    val uiState: StateFlow<RemoteConversationListUiState> = _uiState.asStateFlow()
    private val _pairingDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pairingDisconnected = _pairingDisconnected.asSharedFlow()

    private var requestJob: Job? = null
    private var newConversationOptionsJob: Job? = null
    private var newConversationOptionsGeneration = 0L
    private var executionEventsJob: Job? = null
    private var executionEventObservationRequested = false
    private var requestGeneration = 0L
    private var executionEventRevision = 0L
    private var cachedConnector: PairedConnector? = null
    private var cachedClient: PinnedConnectorClient? = null
    private var cachedDeviceId: String? = null
    private var lastFailedLoad: RemoteListLoad? = null
    private var hasResumedOnce = false
    private var newProjectSelectedByUser = false
    private var newModelSelectedByUser = false
    private var newReasoningSelectedByUser = false
    private var newPermissionSelectedByUser = false
    private val hydrationJobs = mutableMapOf<String, Job>()
    private val hydrationSemaphore = Semaphore(HYDRATION_CONCURRENCY)
    private var hydrationGeneration = 0L

    init {
        viewModelScope.launch {
            connectorStore.connector.collect(::switchConnector)
        }
        viewModelScope.launch {
            readStore.seenCompletionVersions.collect {
                _uiState.value = _uiState.value.withUnreadCompletions()
            }
        }
    }

    fun onResume() {
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        refresh()
    }

    fun startExecutionEventObservation() {
        executionEventObservationRequested = true
        if (executionEventsJob?.isActive == true) return
        val connector = _uiState.value.connector ?: return
        executionEventsJob = viewModelScope.launch {
            while (isActive && _uiState.value.connector == connector) {
                try {
                    val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                        cachedDeviceId = it
                    }
                    val events = remoteClient(connector).awaitConversationEvents(
                        deviceId = deviceId,
                        afterRevision = executionEventRevision,
                    )
                    if (_uiState.value.connector != connector) return@launch
                    executionEventRevision = maxOf(executionEventRevision, events.revision)
                    events.changes
                        .sortedBy { it.revision }
                        .forEach { change -> applyExecutionChange(connector, deviceId, change) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    delay(EVENT_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    fun stopExecutionEventObservation() {
        executionEventObservationRequested = false
        cancelExecutionEventObservation()
    }

    private fun cancelExecutionEventObservation() {
        executionEventsJob?.cancel()
        executionEventsJob = null
    }

    fun refresh() {
        val state = _uiState.value
        if (state.connector == null || state.isInitialLoading || state.isRefreshing) return
        startLoad(RemoteListLoad.Refresh)
    }

    fun loadMore() {
        val state = _uiState.value
        if (
            state.connector == null ||
            state.nextCursor == null ||
            state.isInitialLoading ||
            state.isRefreshing ||
            state.isAppending ||
            lastFailedLoad == RemoteListLoad.Append
        ) return
        startLoad(RemoteListLoad.Append)
    }

    fun markConversationSeen(threadId: String) {
        val state = _uiState.value
        val connectorDeviceId = state.connector?.connectorDeviceId ?: return
        val conversation = state.conversations.firstOrNull { it.threadId == threadId } ?: return
        readStore.markCompletionSeen(connectorDeviceId, conversation)
    }

    fun setConversationPinned(threadId: String, isPinned: Boolean) {
        mutateConversation(threadId, removeOnSuccess = false) { client, deviceId ->
            if (isPinned) {
                client.pinConversation(deviceId, threadId)
            } else {
                client.unpinConversation(deviceId, threadId)
            }
        }
    }

    fun archiveConversation(threadId: String) {
        mutateConversation(threadId, removeOnSuccess = true) { client, deviceId ->
            client.archiveConversation(deviceId, threadId)
        }
    }

    fun clearDisconnectError() {
        val state = _uiState.value
        if (!state.isDisconnecting && state.disconnectError != null) {
            _uiState.value = state.copy(disconnectError = null)
        }
    }

    fun disconnectPairing() {
        val state = _uiState.value
        val connector = state.connector ?: return
        if (state.isDisconnecting) return

        _uiState.value = state.copy(
            isDisconnecting = true,
            disconnectError = null,
        )
        viewModelScope.launch {
            try {
                val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                    cachedDeviceId = it
                }
                try {
                    remoteClient(connector).revoke(deviceId)
                } catch (error: RemotePairingException) {
                    if (!remoteRevocationAlreadyFinal(error.errorCode)) throw error
                }

                // Do not clear a connector that was replaced while the request was in flight.
                if (connectorStore.load() != connector) return@launch
                try {
                    connectorStore.clear()
                } catch (_: Throwable) {
                    _uiState.value = _uiState.value.copy(
                        isDisconnecting = false,
                        disconnectError = "电脑端已断开，本机信息清理失败，请重试",
                    )
                    return@launch
                }
                _pairingDisconnected.emit(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (_uiState.value.connector != connector) return@launch
                _uiState.value = _uiState.value.copy(
                    isDisconnecting = false,
                    disconnectError = "断开失败，请检查连接后重试",
                )
            }
        }
    }

    private fun mutateConversation(
        threadId: String,
        removeOnSuccess: Boolean,
        mutation: suspend (PinnedConnectorClient, String) -> RemoteConversationSummary,
    ) {
        val state = _uiState.value
        val connector = state.connector ?: return
        if (threadId in state.mutatingThreadIds) return
        _uiState.value = state.copy(
            mutatingThreadIds = state.mutatingThreadIds + threadId,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                    cachedDeviceId = it
                }
                val result = mutation(remoteClient(connector), deviceId)
                if (_uiState.value.connector != connector) return@launch
                _uiState.value = _uiState.value.copy(
                    conversations = if (removeOnSuccess) {
                        _uiState.value.conversations.filterNot { it.threadId == threadId }
                    } else {
                        _uiState.value.conversations.map { conversation ->
                            if (conversation.threadId == threadId) result else conversation
                        }
                    },
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                    errorMessage = null,
                ).withUnreadCompletions()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (_uiState.value.connector != connector) return@launch
                _uiState.value = _uiState.value.copy(
                    mutatingThreadIds = _uiState.value.mutatingThreadIds - threadId,
                    errorMessage = error.remoteActionMessage(),
                )
            }
        }
    }

    fun loadNewConversationOptions() {
        val state = _uiState.value
        val connector = state.connector ?: return
        if (
            state.isNewConversationOptionsLoading ||
            state.isCreatingConversation ||
            newConversationOptionsJob?.isActive == true
        ) return
        requestNewConversationOptions(connector = connector, projectPath = null)
    }

    private fun requestNewConversationOptions(
        connector: PairedConnector,
        projectPath: String?,
    ) {
        val generation = ++newConversationOptionsGeneration
        newConversationOptionsJob?.cancel()
        val hasCompleteCachedOptions = _uiState.value.newConversationOptions
            .hasCompleteNewConversationOptions()
        _uiState.value = _uiState.value.copy(
            isNewConversationOptionsLoading = projectPath != null || !hasCompleteCachedOptions,
            newConversationError = null,
        )
        newConversationOptionsJob = viewModelScope.launch {
            try {
                val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                    cachedDeviceId = it
                }
                val freshOptions = remoteClient(connector).newConversationOptions(deviceId, projectPath)
                if (
                    generation != newConversationOptionsGeneration ||
                    _uiState.value.connector != connector
                ) return@launch
                val options = composerOptionsStore.mergeAndWrite(
                    connector.connectorDeviceId,
                    freshOptions,
                )
                val latest = _uiState.value
                val selectedProjectPath = resolveRemoteProjectPath(
                    options = options,
                    authoritativeProjectPath = freshOptions.cwd,
                    selectedProjectPath = latest.selectedNewProjectPath,
                    preserveUserSelection = newProjectSelectedByUser,
                )
                val selectedModel = resolveRemoteModel(
                    options = options,
                    authoritativeModelId = freshOptions.currentModelId,
                    selectedModelId = latest.selectedNewModelId,
                    preserveUserSelection = newModelSelectedByUser,
                )
                val selectedEffort = resolveRemoteReasoningEffort(
                    model = selectedModel,
                    authoritativeEffort = freshOptions.currentReasoningEffort,
                    selectedEffort = latest.selectedNewReasoningEffort,
                    preserveUserSelection = newReasoningSelectedByUser,
                )
                val selectedPermission = resolveRemotePermissionProfile(
                    options = options,
                    authoritativePermissionId = freshOptions.currentPermissionProfileId,
                    selectedPermissionId = latest.selectedNewPermissionProfileId,
                    preserveUserSelection = newPermissionSelectedByUser,
                )
                _uiState.value = _uiState.value.copy(
                    newConversationOptions = options,
                    selectedNewProjectPath = selectedProjectPath,
                    selectedNewModelId = selectedModel?.id,
                    selectedNewReasoningEffort = selectedEffort,
                    selectedNewPermissionProfileId = selectedPermission,
                    isNewConversationOptionsLoading = false,
                    newConversationError = null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (
                    generation != newConversationOptionsGeneration ||
                    _uiState.value.connector != connector
                ) return@launch
                _uiState.value = _uiState.value.copy(
                    isNewConversationOptionsLoading = false,
                    newConversationError = error.remoteListMessage(),
                )
            }
        }
    }

    fun updateNewConversationDraft(value: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            newConversationDraft = value,
            newConversationError = null,
        )
        state.connector?.connectorDeviceId?.let { draftStore.write(it, value) }
    }

    fun selectNewProject(projectPath: String) {
        val state = _uiState.value
        val connector = state.connector ?: return
        if (!shouldSelectRemoteProject(state.newConversationOptions, state.selectedNewProjectPath, projectPath)) return
        _uiState.value = state.copy(
            selectedNewProjectPath = projectPath,
            selectedNewModelId = null,
            selectedNewReasoningEffort = null,
            selectedNewPermissionProfileId = null,
            newConversationError = null,
        )
        newProjectSelectedByUser = true
        newModelSelectedByUser = false
        newReasoningSelectedByUser = false
        newPermissionSelectedByUser = false
        requestNewConversationOptions(connector = connector, projectPath = projectPath)
    }

    fun selectNewModel(modelId: String) {
        val state = _uiState.value
        val model = state.newConversationOptions.models.firstOrNull { it.id == modelId } ?: return
        _uiState.value = state.copy(
            selectedNewModelId = model.id,
            selectedNewReasoningEffort = model.defaultReasoningEffort
                .takeIf { effort -> model.supportedReasoningEfforts.any { it.id == effort } }
                ?: model.supportedReasoningEfforts.firstOrNull()?.id,
            newConversationError = null,
        )
        newModelSelectedByUser = true
        newReasoningSelectedByUser = true
    }

    fun selectNewReasoningEffort(effort: String) {
        val state = _uiState.value
        val model = state.newConversationOptions.models
            .firstOrNull { it.id == state.selectedNewModelId } ?: return
        if (model.supportedReasoningEfforts.none { it.id == effort }) return
        _uiState.value = state.copy(
            selectedNewReasoningEffort = effort,
            newConversationError = null,
        )
        newReasoningSelectedByUser = true
    }

    fun selectNewPermissionProfile(profileId: String) {
        val state = _uiState.value
        if (state.newConversationOptions.permissionProfiles.none { it.id == profileId && it.allowed }) return
        _uiState.value = state.copy(
            selectedNewPermissionProfileId = profileId,
            newConversationError = null,
        )
        newPermissionSelectedByUser = true
    }

    fun createConversation() {
        val state = _uiState.value
        val connector = state.connector ?: return
        val projectPath = state.selectedNewProjectPath ?: return
        val modelId = state.selectedNewModelId ?: return
        val permissionId = state.selectedNewPermissionProfileId ?: return
        val text = state.newConversationDraft.trim()
        if (text.isEmpty() || state.isCreatingConversation) return
        _uiState.value = state.copy(isCreatingConversation = true, newConversationError = null)
        viewModelScope.launch {
            runCatching {
                val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                    cachedDeviceId = it
                }
                remoteClient(connector).createConversation(
                    deviceId = deviceId,
                    request = RemoteConversationCreateRequest(
                        text = text,
                        projectPath = projectPath,
                        modelId = modelId,
                        reasoningEffort = state.selectedNewReasoningEffort,
                        permissionProfileId = permissionId,
                    ),
                )
            }.onSuccess { result ->
                if (_uiState.value.connector != connector) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    isCreatingConversation = false,
                    createdConversationThreadId = result.threadId,
                    newConversationDraft = "",
                    newConversationError = null,
                )
                draftStore.write(connector.connectorDeviceId, "")
            }.onFailure { error ->
                if (_uiState.value.connector != connector) return@onFailure
                _uiState.value = _uiState.value.copy(
                    isCreatingConversation = false,
                    newConversationError = error.remoteListMessage(),
                )
            }
        }
    }

    fun consumeCreatedConversation() {
        _uiState.value = _uiState.value.copy(createdConversationThreadId = null)
    }

    fun retry() {
        when (lastFailedLoad) {
            RemoteListLoad.Initial -> startLoad(RemoteListLoad.Initial)
            RemoteListLoad.Refresh -> startLoad(RemoteListLoad.Refresh)
            RemoteListLoad.Append -> startLoad(RemoteListLoad.Append)
            null -> if (_uiState.value.conversations.isEmpty()) {
                startLoad(RemoteListLoad.Initial)
            } else {
                startLoad(RemoteListLoad.Refresh)
            }
        }
    }

    private fun switchConnector(connector: PairedConnector?) {
        if (connector == _uiState.value.connector) return
        cancelExecutionEventObservation()
        executionEventRevision = 0L
        requestGeneration += 1
        newConversationOptionsGeneration += 1
        requestJob?.cancel()
        requestJob = null
        newConversationOptionsJob?.cancel()
        newConversationOptionsJob = null
        cachedConnector = null
        cachedClient = null
        lastFailedLoad = null
        cancelHydration()
        val cachedOptions = connector?.connectorDeviceId?.let(composerOptionsStore::read)
        val cachedProject = cachedOptions?.projects?.firstOrNull()
        val cachedModel = cachedOptions?.models?.firstOrNull { it.isDefault }
            ?: cachedOptions?.models?.firstOrNull()
        newProjectSelectedByUser = false
        newModelSelectedByUser = false
        newReasoningSelectedByUser = false
        newPermissionSelectedByUser = false
        _uiState.value = RemoteConversationListUiState(
            connector = connector,
            newConversationDraft = connector?.connectorDeviceId?.let(draftStore::read).orEmpty(),
            newConversationOptions = cachedOptions ?: RemoteComposerOptions(),
            selectedNewProjectPath = cachedProject?.path,
            selectedNewModelId = cachedModel?.id,
            selectedNewReasoningEffort = cachedModel?.defaultReasoningEffort
                    ?.takeIf { effort ->
                        cachedModel.supportedReasoningEfforts.any { it.id == effort }
                    }
                ?: cachedModel?.supportedReasoningEfforts?.firstOrNull()?.id,
            selectedNewPermissionProfileId = cachedOptions?.permissionProfiles
                ?.firstOrNull { it.allowed }
                ?.id,
        )
        if (connector != null) {
            startLoad(RemoteListLoad.Initial)
            if (executionEventObservationRequested) startExecutionEventObservation()
        }
    }

    private fun startLoad(load: RemoteListLoad) {
        val state = _uiState.value
        val connector = state.connector ?: return
        val cursor = when (load) {
            RemoteListLoad.Append -> state.nextCursor ?: return
            RemoteListLoad.Initial,
            RemoteListLoad.Refresh -> null
        }

        when (load) {
            RemoteListLoad.Initial -> {
                if (state.isInitialLoading || state.isRefreshing || state.isAppending) return
            }
            RemoteListLoad.Refresh -> {
                if (state.isInitialLoading || state.isRefreshing) return
                requestJob?.cancel()
            }
            RemoteListLoad.Append -> {
                if (state.isInitialLoading || state.isRefreshing || state.isAppending) return
            }
        }

        val generation = ++requestGeneration
        if (load != RemoteListLoad.Append) cancelHydration()
        lastFailedLoad = null
        _uiState.value = state.copy(
            isInitialLoading = load == RemoteListLoad.Initial,
            isRefreshing = load == RemoteListLoad.Refresh,
            isAppending = load == RemoteListLoad.Append,
            errorMessage = null,
        )
        requestJob = viewModelScope.launch {
            try {
                val client = remoteClient(connector)
                val deviceId = cachedDeviceId ?: syncManager.getLocalDeviceId().also {
                    cachedDeviceId = it
                }
                val page = client.listConversations(
                    deviceId = deviceId,
                    limit = REMOTE_PAGE_SIZE,
                    cursor = cursor,
                )
                if (generation != requestGeneration || _uiState.value.connector != connector) return@launch
                executionEventRevision = maxOf(executionEventRevision, page.revision)

                val latest = _uiState.value
                val conversations = when (load) {
                    RemoteListLoad.Append -> latest.conversations + page.conversations
                    RemoteListLoad.Initial,
                    RemoteListLoad.Refresh -> page.conversations
                }.distinctBy(RemoteConversationSummary::threadId)
                _uiState.value = latest.copy(
                    conversations = conversations,
                    nextCursor = page.nextCursor,
                    isInitialLoading = false,
                    isRefreshing = false,
                    isAppending = false,
                    errorMessage = null,
                ).withUnreadCompletions()
                hydratePage(
                    connector = connector,
                    deviceId = deviceId,
                    conversations = page.conversations,
                    generation = hydrationGeneration,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (generation != requestGeneration || _uiState.value.connector != connector) return@launch
                lastFailedLoad = load
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isAppending = false,
                    errorMessage = error.remoteListMessage(),
                )
            }
        }
    }

    private fun remoteClient(connector: PairedConnector): PinnedConnectorClient {
        if (cachedConnector == connector) cachedClient?.let { return it }
        return PinnedConnectorClient(connector, AndroidDeviceIdentityStore()).also { client ->
            cachedConnector = connector
            cachedClient = client
        }
    }

    private fun applyExecutionChange(
        connector: PairedConnector,
        deviceId: String,
        change: com.denggl2.mason.protocol.RemoteConversationExecutionChange,
    ) {
        val current = _uiState.value.conversations.firstOrNull { it.threadId == change.threadId }
            ?: return
        _uiState.value = _uiState.value.copy(
            conversations = _uiState.value.conversations.map { item ->
                if (item.threadId == change.threadId) {
                    item.copy(
                        executionStatus = change.status,
                        latestCompletionId = change.turnId.takeIf {
                            change.status == RemoteExecutionStatus.COMPLETED
                        } ?: item.latestCompletionId,
                    )
                } else {
                    item
                }
            },
        ).withUnreadCompletions()
        if (change.status == RemoteExecutionStatus.RUNNING) return

        hydrationJobs.remove(change.threadId)?.cancel()
        hydratePage(
            connector = connector,
            deviceId = deviceId,
            conversations = listOf(current),
            generation = hydrationGeneration,
        )
    }

    private fun hydratePage(
        connector: PairedConnector,
        deviceId: String,
        conversations: List<RemoteConversationSummary>,
        generation: Long,
    ) {
        conversations.forEach { summary ->
            if (hydrationJobs[summary.threadId]?.isActive == true) return@forEach
            lateinit var job: Job
            job = viewModelScope.launch {
                try {
                    val detail = hydrationSemaphore.withPermit {
                        remoteClient(connector).readConversation(
                            deviceId = deviceId,
                            threadId = summary.threadId,
                        )
                    }
                    if (
                        generation != hydrationGeneration ||
                        _uiState.value.connector != connector
                    ) return@launch
                    val latestPreview = detail.messages.lastOrNull()
                        ?.text
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                    _uiState.value = _uiState.value.copy(
                        conversations = _uiState.value.conversations.map { item ->
                            if (item.threadId == summary.threadId) {
                                item.copy(
                                    preview = latestPreview ?: item.preview,
                                    executionStatus = detail.executionStatus,
                                    latestCompletionId = detail.conversation.latestCompletionId,
                                )
                            } else {
                                item
                            }
                        },
                    ).withUnreadCompletions()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // The list summary remains usable if one detail cannot be hydrated.
                } finally {
                    if (hydrationJobs[summary.threadId] === job) {
                        hydrationJobs.remove(summary.threadId)
                    }
                }
            }
            hydrationJobs[summary.threadId] = job
        }
    }

    private fun cancelHydration() {
        hydrationGeneration += 1
        val jobsToCancel = hydrationJobs.values.toList()
        hydrationJobs.clear()
        jobsToCancel.forEach(Job::cancel)
    }

    private fun RemoteConversationListUiState.withUnreadCompletions(): RemoteConversationListUiState {
        val connectorDeviceId = connector?.connectorDeviceId
            ?: return copy(unreadCompletionThreadIds = emptySet())
        return copy(
            unreadCompletionThreadIds = conversations
                .asSequence()
                .filter { readStore.isCompletionUnread(connectorDeviceId, it) }
                .map(RemoteConversationSummary::threadId)
                .toSet(),
        )
    }

    private fun Throwable.remoteListMessage(): String = when (
        (this as? RemotePairingException)?.errorCode
    ) {
        "DEVICE_REVOKED", "SESSION_REVOKED" -> "远端电脑连接已失效"
        else -> message?.takeIf(String::isNotBlank) ?: "无法加载远端会话"
    }

    private fun Throwable.remoteActionMessage(): String = when (
        (this as? RemotePairingException)?.errorCode
    ) {
        "DEVICE_REVOKED", "SESSION_REVOKED" -> "远端电脑连接已失效"
        "CODEX_APP_SERVER_ERROR" -> "电脑端 Codex 暂不支持这项操作"
        else -> message?.takeIf(String::isNotBlank) ?: "无法完成会话操作"
    }

    private enum class RemoteListLoad {
        Initial,
        Refresh,
        Append,
    }

    private companion object {
        const val REMOTE_PAGE_SIZE = 20
        const val HYDRATION_CONCURRENCY = 4
        const val EVENT_RETRY_DELAY_MILLIS = 1_500L
    }
}

internal fun remoteRevocationAlreadyFinal(errorCode: String?): Boolean = errorCode in setOf(
    "DEVICE_REVOKED",
    "DEVICE_NOT_PAIRED",
    "SESSION_REVOKED",
)
