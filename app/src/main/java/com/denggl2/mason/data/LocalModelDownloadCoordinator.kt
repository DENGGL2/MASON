package com.denggl2.mason.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class LocalModelDownloadCoordinator @Inject constructor(
    private val downloader: LocalModelDownloader,
) {
    private val _states = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())
    val states = _states.asStateFlow()

    private val activeLock = Any()
    private var activeModelId: String? = null
    private var activeJob: Job? = null
    private var requestedStopStatus: LocalModelDownloadStatus? = null

    init {
        refreshStates()
    }

    fun refreshStates() {
        _states.value = LocalModelCatalog.models.associate { model ->
            val current = _states.value[model.id]
            val diskState = downloader.stateFor(model)
            model.id to when {
                current?.status == LocalModelDownloadStatus.Downloading ||
                    current?.status == LocalModelDownloadStatus.Checking ||
                    current?.status == LocalModelDownloadStatus.Verifying -> current
                diskState.downloadedBytes > 0L -> diskState
                else -> diskState.copy(
                    status = current?.status ?: diskState.status,
                    message = current?.message,
                )
            }
        }
    }

    fun isAnyDownloadActive(): Boolean = synchronized(activeLock) {
        activeJob?.isActive == true
    }

    suspend fun runDownload(modelId: String): LocalModelDownloadState {
        val model = requireNotNull(LocalModelCatalog.get(modelId)) { "未找到本地模型配置" }
        val job = coroutineContext.job
        return try {
            synchronized(activeLock) {
                check(activeJob?.isActive != true) { "已有模型正在下载" }
                activeModelId = modelId
                activeJob = job
                requestedStopStatus = null
            }
            downloader.download(model, ::updateState)
            requireNotNull(_states.value[modelId])
        } catch (_: CancellationException) {
            val stopStatus = synchronized(activeLock) {
                requestedStopStatus ?: LocalModelDownloadStatus.Paused
            }
            stoppedDownloadState(downloader.stateFor(model), stopStatus).also(::updateState)
        } catch (e: Exception) {
            downloader.stateFor(model)
                .copy(
                    status = LocalModelDownloadStatus.Failed,
                    message = e.message ?: e.javaClass.simpleName,
                )
                .also(::updateState)
        } finally {
            synchronized(activeLock) {
                if (activeJob === job) {
                    activeJob = null
                    activeModelId = null
                    requestedStopStatus = null
                }
            }
        }
    }

    suspend fun pauseAndJoin(modelId: String) {
        stopAndJoin(modelId, LocalModelDownloadStatus.Paused)
    }

    suspend fun cancelAndJoin(modelId: String) {
        stopAndJoin(modelId, LocalModelDownloadStatus.Cancelled)
        downloader.resetPartialDownload(modelId)
        LocalModelCatalog.get(modelId)?.let { model ->
            updateState(downloader.stateFor(model))
        }
    }

    private suspend fun stopAndJoin(
        modelId: String,
        status: LocalModelDownloadStatus,
    ) {
        val job = synchronized(activeLock) {
            activeJob?.takeIf { activeModelId == modelId }?.also {
                requestedStopStatus = status
            }
        }
        job?.cancelAndJoin()
    }

    fun reset(modelId: String) {
        val model = LocalModelCatalog.get(modelId) ?: return
        updateState(downloader.stateFor(model))
    }

    private fun updateState(state: LocalModelDownloadState) {
        _states.value = _states.value + (state.modelId to state)
    }
}

internal fun stoppedDownloadState(
    diskState: LocalModelDownloadState,
    status: LocalModelDownloadStatus,
): LocalModelDownloadState {
    require(status == LocalModelDownloadStatus.Paused || status == LocalModelDownloadStatus.Cancelled)
    return diskState.copy(
        status = status,
        downloadedBytes = if (status == LocalModelDownloadStatus.Cancelled) 0L else diskState.downloadedBytes,
        message = when (status) {
            LocalModelDownloadStatus.Paused -> "已暂停，可继续下载"
            LocalModelDownloadStatus.Cancelled -> null
            else -> error("不支持的下载停止状态")
        },
    )
}
