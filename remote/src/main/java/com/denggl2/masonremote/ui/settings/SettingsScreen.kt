package com.denggl2.masonremote.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.denggl2.masonremote.BuildConfig
import com.denggl2.masonremote.diagnostics.DiagnosticLog
import com.denggl2.masonremote.ui.chat.LocalChatBackdropState
import com.denggl2.masonremote.ui.chat.blurLayerOuterEdgeFeather
import com.denggl2.masonremote.ui.chat.captureChatBackdrop
import com.denggl2.masonremote.ui.chat.glassClickable
import com.denggl2.masonremote.ui.chat.rememberChatBackdropState
import com.denggl2.masonremote.ui.theme.LocalInterfaceEffects
import com.denggl2.masonremote.ui.theme.MasonAlertDialog
import com.denggl2.masonremote.ui.theme.ProgressiveBlurEdge
import com.denggl2.masonremote.ui.theme.captureProgressiveEdgeBlur
import com.denggl2.masonremote.ui.theme.floatingSurfaceEdge
import com.denggl2.masonremote.ui.theme.floatingSurfaceShadowColor
import com.denggl2.masonremote.ui.theme.glassRefraction
import com.denggl2.masonremote.ui.theme.masonDialogConfirmButtonColor
import com.denggl2.masonremote.ui.theme.masonDialogDismissButtonColors
import com.denggl2.masonremote.ui.theme.progressiveEdgeBlur
import com.denggl2.masonremote.ui.theme.rememberWindowBackdropSnapshot
import com.denggl2.masonremote.ui.theme.rememberProgressiveEdgeBlurState
import com.denggl2.masonremote.ui.theme.requiresBackdropSample
import com.denggl2.masonremote.ui.theme.resolveBackdropBlurRadius
import com.denggl2.masonremote.ui.theme.resolveBackdropCaptureScale
import com.denggl2.masonremote.ui.theme.windowBackdrop
import com.denggl2.masonremote.ui.chat.masonGlassShadow
import com.denggl2.masonremote.ui.remote.RemoteBackButton
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.localizedLabel
import com.denggl2.masonremote.ui.localizedSummary
import com.denggl2.masonremote.ui.localizedCacheMessage
import dev.chrisbanes.haze.HazeState

enum class RemoteThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class RemoteInterfaceStyle(val label: String) {
    NATIVE("原生"),
    GLASS("玻璃"),
}

enum class RemoteFontSizePreference(val label: String) {
    SMALL("小"),
    MEDIUM("中"),
    LARGE("大"),
    EXTRA_LARGE("超大"),
}

enum class RemoteLanguagePreference(val label: String) {
    SYSTEM("跟随系统"),
    CHINESE("中文"),
    ENGLISH("English"),
}

enum class TaskNotificationMode(val label: String, val summary: String) {
    REGULAR("启用常规通知", "常规通知"),
    ISLAND("启用岛通知", "岛通知"),
    DISABLED("不启用", "不启用"),
}

enum class RemoteMessageSendMode(val label: String) {
    STEER("插队"),
    QUEUE("排队"),
}

