package com.denggl2.mason.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.automation.AutomationScheduler
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.connectionIdForModel
import com.denggl2.mason.data.connectionForProvider
import com.denggl2.mason.data.saveConnection
import com.denggl2.mason.data.selectChatModel
import com.denggl2.mason.data.selectInitialImageModel
import com.denggl2.mason.data.ModelReference
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
import com.denggl2.mason.data.ModelListingUnavailableException
import com.denggl2.mason.data.OfficialChannelPreferences
import com.denggl2.mason.data.OfficialChannelPreferencesDataStore
import com.denggl2.mason.data.UserMemoryItem
import com.denggl2.mason.data.UserMemoryScope
import com.denggl2.mason.data.UserMemoryStore
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.llm.ChatClient
import com.denggl2.mason.llm.ApiCapabilityCheck
import com.denggl2.mason.llm.ApiTestResult
import com.denggl2.mason.llm.ChatResponse
import com.denggl2.mason.llm.ModelInvocation
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.agent.ToolGrantStore
import com.denggl2.mason.agent.TaskRunStore
import com.denggl2.mason.model.LocalModelEngineRegistry
import com.denggl2.mason.sync.SyncManager
import com.denggl2.masonremote.data.PairingStore
import com.denggl2.masonremote.transport.PairedConnector
import com.denggl2.masonremote.transport.revokeRemotePairing
import com.denggl2.mason.tool.NotificationTool
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class ApiTestUiState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
    val saved: Boolean = false,
    val capabilityWarning: String? = null,
    val capabilities: List<ApiCapabilityCheck> = emptyList(),
    val targetConnection: ApiConnection? = null,
    val testedConnection: ApiConnection? = null,
    val replacingModelId: String? = null,
    val activeModelId: String? = null,
    val activeModelIds: Set<String> = emptySet(),
    val observedModelCapabilities: Map<String, ApiModelCapabilities> = emptyMap(),
    val observedFailedModels: Set<String> = emptySet(),
)

