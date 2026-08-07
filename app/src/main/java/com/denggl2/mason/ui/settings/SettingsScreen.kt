package com.denggl2.mason.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.AiProviderKind
import com.denggl2.mason.data.AiProviderPreset
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.connectionIdForEndpoint
import com.denggl2.mason.data.connectionIdForModel
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.connectionForProvider
import com.denggl2.mason.data.connectionIdForProvider
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.configuredConnections
import com.denggl2.mason.data.configuredImageModelRef
import com.denggl2.mason.data.configuredVisionModelRef
import com.denggl2.mason.data.resolvedChatModelRef
import com.denggl2.mason.data.resolvedConnections
import com.denggl2.mason.data.resolvedImageModelRef
import com.denggl2.mason.data.resolvedVisionModelRef
import com.denggl2.mason.data.removeConnection
import com.denggl2.mason.data.saveConnection
import com.denggl2.mason.data.selectChatModel
import com.denggl2.mason.data.supportsChatModel
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.LocalModelDownloadState
import com.denggl2.mason.data.LocalModelDownloadStatus
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.LocalModelInstallState
import com.denggl2.mason.data.LocalModelPreset
import com.denggl2.mason.data.MasonAccentPresets
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.FontSizePreference
import com.denggl2.mason.data.OfficialChannelPreferences
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.data.UserMemoryItem
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.data.toComposeColor
import com.denggl2.mason.sync.remote.PairedConnector
import com.denggl2.mason.tool.shouldRequestPostNotificationPermission
import com.denggl2.mason.ui.theme.LocalInterfaceEffects
import com.denggl2.mason.ui.theme.ProgressiveBlurEdge
import com.denggl2.mason.ui.theme.captureProgressiveEdgeBlur
import com.denggl2.mason.ui.theme.glassRefraction
import com.denggl2.mason.ui.theme.progressiveEdgeBlur
import com.denggl2.mason.ui.theme.rememberProgressiveEdgeBlurState
import com.denggl2.mason.ui.theme.rememberWindowBackdropSnapshot
import com.denggl2.mason.ui.theme.windowBackdrop
import com.denggl2.mason.ui.theme.windowBackdropMaterial
import dev.chrisbanes.haze.HazeState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private data class RemoteModelEditorTarget(
    val connectionId: String?,
    val providerId: String,
    val modelId: String? = null,
)

private data class PendingRemoteModelDelete(
    val connectionId: String,
    val modelIds: List<String>,
)

private const val LOCAL_PROVIDER_ID = "local"
private const val INTERFACE_STYLE_SETTING_VISIBLE = true
private const val ACCENT_COLOR_SETTING_VISIBLE = false
private val settingsSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

private fun Modifier.settingsSheetContentBackdrop(): Modifier = composed {
    val interfaceEffects = LocalInterfaceEffects.current
    if (!interfaceEffects.backdropBlurEnabled) {
        return@composed this
    }
    this
        .windowBackdropMaterial(
            enabled = true,
            blurRadius = 40.dp,
            fallbackColor = MaterialTheme.colorScheme.surface,
        )
        .background(
            MaterialTheme.colorScheme.surface.copy(
                alpha = interfaceEffects.largeSurfaceAlpha,
            ),
        )
}

@Composable
private fun settingsSheetSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(
    alpha = if (LocalInterfaceEffects.current.backdropBlurEnabled) 0f else 1f,
)
private val settingsGlassOutline = Color(0xFFBABFCC)
private val settingsGlassShadowColor = settingsGlassOutline.copy(alpha = 0.30f)
private val settingsGlassShadowBlur = 20.dp

private fun Modifier.settingsGlassShadow(
    cornerRadius: Dp,
    blurRadius: Dp = settingsGlassShadowBlur,
): Modifier = composed {
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed drawBehind {
            val blurPx = blurRadius.toPx()
            val cornerPx = cornerRadius.toPx()
            val shadowMask = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(Offset.Zero, size),
                        CornerRadius(cornerPx, cornerPx),
                    ),
                )
            }
            val layers = 12
            clipPath(shadowMask, clipOp = ClipOp.Difference) {
                for (layer in layers downTo 1) {
                    val spread = blurPx * layer / layers
                    drawRoundRect(
                        color = settingsGlassShadowColor.copy(alpha = 0.012f),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2f, size.height + spread * 2f),
                        cornerRadius = CornerRadius(cornerPx + spread),
                    )
                }
            }
        }
    }
    val shadowLayer = remember(graphicsContext, density.density, cornerRadius, blurRadius) {
        graphicsContext.createGraphicsLayer().also { layer ->
            // Figma's shadow blur is a diameter-like value; RenderEffect expects sigma.
            val blurSigmaPx = with(density) { blurRadius.toPx() } * 0.5f
            layer.renderEffect = BlurEffect(
                radiusX = blurSigmaPx,
                radiusY = blurSigmaPx,
                edgeTreatment = TileMode.Decal,
            )
        }
    }
    DisposableEffect(graphicsContext, shadowLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(shadowLayer) }
    }
    drawWithContent {
        val blurPx = blurRadius.toPx()
        val cornerPx = cornerRadius.toPx()
        val contentSize = size
        val paddingPx = blurPx
        val layerSize = IntSize(
            width = (contentSize.width + paddingPx * 2f).toInt().coerceAtLeast(1),
            height = (contentSize.height + paddingPx * 2f).toInt().coerceAtLeast(1),
        )
        shadowLayer.record(layerSize) {
            drawRoundRect(
                color = settingsGlassShadowColor,
                topLeft = Offset(paddingPx, paddingPx),
                size = contentSize,
                cornerRadius = CornerRadius(cornerPx),
            )
        }
        val shadowMask = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(Offset.Zero, contentSize),
                    CornerRadius(cornerPx, cornerPx),
                ),
            )
        }
        clipPath(shadowMask, clipOp = ClipOp.Difference) {
            translate(left = -paddingPx, top = -paddingPx) {
                drawLayer(shadowLayer)
            }
        }
        drawContent()
    }
}

private class SettingsBackdropState(
    val sourceLayer: GraphicsLayer,
    val blurredLayer: GraphicsLayer,
) {
    var sourcePosition by mutableStateOf(Offset.Zero)
    var sourceSize = IntSize.Zero
    @Volatile var capturing: Boolean = false
}

private val LocalSettingsBackdropState = staticCompositionLocalOf<SettingsBackdropState?> { null }

@Composable
private fun rememberSettingsBackdropState(enabled: Boolean): SettingsBackdropState? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val graphicsContext = LocalGraphicsContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val state = remember(graphicsContext, density.density) {
        SettingsBackdropState(
            sourceLayer = graphicsContext.createGraphicsLayer(),
            blurredLayer = graphicsContext.createGraphicsLayer().also {
                it.renderEffect = BlurEffect(
                    radiusX = with(density) { 15.dp.toPx() },
                    radiusY = with(density) { 15.dp.toPx() },
                    edgeTreatment = TileMode.Clamp,
                )
            },
        )
    }
    DisposableEffect(graphicsContext, state) {
        onDispose {
            graphicsContext.releaseGraphicsLayer(state.sourceLayer)
            graphicsContext.releaseGraphicsLayer(state.blurredLayer)
        }
    }
    return state
}

private fun Modifier.captureSettingsBackdrop(state: SettingsBackdropState?): Modifier {
    if (state == null) return this
    return this
        .onGloballyPositioned { coordinates ->
            state.sourcePosition = coordinates.positionInRoot()
        }
        .drawWithContent {
            state.capturing = true
            try {
                val layerSize = IntSize(
                    width = size.width.toInt().coerceAtLeast(1),
                    height = size.height.toInt().coerceAtLeast(1),
                )
                state.sourceSize = layerSize
                state.sourceLayer.record(layerSize) {
                    this@drawWithContent.drawContent()
                }
                state.blurredLayer.record(layerSize) {
                    drawLayer(state.sourceLayer)
                }
            } finally {
                state.capturing = false
            }
            drawLayer(state.sourceLayer)
        }
}

private fun Modifier.settingsBackdrop(positionOverride: IntOffset? = null): Modifier = composed {
    val state = LocalSettingsBackdropState.current ?: return@composed this
    var position by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { coordinates ->
        if (positionOverride == null) {
            position = coordinates.positionInRoot()
        }
    }.drawWithContent {
        if (state.capturing) {
            // The popup is excluded from the source snapshot, preventing
            // recursive blur and ensuring only the page behind it is sampled.
            return@drawWithContent
        }
        val overlayPosition = positionOverride?.let { Offset(it.x.toFloat(), it.y.toFloat()) } ?: position
        val relativePosition = overlayPosition - state.sourcePosition
        if (state.sourceSize == IntSize.Zero) {
            drawContent()
            return@drawWithContent
        }
        clipRect {
            translate(left = -relativePosition.x, top = -relativePosition.y) {
                drawLayer(state.blurredLayer)
            }
        }
        drawContent()
    }
}

