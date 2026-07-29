package com.denggl2.mason.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.AiProviderKind
import com.denggl2.mason.data.AiProviderPreset
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.connectionForProvider
import com.denggl2.mason.data.connectionIdForProvider
import com.denggl2.mason.data.configuredConnections
import com.denggl2.mason.data.resolvedChatModelRef
import com.denggl2.mason.data.resolvedImageModelRef
import com.denggl2.mason.data.resolvedVisionModelRef
import com.denggl2.mason.data.saveConnection
import com.denggl2.mason.data.selectChatModel
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelDownloadState
import com.denggl2.mason.data.LocalModelDownloadStatus
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelPreset
import com.denggl2.mason.data.MasonAccentPresets
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.OfficialChannelPreferences
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.data.UserMemoryItem
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.data.toComposeColor
import java.util.Locale
import kotlinx.coroutines.delay

private enum class SettingsPage {
    Overview,
    ModelSettings,
    AiServiceDetail,
    Memory,
    OfficialChannels,
    About,
}

private enum class TaskNotificationMode(
    val label: String,
) {
    Regular("启用常规通知"),
    Island("启用岛通知"),
    Disabled("不启用"),
}

private enum class AiModelPurpose(val title: String, val actionLabel: String) {
    Chat("选择聊天模型", "设为聊天模型"),
    Vision("选择识图模型", "设为识图模型"),
    ImageGeneration("选择生图模型", "设为生图模型"),
    LocalFallback("选择本地备用", "设为本地备用"),
}

private data class RemoteModelCandidate(
    val connection: ApiConnection,
    val provider: AiProviderPreset,
    val model: AiModelPreset,
) {
    constructor(provider: AiProviderPreset, model: AiModelPreset) : this(
        connection = ApiConnection(
            id = connectionIdForProvider(provider.id),
            providerId = provider.id,
            name = provider.name,
            apiUrl = provider.apiUrl,
            modelIds = listOf(model.id),
        ),
        provider = provider,
        model = model,
    )
}