private enum class SettingsPage { OVERVIEW, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isPaired: Boolean,
    pairedDeviceName: String,
    themeMode: RemoteThemeMode,
    interfaceStyle: RemoteInterfaceStyle,
    fontSize: RemoteFontSizePreference,
    language: RemoteLanguagePreference,
    glassRefractionEnabled: Boolean,
    glassTransparency: Float,
    glassFrost: Float,
    notificationMode: TaskNotificationMode,
    messageSendMode: RemoteMessageSendMode,
    onBack: () -> Unit,
    onDevicePairingClick: () -> Unit,
    onThemeModeChange: (RemoteThemeMode) -> Unit,
    onInterfaceStyleChange: (RemoteInterfaceStyle) -> Unit,
    onFontSizeChange: (RemoteFontSizePreference) -> Unit,
    onLanguageChange: (RemoteLanguagePreference) -> Unit,
    onGlassRefractionChange: (Boolean) -> Unit,
    onGlassTransparencyChange: (Float) -> Unit,
    onGlassFrostChange: (Float) -> Unit,
    onNotificationModeChange: (TaskNotificationMode) -> Unit,
    onMessageSendModeChange: (RemoteMessageSendMode) -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.OVERVIEW) }
    val strings = LocalRemoteStrings.current
    val scrollState = rememberScrollState()
    val settingsInterfaceEffects = LocalInterfaceEffects.current
    val settingsEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = settingsInterfaceEffects.progressiveEdgeBlurEnabled,
    )
    val settingsBackdropState = rememberChatBackdropState(
        enabled = settingsInterfaceEffects.backdropBlurEnabled,
    )
    val settingsFadeDistancePx = with(LocalDensity.current) { 24.dp.toPx() }
    val topFadeProgress by animateFloatAsState(
        targetValue = (scrollState.value / settingsFadeDistancePx).coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "settings_top_edge_fade",
    )
    val bottomFadeProgress by animateFloatAsState(
        targetValue = ((scrollState.maxValue - scrollState.value) / settingsFadeDistancePx)
            .coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "settings_bottom_edge_fade",
    )

    fun navigateBack() {
        if (page == SettingsPage.OVERVIEW) onBack() else page = SettingsPage.OVERVIEW
    }

    BackHandler(onBack = ::navigateBack)

    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safeDrawingPadding.calculateTopPadding()
    val bottomInset = safeDrawingPadding.calculateBottomPadding()
    val headerTop = topInset + 8.dp
    val headerHeight = 48.dp
    val settingsContentTop = headerTop + headerHeight
    val settingsTopFadeHeight = settingsContentTop + 12.dp
    val settingsBottomFadeHeight = bottomInset + 48.dp
    CompositionLocalProvider(LocalChatBackdropState provides settingsBackdropState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureChatBackdrop(settingsBackdropState),
            ) {
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val openingAbout = initialState == SettingsPage.OVERVIEW && targetState == SettingsPage.ABOUT
                        if (openingAbout) {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                            ) + fadeIn(tween(220)) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(180)))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                            ) + fadeIn(tween(220)) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(180)))
                        }
                    },
                    label = "settings-page",
                ) { targetPage ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .settingsEdgeFadeMask(
                                topProgress = topFadeProgress,
                                bottomProgress = bottomFadeProgress,
                                topFadeHeight = settingsTopFadeHeight,
                                bottomFadeHeight = settingsBottomFadeHeight,
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .captureProgressiveEdgeBlur(settingsEdgeBlurState)
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = settingsContentTop + 2.dp,
                                    bottom = bottomInset + 12.dp,
                                ),
                        ) {
                            if (targetPage == SettingsPage.OVERVIEW) {
                                SectionHeader("外观")
                                AppearanceSettingsContent(
                                    selectedMode = themeMode,
                                    selectedStyle = interfaceStyle,
                                    selectedLanguage = language,
                                    glassRefractionEnabled = glassRefractionEnabled,
                                    glassTransparency = glassTransparency,
                                    glassFrost = glassFrost,
                                    selectedFontSize = fontSize,
                                    onModeChange = onThemeModeChange,
                                    onStyleChange = onInterfaceStyleChange,
                                    onLanguageChange = onLanguageChange,
                                    onGlassRefractionChange = onGlassRefractionChange,
                                    onGlassTransparencyPreview = onGlassTransparencyChange,
                                    onGlassTransparencyCommit = onGlassTransparencyChange,
                                    onGlassFrostPreview = onGlassFrostChange,
                                    onGlassFrostCommit = onGlassFrostChange,
                                    onFontSizeChange = onFontSizeChange,
                                )

                                SectionHeader("通知")
                                TaskNotificationSettingsContent(
                                    selectedMode = notificationMode,
                                    onModeChange = onNotificationModeChange,
                                )
                                SectionHeader("对话")
                                MessageSendSettingsContent(
                                    selectedMode = messageSendMode,
                                    onModeChange = onMessageSendModeChange,
                                )

                                SectionHeader("远程配置")
                                SettingGroup {
                                    DevicePairingSettingRow(
                                        isPaired = isPaired,
                                        pairedDeviceName = pairedDeviceName,
                                        onOpenPairing = onDevicePairingClick,
                                    )
                                }

                                SectionHeader("系统")
                                OtherSettingsContent()
                                SettingGroup {
                                    OverviewSettingRow(
                                        title = "关于",
                                        description = "",
                                        onClick = { page = SettingsPage.ABOUT },
                                    )
                                }
                            } else {
                                AboutSettingsContent()
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    SettingsEdgeFades(
                        state = settingsEdgeBlurState,
                        backgroundColor = MaterialTheme.colorScheme.background,
                        topProgress = topFadeProgress,
                        bottomProgress = bottomFadeProgress,
                        topFadeHeight = settingsTopFadeHeight,
                        bottomFadeHeight = settingsBottomFadeHeight,
                    )
                }
            }
            RemoteBackButton(
                onClick = ::navigateBack,
                modifier = Modifier.padding(start = 8.dp, top = headerTop),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = headerTop)
                    .height(headerHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (page == SettingsPage.OVERVIEW) "设置" else "关于",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsContent(
    selectedMode: RemoteThemeMode,
    selectedStyle: RemoteInterfaceStyle,
    selectedLanguage: RemoteLanguagePreference,
    glassRefractionEnabled: Boolean,
    glassTransparency: Float,
    glassFrost: Float,
    selectedFontSize: RemoteFontSizePreference,
    onModeChange: (RemoteThemeMode) -> Unit,
    onStyleChange: (RemoteInterfaceStyle) -> Unit,
    onLanguageChange: (RemoteLanguagePreference) -> Unit,
    onGlassRefractionChange: (Boolean) -> Unit,
    onGlassTransparencyPreview: (Float) -> Unit,
    onGlassTransparencyCommit: (Float) -> Unit,
    onGlassFrostPreview: (Float) -> Unit,
    onGlassFrostCommit: (Float) -> Unit,
    onFontSizeChange: (RemoteFontSizePreference) -> Unit,
) {
    SettingGroup {
        ThemeModeSelectionRow(selectedMode, onModeChange)
        GroupDivider()
        FontSizeSelectionRow(selectedFontSize, onFontSizeChange)
        GroupDivider()
        LanguageSelectionRow(selectedLanguage, onLanguageChange)
        GroupDivider()
        InterfaceStyleSelectionRow(selectedStyle, onStyleChange)
        if (selectedStyle == RemoteInterfaceStyle.GLASS) {
            GroupDivider()
            SwitchSettingRow(
                title = "折射效果",
                description = android13RequirementDescription(Build.VERSION.SDK_INT).orEmpty(),
                checked = glassRefractionEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                onCheckedChange = onGlassRefractionChange,
            )
            GroupDivider()
            GlassValueRow(
                title = "透明度",
                value = glassTransparency,
                inputContentDescription = "输入透明度",
                onValuePreview = onGlassTransparencyPreview,
                onValueCommit = onGlassTransparencyCommit,
            )
            GroupDivider()
            GlassValueRow(
                title = "霜冻",
                value = glassFrost,
                inputContentDescription = "输入霜冻强度",
                onValuePreview = onGlassFrostPreview,
                onValueCommit = onGlassFrostCommit,
            )
        }
    }
}

@Composable
private fun TaskNotificationSettingsContent(
    selectedMode: TaskNotificationMode,
    onModeChange: (TaskNotificationMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    SettingGroup {
        SelectionSettingRow(
            title = "任务通知",
            value = selectedMode.localizedSummary(strings),
            expanded = expanded,
            onClick = { expanded = !expanded },
            onDismiss = { expanded = false },
            menuContent = {
                TaskNotificationMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.localizedLabel(strings)) },
                        trailingIcon = {
                            if (mode == selectedMode) {
                                Icon(Icons.Outlined.Check, contentDescription = strings.t("当前选项"))
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
}

@Composable
private fun MessageSendSettingsContent(
    selectedMode: RemoteMessageSendMode,
    onModeChange: (RemoteMessageSendMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    SettingGroup {
        SelectionSettingRow(
            title = "进行中发送消息",
            value = selectedMode.localizedLabel(strings),
            expanded = expanded,
            onClick = { expanded = !expanded },
            onDismiss = { expanded = false },
            menuContent = {
                RemoteMessageSendMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.localizedLabel(strings)) },
                        trailingIcon = {
                            if (mode == selectedMode) {
                                Icon(Icons.Outlined.Check, contentDescription = strings.t("当前选项"))
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
}

@Composable
private fun DevicePairingSettingRow(
    isPaired: Boolean,
    pairedDeviceName: String,
    onOpenPairing: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable(onClick = onOpenPairing)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isPaired) "已配对（$pairedDeviceName）" else "远端电脑配置",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (isPaired) {
            TextButton(onClick = onOpenPairing) { Text("管理") }
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AboutSettingsContent() {
    SettingGroup {
        AboutRow("版本号", BuildConfig.VERSION_NAME)
        GroupDivider()
        AboutRow("项目地址", "github.com/DENGGL2/CloudX")
        GroupDivider()
        AboutRow("开源协议", "MIT License")
    }
}

@Composable
private fun OtherSettingsContent() {
    val context = LocalContext.current
    val strings = LocalRemoteStrings.current
    val scope = rememberCoroutineScope()
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DiagnosticLog.exportTo(context, destination)
            }
            Toast.makeText(
                context,
                result.fold(
                    { strings.t("诊断日志已导出") },
                    { "诊断日志导出失败：${it.message ?: strings.t("未知错误")}".let(strings::displayText) },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    SettingGroup {
        ActionSettingRow(
            title = "检查更新",
            onClick = { openUrl(context, "https://github.com/DENGGL2/CloudX") },
        )
        GroupDivider()
        ActionSettingRow(
            title = "导出日志",
            onClick = {
                DiagnosticLog.record("DIAGNOSTIC_EXPORT_OPEN")
                exportLogLauncher.launch(DiagnosticLog.suggestedFileName())
            },
        )
        GroupDivider()
        ActionSettingRow(
            title = "清缓存",
            onClick = {
                val previewCache = java.io.File(context.cacheDir, "remote-share")
                cacheSizeBytes = remotePreviewCacheSize(previewCache)
                showClearCacheDialog = true
            },
        )
    }

    if (showClearCacheDialog) {
        MasonAlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            modifier = Modifier.width(328.dp),
            shape = RoundedCornerShape(30.dp),
            scrimAlpha = 0.32f,
            customContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(198.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(19.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "信息确认",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(46.dp))
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .height(50.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "只清理图片预览缓存\n预计清理 ${formatCacheSize(cacheSizeBytes)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(23.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { showClearCacheDialog = false },
                            modifier = Modifier
                                .masonGlassShadow(cornerRadius = 22.dp)
                                .size(width = 130.dp, height = 44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = masonDialogDismissButtonColors(),
                        ) {
                            Text("取消", fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(20.dp))
                        Button(
                            onClick = {
                                val cache = java.io.File(context.cacheDir, "remote-share")
                                val cleared = clearRemotePreviewCache(cache)
                                showClearCacheDialog = false
                                Toast.makeText(
                                    context,
                                    strings.localizedCacheMessage(cleared),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier
                                .masonGlassShadow(cornerRadius = 22.dp)
                                .size(width = 130.dp, height = 44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = masonDialogConfirmButtonColor(MaterialTheme.colorScheme.primary),
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("清理", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun remotePreviewCacheSize(cache: java.io.File): Long =
    cache.listFiles()
        ?.asSequence()
        ?.filter { it.isFile }
        ?.sumOf { it.length() }
        ?: 0L

private fun clearRemotePreviewCache(cache: java.io.File): Boolean {
    if (!cache.exists()) return true
    val files = cache.listFiles()?.filter { it.isFile }.orEmpty()
    val cleared = files.all { it.delete() }
    cache.mkdirs()
    return cleared
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun AppearanceValueRow(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    SelectionSettingRow(
        title = title,
        value = value,
        expanded = expanded,
        onClick = onClick,
        onDismiss = onDismiss,
        menuContent = menuContent,
    )
}

@Composable
private fun ThemeModeSelectionRow(
    selectedMode: RemoteThemeMode,
    onModeChange: (RemoteThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    val options = RemoteThemeMode.entries.map { it to it.localizedLabel(strings) }
    AppearanceValueRow(
        title = "深色模式",
        value = options.first { it.first == selectedMode }.second,
        expanded = expanded,
        onClick = { expanded = !expanded },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Outlined.Check, contentDescription = strings.t("当前模式"))
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
private fun FontSizeSelectionRow(
    selectedFontSize: RemoteFontSizePreference,
    onFontSizeChange: (RemoteFontSizePreference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    val options = RemoteFontSizePreference.entries.map { it to it.localizedLabel(strings) }
    AppearanceValueRow(
        title = "字体大小",
        value = selectedFontSize.localizedLabel(strings),
        expanded = expanded,
        onClick = { expanded = !expanded },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { (fontSize, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (fontSize == selectedFontSize) {
                            Icon(Icons.Outlined.Check, contentDescription = strings.t("当前字体大小"))
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
private fun LanguageSelectionRow(
    selectedLanguage: RemoteLanguagePreference,
    onLanguageChange: (RemoteLanguagePreference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    AppearanceValueRow(
        title = "语言",
        value = selectedLanguage.localizedLabel(strings),
        expanded = expanded,
        onClick = { expanded = !expanded },
        onDismiss = { expanded = false },
        menuContent = {
            RemoteLanguagePreference.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.localizedLabel(strings)) },
                    trailingIcon = {
                        if (language == selectedLanguage) {
                            Icon(Icons.Outlined.Check, contentDescription = strings.t("当前语言"))
                        }
                    },
                    onClick = {
                        expanded = false
                        onLanguageChange(language)
                    },
                )
            }
        },
    )
}

@Composable
private fun InterfaceStyleSelectionRow(
    selectedStyle: RemoteInterfaceStyle,
    onStyleChange: (RemoteInterfaceStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalRemoteStrings.current
    val android12Requirement = android12RequirementDescription(Build.VERSION.SDK_INT)
    data class StyleOption(val style: RemoteInterfaceStyle, val label: String, val description: String?)
    val options = listOf(
        StyleOption(RemoteInterfaceStyle.NATIVE, RemoteInterfaceStyle.NATIVE.localizedLabel(strings), null),
        StyleOption(RemoteInterfaceStyle.GLASS, RemoteInterfaceStyle.GLASS.localizedLabel(strings), android12Requirement),
    )
    AppearanceValueRow(
        title = "风格",
        value = options.first { it.style == selectedStyle }.label,
        expanded = expanded,
        onClick = { expanded = !expanded },
        onDismiss = { expanded = false },
        menuContent = {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            option.description?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    },
                    trailingIcon = {
                        if (option.style == selectedStyle) {
                            Icon(Icons.Outlined.Check, contentDescription = strings.t("当前风格"))
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
private fun GlassValueRow(
    title: String,
    value: Float,
    inputContentDescription: String,
    onValuePreview: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
) {
    var previewValue by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    var editingValue by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf(TextFieldValue()) }
    var inputHadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val strings = LocalRemoteStrings.current

    fun finishEditing() {
        inputValue.text.toIntOrNull()?.takeIf { it in 0..100 }?.let {
            val next = it / 100f
            previewValue = next
            onValuePreview(next)
            onValueCommit(next)
        }
        editingValue = false
        inputHadFocus = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(value) { previewValue = value.coerceIn(0f, 1f) }
    LaunchedEffect(editingValue) {
        if (editingValue) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (editingValue) {
                BasicTextField(
                    value = inputValue,
                    onValueChange = { next ->
                        val parsed = next.text.toIntOrNull()
                        if (next.text.length <= 3 && next.text.all(Char::isDigit) && (next.text.isEmpty() || parsed in 0..100)) {
                            inputValue = next
                            parsed?.let {
                                previewValue = it / 100f
                                onValuePreview(previewValue)
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.width(42.dp).focusRequester(focusRequester).onFocusChanged {
                        if (it.isFocused) inputHadFocus = true else if (editingValue && inputHadFocus) finishEditing()
                    },
                )
                Text("%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp, end = 7.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.glassClickable {
                        val text = (previewValue * 100).toInt().toString()
                        inputValue = TextFieldValue(text, TextRange(0, text.length))
                        inputHadFocus = false
                        editingValue = true
                    }.padding(start = 6.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    Text("${(previewValue * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = strings.displayText(inputContentDescription),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(17.dp),
                    )
                }
            }
        }
        SimpleLineSlider(
            value = previewValue,
            onValueChange = { previewValue = it; onValuePreview(it) },
            onValueChangeFinished = onValueCommit,
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
        modifier = modifier.height(32.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, 0f..1f, 0)
            setProgress { target ->
                val next = target.coerceIn(0f, 1f)
                currentOnValueChange(next)
                currentOnValueChangeFinished(next)
                true
            }
        }.pointerInput(Unit) {
            fun valueForPosition(positionX: Float): Float {
                val inset = 8.dp.toPx()
                return ((positionX - inset) / (size.width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var latest = valueForPosition(down.position.x)
                currentOnValueChange(latest)
                var change = down
                while (change.pressed) {
                    val event = awaitPointerEvent()
                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    latest = valueForPosition(change.position.x)
                    currentOnValueChange(latest)
                }
                currentOnValueChangeFinished(latest)
            }
        },
    ) {
        val inset = 8.dp.toPx()
        val centerY = size.height / 2f
        drawLine(trackColor, Offset(inset, centerY), Offset(size.width - inset, centerY), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(thumbColor, 7.dp.toPx(), Offset(inset + (size.width - inset * 2f) * clampedValue, centerY))
    }
}

@Composable
private fun SelectionSettingRow(
    title: String,
    value: String,
    description: String = "",
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().glassClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (title.isNotBlank()) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (description.isNotBlank()) {
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        SettingsDropdownArrow(expanded, onDismiss, menuContent)
    }
}

@Composable
private fun SettingsDropdownArrow(
    expanded: Boolean,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (expanded) 180 else 120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "settings_dropdown_arrow",
    )
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = strings.t(if (expanded) "收起选项" else "展开选项"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = arrowRotation },
        )
        SettingsPopupMenu(expanded, onDismiss, Modifier.align(Alignment.TopEnd), menuContent)
    }
}

@Composable
private fun SettingsPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsGlassDropdown(expanded, onDismiss, modifier, content)
}

private val settingsGlassShadowBlur = 20.dp

private fun Modifier.settingsGlassShadow(
    cornerRadius: Dp,
    blurRadius: Dp = settingsGlassShadowBlur,
): Modifier = composed {
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val shadowColor = floatingSurfaceShadowColor()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed drawBehind {
            val blurPx = blurRadius.toPx()
            val cornerPx = cornerRadius.toPx()
            val shadowMask = Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx, cornerPx)))
            }
            clipPath(shadowMask, clipOp = ClipOp.Difference) {
                for (layer in 12 downTo 1) {
                    val spread = blurPx * layer / 12
                    drawRoundRect(
                        color = shadowColor.copy(alpha = shadowColor.alpha * 0.04f),
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
            val sigma = with(density) { blurRadius.toPx() } * 0.5f
            layer.renderEffect = BlurEffect(sigma, sigma, TileMode.Decal)
        }
    }
    androidx.compose.runtime.DisposableEffect(graphicsContext, shadowLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(shadowLayer) }
    }
    drawWithContent {
        val blurPx = blurRadius.toPx()
        val cornerPx = cornerRadius.toPx()
        val contentSize = size
        val paddingPx = blurPx
        val layerSize = IntSize(
            (contentSize.width + paddingPx * 2f).toInt().coerceAtLeast(1),
            (contentSize.height + paddingPx * 2f).toInt().coerceAtLeast(1),
        )
        shadowLayer.record(layerSize) {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(paddingPx, paddingPx),
                size = contentSize,
                cornerRadius = CornerRadius(cornerPx),
            )
        }
        val shadowMask = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, contentSize), CornerRadius(cornerPx, cornerPx)))
        }
        clipPath(shadowMask, clipOp = ClipOp.Difference) {
            translate(left = -paddingPx, top = -paddingPx) { drawLayer(shadowLayer) }
        }
        drawContent()
    }
}

@Composable
private fun SettingsGlassDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var popupMounted by remember { mutableStateOf(expanded) }
    val popupMotion = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
            popupMotion.snapTo(0f)
        } else if (popupMounted) {
            popupMotion.animateTo(0f, tween(120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
            popupMounted = false
        }
    }
    if (!popupMounted) return

    val density = LocalDensity.current
    val interfaceEffects = LocalInterfaceEffects.current
    var popupPosition by remember { mutableStateOf(IntOffset.Zero) }
    var opensAbove by remember { mutableStateOf(false) }
    val backdropBlurRadius = interfaceEffects.resolveBackdropBlurRadius(nonGlassRadius = 15.dp)
    val backdropRequired = interfaceEffects.requiresBackdropSample(
        blurRadius = backdropBlurRadius,
        includeRefraction = true,
    )
    val popupBackdrop = rememberWindowBackdropSnapshot(
        enabled = backdropRequired,
        captureScale = interfaceEffects.resolveBackdropCaptureScale(includeRefraction = true),
    )
    val shadowGutter = 24.dp
    val maxMenuHeight = minOf(420.dp, (LocalConfiguration.current.screenHeightDp.dp - shadowGutter * 2 - 24.dp).coerceAtLeast(160.dp))
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val gutter = with(density) { shadowGutter.roundToPx() }
                val gap = with(density) { 4.dp.roundToPx() }
                val surfaceWidth = popupContentSize.width - gutter * 2
                val surfaceHeight = popupContentSize.height - gutter * 2
                val surfaceX = (anchorBounds.right - surfaceWidth).coerceIn(gutter, (windowSize.width - surfaceWidth - gutter).coerceAtLeast(gutter))
                val belowY = anchorBounds.bottom + gap
                val aboveY = anchorBounds.top - gap - surfaceHeight
                val nextOpensAbove = belowY + surfaceHeight + gutter > windowSize.height
                opensAbove = nextOpensAbove
                val surfaceY = (if (nextOpensAbove) aboveY else belowY).coerceIn(gutter, (windowSize.height - surfaceHeight - gutter).coerceAtLeast(gutter))
                popupPosition = IntOffset(surfaceX, surfaceY)
                return IntOffset(surfaceX - gutter, surfaceY - gutter)
            }
        }
    }
    val shape = RoundedCornerShape(30.dp)
    val positionReady = popupPosition != IntOffset.Zero
    val backdropReady = popupBackdrop != null && positionReady
    var opaqueFallbackLocked by remember { mutableStateOf(false) }
    LaunchedEffect(backdropRequired, backdropReady) {
        if (!backdropRequired) {
            opaqueFallbackLocked = false
        } else if (!backdropReady && !opaqueFallbackLocked) {
            delay(300L)
            opaqueFallbackLocked = true
        }
    }
    val useBackdrop = backdropReady && !opaqueFallbackLocked
    val popupReady = positionReady && (!backdropRequired || useBackdrop || opaqueFallbackLocked)
    val popupSurfaceAlpha = if (backdropRequired && !useBackdrop) 1f else interfaceEffects.compactSurfaceAlpha
    LaunchedEffect(expanded, popupReady) {
        if (!expanded) return@LaunchedEffect
        if (!popupReady) popupMotion.snapTo(0f) else {
            popupMotion.animateTo(1f, tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box {
            Box(
                modifier = Modifier.padding(shadowGutter).graphicsLayer {
                    val progress = popupMotion.value
                    val closing = !expanded
                    val hiddenScale = if (closing) 0.98f else 0.96f
                    val hiddenOffset = if (closing) 4.dp.toPx() else 6.dp.toPx()
                    alpha = if (popupReady) progress else 0f
                    scaleX = hiddenScale + (1f - hiddenScale) * progress
                    scaleY = scaleX
                    translationY = (if (opensAbove) 1f else -1f) * hiddenOffset * (1f - progress)
                    transformOrigin = TransformOrigin(1f, if (opensAbove) 1f else 0f)
                },
            ) {
                Box(
                    modifier = Modifier
                        .zIndex(2f)
                        .widthIn(min = 180.dp, max = 220.dp)
                        .settingsGlassShadow(cornerRadius = 30.dp)
                        .floatingSurfaceEdge(shape)
                        .clip(shape),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .glassRefraction(
                                enabled = useBackdrop && interfaceEffects.glassRefractionEnabled,
                                cornerRadius = 30.dp,
                            )
                            .windowBackdrop(
                                snapshot = if (useBackdrop) popupBackdrop else null,
                                windowPosition = popupPosition,
                                blurRadius = backdropBlurRadius,
                                effectAlpha = interfaceEffects.backdropEffectAlpha,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = popupSurfaceAlpha), shape),
                    )
                    Column(
                        modifier = Modifier.heightIn(max = maxMenuHeight).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                        content = content,
                    )
                }
            }
            PopupDismissGutters(shadowGutter, onDismissRequest)
        }
    }
}

@Composable
private fun BoxScope.PopupDismissGutters(gutter: Dp, onDismissRequest: () -> Unit) {
    Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(gutter).clickable(onClick = onDismissRequest))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(gutter).clickable(onClick = onDismissRequest))
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(gutter).clickable(onClick = onDismissRequest))
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(gutter).clickable(onClick = onDismissRequest))
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.48f), fontSize = 12.sp)
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
private fun SectionHeader(title: String) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp, start = 3.dp))
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun GroupDivider(@Suppress("UNUSED_PARAMETER") horizontalPadding: Dp = 14.dp) {
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun OverviewSettingRow(title: String, description: String, onClick: () -> Unit) {
    val strings = LocalRemoteStrings.current
    Row(Modifier.fillMaxWidth().glassClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description.isNotBlank()) Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = strings.t("打开设置项"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ActionSettingRow(title: String, description: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable(onClick = onClick)
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
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

private fun Modifier.settingsEdgeFadeMask(
    topProgress: Float,
    bottomProgress: Float,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    if (size.height <= 0f) return@drawWithContent
    val topStop = (topFadeHeight.toPx() / size.height).coerceIn(0f, 0.45f)
    val bottomStop = (1f - bottomFadeHeight.toPx() / size.height).coerceIn(0.55f, 1f)
    val bottomRange = 1f - bottomStop
    fun topAlpha(step: Float) = 1f - topProgress * (1f - step)
    fun bottomAlpha(step: Float) = 1f - bottomProgress * step
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = topAlpha(0f)),
            topStop * 0.25f to Color.White.copy(alpha = topAlpha(0.15625f)),
            topStop * 0.50f to Color.White.copy(alpha = topAlpha(0.50f)),
            topStop * 0.75f to Color.White.copy(alpha = topAlpha(0.84375f)),
            topStop to Color.White,
            bottomStop to Color.White,
            bottomStop + bottomRange * 0.25f to Color.White.copy(alpha = bottomAlpha(0.15625f)),
            bottomStop + bottomRange * 0.50f to Color.White.copy(alpha = bottomAlpha(0.50f)),
            bottomStop + bottomRange * 0.75f to Color.White.copy(alpha = bottomAlpha(0.84375f)),
            1f to Color.White.copy(alpha = bottomAlpha(1f)),
        ),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun BoxScope.SettingsEdgeFades(
    state: HazeState?,
    backgroundColor: Color,
    topProgress: Float,
    bottomProgress: Float,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
) {
    val glassMaterialEnabled = LocalInterfaceEffects.current.glassMaterialEnabled
    val fadeSurface = backgroundColor.copy(alpha = if (glassMaterialEnabled) 0.10f else 0.995f)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(topFadeHeight)
            .graphicsLayer { alpha = topProgress }
            .blurLayerOuterEdgeFeather(ProgressiveBlurEdge.Bottom, 10.dp)
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Top,
                backgroundColor = backgroundColor,
                smoothBoundary = true,
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
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bottomFadeHeight)
            .graphicsLayer { alpha = bottomProgress }
            .blurLayerOuterEdgeFeather(ProgressiveBlurEdge.Top, 10.dp)
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Bottom,
                backgroundColor = backgroundColor,
                smoothBoundary = true,
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