@Composable
private fun BoxScope.SettingsSheetEdgeFades(
    scrollState: ScrollState,
    blurState: HazeState?,
    surfaceColor: Color,
) {
    val topAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(180),
        label = "settings_sheet_top_fade",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(180),
        label = "settings_sheet_bottom_fade",
    )
    val fadeSurface = surfaceColor.copy(
        alpha = if (LocalInterfaceEffects.current.glassMaterialEnabled) 0.10f else 0.995f,
    )
    if (topAlpha > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer { alpha = topAlpha }
                .progressiveEdgeBlur(
                    state = blurState,
                    edge = ProgressiveBlurEdge.Top,
                    backgroundColor = surfaceColor,
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to fadeSurface,
                            0.32f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.82f),
                            0.70f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.28f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
    if (bottomAlpha > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer { alpha = bottomAlpha }
                .progressiveEdgeBlur(
                    state = blurState,
                    edge = ProgressiveBlurEdge.Bottom,
                    backgroundColor = surfaceColor,
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.30f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.28f),
                            0.68f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.82f),
                            1f to fadeSurface,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun SettingsSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .size(width = 38.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)),
    )
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
    openModelSettingsInitially: Boolean = false,
    onNavigateToPermission: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToDevicePairing: () -> Unit = {},
    onNavigateToPhoneAgent: () -> Unit = {},
    uiPreferences: UiPreferences = UiPreferences(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onInterfaceStyleChange: (InterfaceStyle) -> Unit = {},
    onGlassRefractionChange: (Boolean) -> Unit = {},
    onGlassTransparencyPreview: (Float) -> Unit = {},
    onGlassTransparencyCommit: (Float) -> Unit = {},
    onAccentColorChange: (Long) -> Unit = {},
    onRegularNotificationsChange: (Boolean) -> Unit = {},
    onIslandNotificationsChange: (Boolean) -> Unit = {},
    onFontSizeChange: (FontSizePreference) -> Unit = {},
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
    val pairedConnector by viewModel.pairedConnector.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var page by remember(openModelSettingsInitially) {
        mutableStateOf(
            if (openModelSettingsInitially) SettingsPage.ModelSettings else SettingsPage.Overview,
        )
    }
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
    var showPairingManagementDialog by remember { mutableStateOf(false) }
    var remoteModelEditorTarget by remember { mutableStateOf<RemoteModelEditorTarget?>(null) }
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
    var showDiagnosticExportDialog by remember { mutableStateOf(false) }
    var pendingNotificationPreviewIsland by remember { mutableStateOf<Boolean?>(null) }
    var officialDetail by remember { mutableStateOf<OfficialChannelDetail?>(null) }
    var pendingLocalModelDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingRemoteModelDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingSheetRemoteModelDelete by remember { mutableStateOf<PendingRemoteModelDelete?>(null) }
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val island = pendingNotificationPreviewIsland
        pendingNotificationPreviewIsland = null
        if (granted && island != null) {
            viewModel.previewTaskNotification(island)
        } else if (!granted) {
            Toast.makeText(
                context,
                "通知权限未授予，通知模式已保存，但暂时无法发送通知",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    fun previewNotificationAfterPermission(island: Boolean) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (shouldRequestPostNotificationPermission(Build.VERSION.SDK_INT, permissionGranted)) {
            pendingNotificationPreviewIsland = island
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.previewTaskNotification(island)
        }
    }
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
            SettingsPage.ModelSettings -> {
                if (openModelSettingsInitially) {
                    onBack()
                    SettingsPage.ModelSettings
                } else {
                    SettingsPage.Overview
                }
            }
            else -> SettingsPage.Overview
        }
    }

    BackHandler(onBack = ::navigateBack)

    // Popup blur uses PixelCopy; the legacy full-page capture currently has no consumer.
    val settingsBackdropState: SettingsBackdropState? = null

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

    LaunchedEffect(viewModel) {
        viewModel.diagnosticExportEvent.collect { file ->
            shareDiagnosticReport(context, file)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
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
        CompositionLocalProvider(LocalSettingsBackdropState provides settingsBackdropState) {
        AnimatedContent(
            modifier = Modifier.captureSettingsBackdrop(settingsBackdropState),
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
                    .padding(padding)
                    .navigationBarsPadding(),
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
                    glassRefractionEnabled = uiPreferences.glassRefractionEnabled,
                    glassTransparency = uiPreferences.glassTransparency,
                    selectedColor = uiPreferences.accentColor,
                    selectedFontSize = uiPreferences.fontSize,
                    onModeChange = onThemeModeChange,
                    onStyleChange = onInterfaceStyleChange,
                    onGlassRefractionChange = onGlassRefractionChange,
                    onGlassTransparencyPreview = onGlassTransparencyPreview,
                    onGlassTransparencyCommit = onGlassTransparencyCommit,
                    onAccentColorChange = onAccentColorChange,
                    onFontSizeChange = onFontSizeChange,
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
                    onClearLocalModel = {
                        localModel = ""
                        persistApiConfig(nextLocalModel = "")
                    },
                    onOpenModelSettings = { page = SettingsPage.ModelSettings },
                )
                SettingsOverviewContent(
                    regularNotificationsEnabled = uiPreferences.regularNotificationsEnabled,
                    islandNotificationsEnabled = uiPreferences.islandNotificationsEnabled,
                    onNotificationModeChange = { mode ->
                        when (mode) {
                            TaskNotificationMode.Regular -> {
                                onRegularNotificationsChange(true)
                                onIslandNotificationsChange(false)
                                previewNotificationAfterPermission(island = false)
                            }
                            TaskNotificationMode.Island -> {
                                // Keep the conventional channel on as the fallback below Android 16.
                                onRegularNotificationsChange(true)
                                onIslandNotificationsChange(true)
                                previewNotificationAfterPermission(island = true)
                            }
                            TaskNotificationMode.Disabled -> {
                                onRegularNotificationsChange(false)
                                onIslandNotificationsChange(false)
                            }
                        }
                    },
                    onOpenMemory = { page = SettingsPage.Memory },
                    pairedConnector = pairedConnector,
                    onOpenDevicePairing = onNavigateToDevicePairing,
                    onManageDevicePairing = { showPairingManagementDialog = true },
                    onOpenPhoneAgent = onNavigateToPhoneAgent,
                )
                AiServiceOtherSettingsContent(
                    config = config,
                    backgroundExecutionEnabled = automationPreferences.backgroundExecutionEnabled,
                    onDynamicModelRoutingChange = {
                        dynamicLocalRoutingEnabled = it
                        viewModel.save(
                            config.copy(
                                dynamicLocalRoutingEnabled = it,
                                localModelDirectEnabled = if (it) false else config.localModelDirectEnabled,
                            ),
                        )
                    },
                    onHighRiskConfirmationChange = {
                        requireToolConfirmation = it
                        persistApiConfig(nextRequireToolConfirmation = it)
                    },
                    onBackgroundExecutionChange = viewModel::setBackgroundAutomationEnabled,
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
                    apiTestState = apiTestState,
                    onOpenLocalModels = { openProviderDetail(LOCAL_PROVIDER_ID) },
                    onOpenRemoteModel = { connection, modelId ->
                        remoteModelEditorTarget = RemoteModelEditorTarget(
                            connectionId = connection.id,
                            providerId = connection.providerId,
                            modelId = modelId,
                        )
                    },
                    onAddRemoteModel = {
                        val providerId = AiProviderCatalog.CUSTOM_PROVIDER_ID
                        remoteModelEditorTarget = RemoteModelEditorTarget(
                            connectionId = null,
                            providerId = providerId,
                        )
                    },
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
                            states = localModelStates,
                            downloadStates = localModelDownloadStates,
                            onDownload = { item ->
                                viewModel.downloadLocalModel(item.id)
                            },
                            onPauseDownload = { item -> viewModel.pauseLocalModelDownload(item.id) },
                            onCancelDownload = { item -> viewModel.cancelLocalModelDownload(item.id) },
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
                onExportDiagnostic = { showDiagnosticExportDialog = true },
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
    }

    remoteModelEditorTarget?.let { target ->
        RemoteModelConfigurationSheet(
            initialConnectionId = target.connectionId,
            initialProviderId = target.providerId,
            initialModelId = target.modelId,
            config = config,
            apiTestState = apiTestState,
            onDismiss = {
                remoteModelEditorTarget = null
            },
            onClearTest = viewModel::clearApiTestState,
            onVisibleDraftChange = viewModel::setApiTestVisibleDraft,
            onTest = { connection ->
                viewModel.testApiConnectionDraft(connection, replacingModelId = target.modelId)
            },
            onCancelTest = viewModel::cancelApiTest,
            onDelete = { connectionId, modelIds ->
                pendingSheetRemoteModelDelete = PendingRemoteModelDelete(
                    connectionId = connectionId,
                    modelIds = modelIds,
                )
            },
        )
    }

    pendingSheetRemoteModelDelete?.let { pending ->
        val modelLabel = pending.modelIds.joinToString("、")
        AlertDialog(
            onDismissRequest = { pendingSheetRemoteModelDelete = null },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            title = { Text("删除模型？") },
            text = { Text("将删除 $modelLabel 的配置和能力检测记录，删除后需要重新添加并测试。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pending.modelIds.isNotEmpty()) {
                            viewModel.save(
                                removeRemoteModels(config, pending.connectionId, pending.modelIds),
                            )
                        }
                        pendingSheetRemoteModelDelete = null
                        remoteModelEditorTarget = null
                        viewModel.clearApiTestState()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSheetRemoteModelDelete = null }) {
                    Text("取消")
                }
            },
        )
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

    if (showPairingManagementDialog && pairedConnector != null) {
        AlertDialog(
            onDismissRequest = { showPairingManagementDialog = false },
            title = { Text("取消设备配对？") },
            text = {
                Text("手机会立即清除配对并恢复未配对状态。若电脑离线，电脑端可能暂时保留此设备的授权记录。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPairingManagementDialog = false
                        viewModel.cancelDevicePairing()
                    },
                ) {
                    Text("取消配对", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairingManagementDialog = false }) {
                    Text("返回")
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

    if (showDiagnosticExportDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticExportDialog = false },
            title = { Text("导出诊断记录？") },
            text = {
                Text(
                    "将生成一份本地文本，包含设备与版本、模型状态、最近 5 个对话内容、最近任务步骤和崩溃记录。" +
                        "API Key、Bearer 凭据和网址查询参数会自动脱敏；请在系统分享面板中确认接收方。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiagnosticExportDialog = false
                        viewModel.exportDiagnosticReport()
                    },
                ) {
                    Text("生成并分享")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticExportDialog = false }) {
                    Text("取消")
                }
            },
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
    onClearLocalModel: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    val chatRef = config.configuredChatModelRef()
    val chatConnection = chatRef?.let { config.connection(it.connectionId) }
    val visionRef = config.configuredVisionModelRef()
    val visionConnection = visionRef?.let { config.connection(it.connectionId) }
    val imageRef = config.configuredImageModelRef()
    val imageConnection = imageRef?.let { config.connection(it.connectionId) }
    val resolvedVisionModel = visionRef?.modelId.orEmpty()
    val resolvedImageModel = imageRef?.modelId.orEmpty()
    val chatOptions = configuredRemoteModelCandidates(config, AiModelPurpose.Chat)
    val visionOptions = configuredRemoteModelCandidates(config, AiModelPurpose.Vision)
    val imageOptions = configuredRemoteModelCandidates(config, AiModelPurpose.ImageGeneration)
    val selectedLocal = LocalModelCatalog.get(localModelId)
    val selectedLocalState = localModelStates.firstOrNull { it.modelId == localModelId }
    val installedLocalModels = LocalModelCatalog.models.filter { item ->
        localModelStates.firstOrNull { it.modelId == item.id }?.installed == true
    }

    SectionHeader("当前模型")
    SettingGroup {
        ModelPurposeRow(
            purpose = "聊天",
            modelName = chatRef?.modelId ?: "未配置",
            connected = connectionConfigurationStatus(chatConnection) == "已连接",
            options = chatOptions,
            selectedKey = chatRef?.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { remoteModelCapabilitySummary(it.connection.modelCapabilities.getValue(it.model.id)) },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.Chat, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "识图",
            modelName = resolvedVisionModel.ifBlank { "未配置" },
            connected = connectionConfigurationStatus(visionConnection) == "已连接",
            options = visionOptions,
            selectedKey = visionRef?.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { remoteModelCapabilitySummary(it.connection.modelCapabilities.getValue(it.model.id)) },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.Vision, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "图片生成",
            modelName = resolvedImageModel.ifBlank { "未配置" },
            connected = connectionConfigurationStatus(imageConnection) == "已连接",
            options = imageOptions,
            selectedKey = imageRef?.selectorKey(),
            optionKey = RemoteModelCandidate::selectorKey,
            optionName = { it.model.name },
            optionSource = { remoteModelCapabilitySummary(it.connection.modelCapabilities.getValue(it.model.id)) },
            optionIsFree = { it.model.isFree },
            onSelect = { onSelectRemoteModel(AiModelPurpose.ImageGeneration, it) },
        )
        GroupDivider()
        ModelPurposeRow(
            purpose = "本地模型",
            modelName = selectedLocal?.name ?: "未配置",
            connected = selectedLocalState?.installed == true,
            options = installedLocalModels,
            selectedKey = localModelId,
            optionKey = LocalModelPreset::id,
            optionName = LocalModelPreset::name,
            optionSource = ::localModelTaskLabel,
            optionIsFree = { false },
            onSelect = onSelectLocalModel,
            onClear = onClearLocalModel,
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
    onDynamicModelRoutingChange: (Boolean) -> Unit,
    onHighRiskConfirmationChange: (Boolean) -> Unit,
    onBackgroundExecutionChange: (Boolean) -> Unit,
) {
    SectionHeader("其他设置")
    SettingGroup {
        SwitchSettingRow(
            title = "动态选择使用模型",
            description = "根据任务难度，在已配置的模型中自动选择模型",
            checked = config.dynamicLocalRoutingEnabled,
            onCheckedChange = onDynamicModelRoutingChange,
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
    onClear: (() -> Unit)? = null,
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = buildAnnotatedString {
                    append(modelName)
                    if (!connected && modelName != "未配置") {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append("（未连接）")
                        }
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
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
                if (onClear != null) {
                    DropdownMenuItem(
                        text = { Text("不配置") },
                        onClick = {
                            expanded = false
                            onClear()
                        },
                        trailingIcon = {
                            if (selectedKey.isNullOrBlank()) {
                                Icon(Icons.Outlined.Check, contentDescription = "当前未配置")
                            }
                        },
                    )
                }
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
                                        fontSize = 12.sp,
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
        val savedCapabilities = connection.modelCapabilities[modelId] ?: return@mapNotNull null
        val catalogModel = AiProviderCatalog.getModel(connection.providerId, modelId)
            ?: AiModelPreset(
                id = modelId,
                name = modelId,
                description = "手动添加的模型",
                isFree = AiProviderCatalog.isFreeModel(connection.providerId, modelId),
                supportsTools = connection.toolsSupported,
                supportsVision = false,
                supportsImageGeneration = false,
            )
        val model = catalogModel.copy(
            supportsChat = savedCapabilities.supportsChatModel(),
            supportsTools = savedCapabilities.supportsTools,
            supportsVision = savedCapabilities.supportsVision,
            supportsImageGeneration = savedCapabilities.supportsImageGeneration,
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
    OverviewSettingRow(
        title = "模型接口",
        description = "",
        onClick = onClick,
    )
}

@Composable
private fun ModelSettingsContent(
    config: ApiConfig,
    localModelStates: List<LocalModelFileState>,
    apiTestState: ApiTestUiState,
    onOpenLocalModels: () -> Unit,
    onOpenRemoteModel: (ApiConnection, String) -> Unit,
    onAddRemoteModel: () -> Unit,
) {
    SectionHeader("本地模型")
    SettingGroup {
        val installedCount = localModelStates.count(LocalModelFileState::installed)
        AiProviderRow(
            providerId = LOCAL_PROVIDER_ID,
            name = "本地模型",
            detail = if (installedCount > 0) "$installedCount 个已安装" else "设备本地运行",
            status = if (installedCount > 0) "本地" else "未安装",
            onClick = onOpenLocalModels,
        )
    }

    SectionHeader("远端模型")
    SettingGroup {
        val visibleModels = visibleRemoteModelEntries(config, apiTestState)
        visibleModels.forEachIndexed { index, (connection, modelId) ->
            if (index > 0) GroupDivider(horizontalPadding = 14.dp)
            RemoteModelConfigurationRow(
                connection = connection,
                modelId = modelId,
                apiTestState = apiTestState,
                onClick = { onOpenRemoteModel(connection, modelId) },
            )
        }
        if (visibleModels.isNotEmpty()) GroupDivider(horizontalPadding = 14.dp)
        CompactActionRow(
            title = "+ 添加",
            description = "添加模型 API 配置",
            enabled = true,
            onClick = onAddRemoteModel,
        )
    }
}

internal fun visibleRemoteModelEntries(
    config: ApiConfig,
    apiTestState: ApiTestUiState,
): List<Pair<ApiConnection, String>> {
    val configuredModels = config.resolvedConnections().flatMap { connection ->
        connection.modelIds.map { modelId -> connection to modelId }
    }
    val pendingModels = if (apiTestState.isTesting) {
        apiTestState.targetConnection?.let { connection ->
            connection.modelIds.map { modelId -> connection to modelId }
        }.orEmpty()
    } else {
        emptyList()
    }
    return configuredModels + pendingModels.filterNot { pending ->
        configuredModels.any { configured ->
            configured.first.id == pending.first.id && configured.second == pending.second
        }
    }
}

@Composable
private fun RemoteModelConfigurationRow(
    connection: ApiConnection,
    modelId: String,
    apiTestState: ApiTestUiState,
    onClick: () -> Unit,
) {
    val modelName = AiProviderCatalog.getModel(connection.providerId, modelId)?.name ?: modelId
    val status = remoteModelTestStatus(connection, modelId, apiTestState)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = status,
                color = if (status == "测试中") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "编辑模型配置",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(17.dp),
        )
    }
}

internal fun remoteModelTestStatus(
    connection: ApiConnection,
    modelId: String,
    state: ApiTestUiState,
): String {
    val targetsModel = state.targetConnection?.let { target ->
        target.id == connection.id && modelId in target.modelIds
    } == true
    if (targetsModel) {
        if (state.isTesting && state.activeModelId != null && state.activeModelId != modelId) {
            // A connection test can contain several IDs, but only the active
            // one should be shown as running.
        } else {
            if (state.isTesting) return "测试中"
        }
        connection.modelTestErrors[modelId]
            ?.let { return remoteModelTestFailureStatus(it) }
        val testedCapabilities = state.testedConnection?.modelCapabilities?.get(modelId)
        if (testedCapabilities != null) {
            val savedSignature = connection.verifiedModelSignatures[modelId]
            val testedSignature = state.testedConnection.verifiedModelSignatures[modelId]
            val isSaved = connection.modelCapabilities[modelId] == testedCapabilities &&
                savedSignature != null && savedSignature == testedSignature
            return remoteModelCapabilitySummary(testedCapabilities) +
                if (isSaved) "" else "，待保存"
        }
        if (state.success == false) return "测试失败"
    }
    val observedKey = apiTestModelKey(connection.id, modelId)
    state.observedModelCapabilities[observedKey]
        ?.let { observed ->
            return remoteModelCapabilitySummary(observed) +
                if (connection.modelCapabilities[modelId] == observed) "" else "，待保存"
        }
    if (observedKey in state.observedFailedModels) return "测试失败"
    connection.modelTestErrors[modelId]
        ?.let(::remoteModelTestFailureStatus)
        ?.let { return it }
    return connection.modelCapabilities[modelId]
        ?.let(::remoteModelCapabilitySummary)
        ?: "待测试能力"
}

internal fun remoteModelCapabilitySummary(capabilities: ApiModelCapabilities): String {
    return buildList {
        if (capabilities.supportsChatModel()) add("聊天")
        if (capabilities.supportsTools) add("工具调用")
        if (capabilities.supportsVision) add("识图")
        if (capabilities.supportsImageGeneration) add("生图")
    }.ifEmpty { listOf("能力未知") }.joinToString("、")
}

internal fun remoteModelTestFailureStatus(detail: String): String {
    val httpCode = Regex("\\bHTTP\\s+(\\d{3})\\b", RegexOption.IGNORE_CASE)
        .find(detail)
        ?.groupValues
        ?.getOrNull(1)
    return when {
        httpCode != null -> "未通过测试 HTTP $httpCode"
        detail.contains("timeout", ignoreCase = true) || detail.contains("超时") ->
            "未通过测试 超时"
        else -> "未通过测试"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteModelConfigurationSheet(
    initialConnectionId: String?,
    initialProviderId: String,
    initialModelId: String?,
    config: ApiConfig,
    apiTestState: ApiTestUiState,
    onDismiss: () -> Unit,
    onClearTest: () -> Unit,
    onVisibleDraftChange: (ApiConnection?) -> Unit,
    onTest: (ApiConnection) -> Unit,
    onCancelTest: () -> Unit,
    onDelete: (String, List<String>) -> Unit,
) {
    val selectedProvider = AiProviderCatalog.getProvider(initialProviderId)
        ?: AiProviderCatalog.getProvider(AiProviderCatalog.CUSTOM_PROVIDER_ID)
        ?: AiProviderCatalog.defaultProvider
    val editorKey = "$initialConnectionId::$initialProviderId::$initialModelId"
    val formScrollState = remember(editorKey) { ScrollState(0) }
    val formEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = LocalInterfaceEffects.current.progressiveEdgeBlurEnabled,
    )
    val editingExistingModel = initialModelId != null
    val savedConnection = if (editingExistingModel) {
        initialConnectionId?.let(config::connection)
            ?: config.resolvedConnections().firstOrNull { connection ->
                connection.providerId == initialProviderId &&
                    initialModelId in connection.modelIds
            }
    } else {
        null
    }
    val activeTestConnection = apiTestState.targetConnection?.takeIf { target ->
        initialModelId != null &&
            target.id == initialConnectionId &&
            target.providerId == initialProviderId &&
            initialModelId in target.modelIds &&
            (apiTestState.replacingModelId == initialModelId ||
                apiTestState.replacingModelId == null)
    }
    val draftSourceConnection = activeTestConnection ?: savedConnection
    val quickModelIds = config.resolvedConnections()
        .flatMap(ApiConnection::modelIds)
        .distinct()
    val initialApiUrl = draftSourceConnection?.apiUrl?.takeIf(String::isNotBlank)
        ?: selectedProvider.apiUrl.takeUnless { selectedProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID }
            .orEmpty()
    val initialApiKey = draftSourceConnection?.apiKey.orEmpty()
    val initialWorkspaceId = draftSourceConnection?.workspaceId.orEmpty()
    val initialModelIds = normalizeRemoteModelIds(initialRemoteModelIdDrafts(initialModelId))
    var apiUrl by remember(editorKey) { mutableStateOf(initialApiUrl) }
    var apiKey by remember(editorKey) { mutableStateOf(initialApiKey) }
    var workspaceId by remember(editorKey) { mutableStateOf(initialWorkspaceId) }
    var modelIdDrafts by remember(editorKey) { mutableStateOf(initialModelIds) }
    var pendingModelIdDeleteIndex by remember(editorKey) { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember(editorKey) { mutableStateOf(false) }
    var confirmCancelTest by remember(editorKey) { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var sheetReady by remember(editorKey) { mutableStateOf(false) }

    LaunchedEffect(editorKey) {
        apiUrl = draftSourceConnection?.apiUrl?.takeIf(String::isNotBlank)
            ?: selectedProvider.apiUrl.takeUnless { selectedProvider.id == AiProviderCatalog.CUSTOM_PROVIDER_ID }
                .orEmpty()
        apiKey = draftSourceConnection?.apiKey.orEmpty()
        workspaceId = draftSourceConnection?.workspaceId.orEmpty()
        modelIdDrafts = initialRemoteModelIdDrafts(initialModelId)
        pendingModelIdDeleteIndex = null
        confirmDiscard = false
        confirmCancelTest = false
        keyVisible = false
        sheetReady = false
        // ModalBottomSheet can report its initial hidden anchor as a dismiss
        // during the first composition. Ignore that transient callback.
        withFrameNanos { }
        withFrameNanos { sheetReady = true }
    }

    val draftModelIds = normalizeRemoteModelIds(modelIdDrafts)
    val hasDraftChanges = apiUrl.trim().trimEnd('/') != initialApiUrl.trim().trimEnd('/') ||
        apiKey.trim() != initialApiKey.trim() ||
        workspaceId.trim() != initialWorkspaceId.trim() ||
        draftModelIds != initialModelIds
    val requiresKey = !AiProviderCatalog.allowsBlankApiKey(apiUrl)
    val canTest = apiUrl.isNotBlank() && draftModelIds.isNotEmpty() && (!requiresKey || apiKey.isNotBlank())
    val draftConnection = ApiConnection(
        id = savedConnection?.id
            ?: initialConnectionId
            ?: connectionIdForModel(
                selectedProvider.id,
                apiUrl,
                draftModelIds.firstOrNull().orEmpty(),
            ),
        providerId = selectedProvider.id,
        name = savedConnection?.name ?: selectedProvider.name,
        apiUrl = apiUrl.trim(),
        apiKey = apiKey.trim(),
        modelIds = draftModelIds,
        workspaceId = workspaceId.trim(),
    )
    val testTargetsDraft = apiTestState.targetConnection?.let { target ->
        sameRemoteModelEditorTarget(
            target = target,
            draft = draftConnection,
            editingModelId = initialModelId,
            replacingModelId = apiTestState.replacingModelId,
        ) &&
            testStateTargetsRemoteModelEditor(
                target = target,
                editingModelId = initialModelId,
                replacingModelId = apiTestState.replacingModelId,
                activeModelId = apiTestState.activeModelId,
            )
    } == true
    val isTestingDraft = testTargetsDraft && apiTestState.isTesting
    val testedConnection = apiTestState.testedConnection.takeIf { testTargetsDraft }
    val editorMode = remoteModelSheetMode(
        draft = draftConnection,
        savedConnection = savedConnection,
        editingModelId = initialModelId,
        testState = apiTestState,
    )
    val shouldConfirmDismiss = if (testTargetsDraft && apiTestState.success == false) {
        false
    } else {
        shouldConfirmRemoteModelSheetDismiss(
            mode = editorMode,
            apiUrl = apiUrl,
            apiKey = apiKey,
            requiresApiKey = requiresKey,
            hasDraftChanges = hasDraftChanges,
        )
    }
    val currentShouldConfirmDismiss by rememberUpdatedState(shouldConfirmDismiss)
    val sheetScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            val allowTransition = shouldAllowRemoteModelSheetTransition(
                targetValue = targetValue,
                shouldConfirmDismiss = currentShouldConfirmDismiss,
            )
            if (!allowTransition) {
                confirmDiscard = true
            }
            allowTransition
        },
    )

    val keepSheetVisible = {
        if (!sheetState.isVisible) {
            sheetScope.launch { sheetState.show() }
        }
    }
    val showDiscardConfirmation = {
        confirmDiscard = true
        keepSheetVisible()
    }
    val requestDismiss = {
        if (!sheetReady) {
            keepSheetVisible()
        } else if (currentShouldConfirmDismiss) {
            showDiscardConfirmation()
        } else {
            // Closing the sheet is only a view change. A running API test belongs
            // to the ViewModel/runtime and must continue in the background.
            onDismiss()
        }
    }

    LaunchedEffect(draftConnection) {
        onVisibleDraftChange(draftConnection)
    }
    DisposableEffect(editorKey) {
        onDispose { onVisibleDraftChange(null) }
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        shape = settingsSheetShape,
        containerColor = settingsSheetSurfaceColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !currentShouldConfirmDismiss,
        ),
        dragHandle = null,
    ) {
        BackHandler(
            enabled = currentShouldConfirmDismiss && !confirmDiscard && !confirmCancelTest,
        ) {
            showDiscardConfirmation()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .settingsSheetContentBackdrop()
                .imePadding()
                .padding(bottom = 18.dp),
        ) {
            SettingsSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (editingExistingModel) "配置模型" else "添加模型",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureProgressiveEdgeBlur(formEdgeBlurState)
                        .verticalScroll(formScrollState)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                SectionHeader("接口地址")
                CompactInput(
                    label = "",
                    value = apiUrl,
                    placeholder = "例如 https://api.example.com/v1",
                    enabled = !isTestingDraft,
                    multiline = true,
                ) {
                    apiUrl = it
                    onClearTest()
                }
                SectionHeader("API Key")
                CompactInput(
                    label = "",
                    value = apiKey,
                    placeholder = if (requiresKey) "填写 API Key" else "可选",
                    enabled = !isTestingDraft,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { keyVisible = !keyVisible },
                            enabled = !isTestingDraft,
                        ) {
                            Icon(
                                if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (keyVisible) "隐藏" else "显示",
                            )
                        }
                    },
                ) {
                    apiKey = it
                    onClearTest()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 7.dp, start = 3.dp, end = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Model ID",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (!editingExistingModel) {
                        Text(
                            "添加",
                            color = if (isTestingDraft) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable(enabled = !isTestingDraft) {
                                    modelIdDrafts = modelIdDrafts + ""
                                    onClearTest()
                                }
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                        )
                    }
                }
                modelIdDrafts.forEachIndexed { index, modelId ->
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { value ->
                            modelIdDrafts = modelIdDrafts.toMutableList().also { drafts ->
                                drafts[index] = value.take(200)
                            }
                            onClearTest()
                        },
                        enabled = !isTestingDraft,
                        placeholder = { Text("例如 gpt-4o-mini") },
                        singleLine = true,
                        trailingIcon = if (!editingExistingModel && index > 0) {
                            {
                            IconButton(
                                enabled = !isTestingDraft,
                                onClick = {
                                    if (modelId.isBlank()) {
                                        modelIdDrafts = modelIdDrafts.filterIndexed { itemIndex, _ -> itemIndex != index }
                                        onClearTest()
                                    } else {
                                        pendingModelIdDeleteIndex = index
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除 Model ID",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = themedFieldColors(),
                    )
                }
                val availableQuickModelIds = quickModelIds.filterNot { quickId ->
                    modelIdDrafts.any { it.trim() == quickId }
                }
                if (!editingExistingModel && availableQuickModelIds.isNotEmpty()) {
                    val quickModelIdScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(quickModelIdScrollState)
                                .align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            availableQuickModelIds.forEach { quickId ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable(enabled = !isTestingDraft) {
                                            modelIdDrafts = applyRemoteModelQuickId(modelIdDrafts, quickId)
                                            onClearTest()
                                        }
                                        .height(28.dp)
                                        .padding(horizontal = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = quickId,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (quickModelIdScrollState.canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(24.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.background,
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                        }
                        if (quickModelIdScrollState.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(24.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                }
                when (editorMode) {
                    RemoteModelSheetMode.Verified -> StatusText("已测试通过并保存", true)
                    RemoteModelSheetMode.Testing -> StatusText(
                        apiTestState.message ?: "正在测试模型...",
                        null,
                    )
                    RemoteModelSheetMode.Draft -> if (testTargetsDraft) {
                        StatusText(apiTestState.message, apiTestState.success)
                    }
                }
                val displayedCapabilities = testedConnection ?: savedConnection
                draftModelIds.forEach { modelId ->
                    displayedCapabilities?.modelCapabilities?.get(modelId)?.let { capabilities ->
                        Text(
                            "$modelId：${remoteModelCapabilitySummary(capabilities)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                    Spacer(Modifier.height(12.dp))
                }
                SettingsSheetEdgeFades(
                    scrollState = formScrollState,
                    blurState = formEdgeBlurState,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (editorMode) {
                    RemoteModelSheetMode.Draft -> {
                        Button(
                            onClick = { onTest(draftConnection) },
                            enabled = canTest && !isTestingDraft,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("测试连接")
                        }
                        if (editingExistingModel) {
                            Button(
                                onClick = { onDelete(draftConnection.id, draftModelIds) },
                                enabled = draftModelIds.isNotEmpty() && !isTestingDraft,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text("删除")
                            }
                        }
                    }
                    RemoteModelSheetMode.Testing -> Button(
                        onClick = { confirmCancelTest = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("取消")
                    }
                    RemoteModelSheetMode.Verified -> {
                        Button(
                            onClick = { onTest(draftConnection) },
                            enabled = canTest && !isTestingDraft,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("重新测试")
                        }
                        Button(
                            onClick = { onDelete(draftConnection.id, draftModelIds) },
                            enabled = draftModelIds.isNotEmpty() && !isTestingDraft,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            title = { Text("退出配置？") },
            text = { Text("当前配置尚未测试通过，退出后本次填写的内容不会保存。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onClearTest()
                        onDismiss()
                    },
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        keepSheetVisible()
                    },
                ) {
                    Text("继续配置")
                }
            },
        )
    }

    if (confirmCancelTest) {
        AlertDialog(
            onDismissRequest = { confirmCancelTest = false },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            title = { Text("取消测试？") },
            text = { Text("取消后将停止当前模型测试，本次配置不会保存。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancelTest = false
                        onCancelTest()
                        onDismiss()
                    },
                ) {
                    Text("取消测试", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelTest = false }) {
                    Text("继续测试")
                }
            },
        )
    }

    pendingModelIdDeleteIndex?.let { index ->
        val modelId = modelIdDrafts.getOrNull(index).orEmpty()
        AlertDialog(
            onDismissRequest = { pendingModelIdDeleteIndex = null },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            title = {
                Text(
                    "删除 Model ID？",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    "将从当前配置草稿中移除 $modelId。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        modelIdDrafts = modelIdDrafts.filterIndexed { itemIndex, _ -> itemIndex != index }
                        pendingModelIdDeleteIndex = null
                        onClearTest()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModelIdDeleteIndex = null }) {
                    Text("取消")
                }
            },
        )
    }
}

internal fun normalizeRemoteModelIds(values: List<String>): List<String> = values
    .map { it.trim().take(200) }
    .filter { it.isNotBlank() && it.none(Char::isISOControl) }
    .distinct()

internal fun initialRemoteModelIdDrafts(editingModelId: String?): List<String> =
    listOf(editingModelId.orEmpty())

internal fun applyRemoteModelQuickId(drafts: List<String>, modelId: String): List<String> {
    if (drafts.any { it.trim() == modelId }) return drafts
    val blankIndex = drafts.indexOfFirst(String::isBlank)
    return if (blankIndex >= 0) {
        drafts.toMutableList().also { it[blankIndex] = modelId }
    } else {
        drafts + modelId
    }
}

internal enum class RemoteModelSheetMode {
    Draft,
    Testing,
    Verified,
}

internal fun remoteModelSheetMode(
    draft: ApiConnection,
    savedConnection: ApiConnection?,
    editingModelId: String?,
    testState: ApiTestUiState,
): RemoteModelSheetMode {
    val testTargetsDraft = testState.targetConnection?.let { target ->
        sameRemoteModelEditorTarget(
            target = target,
            draft = draft,
            editingModelId = editingModelId,
            replacingModelId = testState.replacingModelId,
        ) &&
            testStateTargetsRemoteModelEditor(
                target = target,
                editingModelId = editingModelId,
                replacingModelId = testState.replacingModelId,
                activeModelId = testState.activeModelId,
            )
    } == true
    if (testTargetsDraft && testState.isTesting) return RemoteModelSheetMode.Testing
    if (testTargetsDraft && testState.success == true && testState.saved) {
        return RemoteModelSheetMode.Verified
    }
    return if (isSavedRemoteModelDraftVerified(savedConnection, draft, editingModelId)) {
        RemoteModelSheetMode.Verified
    } else {
        RemoteModelSheetMode.Draft
    }
}

internal fun shouldConfirmRemoteModelSheetDismiss(
    mode: RemoteModelSheetMode,
    apiUrl: String,
    apiKey: String,
    requiresApiKey: Boolean,
    hasDraftChanges: Boolean = true,
): Boolean = mode == RemoteModelSheetMode.Draft &&
    hasDraftChanges &&
    apiUrl.isNotBlank() &&
    (!requiresApiKey || apiKey.isNotBlank())

@OptIn(ExperimentalMaterial3Api::class)
internal fun shouldAllowRemoteModelSheetTransition(
    targetValue: SheetValue,
    shouldConfirmDismiss: Boolean,
): Boolean = targetValue != SheetValue.Hidden || !shouldConfirmDismiss

private fun isSavedRemoteModelDraftVerified(
    savedConnection: ApiConnection?,
    draft: ApiConnection,
    editingModelId: String?,
): Boolean {
    val modelId = editingModelId ?: return false
    val saved = savedConnection ?: return false
    if (draft.modelIds != listOf(modelId) || modelId !in saved.modelIds) return false
    if (
        saved.id != draft.id ||
        saved.providerId != draft.providerId ||
        saved.apiUrl.trim().trimEnd('/') != draft.apiUrl.trim().trimEnd('/') ||
        saved.apiKey != draft.apiKey ||
        saved.workspaceId.trim() != draft.workspaceId.trim()
    ) return false
    val expectedSignature = AiProviderCatalog.verificationSignature(
        ApiConfig(
            providerId = draft.providerId,
            apiUrl = draft.apiUrl,
            apiKey = draft.apiKey,
            model = modelId,
        ),
    )
    val savedSignature = saved.verifiedModelSignatures[modelId]
        ?: saved.verifiedSignature.takeIf { saved.modelIds.firstOrNull() == modelId }
    return savedSignature == expectedSignature
}

internal fun sameRemoteModelDraft(first: ApiConnection, second: ApiConnection): Boolean =
    first.id == second.id &&
        first.providerId == second.providerId &&
        first.apiUrl.trim().trimEnd('/') == second.apiUrl.trim().trimEnd('/') &&
        first.apiKey == second.apiKey &&
        normalizeRemoteModelIds(first.modelIds) == normalizeRemoteModelIds(second.modelIds) &&
        first.workspaceId.trim() == second.workspaceId.trim()

internal fun sameRemoteModelEditorTarget(
    target: ApiConnection,
    draft: ApiConnection,
    editingModelId: String?,
    replacingModelId: String?,
): Boolean {
    if (sameRemoteModelDraft(target, draft)) return true
    if (replacingModelId != null || editingModelId.isNullOrBlank()) return false
    return target.id == draft.id &&
        target.providerId == draft.providerId &&
        target.apiUrl.trim().trimEnd('/') == draft.apiUrl.trim().trimEnd('/') &&
        target.apiKey == draft.apiKey &&
        target.workspaceId.trim() == draft.workspaceId.trim() &&
        editingModelId in target.modelIds &&
        draft.modelIds == listOf(editingModelId)
}

internal fun testStateTargetsRemoteModelEditor(
    target: ApiConnection,
    editingModelId: String?,
    replacingModelId: String?,
    activeModelId: String? = null,
): Boolean {
    if (activeModelId != null && editingModelId != null && activeModelId != editingModelId) {
        return false
    }
    return replacingModelId == editingModelId || (
        replacingModelId == null &&
            (editingModelId == null || editingModelId in target.modelIds)
        )
}

internal fun removeRemoteModel(
    config: ApiConfig,
    connectionId: String,
    modelId: String,
): ApiConfig {
    val connection = config.connection(connectionId) ?: return config
    if (modelId !in connection.modelIds) return config

    val remainingModels = connection.modelIds.filterNot { it == modelId }
    val removedChat = config.resolvedChatModelRef() == ModelReference(connectionId, modelId) || (
        config.model == modelId &&
            config.providerId == connection.providerId &&
            config.apiUrl.trim().trimEnd('/') == connection.apiUrl.trim().trimEnd('/')
        )
    val removedVision = config.resolvedVisionModelRef() == ModelReference(connectionId, modelId)
    val removedImage = config.resolvedImageModelRef() == ModelReference(connectionId, modelId)
    val trimmedConnection = connection.copy(
        modelIds = remainingModels,
        toolsSupported = remainingModels.any { remainingId ->
            connection.modelCapabilities[remainingId]?.supportsTools == true
        },
        verifiedSignature = remainingModels.firstNotNullOfOrNull { remainingId ->
            connection.verifiedModelSignatures[remainingId]?.takeIf(String::isNotBlank)
        }.orEmpty(),
        modelCapabilities = connection.modelCapabilities - modelId,
        verifiedModelSignatures = connection.verifiedModelSignatures - modelId,
        modelTestErrors = connection.modelTestErrors - modelId,
    )
    var next = if (remainingModels.isEmpty()) {
        config.removeConnection(connectionId)
    } else {
        config.saveConnection(trimmedConnection)
    }.copy(
        visionModel = if (removedVision) "" else config.visionModel,
        imageModel = if (removedImage) "" else config.imageModel,
        visionModelRef = config.visionModelRef.takeUnless { removedVision },
        imageModelRef = config.imageModelRef.takeUnless { removedImage },
    )

    if (!removedChat) return next

    val sameConnectionFallback = remainingModels.firstOrNull { remainingId ->
        trimmedConnection.modelCapabilities[remainingId]?.supportsChatModel() == true
    } ?: remainingModels.firstOrNull()
    if (sameConnectionFallback != null) {
        return next.selectChatModel(ModelReference(connectionId, sameConnectionFallback))
    }

    next = next.copy(
        providerId = "",
        apiUrl = "",
        apiKey = "",
        model = "",
        toolsEnabled = false,
        verifiedSignature = "",
        chatModelRef = null,
    )
    val otherFallback = next.configuredConnections().firstNotNullOfOrNull { candidate ->
        candidate.modelIds.firstOrNull { candidateId ->
            candidate.modelCapabilities[candidateId]?.supportsChatModel() == true
        }?.let { candidateId -> ModelReference(candidate.id, candidateId) }
    }
    return otherFallback?.let(next::selectChatModel) ?: next
}

internal fun removeRemoteModels(
    config: ApiConfig,
    connectionId: String,
    modelIds: List<String>,
): ApiConfig = normalizeRemoteModelIds(modelIds).fold(config) { current, modelId ->
    removeRemoteModel(current, connectionId, modelId)
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
        fontSize = 12.sp,
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
                fontSize = 12.sp,
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
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
    onNotificationModeChange: (TaskNotificationMode) -> Unit,
    onOpenMemory: () -> Unit,
    pairedConnector: PairedConnector?,
    onOpenDevicePairing: () -> Unit,
    onManageDevicePairing: () -> Unit,
    onOpenPhoneAgent: () -> Unit,
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

    SectionHeader("屏幕助手")
    SettingGroup {
        OverviewSettingRow(
            title = "配置功能",
            description = "",
            onClick = onOpenPhoneAgent,
        )
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
                                    android12RequirementDescription(Build.VERSION.SDK_INT) != null
                                ) {
                                    Text(
                                        text = android12RequirementDescription(Build.VERSION.SDK_INT).orEmpty(),
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
            description = "",
            onClick = onOpenMemory,
        )
    }

    SectionHeader("设备配对")
    SettingGroup {
        DevicePairingSettingRow(
            connector = pairedConnector,
            onOpenPairing = onOpenDevicePairing,
            onManage = onManageDevicePairing,
        )
    }
}

@Composable
private fun DevicePairingSettingRow(
    connector: PairedConnector?,
    onOpenPairing: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (connector == null) onOpenPairing else onManage)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = connector?.let {
                "已配对 ${it.connectorDeviceId.takeLast(4).uppercase()}（${it.displayName}）"
            } ?: "远端电脑配置",
            color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (connector == null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                modifier = Modifier.size(19.dp),
            )
        } else {
            TextButton(onClick = onManage) { Text("管理") }
        }
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
            description = "",
            onClick = onOpenPermission,
        )
        GroupDivider()
        OverviewSettingRow(
            title = "关于",
            description = "",
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
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
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "打开设置项",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.size(18.dp),
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
        fontSize = 14.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
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
    states: List<LocalModelFileState>,
    downloadStates: Map<String, LocalModelDownloadState>,
    onDownload: (LocalModelPreset) -> Unit,
    onPauseDownload: (LocalModelPreset) -> Unit,
    onCancelDownload: (LocalModelPreset) -> Unit,
    onDelete: (LocalModelPreset) -> Unit,
) {
    SettingGroup {
        models.forEachIndexed { index, item ->
            if (index > 0) GroupDivider()
            LocalModelManagementRow(
                item = item,
                state = states.firstOrNull { it.modelId == item.id },
                downloadState = downloadStates[item.id],
                onDownload = { onDownload(item) },
                onPauseDownload = { onPauseDownload(item) },
                onCancelDownload = { onCancelDownload(item) },
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
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloadActive = downloadState?.status in setOf(
        LocalModelDownloadStatus.Checking,
        LocalModelDownloadStatus.Downloading,
        LocalModelDownloadStatus.Verifying,
    )
    val downloadCompleted = downloadState?.status == LocalModelDownloadStatus.Completed
    val hasPartialDownload = !downloadCompleted && (downloadState?.downloadedBytes ?: 0L) > 0L
    val hasLocalFile = state?.state != null && state.state != LocalModelInstallState.NotInstalled
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        val visibleDownloadState = downloadState?.takeIf(::shouldShowLocalModelDownloadProgress)
        if (visibleDownloadState != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { visibleDownloadState.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                localModelDownloadDescription(visibleDownloadState),
                color = if (visibleDownloadState.status == LocalModelDownloadStatus.Failed) {
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
            if (state?.installed != true && !downloadCompleted) {
                if (downloadActive) {
                    TextButton(onClick = onPauseDownload) {
                        Text("暂停")
                    }
                    TextButton(onClick = onCancelDownload) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDownload,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (hasPartialDownload) "继续" else "下载")
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (!downloadActive && (hasLocalFile || hasPartialDownload)) {
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
        LocalModelDownloadStatus.Cancelled -> "已取消"
        LocalModelDownloadStatus.Verifying -> state.message ?: "正在校验文件"
        LocalModelDownloadStatus.Completed -> state.message ?: "下载完成"
        LocalModelDownloadStatus.Failed -> state.message ?: "下载失败，可继续重试"
    }
}

internal fun shouldShowLocalModelDownloadProgress(state: LocalModelDownloadState?): Boolean {
    if (
        state == null ||
        state.status == LocalModelDownloadStatus.Completed ||
        state.status == LocalModelDownloadStatus.Cancelled ||
        state.status == LocalModelDownloadStatus.Idle
    ) return false
    val active = state.status in setOf(
        LocalModelDownloadStatus.Checking,
        LocalModelDownloadStatus.Downloading,
        LocalModelDownloadStatus.Verifying,
    )
    return active || state.downloadedBytes > 0L || state.message != null
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
        SettingsGlassDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.align(Alignment.TopEnd),
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
                fontSize = 14.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
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
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

internal fun officialEntryUrl(provider: AiProviderPreset): String = when (provider.id) {
    "openrouter" -> "https://openrouter.ai/settings/keys"
    "openai" -> "https://platform.openai.com/api-keys"
    "gemini" -> "https://aistudio.google.com/app/apikey"
    "qwen" -> "https://bailian.console.aliyun.com/"
    "siliconflow" -> "https://cloud.siliconflow.cn/account/ak"
    "deepseek" -> "https://platform.deepseek.com/api_keys"
    "kimi" -> "https://platform.moonshot.cn/console/api-keys"
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
    glassRefractionEnabled: Boolean,
    glassTransparency: Float,
    selectedColor: Long,
    selectedFontSize: FontSizePreference,
    onModeChange: (ThemeMode) -> Unit,
    onStyleChange: (InterfaceStyle) -> Unit,
    onGlassRefractionChange: (Boolean) -> Unit,
    onGlassTransparencyPreview: (Float) -> Unit,
    onGlassTransparencyCommit: (Float) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onFontSizeChange: (FontSizePreference) -> Unit,
) {
    SettingGroup {
        ThemeModeSelectionRow(
            selectedMode = selectedMode,
            onModeChange = onModeChange,
        )
        GroupDivider()
        FontSizeSelectionRow(
            selectedFontSize = selectedFontSize,
            onFontSizeChange = onFontSizeChange,
        )
        if (INTERFACE_STYLE_SETTING_VISIBLE) {
            GroupDivider()
            InterfaceStyleSelectionRow(
                selectedStyle = selectedStyle,
                onStyleChange = onStyleChange,
            )
            if (selectedStyle == InterfaceStyle.GLASS) {
                GroupDivider()
                SwitchSettingRow(
                    title = "折射效果",
                    description = android13RequirementDescription(Build.VERSION.SDK_INT).orEmpty(),
                    checked = glassRefractionEnabled,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    onCheckedChange = onGlassRefractionChange,
                )
                GroupDivider()
                GlassTransparencyRow(
                    transparency = glassTransparency,
                    onTransparencyPreview = onGlassTransparencyPreview,
                    onTransparencyCommit = onGlassTransparencyCommit,
                )
            }
        }
        if (ACCENT_COLOR_SETTING_VISIBLE) {
            GroupDivider()
            AccentColorSelectionRow(
                selectedColor = selectedColor,
                onAccentColorChange = onAccentColorChange,
            )
        }
    }
}

@Composable
private fun GlassTransparencyRow(
    transparency: Float,
    onTransparencyPreview: (Float) -> Unit,
    onTransparencyCommit: (Float) -> Unit,
) {
    var previewValue by remember { mutableFloatStateOf(transparency.coerceIn(0f, 1f)) }
    var editingValue by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf(TextFieldValue()) }
    var inputHadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun finishEditing() {
        val percent = inputValue.text.toIntOrNull()
        if (percent != null && percent in 0..100) {
            val value = percent / 100f
            previewValue = value
            onTransparencyPreview(value)
            onTransparencyCommit(value)
        }
        editingValue = false
        inputHadFocus = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(transparency) {
        previewValue = transparency.coerceIn(0f, 1f)
    }
    LaunchedEffect(editingValue) {
        if (editingValue) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "透明度",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (editingValue) {
                BasicTextField(
                    value = inputValue,
                    onValueChange = { next ->
                        val text = next.text
                        val parsed = text.toIntOrNull()
                        if (
                            text.length <= 3 &&
                            text.all(Char::isDigit) &&
                            (text.isEmpty() || (parsed != null && parsed in 0..100))
                        ) {
                            inputValue = next
                            parsed?.let { percent ->
                                val value = percent / 100f
                                previewValue = value
                                onTransparencyPreview(value)
                                onTransparencyCommit(value)
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .width(42.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                inputHadFocus = true
                            } else if (editingValue && inputHadFocus) {
                                finishEditing()
                            }
                        },
                )
                Text(
                    text = "%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, end = 7.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            val text = (previewValue * 100).toInt().toString()
                            inputValue = TextFieldValue(
                                text = text,
                                selection = TextRange(0, text.length),
                            )
                            inputHadFocus = false
                            editingValue = true
                        }
                        .padding(start = 6.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    Text(
                        text = "${(previewValue * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "输入透明度",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(17.dp),
                    )
                }
            }
        }
        SimpleLineSlider(
            value = previewValue,
            onValueChange = { value ->
                previewValue = value
                onTransparencyPreview(value)
            },
            onValueChangeFinished = onTransparencyCommit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SimpleLineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedValue = value.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    Canvas(
        modifier = modifier
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, 0f..1f, 0)
                setProgress { target ->
                    val next = target.coerceIn(0f, 1f)
                    currentOnValueChange(next)
                    currentOnValueChangeFinished(next)
                    true
                }
            }
            .pointerInput(Unit) {
                fun valueForPosition(positionX: Float): Float {
                    val horizontalInset = 8.dp.toPx()
                    val usableWidth = (size.width - horizontalInset * 2f).coerceAtLeast(1f)
                    return ((positionX - horizontalInset) / usableWidth).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latestValue = valueForPosition(down.position.x)
                    currentOnValueChange(latestValue)
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        latestValue = valueForPosition(change.position.x)
                        currentOnValueChange(latestValue)
                    }
                    currentOnValueChangeFinished(latestValue)
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
            center = Offset(trackStart + (trackEnd - trackStart) * clampedValue, centerY),
        )
    }
}

@Composable
private fun InterfaceStyleSelectionRow(
    selectedStyle: InterfaceStyle,
    onStyleChange: (InterfaceStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleSelectedStyle = when (selectedStyle) {
        InterfaceStyle.NATIVE -> InterfaceStyle.NATIVE
        InterfaceStyle.GLASS -> InterfaceStyle.GLASS
        else -> InterfaceStyle.ACRYLIC
    }
    data class StyleOption(
        val style: InterfaceStyle,
        val label: String,
        val description: String?,
    )
    val android12Requirement = android12RequirementDescription(Build.VERSION.SDK_INT)
    val options = listOf(
        StyleOption(
            style = InterfaceStyle.ACRYLIC,
            label = "亚克力",
            description = android12Requirement,
        ),
        StyleOption(
            style = InterfaceStyle.NATIVE,
            label = "原生",
            description = null,
        ),
        StyleOption(
            style = InterfaceStyle.GLASS,
            label = "玻璃",
            description = android12Requirement,
        ),
    )
    SelectionSettingRow(
        title = "风格",
        value = options.first { it.style == visibleSelectedStyle }.label,
        expanded = expanded,
        onClick = { expanded = true },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            option.description?.let { description ->
                                Text(
                                    text = description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (option.style == visibleSelectedStyle) {
                            Icon(Icons.Outlined.Check, contentDescription = "当前风格")
                        }
                    },
                    onClick = {
                        expanded = false
                        onStyleChange(option.style)
                    },
                )
            }
        },
    )
}

internal fun android12RequirementDescription(sdkInt: Int): String? =
    if (sdkInt < Build.VERSION_CODES.S) "仅安卓12+生效" else null

internal fun android13RequirementDescription(sdkInt: Int): String? =
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) "仅安卓13+生效" else null

@Composable
private fun FontSizeSelectionRow(
    selectedFontSize: FontSizePreference,
    onFontSizeChange: (FontSizePreference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        FontSizePreference.SMALL to "小",
        FontSizePreference.MEDIUM to "中",
        FontSizePreference.LARGE to "大",
        FontSizePreference.EXTRA_LARGE to "超大",
    )
    SelectionSettingRow(
        title = "字体大小",
        value = options.first { it.first == selectedFontSize }.second,
        expanded = expanded,
        onClick = { expanded = true },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { (fontSize, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (fontSize == selectedFontSize) {
                            Icon(Icons.Outlined.Check, contentDescription = "当前字体大小")
                        }
                    },
                    onClick = {
                        expanded = false
                        onFontSizeChange(fontSize)
                    },
                )
            }
        },
    )
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
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
            modifier = Modifier.align(Alignment.TopEnd),
            content = menuContent,
        )
    }
}

@Composable
private fun SettingsPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsGlassDropdown(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun SettingsGlassDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val interfaceEffects = LocalInterfaceEffects.current
    var popupPosition by remember { mutableStateOf(IntOffset.Zero) }
    val popupBackdrop = rememberWindowBackdropSnapshot(
        enabled = interfaceEffects.backdropBlurEnabled,
        refreshKey = popupPosition,
    )
    val shadowGutter = 24.dp
    val availableMenuHeight = (
        LocalConfiguration.current.screenHeightDp.dp - shadowGutter * 2 - 24.dp
    ).coerceAtLeast(160.dp)
    val maxMenuHeight = minOf(420.dp, availableMenuHeight)
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gutterPx = with(density) { shadowGutter.roundToPx() }
                val gapPx = with(density) { 4.dp.roundToPx() }
                val surfaceWidth = popupContentSize.width - gutterPx * 2
                val surfaceHeight = popupContentSize.height - gutterPx * 2
                val surfaceX = (anchorBounds.right - surfaceWidth).coerceIn(
                    gutterPx,
                    (windowSize.width - surfaceWidth - gutterPx).coerceAtLeast(gutterPx),
                )
                val belowY = anchorBounds.bottom + gapPx
                val aboveY = anchorBounds.top - gapPx - surfaceHeight
                val desiredSurfaceY = if (belowY + surfaceHeight + gutterPx <= windowSize.height) {
                    belowY
                } else {
                    aboveY
                }
                val surfaceY = desiredSurfaceY.coerceIn(
                    gutterPx,
                    (windowSize.height - surfaceHeight - gutterPx).coerceAtLeast(gutterPx),
                )
                val surfacePosition = IntOffset(surfaceX, surfaceY)
                if (popupPosition != surfacePosition) {
                    popupPosition = surfacePosition
                }
                return IntOffset(surfaceX - gutterPx, surfaceY - gutterPx)
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val popupSurfaceAlpha by animateFloatAsState(
        targetValue = if (
            interfaceEffects.backdropBlurEnabled &&
            (popupBackdrop == null || popupPosition == IntOffset.Zero)
        ) {
            1f
        } else {
            interfaceEffects.compactSurfaceAlpha
        },
        animationSpec = tween(durationMillis = 120),
        label = "settings_popup_surface_alpha",
    )
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(modifier = Modifier.padding(shadowGutter)) {
            Box(
                modifier = Modifier
                    .zIndex(2f)
                    .widthIn(min = 180.dp, max = 220.dp)
                    .settingsGlassShadow(cornerRadius = 14.dp)
                    .clip(shape)
                    .border(
                        0.5.dp,
                        settingsGlassOutline.copy(alpha = 0.30f),
                        shape,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .windowBackdrop(
                            snapshot = popupBackdrop,
                            windowPosition = popupPosition,
                            blurRadius = if (interfaceEffects.glassMaterialEnabled) 18.dp else 15.dp,
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = popupSurfaceAlpha),
                            shape,
                        ),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
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
private fun GroupDivider(horizontalPadding: Dp = 14.dp) {
    Spacer(Modifier.height(0.dp))
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
        SettingsGlassDropdown(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.align(Alignment.TopEnd),
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
    enabled: Boolean = true,
    multiline: Boolean = false,
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
        placeholder = {
            Text(
                placeholder,
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = if (multiline) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        },
        colors = themedFieldColors(),
        shape = RoundedCornerShape(8.dp),
        singleLine = !multiline,
        maxLines = if (multiline) Int.MAX_VALUE else 1,
        readOnly = readOnly,
        enabled = enabled,
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
    enabled: Boolean = true,
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
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.48f,
                    ),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            ),
        )
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
                fontSize = 12.sp,
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
    onExportDiagnostic: () -> Unit,
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
            title = "导出诊断记录",
            description = "生成用于排查问题的脱敏文本，并打开系统分享",
            onClick = onExportDiagnostic,
        )
        GroupDivider()
        ActionSettingRow(
            title = "清除缓存",
            description = "查看临时缓存、运行缓存和崩溃记录后再清理",
            onClick = onOpenCacheClean,
        )
    }
}

private fun shareDiagnosticReport(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, "诊断记录不存在", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Mason 诊断记录")
        clipData = ClipData.newRawUri("Mason 诊断记录", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 Mason 诊断记录"))
    }.onFailure { error ->
        Toast.makeText(
            context,
            "打开分享面板失败：${error.message ?: error.javaClass.simpleName}",
            Toast.LENGTH_SHORT,
        ).show()
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
                    state.items.forEach { item ->
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
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
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