data class ModelRefreshUiState(
    val isRefreshing: Boolean = false,
    val providerId: String? = null,
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

private const val DIAGNOSTIC_CONVERSATION_LIMIT = 5
private const val DIAGNOSTIC_MESSAGE_LIMIT = 20
private const val DIAGNOSTIC_TASK_LIMIT = 10
private const val DIAGNOSTIC_CRASH_LIMIT = 10
internal const val MODEL_TEST_PARALLELISM = 4
internal const val MANUAL_MODEL_ENTRY_MESSAGE =
    "地址不支持获取模型列表，请手动添加模型"

private data class BatchModelTestOutcome(
    val modelId: String,
    val config: ApiConfig,
    val result: ApiTestResult,
)

internal suspend fun <T, R> Iterable<T>.mapWithConcurrencyLimit(
    parallelism: Int,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    require(parallelism > 0)
    val semaphore = Semaphore(parallelism)
    map { item ->
        async {
            semaphore.withPermit { transform(item) }
        }
    }.awaitAll()
}

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
    private val localModelEngines: LocalModelEngineRegistry,
    private val automationPreferencesStore: AutomationPreferencesDataStore,
    private val automationScheduler: AutomationScheduler,
    private val toolGrantStore: ToolGrantStore,
    private val taskRunStore: TaskRunStore,
    private val apiTestRuntime: ApiTestRuntime,
    private val remoteModelDiscoveryRuntime: RemoteModelDiscoveryRuntime,
    private val notificationTool: NotificationTool,
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

    private val remotePairingStore = PairingStore(context)
    private val _pairedConnector = MutableStateFlow(remotePairingStore.currentConnector())
    val pairedConnector: StateFlow<PairedConnector?> = _pairedConnector.asStateFlow()

    private val _alwaysAllowedTools = MutableStateFlow(toolGrantStore.listAlwaysAllowed())
    val alwaysAllowedTools = _alwaysAllowedTools.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent = _toastEvent.asSharedFlow()

    private val _diagnosticExportEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val diagnosticExportEvent = _diagnosticExportEvent.asSharedFlow()

    val apiTestState = apiTestRuntime.state
    val remoteModelDiscoveryState = remoteModelDiscoveryRuntime.state

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

    fun previewTaskNotification(island: Boolean) {
        viewModelScope.launch {
            val args = mapOf(
                "title" to if (island) "岛通知已选择" else "常规通知已启用",
                "text" to if (island && Build.VERSION.SDK_INT < 36) {
                    "当前系统使用常规通知，Android 16 可显示岛通知"
                } else {
                    "Mason 会在后台任务状态变化时通知你"
                },
                NotificationTool.EXTRA_ALLOW_FOREGROUND to "true",
                NotificationTool.EXTRA_PREVIEW_MODE to if (island) {
                    NotificationTool.PREVIEW_MODE_ISLAND
                } else {
                    NotificationTool.PREVIEW_MODE_REGULAR
                },
                NotificationTool.EXTRA_LIVE_UPDATE to island.toString(),
                NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "35",
                NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to if (island) "岛通知" else "",
            )
            val result = notificationTool.execute(args)
            if (!result.success) {
                _toastEvent.emit(result.error ?: "通知发送失败")
                return@launch
            }
            if (island && result.data["live_update_requested"] == "true") {
                delay(3_000L)
                val finalResult = notificationTool.execute(
                    args + mapOf(
                        NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "100",
                        NotificationTool.EXTRA_LIVE_UPDATE_FINAL to "true",
                        NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to "完成",
                    ),
                )
                if (!finalResult.success) {
                    _toastEvent.emit(finalResult.error ?: "通知发送失败")
                }
            }
        }
    }

    fun saveConnection(connection: ApiConnection) {
        viewModelScope.launch {
            val current = config.value
            val updated = current.saveConnection(connection)
            val next = if (current.configuredChatModelRef() == null && connection.modelIds.isNotEmpty()) {
                updated.selectChatModel(ModelReference(connection.id, connection.modelIds.first()))
            } else {
                updated
            }
            configDataStore.updateConfig(next)
        }
    }

    fun saveTestedConnection(): Boolean {
        val testState = apiTestRuntime.current
        if (testState.success != true) return false
        val tested = testState.testedConnection ?: return false
        viewModelScope.launch {
            persistTestedConnection(tested, testState.replacingModelId)
        }
        return true
    }

    private suspend fun persistTestedConnection(
        tested: ApiConnection,
        replacingModelId: String?,
    ): ApiConnection {
        val current = config.value
        val merged = mergeTestedConnection(
            existing = current.connection(tested.id),
            tested = tested,
            replacingModelId = replacingModelId,
        )
        val updated = current.saveConnection(merged)
        val defaultChatModel = merged.modelIds.firstOrNull { modelId ->
            modelId !in merged.modelTestErrors
        }
        val withChatDefault = if (
            current.configuredChatModelRef() == null && defaultChatModel != null
        ) {
            updated.selectChatModel(ModelReference(merged.id, defaultChatModel))
        } else {
            updated
        }
        configDataStore.updateConfig(withChatDefault.selectInitialImageModel(merged))
        return merged
    }

    fun setApiTestVisibleDraft(connection: ApiConnection?) {
        apiTestRuntime.setVisibleDraft(connection)
    }

    fun revokeToolGrant(toolName: String) {
        toolGrantStore.revoke(toolName)
        _alwaysAllowedTools.value = toolGrantStore.listAlwaysAllowed()
        _toastEvent.tryEmit("已撤销 $toolName 的永久授权")
    }

    fun refreshPairingState() {
        _pairedConnector.value = remotePairingStore.currentConnector()
    }

    fun cancelDevicePairing() {
        val connector = remotePairingStore.currentConnector() ?: return
        // Remove the local route immediately so the UI and drawer stop using
        // it even when the computer is offline. Remote revocation is only a
        // best-effort cleanup of the desktop authorization.
        remotePairingStore.clearCurrentConnector()
        _pairedConnector.value = null
        _toastEvent.tryEmit("已取消设备配对")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                revokeRemotePairing(context, connector)
            }
        }
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
        _localModelStates.value = localModelStore.states(LocalModelCatalog.models)
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

    fun cancelLocalModelDownload(modelId: String) {
        LocalModelDownloadService.cancel(context, modelId)
    }

    fun deleteLocalModel(modelId: String) {
        val model = LocalModelCatalog.get(modelId) ?: return
        viewModelScope.launch {
            try {
                localModelDownloadCoordinator.cancelAndJoin(modelId)
                localModelEngines.release(modelId)
                localModelStore.deleteModel(model)
                if (!localModelDownloadCoordinator.isAnyDownloadActive()) {
                    LocalModelDownloadService.clearNotification(context)
                }
                refreshLocalModelStates()
                refreshLocalModelDownloadStates()
                val currentConfig = config.value
                if (currentConfig.localModel == modelId) {
                    val replacement = LocalModelCatalog.models.firstOrNull { candidate ->
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
            LocalModelInstallState.NotInstalled -> "请先导入 ${model.name} 的 ${model.runtime} 模型文件"
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
            val runtimeStatus = localModelEngines.runtimeStatus(modelId)
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
            try {
                val engine = requireNotNull(localModelEngines.engineFor(modelId)) {
                    "未找到本地模型运行时"
                }
                engine.invoke(
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
            } finally {
                // A settings test must not keep the multi-GB runtime resident.
                localModelEngines.release(modelId)
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
        apiTestRuntime.clearCompletedState()
    }

    fun cancelApiTest() {
        if (!apiTestRuntime.cancelActiveTest()) {
            _toastEvent.tryEmit("当前没有正在进行的模型测试")
        }
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
            apiTestRuntime.current = ApiTestUiState(message = message, success = false)
            return
        }

        if (!apiTestRuntime.launch { runId ->
            if (!apiTestRuntime.update(
                    runId,
                    ApiTestUiState(isTesting = true, message = "正在测试连接..."),
                )
            ) return@launch
            val result = chatClient.testConnection(
                apiUrl = config.apiUrl,
                apiKey = config.apiKey,
                model = config.model,
                visionModel = config.visionModel,
                imageModel = config.imageModel,
                requiresApiKey = AiProviderCatalog.requiresApiKey(config),
                testTools = config.toolsEnabled,
                additionalHeaders = buildMap {
                    config.connectionForProvider(config.providerId)?.workspaceId
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("X-DashScope-WorkSpace", it) }
                },
            )
            if (!apiTestRuntime.isActive(runId)) return@launch
            if (result.success) {
                val verifiedSignature = AiProviderCatalog.verificationSignature(config)
                val connectionId = connectionIdForModel(
                    config.providerId,
                    config.apiUrl,
                    config.model,
                )
                val existing = this@SettingsViewModel.config.value.connections
                    .firstOrNull { it.id == connectionId }
                configDataStore.updateConfig(
                    this@SettingsViewModel.config.value.saveConnection(
                        ApiConnection(
                            id = connectionId,
                            providerId = config.providerId,
                            name = existing?.name
                                ?: AiProviderCatalog.getProvider(config.providerId)?.name
                                ?: config.providerId,
                            apiUrl = config.apiUrl,
                            apiKey = config.apiKey,
                            modelIds = buildList {
                                addAll(existing?.modelIds.orEmpty())
                                listOf(config.model, config.visionModel, config.imageModel)
                                    .filterTo(this) { it.isNotBlank() }
                            }.distinct(),
                            toolsSupported = config.toolsEnabled,
                            verifiedSignature = verifiedSignature,
                            workspaceId = existing?.workspaceId.orEmpty(),
                            modelCapabilities = existing?.modelCapabilities.orEmpty(),
                            verifiedModelSignatures = existing?.verifiedModelSignatures.orEmpty(),
                            modelTestErrors = existing?.modelTestErrors.orEmpty(),
                        ),
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
            apiTestRuntime.update(runId, ApiTestUiState(
                isTesting = false,
                message = if (result.success) "已测试通过并保存" else result.message,
                success = result.success,
                saved = result.success,
                capabilityWarning = result.capabilityWarning,
                capabilities = result.capabilities,
                activeModelId = null,
            ))
        }) {
            _toastEvent.tryEmit("已有模型测试正在运行")
        }
    }

    fun testApiConnectionDraft(
        connection: ApiConnection,
        replacingModelId: String? = null,
    ) {
        val modelIds = connection.modelIds.map(String::trim).filter(String::isNotBlank).distinct()
        val targetConnection = connection.copy(modelIds = modelIds)
        val firstModel = modelIds.firstOrNull().orEmpty()
        val validationConfig = ApiConfig(
            providerId = connection.providerId,
            apiUrl = connection.apiUrl,
            apiKey = connection.apiKey,
            model = firstModel,
            toolsEnabled = true,
        )
        validateApiConfig(validationConfig)?.let { message ->
            apiTestRuntime.current = ApiTestUiState(
                message = message,
                success = false,
                targetConnection = targetConnection,
                replacingModelId = replacingModelId,
            )
            return
        }

        val priorState = apiTestRuntime.current

        if (!apiTestRuntime.launch { runId ->
            if (!apiTestRuntime.update(runId, ApiTestUiState(
                isTesting = true,
                message = "正在测试模型...",
                targetConnection = targetConnection,
                replacingModelId = replacingModelId,
                activeModelId = null,
                activeModelIds = emptySet(),
                observedModelCapabilities = priorState.observedModelCapabilities,
                observedFailedModels = priorState.observedFailedModels,
            ))) return@launch
            val completedModelCount = AtomicInteger(0)
            val outcomes = modelIds.mapWithConcurrencyLimit(MODEL_TEST_PARALLELISM) { modelId ->
                if (!apiTestRuntime.update(runId) { state ->
                        val activeModelIds = state.activeModelIds + modelId
                        state.copy(
                            isTesting = true,
                            message = "正在测试模型 ${completedModelCount.get()}/${modelIds.size}",
                            targetConnection = targetConnection,
                            replacingModelId = replacingModelId,
                            activeModelId = activeModelIds.firstOrNull(),
                            activeModelIds = activeModelIds,
                        )
                    }
                ) return@mapWithConcurrencyLimit null

                val draftConfig = validationConfig.copy(
                    model = modelId,
                    visionModel = modelId,
                    imageModel = modelId,
                )
                val result = chatClient.testConnection(
                    apiUrl = connection.apiUrl,
                    apiKey = connection.apiKey,
                    model = modelId,
                    visionModel = modelId,
                    imageModel = modelId,
                    requiresApiKey = AiProviderCatalog.requiresApiKey(draftConfig),
                    testTools = true,
                    additionalHeaders = buildMap {
                        connection.workspaceId.takeIf(String::isNotBlank)
                            ?.let { put("X-DashScope-WorkSpace", it) }
                    },
                )
                val finishedCount = completedModelCount.incrementAndGet()
                if (!apiTestRuntime.update(runId) { state ->
                        val activeModelIds = state.activeModelIds - modelId
                        state.copy(
                            message = "正在测试模型 $finishedCount/${modelIds.size}",
                            activeModelId = activeModelIds.firstOrNull(),
                            activeModelIds = activeModelIds,
                        )
                    }
                ) return@mapWithConcurrencyLimit null
                BatchModelTestOutcome(modelId, draftConfig, result)
            }.filterNotNull()
            if (!apiTestRuntime.isActive(runId) || outcomes.size != modelIds.size) return@launch

            val capabilitiesByModel = linkedMapOf<String, ApiModelCapabilities>()
            val modelTestErrors = linkedMapOf<String, String>()
            val signatures = linkedMapOf<String, String>()
            val resultMessages = mutableListOf<String>()
            val succeededModelKeys = linkedSetOf<String>()
            val failedModelKeys = linkedSetOf<String>()
            val allConnectionsSucceeded = outcomes.all { it.result.success }
            val lastCapabilities = outcomes.lastOrNull()?.result?.capabilities.orEmpty()

            outcomes.forEach { outcome ->
                resultMessages += "${outcome.modelId}：${outcome.result.message}"
                if (outcome.result.success) {
                    capabilitiesByModel[outcome.modelId] = outcome.result.capabilities.toModelCapabilities()
                    signatures[outcome.modelId] = AiProviderCatalog.verificationSignature(outcome.config)
                    succeededModelKeys += apiTestModelKey(connection.id, outcome.modelId)
                } else {
                    modelTestErrors[outcome.modelId] = outcome.result.message.take(1_200)
                    failedModelKeys += apiTestModelKey(connection.id, outcome.modelId)
                }
            }

            val tested = connection.copy(
                modelIds = modelIds,
                toolsSupported = capabilitiesByModel.values.any(ApiModelCapabilities::supportsTools),
                verifiedSignature = signatures[firstModel].orEmpty(),
                modelCapabilities = capabilitiesByModel,
                verifiedModelSignatures = signatures,
                modelTestErrors = modelTestErrors,
            )
            if (!apiTestRuntime.isActive(runId)) return@launch
            persistTestedConnection(tested, replacingModelId)
            if (!apiTestRuntime.isActive(runId)) return@launch
            val completedState = ApiTestUiState(
                message = if (allConnectionsSucceeded) {
                    "已测试通过并保存"
                } else {
                    resultMessages.joinToString("\n")
                },
                success = allConnectionsSucceeded,
                saved = allConnectionsSucceeded,
                capabilities = lastCapabilities,
                targetConnection = tested,
                testedConnection = tested,
                replacingModelId = replacingModelId,
                activeModelId = null,
                activeModelIds = emptySet(),
                observedModelCapabilities = priorState.observedModelCapabilities +
                    capabilitiesByModel.filterKeys { modelId ->
                        apiTestModelKey(connection.id, modelId) in succeededModelKeys
                    }.mapKeys { (modelId, _) -> apiTestModelKey(connection.id, modelId) },
                observedFailedModels = (priorState.observedFailedModels - succeededModelKeys) + failedModelKeys,
            )
            if (!apiTestRuntime.update(runId, completedState)) return@launch
            if (apiTestRuntime.shouldNotifyCompletion(tested)) {
                notificationTool.execute(
                    mapOf(
                        "title" to if (allConnectionsSucceeded) "模型测试完成" else "模型测试未通过",
                        "text" to if (allConnectionsSucceeded) {
                            tested.modelIds.joinToString("、") + " 已测试通过并保存"
                        } else {
                            "返回 Mason 查看模型测试结果"
                        },
                        NotificationTool.EXTRA_LIVE_UPDATE to "true",
                        NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "100",
                        NotificationTool.EXTRA_LIVE_UPDATE_FINAL to "true",
                        NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to "完成",
                    ),
                )
            }
        }) {
            _toastEvent.tryEmit("已有模型测试正在运行")
        }
    }

    fun discoverAndTestRemoteModels(connection: ApiConnection) {
        val target = connection.copy(
            apiUrl = connection.apiUrl.trim(),
            apiKey = connection.apiKey.trim(),
            workspaceId = connection.workspaceId.trim(),
            modelIds = emptyList(),
        )
        if (target.apiUrl.isBlank()) {
            _toastEvent.tryEmit("请先填写接口地址")
            return
        }
        if (!AiProviderCatalog.allowsBlankApiKey(target.apiUrl) && target.apiKey.isBlank()) {
            _toastEvent.tryEmit("请先填写 API Key")
            return
        }

        if (!remoteModelDiscoveryRuntime.launch { runId ->
            if (!remoteModelDiscoveryRuntime.update(
                    runId,
                    RemoteModelDiscoveryUiState(
                        isTesting = true,
                        targetConnection = target,
                        message = "正在获取模型列表...",
                    ),
                )
            ) return@launch

            val modelsResult = modelRepository.fetchModels(
                apiUrl = target.apiUrl,
                apiKey = target.apiKey,
                workspaceId = target.workspaceId,
            )
            val models = modelsResult.getOrElse { error ->
                val unavailable = error is ModelListingUnavailableException
                remoteModelDiscoveryRuntime.update(
                    runId,
                    RemoteModelDiscoveryUiState(
                        targetConnection = target,
                        modelListingAvailable = if (unavailable) false else null,
                        message = if (unavailable) {
                            MANUAL_MODEL_ENTRY_MESSAGE
                        } else {
                            remoteModelRefreshErrorMessage(error)
                        },
                        success = false,
                    ),
                )
                return@launch
            }
            if (models.isEmpty()) {
                remoteModelDiscoveryRuntime.update(
                    runId,
                    RemoteModelDiscoveryUiState(
                        targetConnection = target,
                        modelListingAvailable = false,
                        message = MANUAL_MODEL_ENTRY_MESSAGE,
                        success = false,
                    ),
                )
                return@launch
            }

            if (!remoteModelDiscoveryRuntime.update(
                    runId,
                    RemoteModelDiscoveryUiState(
                        isTesting = true,
                        targetConnection = target,
                        modelListingAvailable = true,
                        models = models,
                        message = "正在测试模型 0/${models.size}",
                    ),
                )
            ) return@launch

            val completedModels = models.mapWithConcurrencyLimit(MODEL_TEST_PARALLELISM) { model ->
                if (!remoteModelDiscoveryRuntime.update(runId) { state ->
                        state.copy(
                            isTesting = true,
                            activeModelIds = state.activeModelIds + model.id,
                            message = "正在测试模型 ${state.completedCount}/${models.size}",
                        )
                    }
                ) return@mapWithConcurrencyLimit null

                val draftConfig = ApiConfig(
                    providerId = target.providerId,
                    apiUrl = target.apiUrl,
                    apiKey = target.apiKey,
                    model = model.id,
                    visionModel = model.id,
                    imageModel = model.id,
                    toolsEnabled = true,
                )
                val result = chatClient.testConnection(
                    apiUrl = target.apiUrl,
                    apiKey = target.apiKey,
                    model = model.id,
                    visionModel = model.id,
                    imageModel = model.id,
                    requiresApiKey = AiProviderCatalog.requiresApiKey(draftConfig),
                    testTools = true,
                    additionalHeaders = buildMap {
                        target.workspaceId.takeIf(String::isNotBlank)
                            ?.let { put("X-DashScope-WorkSpace", it) }
                    },
                )
                if (!remoteModelDiscoveryRuntime.update(runId) { state ->
                        val finishedCount = state.completedCount + 1
                        state.copy(
                            modelCapabilities = if (result.success) {
                                state.modelCapabilities + (model.id to result.capabilities.toModelCapabilities())
                            } else {
                                state.modelCapabilities - model.id
                            },
                            modelTestErrors = if (result.success) {
                                state.modelTestErrors - model.id
                            } else {
                                state.modelTestErrors + (model.id to result.message.take(1_200))
                            },
                            verifiedModelSignatures = if (result.success) {
                                state.verifiedModelSignatures +
                                    (model.id to AiProviderCatalog.verificationSignature(draftConfig))
                            } else {
                                state.verifiedModelSignatures - model.id
                            },
                            activeModelIds = state.activeModelIds - model.id,
                            completedCount = finishedCount,
                            message = "正在测试模型 $finishedCount/${models.size}",
                        )
                    }
                ) return@mapWithConcurrencyLimit null
                model.id
            }.filterNotNull()
            if (!remoteModelDiscoveryRuntime.isActive(runId) || completedModels.size != models.size) {
                return@launch
            }

            val completed = remoteModelDiscoveryRuntime.current.copy(
                isTesting = false,
                activeModelIds = emptySet(),
                completedCount = models.size,
                message = "测试完成，请选择要添加的模型",
                success = true,
            )
            if (!remoteModelDiscoveryRuntime.update(runId, completed)) return@launch
            if (remoteModelDiscoveryRuntime.shouldNotifyCompletion(target)) {
                notificationTool.execute(
                    mapOf(
                        "title" to "模型测试完成",
                        "text" to "返回 Mason 选择要添加的模型",
                        NotificationTool.EXTRA_LIVE_UPDATE to "true",
                        NotificationTool.EXTRA_LIVE_UPDATE_PROGRESS to "100",
                        NotificationTool.EXTRA_LIVE_UPDATE_FINAL to "true",
                        NotificationTool.EXTRA_LIVE_UPDATE_SHORT_TEXT to "完成",
                    ),
                )
            }
        }) {
            _toastEvent.tryEmit("已有模型列表测试正在运行")
        }
    }

    fun completeRemoteModelDiscovery(
        connection: ApiConnection,
        selectedModelIds: Set<String>,
    ): Boolean {
        val state = remoteModelDiscoveryRuntime.current
        if (state.isTesting || state.modelListingAvailable != true) return false
        if (!sameRemoteModelDiscoveryTarget(state.targetConnection, connection)) return false
        val tested = buildSelectedDiscoveredConnection(connection, state, selectedModelIds)
            ?: return false
        remoteModelDiscoveryRuntime.launchBackground {
            persistTestedConnection(tested, replacingModelId = null)
        }
        remoteModelDiscoveryRuntime.clearCompletedState()
        return true
    }

    fun setRemoteModelDiscoveryVisibleDraft(connection: ApiConnection?) {
        remoteModelDiscoveryRuntime.setVisibleDraft(connection)
    }

    fun clearCompletedRemoteModelDiscovery() {
        remoteModelDiscoveryRuntime.clearCompletedState()
    }

    fun refreshOpenRouterFreeModels(apiKey: String) {
        viewModelScope.launch {
            _modelRefreshState.value = ModelRefreshUiState(
                isRefreshing = true,
                providerId = "openrouter",
                message = "正在刷新免费模型...",
            )
            val result = modelRepository.fetchOpenRouterFreeModels(apiKey)
            _modelRefreshState.value = result.fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        ModelRefreshUiState(
                            providerId = "openrouter",
                            message = "没有获取到免费模型，先使用内置预设",
                            success = false,
                        )
                    } else {
                        ModelRefreshUiState(
                            providerId = "openrouter",
                            models = models,
                            message = "已刷新 ${models.size} 个免费模型",
                            success = true,
                        )
                    }
                },
                onFailure = { error ->
                    ModelRefreshUiState(
                        providerId = "openrouter",
                        message = "刷新失败: ${error.message ?: error.javaClass.simpleName}",
                        success = false,
                    )
                },
            )
        }
    }

    fun refreshRemoteModels(
        providerId: String,
        apiUrl: String,
        apiKey: String,
        workspaceId: String = "",
    ) {
        viewModelScope.launch {
            if (apiUrl.isBlank()) {
                _modelRefreshState.value = ModelRefreshUiState(
                    providerId = providerId,
                    message = "无法刷新，请先填写接口地址",
                    success = false,
                )
                return@launch
            }
            if (!AiProviderCatalog.allowsBlankApiKey(apiUrl) && apiKey.isBlank()) {
                _modelRefreshState.value = ModelRefreshUiState(
                    providerId = providerId,
                    message = "无法刷新，请先填写 API Key",
                    success = false,
                )
                return@launch
            }
            _modelRefreshState.value = ModelRefreshUiState(
                isRefreshing = true,
                providerId = providerId,
                message = "正在刷新模型信息...",
            )
            val result = modelRepository.fetchModels(apiUrl, apiKey, workspaceId)
            _modelRefreshState.value = result.fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        ModelRefreshUiState(
                            providerId = providerId,
                            message = "接口没有返回可用模型",
                            success = false,
                        )
                    } else {
                        ModelRefreshUiState(
                            providerId = providerId,
                            models = models,
                            message = "已拉取 ${models.size} 个模型",
                            success = true,
                        )
                    }
                },
                onFailure = { error ->
                    ModelRefreshUiState(
                        providerId = providerId,
                        message = remoteModelRefreshErrorMessage(error),
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

    fun exportDiagnosticReport() {
        viewModelScope.launch {
            try {
                val reportFile = withContext(Dispatchers.IO) {
                    val memory = ActivityManager.MemoryInfo()
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                        ?.getMemoryInfo(memory)
                    val currentConfig = config.value
                    val conversations = syncManager.getConversationsSnapshot()
                        .sortedByDescending { it.updatedAt }
                        .take(DIAGNOSTIC_CONVERSATION_LIMIT)
                        .map { conversation ->
                            DiagnosticConversation(
                                id = conversation.id,
                                title = conversation.title,
                                updatedAt = conversation.updatedAt,
                                messages = syncManager.getMessagesSnapshot(conversation.id)
                                    .takeLast(DIAGNOSTIC_MESSAGE_LIMIT)
                                    .map { message ->
                                        DiagnosticMessage(
                                            role = message.role,
                                            content = message.content,
                                            timestamp = message.timestamp,
                                            toolCallName = message.toolCallName,
                                        )
                                    },
                            )
                        }
                    val report = buildDiagnosticReport(
                        DiagnosticReportInput(
                            generatedAt = System.currentTimeMillis(),
                            appVersion = appVersion,
                            device = DiagnosticDeviceInfo(
                                manufacturer = Build.MANUFACTURER,
                                model = Build.MODEL,
                                androidVersion = Build.VERSION.RELEASE,
                                sdk = Build.VERSION.SDK_INT,
                                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                                locale = Locale.getDefault().toLanguageTag(),
                                totalMemoryBytes = memory.totalMem,
                                availableMemoryBytes = memory.availMem,
                                availableStorageBytes = StatFs(context.filesDir.absolutePath).availableBytes,
                            ),
                            config = currentConfig,
                            localModels = localModelStore.states(LocalModelCatalog.models),
                            conversations = conversations,
                            taskRuns = taskRunStore.list().take(DIAGNOSTIC_TASK_LIMIT),
                            crashes = crashDao.getAll().take(DIAGNOSTIC_CRASH_LIMIT),
                        ),
                    )
                    val reportDir = File(context.cacheDir, "diagnostics")
                    check(reportDir.exists() || reportDir.mkdirs()) { "无法创建诊断目录" }
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    File(reportDir, "mason-diagnostic-$timestamp.txt").apply {
                        writeText(report, Charsets.UTF_8)
                    }
                }
                _diagnosticExportEvent.emit(reportFile)
            } catch (error: Exception) {
                _toastEvent.emit("生成诊断记录失败：${error.message ?: error.javaClass.simpleName}")
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

internal fun mergeTestedConnection(
    existing: ApiConnection?,
    tested: ApiConnection,
    replacingModelId: String?,
): ApiConnection {
    if (existing == null) return tested
    val replacedIds = replacingModelId?.let(::setOf).orEmpty()
    val retainedModelIds = existing.modelIds.filterNot(replacedIds::contains)
    val mergedCapabilities = (existing.modelCapabilities - replacedIds) + tested.modelCapabilities
    val mergedSignatures = (existing.verifiedModelSignatures - replacedIds) + tested.verifiedModelSignatures
    val mergedTestErrors = (existing.modelTestErrors - replacedIds) + tested.modelTestErrors
    return tested.copy(
        modelIds = (retainedModelIds + tested.modelIds).distinct(),
        toolsSupported = mergedCapabilities.values.any(ApiModelCapabilities::supportsTools),
        verifiedSignature = tested.verifiedSignature.ifBlank { existing.verifiedSignature },
        modelCapabilities = mergedCapabilities,
        verifiedModelSignatures = mergedSignatures,
        modelTestErrors = mergedTestErrors,
    )
}

internal fun apiTestModelKey(connectionId: String, modelId: String): String =
    "$connectionId::$modelId"

private fun List<ApiCapabilityCheck>.toModelCapabilities(): ApiModelCapabilities =
    ApiModelCapabilities(
        supportsChat = any { it.label == "聊天" && it.success },
        supportsTools = any { it.label == "工具调用" && it.success },
        supportsVision = any { it.label == "识图" && it.success },
        supportsImageGeneration = any { it.label == "生图" && it.success },
    )

internal fun buildSelectedDiscoveredConnection(
    draft: ApiConnection,
    state: RemoteModelDiscoveryUiState,
    selectedModelIds: Set<String>,
): ApiConnection? {
    val selected = state.models.map(AiModelPreset::id).filter(selectedModelIds::contains)
    val firstModel = selected.firstOrNull() ?: return null
    val capabilities = state.modelCapabilities.filterKeys(selected::contains)
    val signatures = state.verifiedModelSignatures.filterKeys(selected::contains)
    val errors = state.modelTestErrors.filterKeys(selected::contains)
    return draft.copy(
        id = connectionIdForModel(draft.providerId, draft.apiUrl, firstModel),
        modelIds = selected,
        toolsSupported = capabilities.values.any(ApiModelCapabilities::supportsTools),
        verifiedSignature = signatures[firstModel].orEmpty(),
        modelCapabilities = capabilities,
        verifiedModelSignatures = signatures,
        modelTestErrors = errors,
    )
}

internal fun remoteModelRefreshErrorMessage(error: Throwable): String {
    val detail = error.message.orEmpty()
    return when {
        Regex("""\b(401|403)\b""").containsMatchIn(detail) ->
            "无法刷新，API Key 无效或没有访问权限"
        Regex("""\b404\b""").containsMatchIn(detail) ->
            "无法刷新，该服务商不支持获取模型列表"
        detail.contains("timeout", ignoreCase = true) || detail.contains("超时") ->
            "无法刷新，连接服务商超时"
        detail.contains("Unable to resolve host", ignoreCase = true) ->
            "无法刷新，请检查网络连接"
        else -> "无法刷新：${detail.ifBlank { error.javaClass.simpleName }.take(120)}"
    }
}
