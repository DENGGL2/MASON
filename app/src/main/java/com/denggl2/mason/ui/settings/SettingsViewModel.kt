package com.denggl2.mason.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.automation.AutomationScheduler
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.AutomationPreferences
import com.denggl2.mason.data.AutomationPreferencesDataStore
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiModelRepository
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelDownloadCoordinator
import com.denggl2.mason.data.LocalModelDownloadService
import com.denggl2.mason.data.LocalModelDownloadStatus
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.ModelCapabilityHealth
import com.denggl2.mason.data.ModelCapabilityHealthStore
import com.denggl2.mason.data.OfficialChannelPreferences
import com.denggl2.mason.data.OfficialChannelPreferencesDataStore
import com.denggl2.mason.data.UserMemoryItem
import com.denggl2.mason.data.UserMemoryScope
import com.denggl2.mason.data.UserMemoryStore
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ApiCapabilityCheck
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.LiteRtModelEngine
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.agent.ToolGrantStore
import com.denggl2.mason.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ApiTestUiState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
    val capabilityWarning: String? = null,
    val capabilities: List<ApiCapabilityCheck> = emptyList(),
)

data class ModelRefreshUiState(
    val isRefreshing: Boolean = false,
    val models: List<AiModelPreset> = emptyList(),
    val message: String? = null,
    val success: Boolean? = null,
)

data class CacheCategoryUiState(
    val id: String,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val clearable: Boolean,
)

data class CacheOverviewUiState(
    val isLoading: Boolean = false,
    val items: List<CacheCategoryUiState> = emptyList(),
)

