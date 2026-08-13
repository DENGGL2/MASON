package com.denggl2.mason.ui.settings

import com.denggl2.mason.AppForegroundState
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class RemoteModelDiscoveryUiState(
    val isTesting: Boolean = false,
    val targetConnection: ApiConnection? = null,
    val modelListingAvailable: Boolean? = null,
    val models: List<AiModelPreset> = emptyList(),
    val modelCapabilities: Map<String, ApiModelCapabilities> = emptyMap(),
    val modelTestErrors: Map<String, String> = emptyMap(),
    val verifiedModelSignatures: Map<String, String> = emptyMap(),
    val activeModelIds: Set<String> = emptySet(),
    val completedCount: Int = 0,
    val message: String? = null,
    val success: Boolean? = null,
)

@Singleton
class RemoteModelDiscoveryRuntime @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(RemoteModelDiscoveryUiState())
    val state = _state.asStateFlow()

    var current: RemoteModelDiscoveryUiState
        get() = _state.value
        private set(value) {
            _state.value = value
        }

    private val jobLock = Any()
    private var activeJob: Job? = null
    private var nextRunId = 0L
    private var activeRunId: Long? = null

    @Volatile
    private var visibleDraft: ApiConnection? = null

    fun launch(block: suspend (Long) -> Unit): Boolean {
        val job = synchronized(jobLock) {
            if (activeJob?.isActive == true) return false
            val runId = ++nextRunId
            activeRunId = runId
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block(runId)
                } finally {
                    val finishedJob = coroutineContext.job
                    synchronized(jobLock) {
                        if (activeJob === finishedJob) {
                            activeJob = null
                            activeRunId = null
                        }
                    }
                }
            }.also { activeJob = it }
        }
        job.start()
        return true
    }

    fun update(runId: Long, state: RemoteModelDiscoveryUiState): Boolean = synchronized(jobLock) {
        if (activeRunId != runId) return false
        current = state
        true
    }

    fun update(
        runId: Long,
        transform: (RemoteModelDiscoveryUiState) -> RemoteModelDiscoveryUiState,
    ): Boolean = synchronized(jobLock) {
        if (activeRunId != runId) return false
        current = transform(current)
        true
    }

    fun isActive(runId: Long): Boolean = synchronized(jobLock) {
        activeRunId == runId && activeJob?.isActive == true
    }

    fun setVisibleDraft(connection: ApiConnection?) {
        visibleDraft = connection
    }

    fun launchBackground(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun shouldNotifyCompletion(target: ApiConnection): Boolean =
        !AppForegroundState.isForeground || !sameRemoteModelDiscoveryTarget(visibleDraft, target)

    fun clearCompletedState() {
        synchronized(jobLock) {
            if (!current.isTesting) {
                current = RemoteModelDiscoveryUiState()
            }
        }
    }
}

internal fun sameRemoteModelDiscoveryTarget(
    first: ApiConnection?,
    second: ApiConnection?,
): Boolean {
    if (first == null || second == null) return false
    return first.providerId == second.providerId &&
        first.apiUrl.trim().trimEnd('/') == second.apiUrl.trim().trimEnd('/') &&
        first.apiKey == second.apiKey &&
        first.workspaceId.trim() == second.workspaceId.trim()
}