private const val LOCAL_PROVIDER_ID = "local"
private const val LIQUID_GLASS_STYLE_VISIBLE = false

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
    onInterfaceStyleChange: (InterfaceStyle) -> Unit = {},
    onLiquidGlassTransparencyChange: (Float) -> Unit = {},
    onAccentColorChange: (Long) -> Unit = {},
    onRegularNotificationsChange: (Boolean) -> Unit = {},
    onIslandNotificationsChange: (Boolean) -> Unit = {},
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
    val uriHandler = LocalUriHandler.current

    var page by remember { mutableStateOf(SettingsPage.Overview) }
    var providerId by remember(config) { mutableStateOf(config.providerId) }
    var url by remember(config) { mutableStateOf(config.apiUrl) }
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var visionModel by remember(config) { mutableStateOf(config.visionModel) }
    var imageModel by remember(config) { mutableStateOf(config.imageModel) }
    var localModel by remember(config) { mutableStateOf(config.localModel) }
    var offlineFallbackEnabled by remember(config) { mutableStateOf(config.offlineFallbackEnabled) }
    var dynamicLocalRoutingEnabled by remember(config) { mutableStateOf(config.dynamicLocalRoutingEnabled) }
    var phoneToolsEnabled by remember(config) { mutableStateOf(config.phoneToolsEnabled) }
    var toolsEnabled by remember(config) { mutableStateOf(config.toolsEnabled) }
    var requireToolConfirmation by remember(config) { mutableStateOf(config.requireToolConfirmation) }
    var keyVisible by remember { mutableStateOf(false) }
    var detailProviderId by remember { mutableStateOf(config.providerId) }
    var detailUrl by remember { mutableStateOf(config.apiUrl) }
    var detailKey by remember { mutableStateOf(config.apiKey) }
    var detailToolsEnabled by remember { mutableStateOf(config.toolsEnabled) }
    var detailWorkspaceId by remember { mutableStateOf("") }
    var detailEndpointId by remember { mutableStateOf("") }
    var detailModelId by remember { mutableStateOf("") }
    var manualModelId by remember { mutableStateOf("") }
    var showManualModelDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var officialDetail by remember { mutableStateOf<OfficialChannelDetail?>(null) }
    var pendingLocalModelDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingRemoteModelDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingMemoryDeleteId by remember { mutableStateOf<String?>(null) }
    var editingMemoryId by remember { mutableStateOf<String?>(null) }
    var memoryEditorExpanded by remember { mutableStateOf(false) }
    var memoryLabel by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }
    var memoryType by remember { mutableStateOf(UserMemoryType.LICENSE_PLATE) }
    var memorySensitive by remember { mutableStateOf(true) }
    val overviewScrollState = rememberScrollState()
    val modelSettingsScrollState = rememberScrollState()
    val aiServiceDetailScrollState = rememberScrollState()
    val memoryScrollState = rememberScrollState()
    val officialChannelsScrollState = rememberScrollState()
    val aboutScrollState = rememberScrollState()
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
    val detailProvider = AiProviderCatalog.getProvider(detailProviderId)
    val detailCatalogModels = when {
        detailProvider?.id == modelRefreshState.providerId && modelRefreshState.models.isNotEmpty() -> {
            modelRefreshState.models + detailProvider?.modelOptions.orEmpty().filterNot { builtIn ->
                builtIn.isFree || modelRefreshState.models.any { it.id == builtIn.id }
            }
        }
        else -> detailProvider?.modelOptions.orEmpty()
    }
    val detailModelOptions = if (
        detailProviderId == providerId &&
        model.isNotBlank() &&
        detailCatalogModels.none { it.id == model }
    ) {
        listOf(
            AiModelPreset(
                id = model,
                name = model,
                description = "手动添加的模型",
                supportsTools = toolsEnabled,
            ),
        ) + detailCatalogModels
    } else {
        detailCatalogModels
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
        nextDynamicLocalRoutingEnabled: Boolean = dynamicLocalRoutingEnabled,
        nextPhoneToolsEnabled: Boolean = phoneToolsEnabled,
        nextChatModelRef: ModelReference? = config.chatModelRef,
        nextVisionModelRef: ModelReference? = config.visionModelRef,
        nextImageModelRef: ModelReference? = config.imageModelRef,
    ) {
        viewModel.save(
            config.copy(
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
                dynamicLocalRoutingEnabled = nextDynamicLocalRoutingEnabled,
                phoneToolsEnabled = nextPhoneToolsEnabled,
                chatModelRef = nextChatModelRef,
                visionModelRef = nextVisionModelRef,
                imageModelRef = nextImageModelRef,
            ),
        )
    }

    // Do not rewrite DataStore for every character typed into editable API fields.
    LaunchedEffect(url) {
        delay(350)
        if (url != config.apiUrl) persistApiConfig(nextUrl = url)
    }
    LaunchedEffect(key) {
        delay(350)
        if (key != config.apiKey) persistApiConfig(nextKey = key)
    }
    LaunchedEffect(model) {
        delay(350)
        if (model != config.model) persistApiConfig(nextModel = model)
    }
    LaunchedEffect(imageModel) {
        delay(350)
        if (imageModel != config.imageModel) persistApiConfig(nextImageModel = imageModel)
    }

    fun clearMemoryEditor() {
        editingMemoryId = null
        memoryEditorExpanded = false
        memoryLabel = ""
        memoryValue = ""
        memoryType = UserMemoryType.OTHER
        memorySensitive = true
    }

    fun openProviderDetail(id: String) {
        val item = AiProviderCatalog.getProvider(id)
        val savedConnection = config.connectionForProvider(id)
        detailProviderId = id
        detailUrl = when {
            id == AiProviderCatalog.CUSTOM_PROVIDER_ID -> savedConnection?.apiUrl.orEmpty()
            savedConnection != null && savedConnection.apiUrl != item?.apiUrl -> savedConnection.apiUrl
            else -> ""
        }
        detailKey = savedConnection?.apiKey.orEmpty()
        detailToolsEnabled = savedConnection?.toolsSupported ?: item?.toolsEnabledByDefault ?: true
        detailWorkspaceId = savedConnection?.workspaceId.orEmpty()
        detailEndpointId = item?.endpoints
            ?.firstOrNull { it.apiUrl == savedConnection?.apiUrl }
            ?.id
            ?: item?.endpoints?.firstOrNull()?.id.orEmpty()
        detailModelId = savedConnection?.modelIds?.firstOrNull()
            ?: model.takeIf { id == providerId && it.isNotBlank() }
            ?: item?.defaultModel.orEmpty()
        manualModelId = ""
        keyVisible = false
        viewModel.clearApiTestState()
        page = SettingsPage.AiServiceDetail
    }

    fun navigateBack() {
        page = when (page) {
            SettingsPage.Overview -> {
                onBack()
                SettingsPage.Overview
            }
            SettingsPage.AiServiceDetail -> SettingsPage.ModelSettings
            SettingsPage.ModelSettings -> SettingsPage.Overview
            else -> SettingsPage.Overview
        }
    }

    BackHandler(onBack = ::navigateBack)

    LaunchedEffect(providerId, modelRefreshState.models) {
        if (providerId == "openrouter" && modelRefreshState.models.isNotEmpty()) {
            val currentStillExists = modelRefreshState.models.any { it.id == model }
            if (!currentStillExists && selectedModel?.isFree == true) {
                val nextModel = modelRefreshState.models.first().id
                model = nextModel
                toolsEnabled = false
                persistApiConfig(
                    nextModel = nextModel,
                    nextToolsEnabled = false,
                    nextChatModelRef = ModelReference(
                        config.resolvedChatModelRef().connectionId,
                        nextModel,
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
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
                        when (page) {
                            SettingsPage.AiServiceDetail -> if (detailProviderId == LOCAL_PROVIDER_ID) {
                                "本地模型"
                            } else if (detailProviderId == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                                "中转站"
                            } else {
                                detailProvider?.name ?: "服务商"
                            }
                            else -> settingsPageTitle(page)
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = if (
                            page == SettingsPage.ModelSettings ||
                            page == SettingsPage.AiServiceDetail
                        ) 18.sp else 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::navigateBack,
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
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val movingForward = settingsPageDepth(targetState) > settingsPageDepth(initialState)
                if (movingForward) {
                    (slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it / 4 },
                        ) + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetX = { -it / 4 },
                    ) + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            targetOffsetX = { it },
                        ) + fadeOut(tween(150)))
                }
            },
            label = "settings-page",
        ) { targetPage ->
            val targetScrollState = when (targetPage) {
                SettingsPage.Overview -> overviewScrollState
                SettingsPage.ModelSettings -> modelSettingsScrollState
                SettingsPage.AiServiceDetail -> aiServiceDetailScrollState
                SettingsPage.Memory -> memoryScrollState
                SettingsPage.OfficialChannels -> officialChannelsScrollState
                SettingsPage.About -> aboutScrollState
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .verticalScroll(targetScrollState)
                        .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
                ) {
            if (targetPage == SettingsPage.Overview) {
                SectionHeader("外观")
                AppearanceSettingsContent(
                    selectedMode = uiPreferences.themeMode,
                    selectedStyle = uiPreferences.interfaceStyle,
                    liquidGlassTransparency = uiPreferences.liquidGlassTransparency,
                    selectedColor = uiPreferences.accentColor,
                    onModeChange = onThemeModeChange,
                    onStyleChange = onInterfaceStyleChange,
                    onLiquidGlassTransparencyChange = onLiquidGlassTransparencyChange,
                    onAccentColorChange = onAccentColorChange,
                )
                AiServiceOverviewContent(
                    localModelId = localModel,
                    config = config,
                    localModelStates = localModelStates,
                    onSelectRemoteModel = { purpose, candidate ->
                        val reference = ModelReference(candidate.connection.id, candidate.model.id)
                        when (purpose) {
                            AiModelPurpose.Chat -> {
                                val next = config.selectChatModel(reference).copy(
                                    localModelDirectEnabled = false,
                                )
                                providerId = next.providerId
                                url = next.apiUrl
                                key = next.apiKey
                                model = next.model
                                toolsEnabled = next.toolsEnabled
                                viewModel.save(next)
                            }
                            AiModelPurpose.Vision -> {
                                visionModel = candidate.model.id
                                persistApiConfig(
                                    nextVisionModel = candidate.model.id,
                                    nextVisionModelRef = reference,
                                )
                            }
                            AiModelPurpose.ImageGeneration -> {
                                imageModel = candidate.model.id
                                persistApiConfig(
                                    nextImageModel = candidate.model.id,
                                    nextImageModelRef = reference,
                                )
                            }
                            AiModelPurpose.LocalFallback -> Unit
                        }
                    },
                    onSelectLocalModel = { item ->
                        localModel = item.id
                        persistApiConfig(nextLocalModel = item.id)
                    },
                    onOpenModelSettings = { page = SettingsPage.ModelSettings },
                )
                SettingsOverviewContent(
                    regularNotificationsEnabled = uiPreferences.regularNotificationsEnabled,
                    islandNotificationsEnabled = uiPreferences.islandNotificationsEnabled,
                    memoryCount = memoryItems.size,
                    onNotificationModeChange = { mode ->
                        when (mode) {
                            TaskNotificationMode.Regular -> {
                                onRegularNotificationsChange(true)
                                onIslandNotificationsChange(false)
                            }
                            TaskNotificationMode.Island -> {
                                // Keep the conventional channel on as the fallback below Android 16.
                                onRegularNotificationsChange(true)
                                onIslandNotificationsChange(true)
                            }
                            TaskNotificationMode.Disabled -> {
                                onRegularNotificationsChange(false)
                                onIslandNotificationsChange(false)
                            }
                        }
                    },
                    onOpenMemory = { page = SettingsPage.Memory },
                )
                AiServiceOtherSettingsContent(
                    config = config,
                    backgroundExecutionEnabled = automationPreferences.backgroundExecutionEnabled,
                    onOfflineFallbackChange = {
                        offlineFallbackEnabled = it
                        persistApiConfig(nextOfflineFallbackEnabled = it)
                    },
                    onDynamicLocalRoutingChange = {
                        dynamicLocalRoutingEnabled = it
                        persistApiConfig(nextDynamicLocalRoutingEnabled = it)
                    },
                    onHighRiskConfirmationChange = {
                        requireToolConfirmation = it
                        persistApiConfig(nextRequireToolConfirmation = it)
                    },
                    onBackgroundExecutionChange = viewModel::setBackgroundAutomationEnabled,
                    onPhoneToolsChange = {
                        phoneToolsEnabled = it
                        persistApiConfig(nextPhoneToolsEnabled = it)
                    },
                )
                SettingsSecondaryContent(
                    onOpenPermission = onNavigateToPermission,
                    onOpenAbout = { page = SettingsPage.About },
                )
            }

            if (targetPage == SettingsPage.ModelSettings) {
                ModelSettingsContent(
                    config = config,
                    localModelStates = localModelStates,
                    onOpenProvider = { id -> openProviderDetail(id) },
                )
            }

            if (targetPage == SettingsPage.AiServiceDetail) {
                val isLocalProvider = detailProviderId == LOCAL_PROVIDER_ID
                if (!isLocalProvider && detailProvider != null) {
                        val resolvedDetailUrl = resolveProviderDraftUrl(
                            detailProvider,
                            detailUrl,
                            detailEndpointId,
                        )
                        val savedConnection = config.connectionForProvider(detailProvider.id)
                        val detailModel = detailModelId.trim()
                        val detailRequiresKey = AiProviderCatalog.requiresApiKey(
                            providerId = detailProvider.id,
                            apiUrl = resolvedDetailUrl,
                            modelId = detailModel,
                        )
                        val detailHasRequiredValues = isApiDraftTestable(
                            apiUrl = resolvedDetailUrl,
                            apiKey = detailKey,
                            modelId = detailModel,
                            requiresApiKey = detailRequiresKey,
                        )
                        val detailIsSaved = savedConnection != null &&
                            resolvedDetailUrl == savedConnection.apiUrl &&
                            detailKey == savedConnection.apiKey &&
                            detailModel in savedConnection.modelIds &&
                            detailToolsEnabled == savedConnection.toolsSupported &&
                            detailWorkspaceId == savedConnection.workspaceId
                        val detailCanTest = detailHasRequiredValues

                        ConnectionConfigurationState(
                            isSaved = detailIsSaved,
                            isVerified = detailIsSaved && savedConnection?.verifiedSignature?.isNotBlank() == true,
                            missingApiKey = detailRequiresKey && detailKey.isBlank(),
                            missingModelId = detailModel.isBlank(),
                        )
                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            if (detailProvider.endpoints.size > 1) {
                                ProviderEndpointSelector(
                                    provider = detailProvider,
                                    selectedEndpointId = detailEndpointId,
                                    onSelect = {
                                        detailEndpointId = it
                                        viewModel.clearApiTestState()
                                    },
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            SectionHeader("接口地址")
                            CompactInput(
                                label = if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                                    ""
                                } else {
                                    "默认地址"
                                },
                                value = if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                                    detailUrl
                                } else {
                                    ""
                                },
                                placeholder = resolvedDetailUrl,
                                readOnly = detailProvider.id != AiProviderCatalog.CUSTOM_PROVIDER_ID,
                            ) {
                                detailUrl = it
                                viewModel.clearApiTestState()
                            }
                            if (detailRequiresKey) {
                                SectionHeader("API Key")
                                CompactInput(
                                    label = "",
                                    value = detailKey,
                                    placeholder = "填写 ${detailProvider.name} 的 Key",
                                    visualTransformation = if (keyVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { keyVisible = !keyVisible }) {
                                            Icon(
                                                imageVector = if (keyVisible) {
                                                    Icons.Outlined.VisibilityOff
                                                } else {
                                                    Icons.Outlined.Visibility
                                                },
                                                contentDescription = if (keyVisible) "隐藏" else "显示",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                ) {
                                    detailKey = it
                                    viewModel.clearApiTestState()
                                }
                            }
                            if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                                SectionHeader("Model ID")
                                CompactInput(
                                    label = "",
                                    value = detailModelId,
                                    placeholder = "例如 gpt-4o-mini",
                                ) {
                                    detailModelId = it
                                    viewModel.clearApiTestState()
                                }
                            }
                            if (detailProvider.supportsWorkspaceId) {
                                SectionHeader("Workspace ID")
                                CompactInput(
                                    label = "可选",
                                    value = detailWorkspaceId,
                                    placeholder = "仅使用百炼子业务空间时填写",
                                ) {
                                    detailWorkspaceId = it
                                    viewModel.clearApiTestState()
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveConnection(
                                        ApiConnection(
                                            id = connectionIdForProvider(detailProvider.id),
                                            providerId = detailProvider.id,
                                            name = savedConnection?.name ?: detailProvider.name,
                                            apiUrl = resolvedDetailUrl,
                                            apiKey = detailKey,
                                            modelIds = buildList {
                                                addAll(savedConnection?.modelIds.orEmpty())
                                                if (detailModel.isNotBlank()) add(detailModel)
                                            }.distinct(),
                                            toolsSupported = detailToolsEnabled,
                                            verifiedSignature = savedConnection?.verifiedSignature.orEmpty(),
                                            workspaceId = detailWorkspaceId,
                                        ),
                                    )
                                },
                                enabled = detailHasRequiredValues && !detailIsSaved && !apiTestState.isTesting,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("保存配置")
                            }
                            OutlinedButton(
                                onClick = {
                                    val draftConnection = ApiConnection(
                                        id = connectionIdForProvider(detailProvider.id),
                                        providerId = detailProvider.id,
                                        name = savedConnection?.name ?: detailProvider.name,
                                        apiUrl = resolvedDetailUrl,
                                        apiKey = detailKey,
                                        modelIds = buildList {
                                            addAll(savedConnection?.modelIds.orEmpty())
                                            if (detailModel.isNotBlank()) add(detailModel)
                                        }.distinct(),
                                        toolsSupported = detailToolsEnabled,
                                        verifiedSignature = savedConnection?.verifiedSignature.orEmpty(),
                                        workspaceId = detailWorkspaceId,
                                    )
                                    viewModel.testApi(
                                        config.saveConnection(draftConnection).copy(
                                            providerId = detailProvider.id,
                                            apiUrl = resolvedDetailUrl,
                                            apiKey = detailKey,
                                            model = detailModel,
                                            toolsEnabled = detailToolsEnabled,
                                        ),
                                    )
                                },
                                enabled = detailCanTest && !apiTestState.isTesting,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                if (apiTestState.isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text("测试连接")
                                }
                            }
                        }
                        StatusText(message = apiTestState.message, success = apiTestState.success)
                        StatusText(message = apiTestState.capabilityWarning, success = false)

                        if (detailProviderId == providerId && alwaysAllowedTools.isNotEmpty()) {
                            SectionHeader("已记住的工具授权")
                            SettingGroup {
                                alwaysAllowedTools.forEachIndexed { index, toolName ->
                                    if (index > 0) GroupDivider()
                                    ActionSettingRow(
                                        title = toolName,
                                        description = "已设为总是允许；点击撤销",
                                        onClick = { viewModel.revokeToolGrant(toolName) },
                                    )
                                }
                            }
                        }
                    }

                if (isLocalProvider) {
                        SectionHeader("本地模型")
                        LocalModelFallbackContent(
                            models = LocalModelCatalog.models,
                            selectedModelId = localModel,
                            states = localModelStates,
                            downloadStates = localModelDownloadStates,
                            onSelect = { item ->
                                val state = localModelStates.firstOrNull { it.modelId == item.id }
                                if (state?.installed == true) {
                                    localModel = item.id
                                    persistApiConfig(nextLocalModel = item.id)
                                }
                            },
                            onDownload = { item ->
                                viewModel.downloadLocalModel(item.id)
                            },
                            onPauseDownload = { item -> viewModel.pauseLocalModelDownload(item.id) },
                            onDelete = { item -> pendingLocalModelDeleteId = item.id },
                        )
                } else if (detailProvider != null) {
                    val resolvedDetailUrl = resolveProviderDraftUrl(
                        detailProvider,
                        detailUrl,
                        detailEndpointId,
                    )
                    val requiresKey = AiProviderCatalog.requiresApiKey(
                        detailProvider.id,
                        resolvedDetailUrl,
                        detailModelId.ifBlank { detailProvider.defaultModel },
                    )
                    val hasConnectionValues = resolvedDetailUrl.isNotBlank() &&
                        (!requiresKey || detailKey.isNotBlank())
                    val savedModelIds = config.connectionForProvider(detailProvider.id)?.modelIds.orEmpty()
                    val visibleModels = buildList {
                        addAll(detailModelOptions)
                        savedModelIds.filterNot { savedId -> detailModelOptions.any { it.id == savedId } }
                            .forEach { savedId ->
                                add(
                                    AiModelPreset(
                                        id = savedId,
                                        name = savedId,
                                        description = "手动添加的模型",
                                        supportsVision = true,
                                        supportsImageGeneration = true,
                                    ),
                                )
                            }
                    }.distinctBy(AiModelPreset::id)

                    if (hasConnectionValues) {
                        SectionHeader(if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) "模型" else "可选模型")
                        SettingGroup {
                            if (visibleModels.isEmpty()) {
                                ApiHintRow(
                                    title = "暂无模型",
                                    description = "使用下方的添加或拉取功能加入模型。",
                                )
                            } else {
                                visibleModels.forEachIndexed { index, item ->
                                    if (index > 0) GroupDivider(horizontalPadding = 14.dp)
                                    ProviderModelChoiceRow(
                                        providerId = detailProvider.id,
                                        model = item,
                                        selected = item.id == detailModelId,
                                        onSelect = {
                                            detailModelId = item.id
                                            viewModel.clearApiTestState()
                                        },
                                        onDelete = if (
                                            detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID &&
                                            item.id in savedModelIds
                                        ) {
                                            { pendingRemoteModelDeleteId = item.id }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                            if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                                if (visibleModels.isNotEmpty()) GroupDivider(horizontalPadding = 14.dp)
                                CompactActionRow(
                                    title = "+ 添加",
                                    description = "添加另一个 Model ID",
                                    enabled = true,
                                    onClick = { showManualModelDialog = true },
                                )
                            }
                        }

                        SectionHeader("拉取远程模型")
                        SettingGroup {
                            CompactActionRow(
                                title = "拉取远程模型",
                                description = "从当前接口读取可用模型",
                                enabled = !modelRefreshState.isRefreshing,
                                isLoading = modelRefreshState.isRefreshing,
                                onClick = {
                                    viewModel.refreshRemoteModels(
                                        providerId = detailProvider.id,
                                        apiUrl = resolvedDetailUrl,
                                        apiKey = detailKey,
                                        workspaceId = detailWorkspaceId,
                                    )
                                },
                            )
                        }
                        StatusText(
                            message = modelRefreshState.message,
                            success = modelRefreshState.success,
                        )
                    }

                    SectionHeader("服务商")
                    SettingGroup {
                        if (detailProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
                            ApiHintRow(
                                title = "中转站",
                                description = "使用你填写的 OpenAI 兼容接口。",
                            )
                        } else {
                            ActionSettingRow(
                                title = "打开官方入口",
                                description = officialEntryUrl(detailProvider),
                                onClick = { uriHandler.openUri(officialEntryUrl(detailProvider)) },
                            )
                        }
                    }
                }
            }

            if (targetPage == SettingsPage.Memory) {
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
                onDelete = { id -> pendingMemoryDeleteId = id },
            )
            }

            if (targetPage == SettingsPage.OfficialChannels) {
            SectionHeader("官方通道")

            OfficialChannelSettingsContent(
                preferences = officialChannels,
                onAlipayChange = viewModel::setAlipayMcpEnabled,
                onOpenDetail = { officialDetail = it },
            )
            }

            if (targetPage == SettingsPage.About) {
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
        }
    }

    pendingMemoryDeleteId?.let { memoryId ->
        AlertDialog(
            onDismissRequest = { pendingMemoryDeleteId = null },
            title = { Text("删除这条记忆？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMemory(memoryId)
                        pendingMemoryDeleteId = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMemoryDeleteId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCacheDialog) {
        CacheCleanDialog(
            state = cacheOverviewState,
            onRefresh = viewModel::refreshCacheOverview,
            onClean = viewModel::clearCache,
            onDismiss = { showCacheDialog = false },
        )
    }

    if (showManualModelDialog && detailProvider != null) {
        AlertDialog(
            onDismissRequest = { showManualModelDialog = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("添加 Model ID") },
            text = {
                OutlinedTextField(
                    value = manualModelId,
                    onValueChange = { manualModelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("例如 model-name") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = themedFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nextModel = manualModelId.trim()
                        if (nextModel.isNotEmpty()) {
                            val existing = config.connectionForProvider(detailProvider.id)
                            val nextUrl = resolveProviderDraftUrl(
                                detailProvider,
                                detailUrl,
                                detailEndpointId,
                            )
                            val connection = ApiConnection(
                                id = connectionIdForProvider(detailProvider.id),
                                providerId = detailProvider.id,
                                name = existing?.name ?: detailProvider.name,
                                apiUrl = nextUrl,
                                apiKey = detailKey,
                                modelIds = (existing?.modelIds.orEmpty() + nextModel).distinct(),
                                toolsSupported = detailToolsEnabled,
                                verifiedSignature = existing?.verifiedSignature.orEmpty(),
                                workspaceId = detailWorkspaceId,
                            )
                            detailModelId = nextModel
                            viewModel.saveConnection(connection)
                            manualModelId = ""
                            showManualModelDialog = false
                        }
                    },
                    enabled = manualModelId.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualModelDialog = false }) { Text("取消") }
            },
        )
    }

    pendingLocalModelDeleteId?.let { modelId ->
        val localPreset = LocalModelCatalog.get(modelId)
        AlertDialog(
            onDismissRequest = { pendingLocalModelDeleteId = null },
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

    pendingRemoteModelDeleteId?.let { modelId ->
        AlertDialog(
            onDismissRequest = { pendingRemoteModelDeleteId = null },
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除模型") },
            text = { Text("将从当前服务商移除 Model ID：$modelId。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val connection = config.connectionForProvider(detailProviderId)
                        val remainingModels = connection?.modelIds.orEmpty().filterNot { it == modelId }
                        if (connection != null) {
                            val nextConnection = connection.copy(modelIds = remainingModels)
                            val removedChat = config.resolvedChatModelRef().let {
                                it.connectionId == connection.id && it.modelId == modelId
                            }
                            val removedVision = config.resolvedVisionModelRef()?.let {
                                it.connectionId == connection.id && it.modelId == modelId
                            } == true
                            val removedImage = config.resolvedImageModelRef()?.let {
                                it.connectionId == connection.id && it.modelId == modelId
                            } == true
                            var nextConfig = config.saveConnection(nextConnection).copy(
                                visionModel = if (removedVision) "" else config.visionModel,
                                imageModel = if (removedImage) "" else config.imageModel,
                                visionModelRef = config.visionModelRef.takeUnless { removedVision },
                                imageModelRef = config.imageModelRef.takeUnless { removedImage },
                            )
                            if (removedChat) {
                                nextConfig = remainingModels.firstOrNull()?.let { fallbackModel ->
                                    nextConfig.selectChatModel(ModelReference(connection.id, fallbackModel))
                                } ?: nextConfig.copy(model = "", chatModelRef = null)
                            }
                            if (detailModelId == modelId) {
                                detailModelId = remainingModels.firstOrNull().orEmpty()
                            }
                            viewModel.save(nextConfig)
                        }
                        pendingRemoteModelDeleteId = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoteModelDeleteId = null }) { Text("取消") }
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
    SettingsPage.ModelSettings -> "模型管理"
    SettingsPage.AiServiceDetail -> "服务商"
    SettingsPage.Memory -> "记忆"
    SettingsPage.OfficialChannels -> "官方通道"
    SettingsPage.About -> "关于"
}

private fun settingsPageDepth(page: SettingsPage): Int = when (page) {
    SettingsPage.Overview -> 0
    SettingsPage.ModelSettings,
    SettingsPage.Memory,
    SettingsPage.OfficialChannels,
    SettingsPage.About -> 1
    SettingsPage.AiServiceDetail -> 2
}

@Composable
private fun AiServiceOverviewContent(
    localModelId: String,
    config: ApiConfig,
    localModelStates: List<LocalModelFileState>,
    onSelectRemoteModel: (AiModelPurpose, RemoteModelCandidate) -> Unit,
    onSelectLocalModel: (LocalModelPreset) -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    val chatRef = config.resolvedChatModelRef()
    val chatConnection = config.connection(chatRef.connectionId)
    val visionRef = config.resolvedVisionModelRef()
    val visionConnection = visionRef?.let { config.connection(it.connectionId) }
    val imageRef = config.resolvedImageModelRef()
    val imageConnection = imageRef?.let { config.connection(it.connectionId) }
    val resolvedVisionModel = visionRef?.modelId.orEmpty()
    val resolvedImageModel = imageRef?.modelId.orEmpty()
    val selectedLocal = LocalModelCatalog.get(localModelId)
    val selectedLocalState = localModelStates.firstOrNull { it.modelId == localModelId }
    val installedLocalModels = LocalModelCatalog.models.filter { item ->
        localModelStates.firstOrNull { it.modelId == item.id }?.installed == true
    }

    SectionHeader("当前模型")
    SettingGroup {
        ModelPurposeRow(
            purpose = "聊天",
            modelName = chatRef.modelId.ifBlank { "未配置" },
            connected = connectionConfigurationStatus(chatConnection) == "已连接",
            options = configuredRemoteModelCandidates(config, AiModelPurpose.Chat),
            selectedKey = chatRef.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { it.connection.name },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.Chat, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "识图",
            modelName = resolvedVisionModel.ifBlank { "未配置" },
            connected = connectionConfigurationStatus(visionConnection) == "已连接",
            options = configuredRemoteModelCandidates(config, AiModelPurpose.Vision),
            selectedKey = visionRef?.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { it.connection.name },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.Vision, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "图片生成",
            modelName = resolvedImageModel.ifBlank { "未配置" },
            connected = connectionConfigurationStatus(imageConnection) == "已连接",
            options = configuredRemoteModelCandidates(config, AiModelPurpose.ImageGeneration),
            selectedKey = imageRef?.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { it.connection.name },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.ImageGeneration, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "本地模型",
            modelName = selectedLocal?.id ?: "未配置",
            connected = selectedLocalState?.installed == true,
            options = installedLocalModels,
            selectedKey = localModelId,
            optionKey = LocalModelPreset::id,
            optionName = LocalModelPreset::name,
            optionSource = ::localModelTaskLabel,
            optionIsFree = { false },
            onSelect = onSelectLocalModel,
        )
    }

    SectionHeader("模型管理")
    SettingGroup {
        ModelSettingsNavigationRow(onClick = onOpenModelSettings)
    }
}

@Composable
private fun AiServiceOtherSettingsContent(
    config: ApiConfig,
    backgroundExecutionEnabled: Boolean,
    onOfflineFallbackChange: (Boolean) -> Unit,
    onDynamicLocalRoutingChange: (Boolean) -> Unit,
    onHighRiskConfirmationChange: (Boolean) -> Unit,
    onBackgroundExecutionChange: (Boolean) -> Unit,
    onPhoneToolsChange: (Boolean) -> Unit,
) {
    SectionHeader("其他设置")
    SettingGroup {
        SwitchSettingRow(
            title = "自动切换本地模型",
            description = "网络不佳时候自动切换",
            checked = config.offlineFallbackEnabled,
            onCheckedChange = onOfflineFallbackChange,
        )
        GroupDivider()
        SwitchSettingRow(
            title = "动态使用本地模型",
            description = "简单文字优先本地，复杂任务继续使用远程模型",
            checked = config.dynamicLocalRoutingEnabled,
            onCheckedChange = onDynamicLocalRoutingChange,
        )
        GroupDivider()
        SwitchSettingRow(
            title = "高风险操作确认",
            description = "发送、删除、写入和修改系统状态前进行确认",
            checked = config.requireToolConfirmation,
            onCheckedChange = onHighRiskConfirmationChange,
        )
        GroupDivider()
        SwitchSettingRow(
            title = "自动化后台运行",
            description = "定时自动化可由系统在后台拉起执行",
            checked = backgroundExecutionEnabled,
            onCheckedChange = onBackgroundExecutionChange,
        )
        GroupDivider()
        SwitchSettingRow(
            title = "手机工具",
            description = "在用户确认后，允许程序操作手机",
            checked = config.phoneToolsEnabled,
            onCheckedChange = onPhoneToolsChange,
        )
    }
}

@Composable
private fun <T> ModelPurposeRow(
    purpose: String,
    modelName: String,
    connected: Boolean,
    options: List<T>,
    selectedKey: String?,
    optionKey: (T) -> String,
    optionName: (T) -> String,
    optionSource: (T) -> String,
    optionIsFree: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = purpose,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = buildAnnotatedString {
                    append(modelName)
                    if (!connected) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append("（未连接）")
                        }
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            SettingsDropdownArrow(
                expanded = expanded,
                onDismiss = { expanded = false },
                collapsedDescription = "展开模型列表",
                expandedDescription = "收起模型列表",
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无已配置模型", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {},
                        enabled = false,
                    )
                } else {
                    options.forEach { option ->
                        val isSelected = optionKey(option) == selectedKey
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = optionName(option),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = optionSource(option),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (optionIsFree(option)) {
                                        ModelCapabilityLabel("免费")
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = "当前模型",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun ModelReference.selectorKey(): String = "$connectionId::$modelId"

private fun RemoteModelCandidate.selectorKey(): String =
    ModelReference(connection.id, model.id).selectorKey()

private fun configuredRemoteModelCandidates(
    config: ApiConfig,
    purpose: AiModelPurpose,
): List<RemoteModelCandidate> = config.configuredConnections().flatMap { connection ->
    val provider = AiProviderCatalog.getProvider(connection.providerId)
        ?: AiProviderCatalog.getProvider(AiProviderCatalog.CUSTOM_PROVIDER_ID)
        ?: return@flatMap emptyList()
    connection.modelIds.mapNotNull { modelId ->
        val model = AiProviderCatalog.getModel(connection.providerId, modelId)
            ?: AiModelPreset(
                id = modelId,
                name = modelId,
                description = "手动添加的模型",
                isFree = AiProviderCatalog.isFreeModel(connection.providerId, modelId),
                supportsTools = connection.toolsSupported,
                supportsVision = true,
                supportsImageGeneration = true,
            )
        val supportsPurpose = when (purpose) {
            AiModelPurpose.Chat -> model.supportsChat
            AiModelPurpose.Vision -> model.supportsVision
            AiModelPurpose.ImageGeneration -> model.supportsImageGeneration
            AiModelPurpose.LocalFallback -> false
        }
        model.takeIf { supportsPurpose }?.let { RemoteModelCandidate(connection, provider, it) }
    }
}.distinctBy(RemoteModelCandidate::selectorKey)

@Composable
private fun ModelSettingsNavigationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "模型接口",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "打开模型接口",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ModelSettingsContent(
    config: ApiConfig,
    localModelStates: List<LocalModelFileState>,
    onOpenProvider: (String) -> Unit,
) {
    ModelProviderSection(
        title = "官方 API",
        providers = AiProviderCatalog.providers.filter { it.kind == AiProviderKind.Official },
        config = config,
        onOpenProvider = onOpenProvider,
    )
    val customProvider = AiProviderCatalog.getProvider(AiProviderCatalog.CUSTOM_PROVIDER_ID)
    if (customProvider != null) {
        SectionHeader("自定义")
        SettingGroup {
            val savedModels = config.connectionForProvider(customProvider.id)?.modelIds.orEmpty()
            AiProviderRow(
                providerId = customProvider.id,
                name = "中转站",
                detail = if (savedModels.isNotEmpty()) "${savedModels.size} 个已配置模型" else "OpenAI 兼容接口",
                status = providerConfigurationStatus(customProvider, config),
                onClick = { onOpenProvider(customProvider.id) },
            )
        }
    }

    SectionHeader("本地模型")
    SettingGroup {
        val installedCount = localModelStates.count(LocalModelFileState::installed)
        AiProviderRow(
            providerId = LOCAL_PROVIDER_ID,
            name = "本地模型",
            detail = if (installedCount > 0) "$installedCount 个已安装" else "设备本地运行",
            status = if (installedCount > 0) "本地" else "未安装",
            onClick = { onOpenProvider(LOCAL_PROVIDER_ID) },
        )
    }
}

@Composable
private fun ModelProviderSection(
    title: String,
    providers: List<AiProviderPreset>,
    config: ApiConfig,
    onOpenProvider: (String) -> Unit,
) {
    SectionHeader(title)
    SettingGroup {
        providers.forEachIndexed { index, item ->
            if (index > 0) GroupDivider(horizontalPadding = 14.dp)
            val savedModels = config.connectionForProvider(item.id)?.modelIds.orEmpty()
            AiProviderRow(
                providerId = item.id,
                name = item.name,
                detail = when {
                    savedModels.isNotEmpty() -> "${savedModels.size} 个已配置模型"
                    item.modelOptions.isNotEmpty() -> "${item.modelOptions.size} 个可选模型"
                    else -> "OpenAI 兼容接口"
                },
                status = providerConfigurationStatus(item, config),
                onClick = { onOpenProvider(item.id) },
            )
        }
    }
}

private fun remoteConfigurationStatus(config: ApiConfig): String = when {
    config.model.isBlank() -> "未配置"
    AiProviderCatalog.requiresApiKey(config) && config.apiKey.isBlank() -> "缺少 API Key"
    AiProviderCatalog.isVerified(config) -> "可用"
    else -> "待测试"
}

private fun providerConfigurationStatus(provider: AiProviderPreset, config: ApiConfig): String =
    connectionConfigurationStatus(config.connectionForProvider(provider.id))

private fun connectionConfigurationStatus(connection: ApiConnection?): String = when {
    connection == null || connection.modelIds.isEmpty() -> "未配置"
    AiProviderCatalog.requiresApiKey(
        connection.providerId,
        connection.apiUrl,
        connection.modelIds.first(),
    ) && connection.apiKey.isBlank() -> "缺少 API Key"
    connection.verifiedSignature.isNotBlank() -> "已连接"
    else -> "待测试"
}

@Composable
private fun AiModelPickerContent(
    purpose: AiModelPurpose,
    currentProvider: AiProviderPreset,
    currentModelId: String,
    currentVisionModelId: String,
    currentImageModelId: String,
    currentLocalModelId: String,
    config: ApiConfig,
    localModelStates: List<LocalModelFileState>,
    onSelectRemoteModel: (RemoteModelCandidate) -> Unit,
    onSelectLocalModel: (LocalModelPreset) -> Unit,
    onConfigureProvider: (String) -> Unit,
    onManageLocalModels: () -> Unit,
) {
    if (purpose == AiModelPurpose.LocalFallback) {
        val currentLocal = LocalModelCatalog.models.firstOrNull { it.id == currentLocalModelId }
        if (currentLocal != null) {
            SectionHeader("当前")
            val state = localModelStates.firstOrNull { it.modelId == currentLocal.id }
            ModelPickerRow(
                providerId = LOCAL_PROVIDER_ID,
                providerName = localModelTaskLabel(currentLocal),
                modelName = currentLocal.name,
                status = if (state?.installed == true) "已安装" else "未安装",
                actionLabel = null,
                onAction = {},
            )
        }
        SectionHeader("其他模型")
        SettingGroup {
            LocalModelCatalog.models.filterNot { it.id == currentLocalModelId }.forEachIndexed { index, item ->
                if (index > 0) GroupDivider(horizontalPadding = 14.dp)
                val state = localModelStates.firstOrNull { it.modelId == item.id }
                ModelPickerRow(
                    providerId = LOCAL_PROVIDER_ID,
                    providerName = localModelTaskLabel(item),
                    modelName = item.name,
                    status = if (state?.installed == true) "已安装" else "未安装",
                    actionLabel = when {
                        state?.installed == true -> purpose.actionLabel
                        else -> "去下载"
                    },
                    onAction = if (state?.installed == true) {
                        { onSelectLocalModel(item) }
                    } else {
                        onManageLocalModels
                    },
                )
            }
        }
        Text(
            text = "本地模型不需要 API Key，模型文件和推理都保留在设备上。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
        return
    }

    val selectedRef = when (purpose) {
        AiModelPurpose.Chat -> config.resolvedChatModelRef()
        AiModelPurpose.Vision -> config.resolvedVisionModelRef()
        AiModelPurpose.ImageGeneration -> config.resolvedImageModelRef()
        AiModelPurpose.LocalFallback -> null
    }
    val selectedId = selectedRef?.modelId.orEmpty()
    val selectedProviderId = selectedRef?.let { config.connection(it.connectionId)?.providerId }.orEmpty()

    val candidates = buildList {
        AiProviderCatalog.providers.forEach { provider ->
            provider.modelOptions.filter { item ->
                when (purpose) {
                    AiModelPurpose.Chat -> item.supportsChat
                    AiModelPurpose.Vision -> item.supportsVision
                    AiModelPurpose.ImageGeneration -> item.supportsImageGeneration
                    AiModelPurpose.LocalFallback -> false
                }
            }.forEach { item -> add(RemoteModelCandidate(provider, item)) }
        }
        if (selectedId.isNotBlank() && none { it.provider.id == selectedProviderId && it.model.id == selectedId }) {
            add(
                0,
                RemoteModelCandidate(
                    AiProviderCatalog.getProvider(selectedProviderId) ?: currentProvider,
                    AiModelPreset(
                        id = selectedId,
                        name = selectedId,
                        description = "手动添加的模型",
                        supportsTools = purpose == AiModelPurpose.Chat,
                        supportsVision = purpose == AiModelPurpose.Vision,
                        supportsImageGeneration = purpose == AiModelPurpose.ImageGeneration,
                        supportsChat = purpose == AiModelPurpose.Chat,
                    ),
                ),
            )
        }
    }
    val currentCandidate = candidates.firstOrNull {
        it.provider.id == selectedProviderId && it.model.id == selectedId
    }

    SectionHeader("当前")
    if (currentCandidate != null) {
        ModelPickerRow(
            providerId = currentCandidate.provider.id,
            providerName = currentCandidate.provider.name,
            modelName = currentCandidate.model.name,
            status = capabilitySummary(currentCandidate.model),
            actionLabel = null,
            onAction = {},
        )
    } else {
        CompactEmptyRow("尚未配置${purpose.title.removePrefix("选择")}")
    }

    SectionHeader("其他模型")
    SettingGroup {
        candidates.filterNot { candidate ->
            candidate.provider.id == selectedProviderId && candidate.model.id == selectedId
        }.forEachIndexed { index, candidate ->
            if (index > 0) GroupDivider(horizontalPadding = 14.dp)
            val provider = candidate.provider
            val item = candidate.model
            val savedConnection = config.connectionForProvider(provider.id)
            val canSelect = savedConnection != null &&
                savedConnection.modelIds.isNotEmpty() &&
                (!AiProviderCatalog.requiresApiKey(
                    provider.id,
                    savedConnection.apiUrl,
                    item.id,
                ) || savedConnection.apiKey.isNotBlank())
            ModelPickerRow(
                providerId = provider.id,
                providerName = provider.name,
                modelName = item.name,
                status = capabilitySummary(item),
                actionLabel = if (canSelect) purpose.actionLabel else "去配置",
                onAction = if (canSelect) {
                    { onSelectRemoteModel(candidate) }
                } else {
                    { onConfigureProvider(provider.id) }
                },
            )
        }
    }
}

private fun capabilitySummary(model: AiModelPreset): String = buildList {
    if (model.supportsVision) add("视觉")
    if (model.supportsTools) add("工具调用")
    if (model.supportsImageGeneration) add("图片生成")
    if (isEmpty()) add("基础对话")
}.joinToString(" · ")

private fun localModelTaskLabel(model: LocalModelPreset): String =
    if (model.id.startsWith("minicpm", ignoreCase = true)) {
        "轻度任务模型"
    } else {
        "重度任务模型"
    }

@Composable
private fun CompactEmptyRow(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
    )
}

@Composable
private fun ModelPickerRow(
    providerId: String,
    providerName: String,
    modelName: String,
    status: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "$providerName · $status",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel == null) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "当前",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ModelCapabilityLabel(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        modifier = Modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AiProviderRow(
    providerId: String,
    name: String,
    detail: String,
    status: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp).size(17.dp),
        )
    }
}

@Composable
private fun AddProviderRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("添加服务商", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConnectionConfigurationState(
    isSaved: Boolean,
    isVerified: Boolean,
    missingApiKey: Boolean,
    missingModelId: Boolean,
) {
    SectionHeader("当前状态")
    val status = when {
        missingApiKey -> "缺少 API Key"
        missingModelId -> "缺少 Model ID"
        !isSaved -> "有未保存修改"
        isVerified -> "连接可用"
        else -> "配置已保存，等待测试"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProviderModelChoiceRow(
    providerId: String,
    model: AiModelPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                model.id,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "当前模型",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        onDelete?.let { delete ->
            IconButton(onClick = delete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移除模型",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderModelRow(
    providerId: String,
    model: AiModelPreset,
    currentPurposes: Set<AiModelPurpose>,
    availablePurposes: List<AiModelPurpose>,
    canAssign: Boolean,
    onAssign: (AiModelPurpose) -> Unit,
    onConfigure: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val nextPurpose = availablePurposes.firstOrNull { it !in currentPurposes }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Model ID  ${model.id}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                !canAssign -> TextButton(onClick = onConfigure) { Text("去配置", fontSize = 12.sp) }
                nextPurpose != null -> TextButton(onClick = { onAssign(nextPurpose) }) {
                    Text(nextPurpose.actionLabel, fontSize = 12.sp)
                }
                else -> {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("当前", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            onDelete?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除模型",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                modelCapabilityLabels(model).forEach { label ->
                    ModelCapabilityLabel(label)
                }
            }
            if (currentPurposes.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = currentPurposes.joinToString(" · ") { purpose ->
                        when (purpose) {
                            AiModelPurpose.Chat -> "当前聊天模型"
                            AiModelPurpose.Vision -> "当前识图模型"
                            AiModelPurpose.ImageGeneration -> "当前生图模型"
                            AiModelPurpose.LocalFallback -> "当前本地备用"
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun modelCapabilityLabels(model: AiModelPreset): List<String> = buildList {
    var hasExplicitCapability = false
    if (model.supportsVision) {
        add("视觉")
        hasExplicitCapability = true
    }
    if (model.supportsTools) {
        add("工具调用")
        hasExplicitCapability = true
    }
    if (model.id.contains("pro", ignoreCase = true) || model.name.contains("Pro", ignoreCase = true)) {
        add("推理")
        hasExplicitCapability = true
    }
    if (model.supportsImageGeneration) {
        add("图片生成")
        hasExplicitCapability = true
    }
    if (!hasExplicitCapability) add("基础对话")
    add("上下文长度未标注")
}

@Composable
private fun SettingsOverviewContent(
    regularNotificationsEnabled: Boolean,
    islandNotificationsEnabled: Boolean,
    memoryCount: Int,
    onNotificationModeChange: (TaskNotificationMode) -> Unit,
    onOpenMemory: () -> Unit,
) {
    var notificationMenuExpanded by remember { mutableStateOf(false) }
    val notificationMode = when {
        islandNotificationsEnabled -> TaskNotificationMode.Island
        regularNotificationsEnabled -> TaskNotificationMode.Regular
        else -> TaskNotificationMode.Disabled
    }
    val notificationSummary = when (notificationMode) {
        TaskNotificationMode.Regular -> "常规通知"
        TaskNotificationMode.Island -> "岛通知"
        TaskNotificationMode.Disabled -> "不启用"
    }

    SectionHeader("通知")
    SettingGroup {
        SelectionSettingRow(
            title = "任务通知",
            value = notificationSummary,
            expanded = notificationMenuExpanded,
            onClick = { notificationMenuExpanded = true },
            onDismiss = { notificationMenuExpanded = false },
            menuContent = {
                TaskNotificationMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                            Text(
                                text = mode.label,
                            )
                                if (
                                    mode == TaskNotificationMode.Island &&
                                    Build.VERSION.SDK_INT < 36
                                ) {
                                    Text(
                                        text = "仅安卓16+可用",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        },
                        trailingIcon = {
                            if (mode == notificationMode) {
                                Icon(Icons.Outlined.Check, contentDescription = "当前选项")
                            }
                        },
                        onClick = {
                            notificationMenuExpanded = false
                            onNotificationModeChange(mode)
                        },
                    )
                }
            },
        )
    }

    SectionHeader("记忆")
    SettingGroup {
        OverviewSettingRow(
            title = "自定义记忆",
            description = if (memoryCount == 0) "未添加" else "已保存 $memoryCount 条",
            onClick = onOpenMemory,
        )
    }
}

@Composable
private fun SettingsSecondaryContent(
    onOpenPermission: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SectionHeader("系统")
    SettingGroup {
        OverviewSettingRow(
            title = "权限",
            description = "相机、文件、通知等系统权限",
            onClick = onOpenPermission,
        )
        GroupDivider()
        OverviewSettingRow(
            title = "关于",
            description = "版本、更新、日志和缓存清理",
            onClick = onOpenAbout,
        )
    }
}

@Composable
private fun OverviewSettingRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
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
    SectionHeader("编辑")
    if (!editorExpanded) {
        SettingGroup {
            ActionSettingRow(
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
    downloadStates: Map<String, LocalModelDownloadState>,
    onSelect: (LocalModelPreset) -> Unit,
    onDownload: (LocalModelPreset) -> Unit,
    onPauseDownload: (LocalModelPreset) -> Unit,
    onDelete: (LocalModelPreset) -> Unit,
) {
    SettingGroup {
        models.forEachIndexed { index, item ->
            if (index > 0) GroupDivider()
            LocalModelManagementRow(
                item = item,
                state = states.firstOrNull { it.modelId == item.id },
                downloadState = downloadStates[item.id],
                selected = item.id == selectedModelId,
                onClick = { onSelect(item) },
                onDownload = { onDownload(item) },
                onPauseDownload = { onPauseDownload(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun LocalModelManagementRow(
    item: LocalModelPreset,
    state: LocalModelFileState?,
    downloadState: LocalModelDownloadState?,
    selected: Boolean,
    onClick: () -> Unit,
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
    val isSelected = selected && state?.installed == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state?.installed == true, onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
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
                    ModelBadge(localModelStateLabel(state), free = state?.installed == true)
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    "${item.runtime} | 约 ${item.estimatedSizeGb}GB | 建议 ${item.recommendedRamGb}GB 内存",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
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
            ModelSelectionIndicator(selected = isSelected)
        }
        localModelStateDescription(state)?.let { description ->
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
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

private fun localModelStateDescription(state: LocalModelFileState?): String? {
    return when (state?.state) {
        LocalModelInstallState.Installed -> null
        LocalModelInstallState.DeviceMayBeUnsupported -> {
            val size = formatLocalModelSize(state.sizeBytes)
            "模型已导入：$size；当前设备内存约 ${state.availableRamGb}GB，低于建议 ${state.recommendedRamGb}GB"
        }
        LocalModelInstallState.FileMissing -> "模型文件异常，请删除后重新下载"
        LocalModelInstallState.NotInstalled, null -> null
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

private fun officialEntryUrl(provider: AiProviderPreset): String = when (provider.id) {
    "openrouter" -> "https://openrouter.ai/settings/keys"
    "openai" -> "https://platform.openai.com/api-keys"
    "gemini" -> "https://aistudio.google.com/app/apikey"
    "qwen" -> "https://bailian.console.aliyun.com/"
    "siliconflow" -> "https://cloud.siliconflow.cn/account/ak"
    "deepseek" -> "https://platform.deepseek.com/api_keys"
    "mimo" -> "https://platform.xiaomimimo.com/"
    else -> "https://platform.openai.com/docs/api-reference/chat"
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "白天"
    ThemeMode.DARK -> "夜间"
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
private fun AppearanceSettingsContent(
    selectedMode: ThemeMode,
    selectedStyle: InterfaceStyle,
    liquidGlassTransparency: Float,
    selectedColor: Long,
    onModeChange: (ThemeMode) -> Unit,
    onStyleChange: (InterfaceStyle) -> Unit,
    onLiquidGlassTransparencyChange: (Float) -> Unit,
    onAccentColorChange: (Long) -> Unit,
) {
    SettingGroup {
        ThemeModeSelectionRow(
            selectedMode = selectedMode,
            onModeChange = onModeChange,
        )
        GroupDivider()
        InterfaceStyleSelectionRow(
            selectedStyle = selectedStyle,
            onStyleChange = onStyleChange,
        )
        if (LIQUID_GLASS_STYLE_VISIBLE && selectedStyle == InterfaceStyle.LIQUID_GLASS) {
            GroupDivider()
            LiquidGlassTransparencyRow(
                transparency = liquidGlassTransparency,
                onTransparencyChange = onLiquidGlassTransparencyChange,
            )
        }
        GroupDivider()
        AccentColorSelectionRow(
            selectedColor = selectedColor,
            onAccentColorChange = onAccentColorChange,
        )
    }
}

@Composable
private fun InterfaceStyleSelectionRow(
    selectedStyle: InterfaceStyle,
    onStyleChange: (InterfaceStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleSelectedStyle = selectedStyle.takeUnless {
        it == InterfaceStyle.LIQUID_GLASS && !LIQUID_GLASS_STYLE_VISIBLE
    } ?: InterfaceStyle.ACRYLIC
    val options = buildList {
        add(InterfaceStyle.ACRYLIC to "亚克力")
        add(InterfaceStyle.MATERIAL3 to "Material3")
        if (LIQUID_GLASS_STYLE_VISIBLE) {
            add(InterfaceStyle.LIQUID_GLASS to "液态玻璃")
        }
    }
    SelectionSettingRow(
        title = "风格",
        value = options.first { it.first == visibleSelectedStyle }.second,
        expanded = expanded,
        onClick = { expanded = true },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { (style, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (style == visibleSelectedStyle) {
                            Icon(Icons.Outlined.Check, contentDescription = "当前风格")
                        }
                    },
                    onClick = {
                        expanded = false
                        onStyleChange(style)
                    },
                )
            }
        },
    )
}

@Composable
private fun LiquidGlassTransparencyRow(
    transparency: Float,
    onTransparencyChange: (Float) -> Unit,
) {
    var showValueDialog by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf("") }
    val inputPercent = inputValue.toIntOrNull()
    val inputValid = inputPercent != null && inputPercent in 0..100

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "透明度",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(transparency * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            IconButton(
                onClick = {
                    inputValue = (transparency * 100).toInt().toString()
                    showValueDialog = true
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "输入透明度",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        SimpleLineSlider(
            value = transparency,
            onValueChange = onTransparencyChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showValueDialog) {
        AlertDialog(
            onDismissRequest = { showValueDialog = false },
            title = { Text("输入透明度") },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { next ->
                        if (next.length <= 3 && next.all(Char::isDigit)) {
                            inputValue = next
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("%") },
                    isError = inputValue.isNotEmpty() && !inputValid,
                    supportingText = {
                        if (inputValue.isNotEmpty() && !inputValid) {
                            Text("请输入 0-100")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = inputValid,
                    onClick = {
                        val percent = inputPercent ?: return@TextButton
                        onTransparencyChange(percent / 100f)
                        showValueDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showValueDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SimpleLineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val rangeLength = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeLength == 0f) {
        0f
    } else {
        ((value - valueRange.start) / rangeLength).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, 0)
                setProgress { target ->
                    onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(valueRange, onValueChange) {
                fun updateValue(positionX: Float) {
                    val horizontalInset = 8.dp.toPx()
                    val usableWidth = (size.width - horizontalInset * 2f).coerceAtLeast(1f)
                    val nextFraction = ((positionX - horizontalInset) / usableWidth).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + rangeLength * nextFraction)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateValue(down.position.x)
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        updateValue(change.position.x)
                    }
                }
            },
    ) {
        val horizontalInset = 8.dp.toPx()
        val centerY = size.height / 2f
        val trackStart = horizontalInset
        val trackEnd = size.width - horizontalInset
        drawLine(
            color = trackColor,
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = thumbColor,
            radius = 7.dp.toPx(),
            center = Offset(trackStart + (trackEnd - trackStart) * fraction, centerY),
        )
    }
}

@Composable
private fun ThemeModeSelectionRow(
    selectedMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.LIGHT to "浅色",
        ThemeMode.DARK to "深色",
    )
    val selectedLabel = options.first { it.first == selectedMode }.second
    SelectionSettingRow(
        title = "深色模式",
        value = selectedLabel,
        expanded = expanded,
        onClick = { expanded = true },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Outlined.Check, contentDescription = "当前模式")
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeChange(mode)
                    },
                )
            }
        },
    )
}

@Composable
private fun AccentColorSelectionRow(
    selectedColor: Long,
    onAccentColorChange: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val blackAccent = MasonAccentPresets.first { it.name == "纯黑" }
    SelectionSettingRow(
        title = "主题色",
        value = "黑色",
        expanded = expanded,
        onClick = { expanded = true },
        onDismiss = { expanded = false },
        menuContent = {
            DropdownMenuItem(
                leadingIcon = {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(blackAccent.color.toComposeColor())
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                },
                text = { Text("黑色") },
                trailingIcon = {
                    if (selectedColor == blackAccent.color) {
                        Icon(Icons.Outlined.Check, contentDescription = "当前主题色")
                    }
                },
                onClick = {
                    expanded = false
                    onAccentColorChange(blackAccent.color)
                },
            )
        },
    )
}

@Composable
private fun SelectionSettingRow(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(6.dp))
        SettingsDropdownArrow(
            expanded = expanded,
            onDismiss = onDismiss,
            collapsedDescription = "展开选项",
            expandedDescription = "收起选项",
            menuContent = menuContent,
        )
    }
}

@Composable
private fun SettingsDropdownArrow(
    expanded: Boolean,
    onDismiss: () -> Unit,
    collapsedDescription: String,
    expandedDescription: String,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) expandedDescription else collapsedDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        SettingsPopupMenu(
            expanded = expanded,
            onDismiss = onDismiss,
            content = menuContent,
        )
    }
}

@Composable
private fun SettingsPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
        content = content,
    )
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchSettingRow(
        title = title,
        description = "",
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
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
            .fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun settingsGlassBrush(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

@Composable
private fun GroupDivider(horizontalPadding: Dp = 14.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = horizontalPadding),
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
private fun ProviderEndpointSelector(
    provider: AiProviderPreset,
    selectedEndpointId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(provider.id) { mutableStateOf(false) }
    val selected = provider.endpoints.firstOrNull { it.id == selectedEndpointId }
        ?: provider.endpoints.first()
    Text(
        text = "服务区域",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(selected.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            SettingsDropdownArrow(
                expanded = expanded,
                onDismiss = { expanded = false },
                collapsedDescription = "选择服务区域",
                expandedDescription = "收起服务区域",
            ) {
                provider.endpoints.forEach { endpoint ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(endpoint.name)
                                Text(
                                    endpoint.apiUrl,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        trailingIcon = {
                            if (endpoint.id == selected.id) {
                                Icon(Icons.Outlined.Check, contentDescription = "当前区域")
                            }
                        },
                        onClick = {
                            onSelect(endpoint.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInput(
    label: String,
    value: String,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    if (label.isNotBlank()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = themedFieldColors(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        readOnly = readOnly,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
}

internal fun resolveProviderDraftUrl(
    provider: AiProviderPreset,
    draftUrl: String,
    endpointId: String = "",
): String =
    if (provider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID) {
        draftUrl.trim()
    } else {
        provider.endpoints.firstOrNull { it.id == endpointId }?.apiUrl
            ?: draftUrl.ifBlank { provider.apiUrl }.trim()
    }

internal fun isApiDraftTestable(
    apiUrl: String,
    apiKey: String,
    modelId: String,
    requiresApiKey: Boolean,
): Boolean = apiUrl.isNotBlank() && modelId.isNotBlank() &&
    (!requiresApiKey || apiKey.isNotBlank())

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
            if (description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CompactActionRow(
    title: String,
    description: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ActionSettingRow(
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
        if (isLoading) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
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
            title = "检查更新",
            description = "打开 GitHub Releases，查看是否有新版本",
            onClick = { uriHandler.openUri("https://github.com/DENGGL2/MASON/releases") },
        )
        GroupDivider()
        ActionSettingRow(
            title = "查看版本日志",
            description = "查看近期提交和版本变化",
            onClick = { uriHandler.openUri("https://github.com/DENGGL2/MASON/commits/main") },
        )
    }

    SectionHeader("本机")
    SettingGroup {
        ActionSettingRow(
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