data class LocalModelTestUiState(
    val modelId: String? = null,
    val isTesting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configDataStore: ApiConfigDataStore,
    private val modelCapabilityHealthStore: ModelCapabilityHealthStore,
    private val chatClient: ChatClient,
    private val modelRepository: AiModelRepository,
    private val syncManager: SyncManager,
    private val crashDao: CrashDao,
    private val userMemoryStore: UserMemoryStore,
    private val officialChannelStore: OfficialChannelPreferencesDataStore,
    private val localModelStore: LocalModelStore,
    private val localModelDownloadCoordinator: LocalModelDownloadCoordinator,
    private val liteRtModelEngine: LiteRtModelEngine,
    private val automationPreferencesStore: AutomationPreferencesDataStore,
    private val automationScheduler: AutomationScheduler,
    private val toolGrantStore: ToolGrantStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val config: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    val memoryItems: StateFlow<List<UserMemoryItem>> = userMemoryStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val officialChannels: StateFlow<OfficialChannelPreferences> = officialChannelStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfficialChannelPreferences())

    val automationPreferences: StateFlow<AutomationPreferences> = automationPreferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationPreferences())

    private val _alwaysAllowedTools = MutableStateFlow(toolGrantStore.listAlwaysAllowed())
    val alwaysAllowedTools = _alwaysAllowedTools.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent = _toastEvent.asSharedFlow()

    private val _apiTestState = MutableStateFlow(ApiTestUiState())
    val apiTestState = _apiTestState.asStateFlow()

    private val _modelRefreshState = MutableStateFlow(ModelRefreshUiState())
    val modelRefreshState = _modelRefreshState.asStateFlow()

    private val _cacheOverviewState = MutableStateFlow(CacheOverviewUiState())
    val cacheOverviewState = _cacheOverviewState.asStateFlow()

    private val _localModelStates = MutableStateFlow<List<LocalModelFileState>>(emptyList())
    val localModelStates = _localModelStates.asStateFlow()

    private val _localModelTestState = MutableStateFlow(LocalModelTestUiState())
    val localModelTestState = _localModelTestState.asStateFlow()

    val localModelDownloadStates = localModelDownloadCoordinator.states

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

    fun revokeToolGrant(toolName: String) {
        toolGrantStore.revoke(toolName)
        _alwaysAllowedTools.value = toolGrantStore.listAlwaysAllowed()
        _toastEvent.tryEmit("已撤销 $toolName 的永久授权")
    }

    fun setBackgroundAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                automationPreferencesStore.updateBackgroundExecutionEnabled(enabled)
                automationScheduler.syncAll(enabled)
            }.onSuccess {
                _toastEvent.emit(if (enabled) "后台自动化已开启" else "后台自动化已关闭")
            }.onFailure { error ->
                _toastEvent.emit("后台自动化设置失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    init {
        refreshLocalModelStates()
        localModelDownloadCoordinator.refreshStates()
        viewModelScope.launch {
            localModelDownloadCoordinator.states.collect { states ->
                if (states.values.any { it.status == LocalModelDownloadStatus.Completed }) {
                    refreshLocalModelStates()
                }
            }
        }
    }

    fun refreshLocalModelStates() {
        _localModelStates.value = localModelStore.states(LocalModelCatalog.gemmaModels)
    }

    private fun refreshLocalModelDownloadStates() {
        localModelDownloadCoordinator.refreshStates()
    }

    fun downloadLocalModel(modelId: String) {
        val model = LocalModelCatalog.get(modelId) ?: run {
            _toastEvent.tryEmit("未找到本地模型配置")
            return
        }
        if (localModelStore.stateFor(model).installed) {
            _toastEvent.tryEmit("${model.name} 已安装")
            return
        }
        if (localModelDownloadCoordinator.isAnyDownloadActive()) {
            _toastEvent.tryEmit("请先暂停当前模型下载")
            return
        }
        runCatching {
            LocalModelDownloadService.start(context, modelId)
        }.onFailure { error ->
            _toastEvent.tryEmit("无法启动后台下载：${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun pauseLocalModelDownload(modelId: String) {
        LocalModelDownloadService.pause(context, modelId)
    }

    fun deleteLocalModel(modelId: String) {
        val model = LocalModelCatalog.get(modelId) ?: return
        viewModelScope.launch {
            try {
                localModelDownloadCoordinator.cancelAndJoin(modelId)
                liteRtModelEngine.release()
                localModelStore.deleteModel(model)
                if (!localModelDownloadCoordinator.isAnyDownloadActive()) {
                    LocalModelDownloadService.clearNotification(context)
                }
                refreshLocalModelStates()
                refreshLocalModelDownloadStates()
                val currentConfig = config.value
                if (currentConfig.localModel == modelId) {
                    val replacement = LocalModelCatalog.gemmaModels.firstOrNull { candidate ->
                        localModelStore.stateFor(candidate).installed
                    }
                    configDataStore.updateConfig(
                        currentConfig.copy(
                            localModel = replacement?.id.orEmpty(),
                            localModelDirectEnabled = false,
                            offlineFallbackEnabled = currentConfig.offlineFallbackEnabled && replacement != null,
                        ),
                    )
                }
                _toastEvent.emit("已删除 ${model.name} 的本地文件")
            } catch (e: Exception) {
                _toastEvent.emit("删除失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun importLocalModel(modelId: String, uri: Uri) {
        val model = LocalModelCatalog.get(modelId) ?: run {
            _toastEvent.tryEmit("未找到本地模型配置")
            return
        }
        viewModelScope.launch {
            try {
                localModelStore.importModel(model, uri)
                refreshLocalModelStates()
                _toastEvent.emit("已导入 ${model.name}")
            } catch (e: Exception) {
                _toastEvent.emit("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun testLocalModel(modelId: String) {
        val model = LocalModelCatalog.get(modelId) ?: run {
            _localModelTestState.value = LocalModelTestUiState(
                modelId = modelId,
                message = "未找到本地模型配置",
                success = false,
            )
            return
        }
        val fileState = localModelStore.stateFor(model)
        val diagnostics = fileState.diagnosticSummary
        val unavailable = when (fileState.state) {
            LocalModelInstallState.Installed,
            LocalModelInstallState.DeviceMayBeUnsupported -> null
            LocalModelInstallState.FileMissing -> "模型文件异常，请重新导入 ${model.name}"
            LocalModelInstallState.NotInstalled -> "请先导入 ${model.name} 的 LiteRT-LM 模型文件"
        }
        if (unavailable != null) {
            _localModelTestState.value = LocalModelTestUiState(
                modelId = modelId,
                message = "$unavailable\n$diagnostics",
                success = false,
            )
            return
        }

        viewModelScope.launch {
            _localModelTestState.value = LocalModelTestUiState(
                modelId = modelId,
                isTesting = true,
                message = "正在测试本地模型...",
            )
            val runtimeStatus = liteRtModelEngine.runtimeStatus()
            if (!runtimeStatus.available) {
                _localModelTestState.value = LocalModelTestUiState(
                    modelId = modelId,
                    message = "${runtimeStatus.message}\n$diagnostics",
                    success = false,
                )
                return@launch
            }

            var text = ""
            var error: String? = null
            liteRtModelEngine.invoke(
                ModelInvocation(
                    modality = ModelModality.Text,
                    modelId = modelId,
                    messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = "用一句中文回答：Mason 本地模型测试成功了吗？",
                        ),
                    ),
                ),
            ).collect { response ->
                when (response) {
                    is ChatResponse.TextChunk -> {
                        text += response.text
                    }
                    is ChatResponse.Error -> {
                        error = response.message
                    }
                    else -> Unit
                }
            }

            _localModelTestState.value = if (text.isNotBlank()) {
                LocalModelTestUiState(
                    modelId = modelId,
                    message = "测试成功：${text.trim().take(80)}\n$diagnostics",
                    success = true,
                )
            } else {
                LocalModelTestUiState(
                    modelId = modelId,
                    message = "${error ?: "本地模型没有返回内容"}\n$diagnostics",
                    success = false,
                )
            }
        }
    }

    fun validateApiConfig(config: ApiConfig): String? {
        if (config.apiUrl.isBlank()) return "请填写 API 地址"
        if (config.model.isBlank()) return "请填写模型名称"
        if (config.apiKey.isBlank() && AiProviderCatalog.requiresApiKey(config)) {
            return "当前模型需要 API Key，请先填写"
        }
        return null
    }

    fun clearApiTestState() {
        _apiTestState.value = ApiTestUiState()
    }

    fun saveMemory(
        id: String?,
        label: String,
        value: String,
        type: UserMemoryType,
        sensitive: Boolean,
    ) {
        if (label.isBlank() || value.isBlank()) {
            _toastEvent.tryEmit("请先填写名称和内容")
            return
        }

        viewModelScope.launch {
            val existing = id?.let { memoryId ->
                userMemoryStore.items.value.firstOrNull { it.id == memoryId }
            }
            userMemoryStore.upsert(
                UserMemoryItem(
                    id = id ?: UUID.randomUUID().toString(),
                    label = label.trim(),
                    value = value.trim(),
                    type = type,
                    sensitive = sensitive,
                    createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                    enabled = existing?.enabled ?: true,
                    autoUse = existing?.autoUse ?: !sensitive,
                    scope = existing?.scope ?: UserMemoryScope.GLOBAL,
                    scopeId = existing?.scopeId,
                    keywords = existing?.keywords.orEmpty(),
                    lastUsedAtMillis = existing?.lastUsedAtMillis,
                ),
            )
            _toastEvent.emit(if (id == null) "已加入记忆" else "已更新记忆")
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            userMemoryStore.delete(id)
            _toastEvent.emit("已删除记忆")
        }
    }

    fun setWechatOfficialEnabled(enabled: Boolean) {
        viewModelScope.launch { officialChannelStore.updateWechatOfficialEnabled(enabled) }
    }

    fun setAlipayMcpEnabled(enabled: Boolean) {
        viewModelScope.launch { officialChannelStore.updateAlipayMcpEnabled(enabled) }
    }

    fun setMeituanMcpEnabled(enabled: Boolean) {
        viewModelScope.launch { officialChannelStore.updateMeituanMcpEnabled(enabled) }
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
                visionModel = config.visionModel,
                imageModel = config.imageModel,
                requiresApiKey = AiProviderCatalog.requiresApiKey(config),
                testTools = config.toolsEnabled,
            )
            if (result.success) {
                configDataStore.updateConfig(
                    config.copy(
                        verifiedSignature = AiProviderCatalog.verificationSignature(config),
                    ),
                )
            }
            if (result.capabilities.isNotEmpty()) {
                modelCapabilityHealthStore.save(
                    config = config,
                    capabilities = result.capabilities.associate { capability ->
                        capability.label to ModelCapabilityHealth(
                            available = capability.success,
                            detail = capability.detail,
                        )
                    },
                )
            }
            _apiTestState.value = ApiTestUiState(
                isTesting = false,
                message = result.message,
                success = result.success,
                capabilityWarning = result.capabilityWarning,
                capabilities = result.capabilities,
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

    fun exportConversations() {
        viewModelScope.launch {
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "mason"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val file = File(downloadDir, "mason_backup_$timestamp.md")
                val success = syncManager.exportMarkdownToFile(file)
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

    fun refreshCacheOverview() {
        viewModelScope.launch {
            _cacheOverviewState.value = CacheOverviewUiState(isLoading = true)
            _cacheOverviewState.value = CacheOverviewUiState(
                items = withContext(Dispatchers.IO) {
                    buildCacheOverview()
                },
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    clearDirectoryContent(context.cacheDir)
                    context.codeCacheDir?.let(::clearDirectoryContent)
                    crashDao.clearAll()
                }
                _toastEvent.emit("已清理缓存")
                refreshCacheOverview()
            } catch (e: Exception) {
                _toastEvent.emit("清理失败：${e.message}")
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

    private suspend fun buildCacheOverview(): List<CacheCategoryUiState> {
        val conversations = syncManager.getConversationsSnapshotCount()
        val messages = syncManager.getMessagesSnapshotCount()
        val crashCount = crashDao.getCount()
        val backupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "mason",
        )

        return listOf(
            CacheCategoryUiState(
                id = "temp",
                title = "临时缓存",
                description = "图片缩略图、临时文件和系统缓存",
                sizeBytes = context.cacheDir.safeSize(),
                clearable = true,
            ),
            CacheCategoryUiState(
                id = "code",
                title = "运行缓存",
                description = "Compose、WebView 或运行时生成的缓存",
                sizeBytes = context.codeCacheDir?.safeSize() ?: 0L,
                clearable = true,
            ),
            CacheCategoryUiState(
                id = "crash",
                title = "崩溃记录",
                description = "$crashCount 条本地崩溃诊断记录",
                sizeBytes = crashCount * 2048L,
                clearable = true,
            ),
            CacheCategoryUiState(
                id = "conversation",
                title = "对话数据",
                description = "$conversations 个对话，$messages 条消息；不会在缓存清理中删除",
                sizeBytes = 0L,
                clearable = false,
            ),
            CacheCategoryUiState(
                id = "backup",
                title = "导出备份",
                description = "Downloads/mason 下的 Markdown 备份；由用户自行管理",
                sizeBytes = backupDir.safeSize(),
                clearable = false,
            ),
        )
    }

    private fun File.safeSize(): Long {
        if (!exists()) return 0L
        return runCatching {
            if (isFile) {
                length()
            } else {
                walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
            }
        }.getOrDefault(0L)
    }

    private fun clearDirectoryContent(root: File) {
        if (!root.exists() || !root.isDirectory) return
        root.listFiles().orEmpty().forEach { file ->
            runCatching {
                if (file.isDirectory) {
                    clearDirectoryContent(file)
                }
                file.delete()
            }
        }
    }
}
