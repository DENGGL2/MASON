package com.denggl2.mason.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiModelRepository
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ApiTestUiState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

data class ModelRefreshUiState(
    val isRefreshing: Boolean = false,
    val models: List<AiModelPreset> = emptyList(),
    val message: String? = null,
    val success: Boolean? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configDataStore: ApiConfigDataStore,
    private val chatClient: ChatClient,
    private val modelRepository: AiModelRepository,
    private val syncManager: SyncManager,
    private val crashDao: CrashDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val config: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent = _toastEvent.asSharedFlow()

    private val _apiTestState = MutableStateFlow(ApiTestUiState())
    val apiTestState = _apiTestState.asStateFlow()

    private val _modelRefreshState = MutableStateFlow(ModelRefreshUiState())
    val modelRefreshState = _modelRefreshState.asStateFlow()

    val appVersion: String by lazy {
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    fun save(config: ApiConfig) {
        viewModelScope.launch {
            configDataStore.updateConfig(config)
        }
    }

    fun validateApiConfig(config: ApiConfig): String? {
        if (config.apiUrl.isBlank()) return "请填写 API 地址"
        if (config.model.isBlank()) return "请填写模型名称"
        if (config.apiKey.isBlank() && !allowsBlankApiKey(config.apiUrl)) {
            return "请填写 API Key；只有本地地址可以留空"
        }
        return null
    }

    fun testApi(config: ApiConfig) {
        validateApiConfig(config)?.let { message ->
            _apiTestState.value = ApiTestUiState(message = message, success = false)
            return
        }

        viewModelScope.launch {
            _apiTestState.value = ApiTestUiState(isTesting = true, message = "正在测试连接...")
            val result = chatClient.testConnection(
                apiUrl = config.apiUrl,
                apiKey = config.apiKey,
                model = config.model,
            )
            _apiTestState.value = ApiTestUiState(
                isTesting = false,
                message = result.message,
                success = result.success,
            )
        }
    }

    fun refreshOpenRouterFreeModels(apiKey: String) {
        viewModelScope.launch {
            _modelRefreshState.value = ModelRefreshUiState(
                isRefreshing = true,
                message = "正在刷新免费模型...",
            )
            val result = modelRepository.fetchOpenRouterFreeModels(apiKey)
            _modelRefreshState.value = result.fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        ModelRefreshUiState(
                            message = "没有获取到免费模型，先使用内置预设",
                            success = false,
                        )
                    } else {
                        ModelRefreshUiState(
                            models = models,
                            message = "已刷新 ${models.size} 个免费模型",
                            success = true,
                        )
                    }
                },
                onFailure = { error ->
                    ModelRefreshUiState(
                        message = "刷新失败: ${error.message ?: error.javaClass.simpleName}",
                        success = false,
                    )
                },
            )
        }
    }

    private fun allowsBlankApiKey(apiUrl: String): Boolean {
        val normalized = apiUrl.lowercase()
        return listOf("localhost", "127.0.0.1", "10.0.2.2").any { it in normalized }
    }

    fun exportConversations() {
        viewModelScope.launch {
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "mason"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val file = File(downloadDir, "mason_backup_$timestamp.json")
                val success = syncManager.exportToFile(file)
                if (success) {
                    _toastEvent.emit("导出成功：${file.absolutePath}")
                } else {
                    _toastEvent.emit("导出失败")
                }
            } catch (e: Exception) {
                _toastEvent.emit("导出失败：${e.message}")
            }
        }
    }

    fun importConversations(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = syncManager.importFromUri(uri)
                _toastEvent.emit("导入成功：${count} 个对话")
            } catch (e: Exception) {
                _toastEvent.emit("导入失败：${e.message}")
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            try {
                syncManager.clearAll()
                _toastEvent.emit("已清除所有对话")
            } catch (e: Exception) {
                _toastEvent.emit("清除失败：${e.message}")
            }
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch {
            try {
                crashDao.clearAll()
                _toastEvent.emit("已清除崩溃日志")
            } catch (e: Exception) {
                _toastEvent.emit("清除失败：${e.message}")
            }
        }
    }
}
