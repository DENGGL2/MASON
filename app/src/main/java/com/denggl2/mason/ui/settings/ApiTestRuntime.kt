package com.denggl2.mason.ui.settings

import com.denggl2.mason.AppForegroundState
import com.denggl2.mason.data.ApiConnection
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

@Singleton
class ApiTestRuntime @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ApiTestUiState())
    val state = _state.asStateFlow()

    var current: ApiTestUiState
        get() = _state.value
        set(value) {
            _state.value = value
        }

    private val jobLock = Any()
    private var activeJob: Job? = null
    private var nextRunId = 0L
    private var activeRunId: Long? = null

    @Volatile
    private var visibleDraft: ApiConnection? = null

    fun update(state: ApiTestUiState) {
        current = state
    }

    fun clearCompletedState() {
        if (!_state.value.isTesting) {
            _state.value = ApiTestUiState()
        }
    }

    fun setVisibleDraft(connection: ApiConnection?) {
        visibleDraft = connection
    }

    fun shouldNotifyCompletion(target: ApiConnection): Boolean =
        shouldNotifyApiTestCompletion(
            appForeground = AppForegroundState.isForeground,
            visibleDraft = visibleDraft,
            target = target,
        )

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

    fun update(runId: Long, state: ApiTestUiState): Boolean = synchronized(jobLock) {
        if (activeRunId != runId) return false
        _state.value = state
        true
    }

    fun update(
        runId: Long,
        transform: (ApiTestUiState) -> ApiTestUiState,
    ): Boolean = synchronized(jobLock) {
        if (activeRunId != runId) return false
        _state.value = transform(_state.value)
        true
    }

    fun isActive(runId: Long): Boolean = synchronized(jobLock) {
        activeRunId == runId && activeJob?.isActive == true
    }

    fun cancelActiveTest(): Boolean {
        val job = synchronized(jobLock) {
            val running = activeJob?.takeIf(Job::isActive) ?: return false
            val previous = _state.value
            activeJob = null
            activeRunId = null
            _state.value = previous.copy(
                isTesting = false,
                message = "已取消测试，配置未保存",
                success = false,
                testedConnection = null,
                targetConnection = null,
                replacingModelId = null,
                activeModelId = null,
                activeModelIds = emptySet(),
            )
            running
        }
        job.cancel()
        return true
    }
}

internal fun shouldNotifyApiTestCompletion(
    appForeground: Boolean,
    visibleDraft: ApiConnection?,
    target: ApiConnection,
): Boolean = !appForeground || visibleDraft != target
