package com.denggl2.mason.ui.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedConnectorClient
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteConversationUiState(
    val isLoading: Boolean = false,
    val detail: RemoteConversationDetail? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class RemoteConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectorStore: PairedConnectorStore,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val _uiState = MutableStateFlow(RemoteConversationUiState())
    val uiState: StateFlow<RemoteConversationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.value = RemoteConversationUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val connector = connectorStore.load() ?: error("设备已取消配对")
                val deviceId = syncManager.getLocalDeviceId()
                PinnedConnectorClient(connector, AndroidDeviceIdentityStore())
                    .readConversation(deviceId = deviceId, threadId = threadId)
            }.onSuccess { detail ->
                _uiState.value = RemoteConversationUiState(isLoading = false, detail = detail)
            }.onFailure {
                _uiState.value = RemoteConversationUiState(
                    isLoading = false,
                    errorMessage = it.message?.takeIf(String::isNotBlank) ?: "无法加载电脑对话",
                )
            }
        }
    }
}
