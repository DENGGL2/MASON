package com.denggl2.mason.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.AiProviderPreset
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.IslandVendorMode
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelDownloadState
import com.denggl2.mason.data.LocalModelDownloadStatus
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelPreset
import com.denggl2.mason.data.MasonAccentPresets
import com.denggl2.mason.data.NotificationDeliveryMode
import com.denggl2.mason.data.OfficialChannelPreferences
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.data.UserMemoryItem
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.data.toComposeColor
import java.util.Locale

private enum class SettingsPage {
    Overview,
    Appearance,
    AiService,
    ApiGuide,
    ProviderBalance,
    NotificationIsland,
    Memory,
    OfficialChannels,
    About,
}

private enum class OfficialChannelDetail(
    val title: String,
    val body: String,
) {
    Wechat(
        "微信 A2A（合作中）",
        "目前更接近微信与系统级助手的受控合作能力，适合在用户授权后发消息、发起语音/视频通话等明确动作。它不是公开通用 MCP，Mason 先禁用开关。",
    ),
    Alipay(
        "支付宝 MCP（商户侧）",
        "支付宝已提供开放平台支付 MCP 方向的能力，主要面向商户收单、交易查询、退款等服务端场景。个人用户不能直接拿它控制自己的支付宝 App 完成付款；Mason 只能准备信息、打开入口，最终确认仍由用户完成。",
    ),
    Meituan(
        "美团 MCP",
        "当前先做接口位预留。未找到可直接控制用户 App 的官方 MCP 发布口径前，不会把它作为可执行器启用。",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPermission: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    uiPreferences: UiPreferences = UiPreferences(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAccentColorChange: (Long) -> Unit = {},
    onNotificationIslandEnabledChange: (Boolean) -> Unit = {},
    onNotificationDeliveryModeChange: (NotificationDeliveryMode) -> Unit = {},
    onNotifyOnTaskCompleteChange: (Boolean) -> Unit = {},
    onNotifyOnPaymentSuccessChange: (Boolean) -> Unit = {},
    onIslandVendorModeChange: (IslandVendorMode) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val apiTestState by viewModel.apiTestState.collectAsState()
    val modelRefreshState by viewModel.modelRefreshState.collectAsState()
    val cacheOverviewState by viewModel.cacheOverviewState.collectAsState()
    val localModelStates by viewModel.localModelStates.collectAsState()
    val localModelTestState by viewModel.localModelTestState.collectAsState()
    val localModelDownloadStates by viewModel.localModelDownloadStates.collectAsState()
    val memoryItems by viewModel.memoryItems.collectAsState()
    val officialChannels by viewModel.officialChannels.collectAsState()
    val automationPreferences by viewModel.automationPreferences.collectAsState()
    val alwaysAllowedTools by viewModel.alwaysAllowedTools.collectAsState()
    val context = LocalContext.current

    var page by remember { mutableStateOf(SettingsPage.Overview) }
    var providerId by remember(config) { mutableStateOf(config.providerId) }
    var url by remember(config) { mutableStateOf(config.apiUrl) }
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var visionModel by remember(config) { mutableStateOf(config.visionModel) }
    var imageModel by remember(config) { mutableStateOf(config.imageModel) }
    var localModel by remember(config) { mutableStateOf(config.localModel) }
    var offlineFallbackEnabled by remember(config) { mutableStateOf(config.offlineFallbackEnabled) }
    var toolsEnabled by remember(config) { mutableStateOf(config.toolsEnabled) }
    var requireToolConfirmation by remember(config) { mutableStateOf(config.requireToolConfirmation) }
    var keyVisible by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var officialDetail by remember { mutableStateOf<OfficialChannelDetail?>(null) }
    var pendingLocalModelImportId by remember { mutableStateOf<String?>(null) }
    var pendingLocalModelDeleteId by remember { mutableStateOf<String?>(null) }
    var editingMemoryId by remember { mutableStateOf<String?>(null) }
    var memoryEditorExpanded by remember { mutableStateOf(false) }
    var memoryLabel by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }
    var memoryType by remember { mutableStateOf(UserMemoryType.LICENSE_PLATE) }
    var memorySensitive by remember { mutableStateOf(true) }
    var customAccent by remember(uiPreferences.accentColor) {
        mutableStateOf(uiPreferences.accentColor.toRgbHex())
    }
    val pageScrollState = rememberScrollState()

    val provider = AiProviderCatalog.getProvider(providerId)
        ?: AiProviderCatalog.defaultProvider
    val modelOptions = if (provider.id == "openrouter" && modelRefreshState.models.isNotEmpty()) {
        modelRefreshState.models + provider.modelOptions.filterNot { builtIn ->
            builtIn.isFree || modelRefreshState.models.any { it.id == builtIn.id }
        }
    } else {
        provider.modelOptions
    }
    val selectedModel = modelOptions.firstOrNull { it.id == model }
        ?: AiProviderCatalog.getModel(provider.id, model)
    val isCustomProvider = provider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID
    val currentRequiresApiKey = AiProviderCatalog.requiresApiKey(
        providerId = provider.id,
        apiUrl = url,
        modelId = model,
    )
    val localModelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val modelId = pendingLocalModelImportId
        pendingLocalModelImportId = null
        if (uri != null && modelId != null) {
            viewModel.importLocalModel(modelId, uri)
        }
    }

    fun persistApiConfig(
        nextProviderId: String = providerId,
        nextUrl: String = url,
        nextKey: String = key,
        nextModel: String = model,
        nextVisionModel: String = visionModel,
        nextImageModel: String = imageModel,
        nextLocalModel: String = localModel,
        nextOfflineFallbackEnabled: Boolean = offlineFallbackEnabled,
        nextToolsEnabled: Boolean = toolsEnabled,
        nextRequireToolConfirmation: Boolean = requireToolConfirmation,
    ) {
        viewModel.save(
            ApiConfig(
                providerId = nextProviderId,
                apiUrl = nextUrl,
                apiKey = nextKey,
                model = nextModel,
                visionModel = nextVisionModel,
                imageModel = nextImageModel,
                localModel = nextLocalModel,
                localModelDirectEnabled = config.localModelDirectEnabled,
                offlineFallbackEnabled = nextOfflineFallbackEnabled,
                toolsEnabled = nextToolsEnabled,
                requireToolConfirmation = nextRequireToolConfirmation,
            ),
        )
    }

    fun clearMemoryEditor() {
        editingMemoryId = null
        memoryEditorExpanded = false
        memoryLabel = ""
        memoryValue = ""
        memoryType = UserMemoryType.OTHER
        memorySensitive = true
    }

    LaunchedEffect(providerId, modelRefreshState.models) {
        if (providerId == "openrouter" && modelRefreshState.models.isNotEmpty()) {
            val currentStillExists = modelRefreshState.models.any { it.id == model }
            if (!currentStillExists && selectedModel?.isFree == true) {
                val nextModel = modelRefreshState.models.first().id
                model = nextModel
                toolsEnabled = false
                persistApiConfig(nextModel = nextModel, nextToolsEnabled = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(page) {
        pageScrollState.scrollTo(0)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        settingsPageTitle(page),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            page = when (page) {
                                SettingsPage.Overview -> {
                                    onBack()
                                    SettingsPage.Overview
                                }
                                SettingsPage.ApiGuide,
                                SettingsPage.ProviderBalance -> SettingsPage.AiService
                                else -> SettingsPage.Overview
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(pageScrollState)
                .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
        ) {
            if (page == SettingsPage.Overview) {
                SettingsOverviewContent(
                    uiPreferences = uiPreferences,
                    provider = provider,
                    selectedModel = selectedModel,
                    model = model,
                    url = url,
                    key = key,
                    memoryCount = memoryItems.size,
                    officialChannels = officialChannels,
                    apiConfig = config,
                    appVersion = viewModel.appVersion,
                    onOpenAppearance = { page = SettingsPage.Appearance },
                    onOpenAi = { page = SettingsPage.AiService },
                    onOpenIsland = { page = SettingsPage.NotificationIsland },
                    onOpenMemory = { page = SettingsPage.Memory },
                    onOpenIntegrations = onNavigateToIntegrations,
                    onOpenPermission = onNavigateToPermission,
                    onOpenAbout = { page = SettingsPage.About },
                )
            }

            if (page == SettingsPage.ApiGuide) {
                ApiGuideContent(provider = provider)
            }

            if (page == SettingsPage.ProviderBalance) {
                ProviderBalanceContent(provider = provider)
            }

            if (page == SettingsPage.Appearance) {
            SectionHeader("外观")

            ThemeModeSetting(
                selectedMode = uiPreferences.themeMode,
                onModeChange = onThemeModeChange,
            )

            AccentColorSetting(
                selectedColor = uiPreferences.accentColor,
                customAccent = customAccent,
                onCustomAccentChange = { customAccent = it },
                onAccentColorChange = onAccentColorChange,
                onInvalidColor = {
                    Toast.makeText(context, "请输入 #RRGGBB 格式的颜色", Toast.LENGTH_SHORT).show()
                },
            )
            }

            if (page == SettingsPage.AiService) {
            SectionHeader("AI 服务")

            SettingGroup {
                DropdownSettingRow(
                    title = "服务商",
                    value = provider.name,
                    description = provider.description,
                    expanded = showProviderMenu,
                    onExpandedChange = { showProviderMenu = it },
                ) {
                    AiProviderCatalog.providers.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.name, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        item.description,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            onClick = {
                                val nextUrl = item.apiUrl
                                val nextModel = item.defaultModel
                                val nextToolsEnabled = item.toolsEnabledByDefault
                                providerId = item.id
                                url = nextUrl
                                model = nextModel
                                visionModel = ""
                                imageModel = ""
                                toolsEnabled = nextToolsEnabled
                                persistApiConfig(
                                    nextProviderId = item.id,
                                    nextUrl = nextUrl,
                                    nextModel = nextModel,
                                    nextVisionModel = "",
                                    nextImageModel = "",
                                    nextToolsEnabled = nextToolsEnabled,
                                )
                                viewModel.clearApiTestState()
                                showProviderMenu = false
                            },
                        )
                    }
                }

                if (provider.id == "openrouter") {
                    GroupDivider()
                    ActionSettingRow(
                        icon = Icons.Outlined.Refresh,
                        title = "刷新免费模型",
                        description = "从 OpenRouter 拉取当前可用的 :free 模型",
                        enabled = !modelRefreshState.isRefreshing,
                        isLoading = modelRefreshState.isRefreshing,
                        onClick = { viewModel.refreshOpenRouterFreeModels(key) },
                    )
                }

                GroupDivider()
                ActionSettingRow(
                    icon = Icons.Outlined.Storage,
                    title = "服务商余额",
                    description = "查看各平台余额、账单和用量入口",
                    onClick = { page = SettingsPage.ProviderBalance },
                )

                GroupDivider()
                ActionSettingRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "查看教程",
                    description = "注册、购买额度、复制 Key 和填入 Mason",
                    onClick = { page = SettingsPage.ApiGuide },
                )
            }

            StatusText(
                message = modelRefreshState.message,
                success = modelRefreshState.success,
            )

            SectionHeader("执行策略")
            SettingGroup {
                SwitchSettingRow(
                    title = "执行前确认高风险能力",
                    description = "发送消息、写入文件、打开应用、读取隐私等动作先弹窗确认；建议保持开启。",
                    checked = requireToolConfirmation,
                    onCheckedChange = {
                        requireToolConfirmation = it
                        persistApiConfig(nextRequireToolConfirmation = it)
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = "允许自动化后台运行",
                    description = "开启后，定时自动化可在 Mason 退出后由系统拉起执行；关闭不会删除任务和日志。",
                    checked = automationPreferences.backgroundExecutionEnabled,
                    onCheckedChange = viewModel::setBackgroundAutomationEnabled,
                )
                GroupDivider()
                SwitchSettingRow(
                    title = "网络失败时尝试本地模型",
                    description = "远程模型失败后改用已安装的本地模型继续文字问答；手机工具和附件仍需远程模型。",
                    checked = offlineFallbackEnabled,
                    onCheckedChange = {
                        val fallbackModel = localModel.ifBlank { LocalModelCatalog.gemmaModels.firstOrNull()?.id.orEmpty() }
                        offlineFallbackEnabled = it
                        localModel = fallbackModel
                        persistApiConfig(
                            nextOfflineFallbackEnabled = it,
                            nextLocalModel = fallbackModel,
                        )
                    },
                )
            }

            if (alwaysAllowedTools.isNotEmpty()) {
                SectionHeader("已记住的工具授权")
                SettingGroup {
                    alwaysAllowedTools.forEachIndexed { index, toolName ->
                        if (index > 0) GroupDivider()
                        ActionSettingRow(
                            icon = Icons.Outlined.Lock,
                            title = toolName,
                            description = "已设为总是允许；点击撤销",
                            onClick = { viewModel.revokeToolGrant(toolName) },
                        )
                    }
                }
            }

            SectionHeader("本地模型")
            LocalModelFallbackContent(
                models = LocalModelCatalog.gemmaModels,
                selectedModelId = localModel,
                states = localModelStates,
                testState = localModelTestState,
                downloadStates = localModelDownloadStates,
                fallbackEnabled = offlineFallbackEnabled,
                onSelect = { item ->
                    localModel = item.id
                    persistApiConfig(nextLocalModel = item.id)
                },
                onImport = { item ->
                    localModel = item.id
                    pendingLocalModelImportId = item.id
                    persistApiConfig(nextLocalModel = item.id)
                    localModelImportLauncher.launch(arrayOf("*/*"))
                },
                onTest = { item ->
                    localModel = item.id
                    persistApiConfig(nextLocalModel = item.id)
                    viewModel.testLocalModel(item.id)
                },
                onDownload = { item ->
                    localModel = item.id
                    persistApiConfig(nextLocalModel = item.id)
                    viewModel.downloadLocalModel(item.id)
                },
                onPauseDownload = { item ->
                    viewModel.pauseLocalModelDownload(item.id)
                },
                onDelete = { item ->
                    pendingLocalModelDeleteId = item.id
                },
            )

            if (!isCustomProvider && modelOptions.isNotEmpty()) {
                val chatModels = modelOptions.filter(AiModelPreset::supportsChat)
                val freeModels = chatModels.filter { it.isFree || it.id.endsWith(":free", ignoreCase = true) }
                val paidModels = chatModels.filterNot { it.isFree || it.id.endsWith(":free", ignoreCase = true) }

                SectionHeader("能力说明")
                SettingGroup {
                    ApiHintRow(
                        title = "看清模型能做什么",
                        description = "对话：文字聊天；流式：边生成边显示；工具：能调用手机能力；多模态/识图/生图：用于处理图片或生成图片。",
                    )
                    GroupDivider()
                    ApiHintRow(
                        title = "免费不等于免 Key",
                        description = "远程平台的免费模型通常也需要 Key 来识别账号和限额；只有本地或局域网模型地址可以不填 Key。",
                    )
                    GroupDivider()
                    ApiHintRow(
                        title = "能力编排",
                        description = "文字、识图和生图可以分别选择模型；Mason 会根据消息和附件自动路由。",
                    )
                }

                SectionHeader("模型")
                ModelPresetGroup(
                    title = "免费额度模型",
                    description = "模型调用免费或有免费额度，但远程平台通常仍要 Key",
                    models = freeModels,
                    selectedModelId = model,
                    providerId = provider.id,
                    defaultExpanded = freeModels.size <= 4,
                    onSelect = { item ->
                        val nextToolsEnabled = item.supportsTools
                        model = item.id
                        toolsEnabled = nextToolsEnabled
                        persistApiConfig(
                            nextModel = item.id,
                            nextToolsEnabled = nextToolsEnabled,
                        )
                        viewModel.clearApiTestState()
                    },
                )
                ModelPresetGroup(
                    title = "付费 / 自备 Key",
                    description = "需要对应服务商的 API Key 或账户余额",
                    models = paidModels,
                    selectedModelId = model,
                    providerId = provider.id,
                    defaultExpanded = paidModels.any { it.id == model },
                    onSelect = { item ->
                        val nextToolsEnabled = item.supportsTools
                        model = item.id
                        toolsEnabled = nextToolsEnabled
                        persistApiConfig(
                            nextModel = item.id,
                            nextToolsEnabled = nextToolsEnabled,
                        )
                        viewModel.clearApiTestState()
                    },
                )

                SectionHeader("能力编排")
                val visionModels = modelOptions.filter { it.supportsVision }
                val imageModels = modelOptions.filter { it.supportsImageGeneration }
                ModelOrchestrationContent(
                    chatModelName = selectedModel?.name ?: model.ifBlank { "未选择" },
                    visionModels = visionModels,
                    imageModels = imageModels,
                    selectedVisionModelId = visionModel,
                    selectedImageModelId = imageModel,
                    onSelectVisionModel = { item ->
                        visionModel = item.id
                        persistApiConfig(nextVisionModel = item.id)
                    },
                    onSelectImageModel = { item ->
                        imageModel = item.id
                        persistApiConfig(nextImageModel = item.id)
                    },
                )
            }

            SectionHeader("API 配置")

            if (!isCustomProvider && currentRequiresApiKey) {
                SettingGroup {
                    SettingRow(
                        title = "接口地址",
                        value = provider.apiUrl,
                        description = "由服务商预设管理，切换服务商后自动更新",
                        onClick = {},
                    )
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        CompactInput(
                            label = "API Key",
                            value = key,
                            placeholder = "填写 ${provider.name} 的 Key",
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (keyVisible) "隐藏" else "显示",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            key = it
                            viewModel.clearApiTestState()
                            persistApiConfig(nextKey = it)
                        }
                    }
                    if (provider.id == "openrouter" && selectedModel?.id == "~openai/gpt-latest") {
                        GroupDivider()
                        ApiHintRow(
                            title = "这是付费路由，不会自动切换",
                            description = "OpenRouter 是聚合平台；这个选项表示通过 OpenRouter 调用 OpenAI，需要 OpenRouter Key 和余额。",
                        )
                    }
                    if (selectedModel?.supportsTools == true) {
                        GroupDivider()
                        SwitchSettingRow(
                            title = "允许调用手机工具",
                            description = "仅在当前模型和服务商接口都支持 function calling 时开启",
                            checked = toolsEnabled,
                            onCheckedChange = {
                                toolsEnabled = it
                                viewModel.clearApiTestState()
                                persistApiConfig(nextToolsEnabled = it)
                            },
                        )
                    }
                }
            }

            if (isCustomProvider) {
                SettingGroup {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        CompactInput("API 地址", url, provider.apiUrl) {
                            url = it
                            viewModel.clearApiTestState()
                            persistApiConfig(nextUrl = it)
                        }
                    }
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        CompactInput(
                            label = "API Key",
                            value = key,
                            placeholder = "sk-...",
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (keyVisible) "隐藏" else "显示",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            key = it
                            viewModel.clearApiTestState()
                            persistApiConfig(nextKey = it)
                        }
                    }
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        CompactInput("模型名称", model, provider.defaultModel.ifBlank { "model-name" }) {
                            model = it
                            viewModel.clearApiTestState()
                            persistApiConfig(nextModel = it)
                        }
                    }
                    GroupDivider()
                    SwitchSettingRow(
                        title = "允许调用手机工具",
                        description = "仅在模型和中转站都支持 function calling 时开启",
                        checked = toolsEnabled,
                        onCheckedChange = {
                            toolsEnabled = it
                            viewModel.clearApiTestState()
                            persistApiConfig(nextToolsEnabled = it)
                        },
                    )
                }
            }

            if (!isCustomProvider && !currentRequiresApiKey) {
                SettingGroup {
                    ApiHintRow(
                        title = "当前地址可直接试用",
                        description = "本地或局域网模型地址可以不填 API Key；远程服务一般仍需要 Key。",
                    )
                }
            }

            StatusText(
                message = apiTestState.message,
                success = apiTestState.success,
            )

            OutlinedButton(
                onClick = {
                    viewModel.testApi(
                        ApiConfig(
                            providerId = providerId,
                            apiUrl = url,
                            apiKey = key,
                            model = model,
                            visionModel = visionModel,
                            imageModel = imageModel,
                            localModel = localModel,
                            localModelDirectEnabled = config.localModelDirectEnabled,
                            offlineFallbackEnabled = offlineFallbackEnabled,
                            toolsEnabled = toolsEnabled,
                            requireToolConfirmation = requireToolConfirmation,
                        ),
                    )
                },
                enabled = !apiTestState.isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (apiTestState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text("测试连接", color = MaterialTheme.colorScheme.primary)
                }
            }
            }

            if (page == SettingsPage.NotificationIsland) {
            SectionHeader("通知")

            NotificationIslandSetting(
                islandEnabled = uiPreferences.notificationIslandEnabled,
                deliveryMode = uiPreferences.notificationDeliveryMode,
                notifyOnTaskComplete = uiPreferences.notifyOnTaskComplete,
                notifyOnPaymentSuccess = uiPreferences.notifyOnPaymentSuccess,
                islandVendorMode = uiPreferences.islandVendorMode,
                onIslandEnabledChange = onNotificationIslandEnabledChange,
                onDeliveryModeChange = onNotificationDeliveryModeChange,
                onNotifyOnTaskCompleteChange = onNotifyOnTaskCompleteChange,
                onNotifyOnPaymentSuccessChange = onNotifyOnPaymentSuccessChange,
                onIslandVendorModeChange = onIslandVendorModeChange,
            )
            }

            if (page == SettingsPage.Memory) {
            SectionHeader("记忆")

            MemorySettingsContent(
                memoryItems = memoryItems,
                editingId = editingMemoryId,
                editorExpanded = memoryEditorExpanded,
                label = memoryLabel,
                value = memoryValue,
                sensitive = memorySensitive,
                onLabelChange = { memoryLabel = it },
                onValueChange = { memoryValue = it },
                onSensitiveChange = { memorySensitive = it },
                onAddClick = {
                    editingMemoryId = null
                    memoryLabel = ""
                    memoryValue = ""
                    memoryType = UserMemoryType.OTHER
                    memorySensitive = true
                    memoryEditorExpanded = true
                },
                onSave = {
                    viewModel.saveMemory(
                        id = editingMemoryId,
                        label = memoryLabel,
                        value = memoryValue,
                        type = inferMemoryType(memoryLabel, memoryValue),
                        sensitive = memorySensitive,
                    )
                    clearMemoryEditor()
                },
                onCancelEdit = { clearMemoryEditor() },
                onEdit = { item ->
                    editingMemoryId = item.id
                    memoryLabel = item.label
                    memoryValue = item.value
                    memoryType = item.type
                    memorySensitive = item.sensitive
                    memoryEditorExpanded = true
                },
                onDelete = viewModel::deleteMemory,
            )
            }

            if (page == SettingsPage.OfficialChannels) {
            SectionHeader("官方通道")

            OfficialChannelSettingsContent(
                preferences = officialChannels,
                onAlipayChange = viewModel::setAlipayMcpEnabled,
                onOpenDetail = { officialDetail = it },
            )
            }

            if (page == SettingsPage.About) {
            SectionHeader("关于")

            AboutSettingsContent(
                appVersion = viewModel.appVersion,
                onOpenCacheClean = {
                    showCacheDialog = true
                    viewModel.refreshCacheOverview()
                },
            )
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    if (showCacheDialog) {
        CacheCleanDialog(
            state = cacheOverviewState,
            onRefresh = viewModel::refreshCacheOverview,
            onClean = viewModel::clearCache,
            onDismiss = { showCacheDialog = false },
        )
    }

    pendingLocalModelDeleteId?.let { modelId ->
        val localPreset = LocalModelCatalog.get(modelId)
        AlertDialog(
            onDismissRequest = { pendingLocalModelDeleteId = null },
            title = { Text("删除本地模型") },
            text = {
                Text("将删除 ${localPreset?.name ?: modelId} 的模型文件和未完成下载。切换远程模型不会执行此操作。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalModelDeleteId = null
                        viewModel.deleteLocalModel(modelId)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalModelDeleteId = null }) {
                    Text("取消")
                }
            },
        )
    }

    officialDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { officialDetail = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(detail.title) },
            text = { Text(detail.body, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { officialDetail = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

private fun settingsPageTitle(page: SettingsPage): String = when (page) {
    SettingsPage.Overview -> "设置"
    SettingsPage.Appearance -> "外观"
    SettingsPage.AiService -> "AI 服务"
    SettingsPage.ApiGuide -> "API 教程"
    SettingsPage.ProviderBalance -> "服务商余额"
    SettingsPage.NotificationIsland -> "通知"
    SettingsPage.Memory -> "记忆"
    SettingsPage.OfficialChannels -> "官方通道"
    SettingsPage.About -> "关于"
}

@Composable
private fun SettingsOverviewContent(
    uiPreferences: UiPreferences,
    provider: AiProviderPreset,
    selectedModel: AiModelPreset?,
    model: String,
    url: String,
    key: String,
    memoryCount: Int,
    officialChannels: OfficialChannelPreferences,
    apiConfig: ApiConfig,
    appVersion: String,
    onOpenAppearance: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenIsland: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SectionHeader("设置项")
    SettingGroup {
        OverviewSettingRow(
            icon = Icons.Outlined.Palette,
            title = "外观",
            description = "深浅色、主题色和自定义取色",
            onClick = onOpenAppearance,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.Outlined.Key,
            title = "AI 服务",
            description = "模型预设、API Key 和中转站配置",
            onClick = onOpenAi,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.Outlined.Notifications,
            title = "通知",
            description = "常规通知、通知岛样式和完成提醒",
            onClick = onOpenIsland,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.Outlined.Fingerprint,
            title = "记忆",
            description = "车牌、地址、身份信息等本机加密记忆",
            onClick = onOpenMemory,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.Outlined.Hub,
            title = "扩展能力",
            description = "应用协作（A2A）和工具扩展（MCP）",
            onClick = onOpenIntegrations,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.Outlined.Lock,
            title = "权限",
            description = "相机、文件、通知等系统权限",
            onClick = onOpenPermission,
        )
        GroupDivider()
        OverviewSettingRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = "关于",
            description = "版本、更新、日志和缓存清理",
            onClick = onOpenAbout,
        )
    }
}

@Composable
private fun OverviewSettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun MemorySettingsContent(
    memoryItems: List<UserMemoryItem>,
    editingId: String?,
    editorExpanded: Boolean,
    label: String,
    value: String,
    sensitive: Boolean,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSensitiveChange: (Boolean) -> Unit,
    onAddClick: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (UserMemoryItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    SettingGroup {
        ApiHintRow(
            title = "只存本机，加密保存",
            description = "适合保存车牌、常用地址等信息。敏感记忆默认不会自动发送给模型，需要你在对话或自动化里明确使用。",
        )
    }

    SectionHeader("编辑")
    if (!editorExpanded) {
        SettingGroup {
            ActionSettingRow(
                icon = Icons.Outlined.Key,
                title = "添加记忆",
                description = "点击后填写名称和内容，例如车牌、常用地址、偏好说明",
                onClick = onAddClick,
            )
        }
    } else {
        SettingGroup {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                CompactInput(
                    label = "名称",
                    value = label,
                    placeholder = "例如：我的车牌 / 公司地址",
                    onValueChange = onLabelChange,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("填入希望 Mason 记住的文字") },
                    minLines = 2,
                    maxLines = 5,
                    colors = themedFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            GroupDivider()
            SwitchSettingRow(
                title = "按敏感信息处理",
                description = "默认隐藏内容，不主动进入模型上下文",
                checked = sensitive,
                onCheckedChange = onSensitiveChange,
            )
            GroupDivider()
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (editingId == null) "加入记忆" else "更新记忆", color = MaterialTheme.colorScheme.onPrimary)
                }
                OutlinedButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    SectionHeader("已保存")
    SettingGroup {
        if (memoryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无本地记忆",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            memoryItems.forEachIndexed { index, item ->
                if (index > 0) GroupDivider()
                MemoryItemRow(
                    item = item,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MemoryTypeChoice(
    type: UserMemoryType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            type.label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun MemoryItemRow(
    item: UserMemoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (item.sensitive) "敏感内容已隐藏，点按可编辑" else item.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun OfficialChannelSettingsContent(
    preferences: OfficialChannelPreferences,
    onAlipayChange: (Boolean) -> Unit,
    onOpenDetail: (OfficialChannelDetail) -> Unit,
) {
    SettingGroup {
        ApiHintRow(
            title = "暂无可接入官方通道",
            description = "等微信、支付宝、美团等平台提供可公开接入的 MCP / A2A 能力后，会在这里显示。当前不展示开关，避免误以为已经可以控制这些 App。",
        )
    }
}

@Composable
private fun OfficialChannelRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "查看详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (enabled) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Text(
                "未发布",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LocalModelFallbackContent(
    models: List<LocalModelPreset>,
    selectedModelId: String,
    states: List<LocalModelFileState>,
    testState: LocalModelTestUiState,
    downloadStates: Map<String, LocalModelDownloadState>,
    fallbackEnabled: Boolean,
    onSelect: (LocalModelPreset) -> Unit,
    onImport: (LocalModelPreset) -> Unit,
    onTest: (LocalModelPreset) -> Unit,
    onDownload: (LocalModelPreset) -> Unit,
    onPauseDownload: (LocalModelPreset) -> Unit,
    onDelete: (LocalModelPreset) -> Unit,
) {
    SettingGroup {
        ApiHintRow(
            title = if (fallbackEnabled) "已启用本地兜底" else "本地模型待启用",
            description = "可直接下载或手动导入 Gemma 4；下载支持暂停、继续、空间检查和 SHA-256 校验。",
        )
        models.forEach { item ->
            GroupDivider()
            LocalModelRowWithTest(
                item = item,
                state = states.firstOrNull { it.modelId == item.id },
                testState = testState.takeIf { it.modelId == item.id },
                downloadState = downloadStates[item.id],
                selected = item.id == selectedModelId,
                onClick = { onSelect(item) },
                onImport = { onImport(item) },
                onTest = { onTest(item) },
                onDownload = { onDownload(item) },
                onPauseDownload = { onPauseDownload(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun LocalModelRowWithTest(
    item: LocalModelPreset,
    state: LocalModelFileState?,
    testState: LocalModelTestUiState?,
    downloadState: LocalModelDownloadState?,
    selected: Boolean,
    onClick: () -> Unit,
    onImport: () -> Unit,
    onTest: () -> Unit,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloadActive = downloadState?.status in setOf(
        LocalModelDownloadStatus.Checking,
        LocalModelDownloadStatus.Downloading,
        LocalModelDownloadStatus.Verifying,
    )
    val hasPartialDownload = (downloadState?.downloadedBytes ?: 0L) > 0L
    val hasLocalFile = state?.state != null && state.state != LocalModelInstallState.NotInstalled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(7.dp))
                    ModelBadge("本地", free = true)
                    Spacer(Modifier.width(7.dp))
                    ModelBadge(localModelStateLabel(state), free = state?.installed == true)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${item.runtime} | 约 ${item.estimatedSizeGb}GB | 建议 ${item.recommendedRamGb}GB 内存",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            ModelSelectionIndicator(selected = selected)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            localModelStateDescription(state),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        if (downloadState != null && (downloadActive || hasPartialDownload || downloadState.message != null)) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { downloadState.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                localModelDownloadDescription(downloadState),
                color = if (downloadState.status == LocalModelDownloadStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state?.installed != true) {
                OutlinedButton(
                    onClick = if (downloadActive) onPauseDownload else onDownload,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = if (downloadActive) Icons.Outlined.Pause else Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (downloadActive) "暂停" else if (hasPartialDownload) "继续" else "下载")
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(
                onClick = onImport,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (state?.installed == true) "重新导入" else "导入")
            }
            if (state?.installed == true) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onTest,
                    enabled = testState?.isTesting != true,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (testState?.isTesting == true) "测试中" else "测试")
                }
            }
            if (hasLocalFile || hasPartialDownload) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除本地模型",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (!testState?.message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                testState?.message.orEmpty(),
                color = if (testState?.success == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalModelRow(
    item: LocalModelPreset,
    state: LocalModelFileState?,
    testState: LocalModelTestUiState?,
    selected: Boolean,
    onClick: () -> Unit,
    onImport: () -> Unit,
    onTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(7.dp))
                    ModelBadge("本地", free = true)
                    Spacer(Modifier.width(7.dp))
                    ModelBadge(localModelStateLabel(state), free = state?.installed == true)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${item.runtime} · 约 ${item.estimatedSizeGb}GB · 建议 ${item.recommendedRamGb}GB 内存",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            ModelSelectionIndicator(selected = selected)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                localModelStateDescription(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onImport,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (state?.installed == true) "重新导入" else "导入模型")
            }
        }
    }
}

private fun localModelStateLabel(state: LocalModelFileState?): String {
    return when (state?.state) {
        LocalModelInstallState.Installed -> "已安装"
        LocalModelInstallState.DeviceMayBeUnsupported -> "设备吃力"
        LocalModelInstallState.FileMissing -> "文件异常"
        LocalModelInstallState.NotInstalled, null -> "未安装"
    }
}

private fun localModelStateDescription(state: LocalModelFileState?): String {
    return when (state?.state) {
        LocalModelInstallState.Installed -> {
            val size = formatLocalModelSize(state.sizeBytes)
            "模型文件已在本机：$size"
        }
        LocalModelInstallState.DeviceMayBeUnsupported -> {
            val size = formatLocalModelSize(state.sizeBytes)
            "模型已导入：$size；当前设备内存约 ${state.availableRamGb}GB，低于建议 ${state.recommendedRamGb}GB"
        }
        LocalModelInstallState.FileMissing -> "模型路径存在异常，请重新导入单个 LiteRT-LM 模型文件"
        LocalModelInstallState.NotInstalled, null -> "未安装模型文件；导入后才可作为离线兜底候选"
    }
}

private fun localModelDownloadDescription(state: LocalModelDownloadState): String {
    val progress = "${formatLocalModelSize(state.downloadedBytes)} / ${formatLocalModelSize(state.totalBytes)}"
    return when (state.status) {
        LocalModelDownloadStatus.Idle -> state.message ?: "等待下载"
        LocalModelDownloadStatus.Checking -> state.message ?: "正在检查下载条件"
        LocalModelDownloadStatus.Downloading -> "正在下载：$progress"
        LocalModelDownloadStatus.Paused -> "已暂停：$progress"
        LocalModelDownloadStatus.Verifying -> state.message ?: "正在校验文件"
        LocalModelDownloadStatus.Completed -> state.message ?: "下载完成"
        LocalModelDownloadStatus.Failed -> state.message ?: "下载失败，可继续重试"
    }
}

private fun formatLocalModelSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 0.1) return "%.1f GB".format(Locale.US, gb)
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(Locale.US, mb)
}

@Composable
private fun ModelOrchestrationContent(
    chatModelName: String,
    visionModels: List<AiModelPreset>,
    imageModels: List<AiModelPreset>,
    selectedVisionModelId: String,
    selectedImageModelId: String,
    onSelectVisionModel: (AiModelPreset) -> Unit,
    onSelectImageModel: (AiModelPreset) -> Unit,
) {
    SettingGroup {
        ApiHintRow(
            title = "智能搭配不同模型",
            description = "文字、识图、生图可以分别指定模型。当前先保存配置，端内识图/生图调用链路后续接入。",
        )
        GroupDivider()
        OrchestrationSlotRow(
            title = "主对话",
            value = chatModelName,
            enabled = true,
            status = "已就绪",
        )
        GroupDivider()
        OrchestrationModelSlotRow(
            title = "识图",
            emptyText = "当前服务商暂无可识图模型",
            models = visionModels,
            selectedModelId = selectedVisionModelId,
            onSelect = onSelectVisionModel,
        )
        GroupDivider()
        OrchestrationModelSlotRow(
            title = "生图",
            emptyText = "当前服务商暂无生图模型",
            models = imageModels,
            selectedModelId = selectedImageModelId,
            onSelect = onSelectImageModel,
        )
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun OrchestrationModelSlotRow(
    title: String,
    emptyText: String,
    models: List<AiModelPreset>,
    selectedModelId: String,
    onSelect: (AiModelPreset) -> Unit,
) {
    var expanded by remember(title, models) { mutableStateOf(false) }
    val selectedModel = models.firstOrNull { it.id == selectedModelId }

    Box {
        OrchestrationSlotRow(
            title = title,
            value = selectedModel?.name ?: emptyText,
            enabled = models.isNotEmpty(),
            status = if (selectedModel != null) "已配置" else "选择",
            onClick = if (models.isNotEmpty()) {
                { expanded = true }
            } else {
                null
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            models.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.name, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                item.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OrchestrationSlotRow(
    title: String,
    value: String,
    enabled: Boolean,
    status: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Text(
            status,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ModelPresetGroup(
    title: String,
    description: String,
    models: List<AiModelPreset>,
    selectedModelId: String,
    providerId: String,
    defaultExpanded: Boolean,
    onSelect: (AiModelPreset) -> Unit,
) {
    if (models.isEmpty()) return
    val idsKey = models.joinToString("|") { it.id }
    var expanded by remember(title, idsKey) { mutableStateOf(defaultExpanded) }
    val selectedModel = models.firstOrNull { it.id == selectedModelId }

    SettingGroup {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    selectedModel?.let { "已选 ${it.name}" } ?: "$description · ${models.size} 个",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            models.forEachIndexed { index, item ->
                GroupDivider()
                ModelPresetRow(
                    item = item,
                    providerId = providerId,
                    selected = item.id == selectedModelId,
                    onClick = { onSelect(item) },
                )
                if (index == models.lastIndex) {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun ModelPresetRow(
    item: AiModelPreset,
    providerId: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val free = item.isFree || item.id.endsWith(":free", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(7.dp))
                ModelBadge(if (free) "免费" else "需 Key", free)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                modelDescription(item, providerId),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            ModelCapabilityStrip(item)
            Spacer(Modifier.height(4.dp))
            Text(
                item.id,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        ModelSelectionIndicator(selected = selected)
    }
}

@Composable
private fun ModelCapabilityStrip(item: AiModelPreset) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CapabilityBadge("对话", enabled = true)
        CapabilityBadge("流式", enabled = item.supportsStreaming)
        CapabilityBadge("工具", enabled = item.supportsTools)
        CapabilityBadge("多模态", enabled = item.supportsVision || item.supportsImageGeneration)
        CapabilityBadge(
            label = if (item.supportsVision) "模型可识图" else "无识图",
            enabled = item.supportsVision,
        )
        CapabilityBadge(
            label = if (item.supportsImageGeneration) "可生图" else "无生图",
            enabled = item.supportsImageGeneration,
        )
    }
}

@Composable
private fun CapabilityBadge(
    label: String,
    enabled: Boolean,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = if (enabled) 0.11f else 0.08f))
            .border(
                1.dp,
                color.copy(alpha = if (enabled) 0.18f else 0.10f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ModelBadge(label: String, free: Boolean) {
    val tint = if (free) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (free) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
            )
            .border(
                1.dp,
                if (free) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ModelSelectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ApiKeyInput(
    providerName: String,
    key: String,
    keyVisible: Boolean,
    onKeyVisibleChange: (Boolean) -> Unit,
    onKeyChange: (String) -> Unit,
) {
    CompactInput(
        label = "API Key",
        value = key,
        placeholder = "填写 $providerName 的 Key",
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onKeyVisibleChange(!keyVisible) }) {
                Icon(
                    imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (keyVisible) "隐藏" else "显示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onValueChange = onKeyChange,
    )
}

@Composable
private fun ApiHintRow(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ApiGuideContent(provider: AiProviderPreset) {
    val uriHandler = LocalUriHandler.current
    val guide = apiGuideForProvider(provider)

    SectionHeader(provider.name)
    SettingGroup {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                guide.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                guide.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(13.dp))
            GuideVisual(guide.visualLabels)
        }
    }

    SectionHeader("步骤")
    SettingGroup {
        guide.steps.forEachIndexed { index, step ->
            if (index > 0) GroupDivider()
            GuideStepRow(index + 1, step)
        }
        GroupDivider()
        ActionSettingRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = "打开官方入口",
            description = guide.url,
            onClick = { uriHandler.openUri(guide.url) },
        )
    }
}

@Composable
private fun ProviderBalanceContent(provider: AiProviderPreset) {
    val uriHandler = LocalUriHandler.current
    val balance = balanceGuideForProvider(provider)

    SectionHeader(provider.name)
    SettingGroup {
        ApiHintRow(
            title = balance.title,
            description = balance.description,
        )
        GroupDivider()
        ActionSettingRow(
            icon = Icons.Outlined.Storage,
            title = "打开余额 / 用量页面",
            description = balance.url,
            onClick = { uriHandler.openUri(balance.url) },
        )
    }

    SectionHeader("说明")
    SettingGroup {
        ApiHintRow(
            title = "账户余额不等于本轮消耗",
            description = "主界面只显示模型接口回传的本轮 token 消耗；账户余额、免费额度和账单限制由服务商后台管理。",
        )
        GroupDivider()
        ApiHintRow(
            title = "后续可做平台适配",
            description = "OpenRouter 这类有余额接口的平台可以接自动查询；其他平台优先跳官方后台，避免显示不准。",
        )
    }
}

@Composable
private fun GuideVisual(labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (index + 1).toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GuideStepRow(
    number: Int,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class ApiGuide(
    val title: String,
    val summary: String,
    val url: String,
    val visualLabels: List<String>,
    val steps: List<String>,
)

private data class BalanceGuide(
    val title: String,
    val description: String,
    val url: String,
)

private fun balanceGuideForProvider(provider: AiProviderPreset): BalanceGuide = when (provider.id) {
    "openrouter" -> BalanceGuide(
        title = "OpenRouter 可查看账户 Credits",
        description = "免费路由通常也需要 Key 来识别用户和限额；付费路由需要账户有余额。",
        url = "https://openrouter.ai/settings/credits",
    )
    "openai" -> BalanceGuide(
        title = "OpenAI 在平台后台查看账单和用量",
        description = "不同组织、项目和计费方式会影响可用额度，Mason 不在本地缓存账户余额。",
        url = "https://platform.openai.com/usage",
    )
    "gemini" -> BalanceGuide(
        title = "Gemini 在 Google AI Studio / Cloud 查看额度",
        description = "免费额度、地区限制和计费开关由 Google 账户和项目决定。",
        url = "https://aistudio.google.com/usage",
    )
    "deepseek" -> BalanceGuide(
        title = "DeepSeek 在开放平台查看余额",
        description = "Flash / Pro 可共用同一个 Key，实际费用和额度以平台账单为准。",
        url = "https://platform.deepseek.com/usage",
    )
    "mimo" -> BalanceGuide(
        title = "MiMo 在小米平台查看用量",
        description = "标准 / Pro 模式可以搭配同一个服务商 Key 使用，额度以小米后台为准。",
        url = "https://mimo.mi.com",
    )
    "siliconflow" -> BalanceGuide(
        title = "SiliconFlow 在控制台查看余额",
        description = "不同开源模型有不同免费/付费规则，调用前先确认模型价格和账户余额。",
        url = "https://cloud.siliconflow.cn/account/balance",
    )
    else -> BalanceGuide(
        title = "自定义中转站以平台后台为准",
        description = "中转站的余额、套餐和限流规则不统一，Mason 只能展示本轮接口回传的 token 消耗。",
        url = "https://platform.openai.com/usage",
    )
}

private fun apiGuideForProvider(provider: AiProviderPreset): ApiGuide = when (provider.id) {
    "openrouter" -> ApiGuide(
        title = "OpenRouter Key 获取流程",
        summary = "OpenRouter 是模型聚合平台。你需要手动选择免费路由或付费路由；Mason 不会根据额度自动从免费切到付费。",
        url = "https://openrouter.ai/settings/keys",
        visualLabels = listOf("注册登录", "创建 Key", "充值额度", "填入 Mason"),
        steps = listOf(
            "打开 OpenRouter，登录后进入 Keys 页面，创建一个新的 API Key。",
            "如果只用 :free 模型，可以先不充值；如果选择 OpenAI via OpenRouter 这类付费路由，需要先给 OpenRouter 账户充值或绑定额度。",
            "复制 Key，回到 Mason 的 AI 服务页，选择 OpenRouter，并在付费模型或需要 Key 的模型下填入。",
        ),
    )
    "openai" -> ApiGuide(
        title = "OpenAI Key 获取流程",
        summary = "适合自备用量和账单。需要在 OpenAI 平台创建 Key，并确保账户有可用额度或组织计费已开启。",
        url = "https://platform.openai.com/api-keys",
        visualLabels = listOf("平台登录", "创建 Key", "确认额度", "填入 Mason"),
        steps = listOf(
            "打开 OpenAI Platform，进入 API keys 页面，创建新的 secret key。",
            "进入 Billing 或 Usage 确认账户额度、组织和项目设置可用。",
            "复制 Key，回到 Mason 选择 OpenAI 服务商，填入 API Key 后测试连接。",
        ),
    )
    "gemini" -> ApiGuide(
        title = "Gemini Key 获取流程",
        summary = "适合 Google Gemini 的 OpenAI 兼容接口。通常从 Google AI Studio 创建 API Key，再按额度和地区规则使用。",
        url = "https://aistudio.google.com/app/apikey",
        visualLabels = listOf("AI Studio", "创建 Key", "选择模型", "填入 Mason"),
        steps = listOf(
            "打开 Google AI Studio 的 API key 页面，选择或创建项目并生成 Key。",
            "确认当前地区、额度和模型权限可用。",
            "复制 Key，回到 Mason 选择 Gemini 服务商，填入 API Key 后测试连接。",
        ),
    )
    "siliconflow" -> ApiGuide(
        title = "硅基流动 Key 获取流程",
        summary = "适合接入开源模型和国内可访问的 OpenAI 兼容接口。部分模型免费，部分模型需要账户余额。",
        url = "https://cloud.siliconflow.cn/account/ak",
        visualLabels = listOf("控制台", "创建 Key", "选择模型", "填入 Mason"),
        steps = listOf(
            "打开硅基流动控制台，进入账号的 API Key 管理页面并创建 Key。",
            "在模型广场或文档里确认要使用的模型名称和是否需要余额。",
            "复制 Key，回到 Mason 选择 SiliconFlow，按模型预设或自定义模型名填入后测试连接。",
        ),
    )
    "deepseek" -> ApiGuide(
        title = "DeepSeek Key 获取流程",
        summary = "适合作为默认后端。DeepSeek 使用 OpenAI 兼容接口，一个 Key 通常可以在同账号可用的 Flash / Pro 模式间手动切换。",
        url = "https://platform.deepseek.com/api_keys",
        visualLabels = listOf("开放平台", "创建 Key", "确认余额", "填入 Mason"),
        steps = listOf(
            "打开 DeepSeek 开放平台，进入 API keys 页面创建新的 Key。",
            "确认账户余额、模型权限和调用限额；Flash / Pro 不是两套 Key，而是同一服务商下的不同模型模式。",
            "复制 Key，回到 Mason 选择 DeepSeek，填入 API Key 后测试连接；主页输入框会显示可快速切换的模式。",
        ),
    )
    "mimo" -> ApiGuide(
        title = "Xiaomi MiMo Key 获取流程",
        summary = "小米 MiMo 提供 OpenAI 兼容接口。V2.5 / V2.5 Pro 使用同一个 Key，Mason 会在主页提供手动模式切换。",
        url = "https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call",
        visualLabels = listOf("MiMo 文档", "创建 Key", "选择模式", "填入 Mason"),
        steps = listOf(
            "打开小米 MiMo 文档或控制台，按官方流程创建 API Key。",
            "确认账号具备 V2.5 系列调用权限；标准 / Pro 会作为同供应商下的可切换模型显示。",
            "复制 Key，回到 Mason 选择 Xiaomi MiMo，填入 API Key 后测试连接。",
        ),
    )
    else -> ApiGuide(
        title = "自定义中转站配置",
        summary = "适合任何 OpenAI Chat Completions 兼容服务。你需要准备 Base URL、API Key 和模型名称。",
        url = "https://platform.openai.com/docs/api-reference/chat",
        visualLabels = listOf("复制地址", "复制 Key", "模型名称", "填入 Mason"),
        steps = listOf(
            "从中转站后台复制 OpenAI 兼容的 API 地址，通常以 /v1 结尾。",
            "复制中转站提供的 API Key，并确认账户额度和模型权限。",
            "把 API 地址、Key、模型名称填入 Mason；只有确认支持工具调用时再打开手机工具开关。",
        ),
    )
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "白天"
    ThemeMode.DARK -> "夜间"
}

private fun islandSummary(preferences: UiPreferences): String {
    if (!preferences.notificationIslandEnabled) return "已关闭"
    val vendor = when (preferences.islandVendorMode) {
        IslandVendorMode.AUTO -> "自动"
        IslandVendorMode.XIAOMI -> "小米"
        IslandVendorMode.VIVO -> "vivo"
        IslandVendorMode.OPPO -> "OPPO"
    }
    val completion = if (preferences.notifyOnTaskComplete) "完成后通知" else "仅基础通知"
    val payment = if (preferences.notifyOnPaymentSuccess) "支付通知" else "无支付通知"
    return "$vendor · $completion · $payment"
}

private fun officialChannelSummary(preferences: OfficialChannelPreferences): String {
    val enabled = listOfNotNull(
        "微信".takeIf { preferences.wechatOfficialEnabled },
        "支付宝".takeIf { preferences.alipayMcpEnabled },
        "美团".takeIf { preferences.meituanMcpEnabled },
    )
    if (enabled.isEmpty()) return "未开启"
    return enabled.joinToString(" / ")
}

private fun apiStatusLabel(config: ApiConfig): String {
    val requiresKey = AiProviderCatalog.requiresApiKey(config)
    return when {
        !requiresKey -> "本地"
        config.apiKey.isBlank() -> "需 Key"
        !AiProviderCatalog.isVerified(config) -> "待验证"
        else -> "Key 已配置"
    }
}

private fun inferMemoryType(label: String, value: String): UserMemoryType {
    val text = "$label $value"
    return when {
        "车牌" in text || Regex("[\\u4e00-\\u9fa5][A-Z0-9]{5,7}").containsMatchIn(text) ->
            UserMemoryType.LICENSE_PLATE
        "身份证" in text || "身份" in text -> UserMemoryType.IDENTITY
        "地址" in text || "小区" in text || "公司" in text || "家" in text -> UserMemoryType.ADDRESS
        "支付" in text || "支付宝" in text || "微信" in text -> UserMemoryType.PAYMENT
        else -> UserMemoryType.OTHER
    }
}

private fun modelDescription(item: AiModelPreset, providerId: String): String {
    if (providerId == "openrouter" && item.id == "~openai/gpt-latest") {
        return "OpenRouter 的 OpenAI 付费路由，需要 OpenRouter Key 和余额，不是免费 OpenAI 模型"
    }
    return item.description
}

@Composable
private fun ThemeModeSetting(
    selectedMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(settingsGlassBrush())
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text("深浅色模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "参考系统外观，也可以手动固定白天或夜间模式。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeModeChoice("跟随", ThemeMode.SYSTEM, selectedMode, onModeChange, Modifier.weight(1f))
            ThemeModeChoice("白天", ThemeMode.LIGHT, selectedMode, onModeChange, Modifier.weight(1f))
            ThemeModeChoice("夜间", ThemeMode.DARK, selectedMode, onModeChange, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun ThemeModeChoice(
    label: String,
    mode: ThemeMode,
    selectedMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedMode == mode
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp),
            )
            .clickable { onModeChange(mode) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AccentColorSetting(
    selectedColor: Long,
    customAccent: String,
    onCustomAccentChange: (String) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onInvalidColor: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(settingsGlassBrush())
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text("主题色", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "纯色预设和自定义取色都会作为 Mason 的主色调。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MasonAccentPresets.chunked(4).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowPresets.forEach { preset ->
                        ColorSwatch(
                            name = preset.name,
                            color = preset.color,
                            selected = selectedColor == preset.color,
                            onClick = { onAccentColorChange(preset.color) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - rowPresets.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background((customAccent.parseRgbColor() ?: selectedColor).toComposeColor())
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), CircleShape),
            )
            OutlinedTextField(
                value = customAccent,
                onValueChange = onCustomAccentChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("#4FC3F7") },
                colors = themedFieldColors(),
                shape = RoundedCornerShape(8.dp),
            )
            Button(
                onClick = {
                    val parsed = customAccent.parseRgbColor()
                    if (parsed == null) {
                        onInvalidColor()
                    } else {
                        onAccentColorChange(parsed)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(48.dp),
            ) {
                Text("应用", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun NotificationIslandSetting(
    islandEnabled: Boolean,
    deliveryMode: NotificationDeliveryMode,
    notifyOnTaskComplete: Boolean,
    notifyOnPaymentSuccess: Boolean,
    islandVendorMode: IslandVendorMode,
    onIslandEnabledChange: (Boolean) -> Unit,
    onDeliveryModeChange: (NotificationDeliveryMode) -> Unit,
    onNotifyOnTaskCompleteChange: (Boolean) -> Unit,
    onNotifyOnPaymentSuccessChange: (Boolean) -> Unit,
    onIslandVendorModeChange: (IslandVendorMode) -> Unit,
) {
    SettingGroup {
        SwitchSettingRow(
            title = "启用通知",
            description = "事务完成、支付结果等提醒都受这个总开关控制",
            checked = islandEnabled,
            onCheckedChange = onIslandEnabledChange,
        )

        if (!islandEnabled) return@SettingGroup

        GroupDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text("通知方式", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                "常规通知最稳定；通知岛通知会按小米、vivo、OPPO 的岛形样式预留展示。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotificationModeChoice(
                    label = "常规通知",
                    mode = NotificationDeliveryMode.REGULAR,
                    selectedMode = deliveryMode,
                    onModeChange = onDeliveryModeChange,
                    modifier = Modifier.weight(1f),
                )
                NotificationModeChoice(
                    label = "通知岛通知",
                    mode = NotificationDeliveryMode.ISLAND,
                    selectedMode = deliveryMode,
                    onModeChange = onDeliveryModeChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        GroupDivider()
        SwitchSettingRow(
            title = "事务完成后通知",
            description = "调用工具、生成文件或自动化执行完成后发送提醒",
            checked = notifyOnTaskComplete,
            onCheckedChange = onNotifyOnTaskCompleteChange,
        )

        GroupDivider()
        SwitchSettingRow(
            title = "支付成功通知",
            description = "支付完成后展示成功状态；读取支付结果需要系统授权或官方回调",
            checked = notifyOnPaymentSuccess,
            onCheckedChange = onNotifyOnPaymentSuccessChange,
        )

        if (deliveryMode == NotificationDeliveryMode.ISLAND) {
            GroupDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text("通知岛样式", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "自动会优先按当前手机品牌选择，也可以手动固定厂商样式。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    IslandVendorChoice("自动", IslandVendorMode.AUTO, islandVendorMode, onIslandVendorModeChange, Modifier.weight(1f))
                    IslandVendorChoice("小米", IslandVendorMode.XIAOMI, islandVendorMode, onIslandVendorModeChange, Modifier.weight(1f))
                    IslandVendorChoice("vivo", IslandVendorMode.VIVO, islandVendorMode, onIslandVendorModeChange, Modifier.weight(1f))
                    IslandVendorChoice("OPPO", IslandVendorMode.OPPO, islandVendorMode, onIslandVendorModeChange, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NotificationModeChoice(
    label: String,
    mode: NotificationDeliveryMode,
    selectedMode: NotificationDeliveryMode,
    onModeChange: (NotificationDeliveryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedMode == mode
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp),
            )
            .clickable { onModeChange(mode) }
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IslandVendorChoice(
    label: String,
    mode: IslandVendorMode,
    selectedMode: IslandVendorMode,
    onModeChange: (IslandVendorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedMode == mode
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp),
            )
            .clickable { onModeChange(mode) }
            .padding(horizontal = 7.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColorSwatch(
    name: String,
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.toComposeColor())
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = swatchContentColor(color),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 7.dp, start = 3.dp),
    )
}

@Composable
private fun SettingGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(settingsGlassBrush())
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            ),
        content = content,
    )
}

@Composable
private fun settingsGlassBrush(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.11f),
    )
}

@Composable
private fun DropdownSettingRow(
    title: String,
    value: String,
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Box {
        SettingRow(
            title = title,
            value = value,
            description = description,
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            menuContent()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
        value,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.64f),
        )
    }
}

@Composable
private fun CompactInput(
    label: String,
    value: String,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = themedFieldColors(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        destructive -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) tint else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun StatusText(
    message: String?,
    success: Boolean?,
) {
    if (message == null) return
    Text(
        text = message,
        color = when (success) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun AboutSettingsContent(
    appVersion: String,
    onOpenCacheClean: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    SettingGroup {
        AboutRow("版本号", appVersion)
        GroupDivider()
        AboutRow("项目地址", "github.com/denggl2/MASON")
        GroupDivider()
        AboutRow("开源协议", "MIT License")
    }

    SectionHeader("更新")
    SettingGroup {
        ActionSettingRow(
            icon = Icons.Outlined.Refresh,
            title = "检查更新",
            description = "打开 GitHub Releases，查看是否有新版本",
            onClick = { uriHandler.openUri("https://github.com/DENGGL2/MASON/releases") },
        )
        GroupDivider()
        ActionSettingRow(
            icon = Icons.Outlined.Info,
            title = "查看版本日志",
            description = "查看近期提交和版本变化",
            onClick = { uriHandler.openUri("https://github.com/DENGGL2/MASON/commits/main") },
        )
    }

    SectionHeader("本机")
    SettingGroup {
        ActionSettingRow(
            icon = Icons.Outlined.Delete,
            title = "清除缓存",
            description = "查看临时缓存、运行缓存和崩溃记录后再清理",
            onClick = onOpenCacheClean,
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CacheCleanDialog(
    state: CacheOverviewUiState,
    onRefresh: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("缓存清理") },
        text = {
            Column {
                if (state.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在扫描缓存...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        CacheCategoryRow(item)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClean()
                    onDismiss()
                },
                enabled = !state.isLoading,
            ) {
                Text("立即清理", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text("重新扫描")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

@Composable
private fun CacheCategoryRow(item: CacheCategoryUiState) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = if (item.clearable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatBytes(item.sizeBytes),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (!item.clearable) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "不会被本次清理删除",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

@Composable
private fun themedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun Long.toRgbHex(): String = "#%06X".format((this and 0xFFFFFF).toInt())

private fun String.parseRgbColor(): Long? {
    val raw = trim().removePrefix("#")
    if (raw.length != 6) return null
    return raw.toLongOrNull(16)?.let { 0xFF000000 or it }
}

private fun swatchContentColor(color: Long): Color {
    val red = ((color shr 16) and 0xFFL) / 255.0
    val green = ((color shr 8) and 0xFFL) / 255.0
    val blue = (color and 0xFFL) / 255.0
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return if (luminance > 0.58) Color(0xFF111318) else Color.White
}
