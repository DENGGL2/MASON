package com.denggl2.mason.ui.chat

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.rememberDrawerState
import com.denggl2.mason.ui.theme.MasonAlertDialog as AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.denggl2.mason.R
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.extractArtifactMetadataMarkers
import com.denggl2.mason.data.extractModelParticipation
import com.denggl2.mason.data.ModelContribution
import com.denggl2.mason.data.ModelParticipation
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.stripModelParticipationMarkers
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.LocalModelCatalog
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.configuredImageModelRef
import com.denggl2.mason.data.configuredVisionModelRef
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.MasonSkillParameter
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepKind
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.TaskRunStatus
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.extractTaskRunMarker
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.automation.AutomationApplyResult
import com.denggl2.mason.automation.AutomationDraft
import com.denggl2.mason.automation.AutomationDraftService
import com.denggl2.mason.agent.ToolRiskLevel
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.integration.CapabilityRequirement
import com.denggl2.mason.integration.CapabilityRequirementStatus
import com.denggl2.mason.integration.extractCapabilityRequirementMarker
import com.denggl2.mason.integration.stripCapabilityRequirementMarkers
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.ui.conversation.ConversationListItem
import com.denggl2.mason.ui.conversation.ConversationListViewModel
import com.denggl2.mason.ui.conversation.RemoteConversationListUiState
import com.denggl2.mason.ui.theme.LocalInterfaceEffects
import com.denggl2.mason.ui.theme.MASON_OVERLAY_SCRIM_ALPHA
import com.denggl2.mason.ui.theme.MasonSheetShape
import com.denggl2.mason.ui.theme.ProgressiveBlurEdge
import com.denggl2.mason.ui.theme.WindowBackdropSnapshot
import com.denggl2.mason.ui.theme.captureProgressiveEdgeBlur
import com.denggl2.mason.ui.theme.captureWindowBackdropSnapshot
import com.denggl2.mason.ui.theme.floatingSurfaceEdge
import com.denggl2.mason.ui.theme.floatingSurfaceShadowColor
import com.denggl2.mason.ui.theme.glassRefraction
import com.denggl2.mason.ui.theme.progressiveEdgeBlur
import com.denggl2.mason.ui.theme.rememberProgressiveEdgeBlurState
import com.denggl2.mason.ui.theme.rememberWindowBackdropSnapshot
import com.denggl2.mason.ui.theme.requiresBackdropSample
import com.denggl2.mason.ui.theme.resolveBackdropCaptureScale
import com.denggl2.mason.ui.theme.resolveBackdropBlurRadius
import com.denggl2.mason.ui.theme.windowBackdrop
import com.denggl2.mason.ui.theme.windowBackdropMaterial
import com.denggl2.mason.ui.theme.masonOverlayWindowInsets
import com.denggl2.mason.ui.theme.masonSheetContainerColor
import com.denggl2.mason.ui.theme.masonSheetSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

private const val USER_CONTEXT_HEADER = "Mason 附加上下文"
private val TOOL_DETAIL_JSON = Json { prettyPrint = true }
private val masonGlassShadowBlur = 20.dp
private const val LIVE_BACKDROP_BLUR_ENABLED = true
private const val CHAT_POPUP_BACKDROP_WAIT_MILLIS = 300L
private const val DROPDOWN_ENTER_DURATION_MILLIS = 180
private const val DROPDOWN_EXIT_DURATION_MILLIS = 120
private val DropdownEnterEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val DropdownExitEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal fun Modifier.masonGlassShadow(
    cornerRadius: Dp,
    blurRadius: Dp = masonGlassShadowBlur,
): Modifier = composed {
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val shadowColor = floatingSurfaceShadowColor()
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
                color = shadowColor,
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

@Composable
internal fun ChatGlassDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp,
    cornerRadius: Dp,
    alignEnd: Boolean,
    subduedMaterial: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var popupMounted by remember { mutableStateOf(expanded) }
    val popupMotion = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
        } else if (popupMounted) {
            popupMotion.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = DROPDOWN_EXIT_DURATION_MILLIS,
                    easing = DropdownExitEasing,
                ),
            )
            popupMounted = false
        }
    }
    if (!popupMounted) return

    val density = LocalDensity.current
    val interfaceEffects = LocalInterfaceEffects.current
    var surfacePosition by remember { mutableStateOf(IntOffset.Zero) }
    var opensAbove by remember { mutableStateOf(false) }
    val backdropBlurRadius = if (subduedMaterial) {
        12.dp
    } else {
        interfaceEffects.resolveBackdropBlurRadius(nonGlassRadius = 15.dp)
    }
    val backdropRequired = interfaceEffects.requiresBackdropSample(
        blurRadius = backdropBlurRadius,
        includeRefraction = !subduedMaterial,
    )
    val popupBackdrop = rememberWindowBackdropSnapshot(
        enabled = backdropRequired,
        captureScale = interfaceEffects.resolveBackdropCaptureScale(
            includeRefraction = !subduedMaterial,
        ),
    )
    val shadowGutter = if (subduedMaterial) 12.dp else 24.dp
    val positionProvider = remember(density, alignEnd) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = with(density) {
                val gutterPx = shadowGutter.roundToPx()
                val gapPx = 4.dp.roundToPx()
                val surfaceWidth = popupContentSize.width - gutterPx * 2
                val surfaceHeight = popupContentSize.height - gutterPx * 2
                val desiredSurfaceX = if (alignEnd) {
                    anchorBounds.right - surfaceWidth
                } else {
                    anchorBounds.left
                }
                val surfaceX = desiredSurfaceX.coerceIn(
                    gutterPx,
                    (windowSize.width - surfaceWidth - gutterPx).coerceAtLeast(gutterPx),
                )
                val belowY = anchorBounds.bottom + gapPx
                val aboveY = anchorBounds.top - gapPx - surfaceHeight
                val nextOpensAbove = belowY + surfaceHeight + gutterPx > windowSize.height
                val desiredSurfaceY = if (!nextOpensAbove) {
                    belowY
                } else {
                    aboveY
                }
                if (opensAbove != nextOpensAbove) {
                    opensAbove = nextOpensAbove
                }
                val surfaceY = desiredSurfaceY.coerceIn(
                    gutterPx,
                    (windowSize.height - surfaceHeight - gutterPx).coerceAtLeast(gutterPx),
                )
                val nextSurfacePosition = IntOffset(surfaceX, surfaceY)
                if (surfacePosition != nextSurfacePosition) {
                    surfacePosition = nextSurfacePosition
                }
                IntOffset(surfaceX - gutterPx, surfaceY - gutterPx)
            }
        }
    }
    val shape = RoundedCornerShape(cornerRadius)
    val positionReady = surfacePosition != IntOffset.Zero
    val backdropReady = popupBackdrop != null && positionReady
    var opaqueFallbackLocked by remember { mutableStateOf(false) }
    LaunchedEffect(backdropRequired, backdropReady) {
        if (!backdropRequired) {
            opaqueFallbackLocked = false
        } else if (!backdropReady && !opaqueFallbackLocked) {
            delay(CHAT_POPUP_BACKDROP_WAIT_MILLIS)
            opaqueFallbackLocked = true
        }
    }
    val useBackdrop = backdropReady && !opaqueFallbackLocked
    val popupReady = positionReady && (
        !backdropRequired ||
            useBackdrop ||
            opaqueFallbackLocked
        )
    val popupSurfaceAlpha = when {
        backdropRequired && !useBackdrop -> 1f
        subduedMaterial -> interfaceEffects.compactSurfaceAlpha.coerceAtLeast(0.82f)
        else -> interfaceEffects.compactSurfaceAlpha
    }
    LaunchedEffect(expanded, popupReady) {
        when {
            !expanded -> Unit
            !popupReady -> popupMotion.snapTo(0f)
            else -> popupMotion.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DROPDOWN_ENTER_DURATION_MILLIS,
                    easing = DropdownEnterEasing,
                ),
            )
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box {
            Box(
                modifier = Modifier
                .padding(shadowGutter)
                .graphicsLayer {
                    val progress = popupMotion.value
                    val closing = !expanded
                    val hiddenScale = if (closing) 0.98f else 0.96f
                    val hiddenOffset = if (closing) 4.dp.toPx() else 6.dp.toPx()
                    val scale = hiddenScale + (1f - hiddenScale) * progress
                    alpha = if (popupReady) progress else 0f
                    scaleX = scale
                    scaleY = scale
                    translationY = (if (opensAbove) 1f else -1f) *
                        hiddenOffset * (1f - progress)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (alignEnd) 1f else 0f,
                        pivotFractionY = if (opensAbove) 1f else 0f,
                    )
                },
            ) {
            Box(
                modifier = Modifier
                    .width(width)
                    .then(
                        if (subduedMaterial) {
                            Modifier
                                .masonGlassShadow(cornerRadius, blurRadius = 8.dp)
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                    shape = shape,
                                )
                        } else {
                            Modifier
                                .masonGlassShadow(cornerRadius)
                                .floatingSurfaceEdge(shape)
                        },
                    )
                    .clip(shape),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .glassRefraction(
                            enabled = useBackdrop &&
                                !subduedMaterial &&
                                interfaceEffects.glassRefractionEnabled,
                            cornerRadius = cornerRadius,
                        )
                        .windowBackdrop(
                            snapshot = if (useBackdrop) popupBackdrop else null,
                            windowPosition = surfacePosition,
                            blurRadius = backdropBlurRadius,
                            effectAlpha = interfaceEffects.backdropEffectAlpha,
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = popupSurfaceAlpha,
                            ),
                            shape,
                        ),
                )
                Column(content = content)
            }
            }
            PopupDismissGutters(
                gutter = shadowGutter,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
internal fun BoxScope.PopupDismissGutters(
    gutter: Dp,
    onDismissRequest: () -> Unit,
) {
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val dismissModifier = Modifier.clickable(
        interactionSource = dismissInteractionSource,
        indication = null,
        onClick = onDismissRequest,
    )
    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(gutter)
                .then(dismissModifier),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(gutter)
                .then(dismissModifier),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(gutter)
                .then(dismissModifier),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(gutter)
                .then(dismissModifier),
        )
    }
}

internal enum class ChatBackdropBlur {
    Strong,
    Soft,
    Drawer,
}

internal val LocalChatBackdropState = staticCompositionLocalOf<HazeState?> { null }

@Composable
private fun ChatSheetDragHandle(
    modifier: Modifier = Modifier,
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .padding(top = topPadding, bottom = bottomPadding)
            .size(width = 38.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)),
    )
}

@Composable
private fun BoxScope.ChatSheetEdgeFades(
    scrollState: ScrollState,
    blurState: HazeState?,
    surfaceColor: Color,
) {
    val topAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(180),
        label = "chat_sheet_top_fade",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(180),
        label = "chat_sheet_bottom_fade",
    )
    ChatSheetEdgeFades(blurState, surfaceColor, topAlpha, bottomAlpha)
}

@Composable
private fun BoxScope.ChatSheetEdgeFades(
    listState: LazyListState,
    blurState: HazeState?,
    surfaceColor: Color,
) {
    val topAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(180),
        label = "chat_sheet_list_top_fade",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollForward) 1f else 0f,
        animationSpec = tween(180),
        label = "chat_sheet_list_bottom_fade",
    )
    ChatSheetEdgeFades(blurState, surfaceColor, topAlpha, bottomAlpha)
}

@Composable
private fun BoxScope.ChatSheetEdgeFades(
    blurState: HazeState?,
    surfaceColor: Color,
    topAlpha: Float,
    bottomAlpha: Float,
) {
    val glassMaterialEnabled = LocalInterfaceEffects.current.glassMaterialEnabled
    val fadeSurface = surfaceColor.copy(alpha = if (glassMaterialEnabled) 0.10f else 0.995f)
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
internal fun rememberChatBackdropState(enabled: Boolean): HazeState? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return remember { HazeState() }
}

internal fun Modifier.captureChatBackdrop(state: HazeState?): Modifier {
    if (state == null) return this
    return this.haze(state)
}

private fun Modifier.chatBackdrop(
    blur: ChatBackdropBlur = ChatBackdropBlur.Strong,
): Modifier = composed {
    val state = LocalChatBackdropState.current ?: return@composed this
    val interfaceEffects = LocalInterfaceEffects.current
    val backdropBaseColor = MaterialTheme.colorScheme.surface
    val blurRadius = interfaceEffects.resolveBackdropBlurRadius(
        nonGlassRadius = when (blur) {
            ChatBackdropBlur.Strong -> 32.dp
            ChatBackdropBlur.Soft -> 20.dp
            ChatBackdropBlur.Drawer -> 32.dp
        },
    )
    this.hazeChild(
        state = state,
        style = HazeStyle(
            backgroundColor = backdropBaseColor,
            tint = HazeTint(Color.Transparent),
            blurRadius = blurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(Color.Transparent),
        ),
    ) {
        blurEnabled = interfaceEffects.backdropEffectAlpha > 0f
        alpha = interfaceEffects.backdropEffectAlpha
    }
}

private fun Modifier.drawerListEdgeFadeMask(
    topProgress: Float,
    bottomProgress: Float,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val topStop = (topFadeHeight.toPx() / size.height).coerceIn(0f, 0.45f)
    val bottomStop = (1f - (bottomFadeHeight.toPx() / size.height)).coerceIn(0.55f, 1f)
    val bottomRange = 1f - bottomStop
    fun topMaskAlpha(smoothStep: Float) = 1f - topProgress * (1f - smoothStep)
    fun bottomMaskAlpha(smoothStep: Float) = 1f - bottomProgress * smoothStep
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = topMaskAlpha(0f)),
            topStop * 0.25f to Color.White.copy(alpha = topMaskAlpha(0.15625f)),
            topStop * 0.50f to Color.White.copy(alpha = topMaskAlpha(0.50f)),
            topStop * 0.75f to Color.White.copy(alpha = topMaskAlpha(0.84375f)),
            topStop to Color.White,
            bottomStop to Color.White,
            bottomStop + bottomRange * 0.25f to Color.White.copy(alpha = bottomMaskAlpha(0.15625f)),
            bottomStop + bottomRange * 0.50f to Color.White.copy(alpha = bottomMaskAlpha(0.50f)),
            bottomStop + bottomRange * 0.75f to Color.White.copy(alpha = bottomMaskAlpha(0.84375f)),
            1f to Color.White.copy(alpha = bottomMaskAlpha(1f)),
        ),
        blendMode = BlendMode.DstIn,
    )
}

internal fun Modifier.blurLayerOuterEdgeFeather(
    edge: ProgressiveBlurEdge,
    featherHeight: Dp,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val featherFraction = (featherHeight.toPx() / size.height).coerceIn(0f, 1f)
    val mask = when (edge) {
        ProgressiveBlurEdge.Top -> Brush.verticalGradient(
            0f to Color.Transparent,
            featherFraction to Color.White,
            1f to Color.White,
        )
        ProgressiveBlurEdge.Bottom -> Brush.verticalGradient(
            0f to Color.White,
            (1f - featherFraction) to Color.White,
            1f to Color.Transparent,
        )
    }
    drawRect(brush = mask, blendMode = BlendMode.DstIn)
}

internal enum class ChatSurfaceRole {
    Compact,
    Large,
}

@Composable
private fun chatFloatingSurfaceAlpha(
    role: ChatSurfaceRole,
    blurred: Float,
    fallback: Float,
): Float {
    val interfaceEffects = LocalInterfaceEffects.current
    return when {
        !interfaceEffects.backdropBlurEnabled -> 1f
        interfaceEffects.glassMaterialEnabled -> when (role) {
            ChatSurfaceRole.Compact -> interfaceEffects.compactSurfaceAlpha
            ChatSurfaceRole.Large -> interfaceEffects.largeSurfaceAlpha
        }
        LocalChatBackdropState.current != null -> blurred
        else -> fallback
    }
}

@Composable
internal fun BoxScope.ChatGlassMaterial(
    shape: Shape,
    cornerRadius: Dp,
    role: ChatSurfaceRole,
    blur: ChatBackdropBlur = ChatBackdropBlur.Strong,
    refraction: Boolean = false,
    blurredAlpha: Float,
    fallbackAlpha: Float,
    borderWidth: Dp = 0.5.dp,
    borderColor: Color? = null,
) {
    val interfaceEffects = LocalInterfaceEffects.current
    Box(modifier = Modifier.matchParentSize().clip(shape)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassRefraction(
                    enabled = refraction && interfaceEffects.glassRefractionEnabled,
                    cornerRadius = cornerRadius,
                )
                .chatBackdrop(blur),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = chatFloatingSurfaceAlpha(
                            role = role,
                            blurred = blurredAlpha,
                            fallback = fallbackAlpha,
                        ),
                    ),
                    shape,
                ),
        )
    }
    if (borderWidth > 0.dp) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .floatingSurfaceEdge(
                    shape = shape,
                    nonGlassWidth = borderWidth,
                    nonGlassColor = borderColor,
                ),
        )
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .masonGlassShadow(cornerRadius = 24.dp)
            .clip(CircleShape),
    ) {
        ChatGlassMaterial(
            shape = CircleShape,
            cornerRadius = 24.dp,
            role = ChatSurfaceRole.Compact,
            refraction = true,
            blurredAlpha = 0.80f,
            fallbackAlpha = 1f,
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

private data class AnswerSection(val label: String, val text: String)
private data class ToolExecutionDetail(
    val step: TaskStep,
    val result: ChatMessage?,
)
private enum class EmptyChatExpression {
    Tilted,
    Surprised,
    Smile,
    Frown,
    Question,
}
private data class EmptyChatVariant(
    val expression: EmptyChatExpression,
    val layers: List<Int>,
    val prompt: String,
    val idleDelayMs: Long,
    val faceAspectRatio: Float,
)
private val emptyChatVariants = listOf(
    EmptyChatVariant(
        expression = EmptyChatExpression.Tilted,
        layers = listOf(
            R.drawable.mason_empty_face_tilted_0,
            R.drawable.mason_empty_face_tilted_1,
            R.drawable.mason_empty_face_tilted_2,
        ),
        prompt = "我们应该先做什么？",
        idleDelayMs = 3_800L,
        faceAspectRatio = 74f / 68f,
    ),
    EmptyChatVariant(
        expression = EmptyChatExpression.Surprised,
        layers = listOf(
            R.drawable.mason_empty_face_surprised_0,
            R.drawable.mason_empty_face_surprised_1,
            R.drawable.mason_empty_face_surprised_2,
        ),
        prompt = "今天想试点不一样的吗？",
        idleDelayMs = 4_600L,
        faceAspectRatio = 59f / 67f,
    ),
    EmptyChatVariant(
        expression = EmptyChatExpression.Smile,
        layers = listOf(
            R.drawable.mason_empty_face_smile_0,
            R.drawable.mason_empty_face_smile_1,
            R.drawable.mason_empty_face_smile_2,
        ),
        prompt = "一起把想法做出来。",
        idleDelayMs = 3_400L,
        faceAspectRatio = 80f / 69f,
    ),
    EmptyChatVariant(
        expression = EmptyChatExpression.Frown,
        layers = listOf(
            R.drawable.mason_empty_face_frown_0,
            R.drawable.mason_empty_face_frown_1,
            R.drawable.mason_empty_face_frown_2,
        ),
        prompt = "卡住的事，也可以从这里开始。",
        idleDelayMs = 4_200L,
        faceAspectRatio = 74f / 61f,
    ),
    EmptyChatVariant(
        expression = EmptyChatExpression.Question,
        layers = listOf(
            R.drawable.mason_empty_face_question_0,
            R.drawable.mason_empty_face_question_1,
            R.drawable.mason_empty_face_question_2,
        ),
        prompt = "现在最值得解决的问题是什么？",
        idleDelayMs = 4_900L,
        faceAspectRatio = 66f / 74f,
    ),
)
private val emptyChatVariantCursor = AtomicInteger(0)

private fun nextEmptyChatVariantIndex(): Int =
    emptyChatVariantCursor.getAndUpdate { current -> (current + 1) % emptyChatVariants.size }

private fun emptyChatRubberBand(value: Float, limit: Float): Float {
    val magnitude = abs(value)
    if (magnitude <= limit) return value
    return kotlin.math.sign(value) * (limit + (magnitude - limit) * 0.18f)
}

internal enum class AttachmentKind { Image, File }
internal data class PendingAttachment(
    val kind: AttachmentKind,
    val name: String,
    val uri: String,
)
internal data class SkillOption(
    val name: String,
    val description: String,
    val path: String,
    val invocationName: String = name,
    val instructions: String = "",
    val parameters: List<MasonSkillParameter> = emptyList(),
    val parameterValues: Map<String, String> = emptyMap(),
)
private data class UserMessagePresentation(
    val body: String,
    val attachments: List<PendingAttachment>,
    val skill: SkillOption?,
)

internal data class ChatModelMenuSummary(
    val chatModelName: String,
    val visionModelName: String,
    val imageModelName: String,
    val hasConfiguredModel: Boolean,
)

internal enum class ComposerPrimaryAction {
    Send,
    Stop,
    Disabled,
}

internal fun composerPrimaryAction(
    isGenerating: Boolean,
    hasSendableInput: Boolean,
): ComposerPrimaryAction = when {
    hasSendableInput -> ComposerPrimaryAction.Send
    isGenerating -> ComposerPrimaryAction.Stop
    else -> ComposerPrimaryAction.Disabled
}

internal fun shouldShowScrollToBottom(
    hasMessages: Boolean,
    canScrollForward: Boolean,
    scrollInProgress: Boolean,
): Boolean = hasMessages && canScrollForward && !scrollInProgress

internal fun chatModelMenuSummary(config: ApiConfig): ChatModelMenuSummary {
    val remoteReference = config.configuredChatModelRef()
    val remoteName = remoteReference?.modelId
    val localName = config.localModel
        .takeIf(String::isNotBlank)
        ?.let { LocalModelCatalog.get(it)?.name ?: it }
    val chatModelName = when {
        config.localModelDirectEnabled -> localName ?: "未配置"
        config.dynamicLocalRoutingEnabled -> listOfNotNull(remoteName, localName)
            .distinct()
            .joinToString(" / ")
            .ifBlank { "未配置" }
        remoteReference != null -> remoteReference.modelId
        else -> "未配置"
    }
    val visionModelName = config.configuredVisionModelRef()?.modelId ?: "未配置"
    val imageModelName = config.configuredImageModelRef()?.modelId ?: "未配置"

    return ChatModelMenuSummary(
        chatModelName = chatModelName,
        visionModelName = visionModelName,
        imageModelName = imageModelName,
        hasConfiguredModel = listOf(chatModelName, visionModelName, imageModelName)
            .any { it != "未配置" },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToModelSettings: () -> Unit,
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToPermission: () -> Unit = {},
    onConversationSelected: (Long, Boolean) -> Unit,
    onNewChat: (() -> Unit)? = null,
    onDevicePairing: () -> Unit = {},
    onOpenRemoteConversations: () -> Unit = {},
    onOpenWorkbench: () -> Unit,
    notificationTaskCommand: String? = null,
    startFresh: Boolean = false,
    onStartFreshConsumed: () -> Unit = {},
    drawerResetGeneration: Int = 0,
    drawerBackdropSnapshot: WindowBackdropSnapshot?,
    onDrawerBackdropSnapshotChange: (WindowBackdropSnapshot?) -> Unit,
    onConversationBound: (Long?) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
    historyViewModel: ConversationListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val installedSkills by viewModel.installedSkills.collectAsState()
    val conversations by historyViewModel.conversations.collectAsState()
    val remoteConversations by historyViewModel.remoteConversations.collectAsState()
    val drawerConversations = remember(conversations) {
        conversations.filterNot { item ->
            item.lastMessage == null && item.conversation.title == "新对话"
        }
    }
    LaunchedEffect(startFresh) {
        if (startFresh) {
            viewModel.startFreshConversation()
            onStartFreshConsumed()
        }
    }
    LaunchedEffect(startFresh, uiState.conversationId, uiState.messages.size) {
        onConversationBound(
            uiState.conversationId
                ?.takeIf { !startFresh || uiState.messages.any { message -> message.role == "user" } },
        )
    }
    DisposableEffect(uiState.conversationId) {
        val conversationId = uiState.conversationId
        conversationId?.let(historyViewModel::markConversationForegrounded)
        onDispose {
            conversationId?.let(historyViewModel::markConversationBackgrounded)
        }
    }
    var inputText by rememberSaveable { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var selectedSkill by remember { mutableStateOf<SkillOption?>(null) }
    var parameterizingSkill by remember { mutableStateOf<SkillOption?>(null) }
    var showSkillPicker by remember { mutableStateOf(false) }
    var topModelMenuExpanded by remember { mutableStateOf(false) }
    var selectedToolExecution by remember { mutableStateOf<ToolExecutionDetail?>(null) }
    var showApprovalDetails by remember { mutableStateOf(false) }
    var pendingDrawerDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    var followLatestMessages by remember { mutableStateOf(true) }
    var scrollToBottomInProgress by remember { mutableStateOf(false) }
    var drawerSessionActive by remember { mutableStateOf(false) }
    var drawerContentResetGeneration by remember { mutableIntStateOf(0) }
    var processedNotificationCommand by remember(notificationTaskCommand) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var initiallyPositionedConversationId by remember { mutableStateOf<Long?>(null) }
    var initiallyPositionedFreshConversation by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var inputBarHeightPx by remember(density) {
        mutableIntStateOf(with(density) { 96.dp.roundToPx() })
    }
    val inputBarHeight = with(density) { inputBarHeightPx.toDp() }
    val topFadeRevealDistancePx = with(density) { 24.dp.toPx() }
    val topFadeProgress = remember(listState, topFadeRevealDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / topFadeRevealDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listIsDragged by listState.interactionSource.collectIsDraggedAsState()
    val activity = remember(context) { context.findActivity() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var handledDrawerResetGeneration by rememberSaveable {
        mutableIntStateOf(drawerResetGeneration)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed) {
            drawerSessionActive = true
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else {
            // Keep the root exit handler disabled until the drawer's own back event is fully settled.
            delay(120)
            if (drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed) {
                val shouldResetDrawerContent = drawerSessionActive
                drawerSessionActive = false
                if (shouldResetDrawerContent) drawerContentResetGeneration += 1
            }
        }
    }

    LaunchedEffect(drawerResetGeneration) {
        if (handledDrawerResetGeneration != drawerResetGeneration) {
            drawerState.snapTo(DrawerValue.Closed)
            drawerSessionActive = false
            drawerContentResetGeneration += 1
            handledDrawerResetGeneration = drawerResetGeneration
        }
    }

    BackHandler(enabled = !showExitConfirmation) {
        if (drawerSessionActive) {
            scope.launch {
                drawerState.close()
            }
        } else {
            showExitConfirmation = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistAttachmentReadPermission(context, it)
            pendingAttachments = pendingAttachments + PendingAttachment(
                kind = AttachmentKind.Image,
                name = resolveDisplayName(context, it) ?: "图片",
                uri = it.toString(),
            )
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistAttachmentReadPermission(context, it)
            pendingAttachments = pendingAttachments + PendingAttachment(
                kind = AttachmentKind.File,
                name = resolveDisplayName(context, it) ?: "文件",
                uri = it.toString(),
            )
        }
    }

    val toolResultsByCallId = remember(uiState.messages) {
        uiState.messages
            .filter { it.role == "tool" && !it.tool_call_id.isNullOrBlank() }
            .associateBy { it.tool_call_id.orEmpty() }
    }
    val visibleMessages = uiState.messages.filterNot { message ->
        (message.role == "assistant" &&
            message.content.isNullOrBlank() &&
            !message.tool_calls.isNullOrEmpty()) ||
            (message.role == "tool" && !isVisibleToolPresentation(message))
    }
    val processInsertIndex = remember(visibleMessages, uiState.taskSteps) {
        if (uiState.taskSteps.isEmpty()) {
            -1
        } else {
            (visibleMessages.indexOfLast { it.role == "user" } + 1).coerceIn(0, visibleMessages.size)
        }
    }
    val appliedAutomationDraftIds = remember(visibleMessages) {
        visibleMessages.mapNotNull { message ->
            AutomationDraftService.extractAutomationApplyMarker(message.content.orEmpty())?.draftId
        }.toSet()
    }
    val pendingCapabilityRequirement = remember(visibleMessages, uiState.taskRun?.id) {
        visibleMessages.asReversed()
            .firstNotNullOfOrNull { message -> extractCapabilityRequirementMarker(message.content.orEmpty()) }
            ?.takeIf { requirement -> requirement.taskRunId == uiState.taskRun?.id }
    }
    val hasMessages = visibleMessages.isNotEmpty() ||
        uiState.streamingContent.isNotEmpty() ||
        uiState.toolCallStatus != null ||
        uiState.pendingToolApproval != null
    val isFreshEmptyChat = uiState.conversationId == null && !hasMessages
    val showScrollToBottom by remember(hasMessages, listState) {
        derivedStateOf {
            shouldShowScrollToBottom(
                hasMessages = hasMessages,
                canScrollForward = listState.canScrollForward,
                scrollInProgress = scrollToBottomInProgress,
            )
        }
    }
    val pageBackgroundColor = MaterialTheme.colorScheme.background
    val lastAssistantTimestamp = visibleMessages.lastOrNull { it.role == "assistant" }?.timestamp

    LaunchedEffect(notificationTaskCommand, uiState.taskRun?.id, uiState.isStreaming) {
        val run = uiState.taskRun
        if (
            processedNotificationCommand ||
            notificationTaskCommand == null ||
            run == null
        ) return@LaunchedEffect
        val canRunCommand = when (notificationTaskCommand) {
            NotificationTool.TASK_COMMAND_RESUME ->
                run.status == TaskRunStatus.WaitingForUser && !uiState.isStreaming
            NotificationTool.TASK_COMMAND_CANCEL ->
                run.status !in setOf(TaskRunStatus.Completed, TaskRunStatus.Failed, TaskRunStatus.Cancelled)
            else -> false
        }
        if (!canRunCommand) return@LaunchedEffect
        processedNotificationCommand = true
        when (notificationTaskCommand) {
            NotificationTool.TASK_COMMAND_RESUME -> viewModel.resumeCurrentTask()
            NotificationTool.TASK_COMMAND_CANCEL -> viewModel.cancelCurrentTask()
        }
    }
    val configuredChatRef = remember(apiConfig) { apiConfig.configuredChatModelRef() }
    val configuredChatConnection = remember(apiConfig, configuredChatRef) {
        configuredChatRef?.let { apiConfig.connection(it.connectionId) }
    }
    val selectedLocalModelInstalled = remember(apiConfig) {
        viewModel.isLocalModelInstalled(apiConfig.localModel)
    }
    val canSendToModel = remember(apiConfig, selectedLocalModelInstalled) {
        canStartModelTask(apiConfig, selectedLocalModelInstalled)
    }
    val modeSwitchModels = remember(configuredChatRef, configuredChatConnection) {
        val reference = configuredChatRef ?: return@remember emptyList()
        val connection = configuredChatConnection ?: return@remember emptyList()
        AiProviderCatalog.quickSwitchModels(connection.providerId, reference.modelId)
            .filter { connection.supportsModel(it.id) }
    }
    val currentProviderName = configuredChatConnection?.name.orEmpty()
    val topModelSummary = remember(apiConfig) { chatModelMenuSummary(apiConfig) }
    val apiWarning = remember(apiConfig, canSendToModel) {
        when {
            !canSendToModel -> "启用功能需要配置模型"
            apiConfig.localModelDirectEnabled ||
                (apiConfig.dynamicLocalRoutingEnabled && configuredChatRef == null) -> null
            AiProviderCatalog.requiresApiKey(apiConfig) && apiConfig.apiKey.isBlank() -> {
                if (AiProviderCatalog.isFreeModel(apiConfig.providerId, apiConfig.model)) {
                    "免费模型仍需平台 Key，用来识别账号和限额"
                } else {
                    "当前模型需要 API Key，先去设置里填写"
                }
            }
            AiProviderCatalog.requiresApiKey(apiConfig) && !AiProviderCatalog.isVerified(apiConfig) ->
                "API Key 尚未验证，建议先测试连接"
            else -> null
        }
    }

    suspend fun scrollListToBottom(animate: Boolean) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex < 0) return
        if (animate) {
            listState.animateScrollToItem(lastIndex)
        } else {
            listState.scrollToItem(lastIndex)
        }
        val layoutInfo = listState.layoutInfo
        val lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
        val visibleBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val remainingDistance = lastItem.offset + lastItem.size - visibleBottom
        if (remainingDistance > 0) {
            if (animate) {
                listState.animateScrollBy(remainingDistance.toFloat())
            } else {
                listState.scrollBy(remainingDistance.toFloat())
            }
        }
    }

    LaunchedEffect(
        uiState.conversationId,
        uiState.messages.size,
        uiState.streamingContent,
        uiState.toolCallStatus,
    ) {
        viewModel.recheckPendingCapability()
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex < 0) return@LaunchedEffect

        val conversationId = uiState.conversationId
        val needsInitialPosition = conversationId?.let { id ->
            initiallyPositionedConversationId != id
        } ?: !initiallyPositionedFreshConversation

        if (needsInitialPosition) {
            scrollListToBottom(animate = false)
            if (conversationId == null) {
                initiallyPositionedFreshConversation = true
            } else {
                initiallyPositionedConversationId = conversationId
            }
        } else if (followLatestMessages) {
            scrollListToBottom(animate = true)
        }
    }

    LaunchedEffect(listIsDragged) {
        if (listIsDragged) {
            followLatestMessages = false
            composerExpanded = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else if (!listState.canScrollForward) {
            followLatestMessages = true
        }
    }

    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward) followLatestMessages = true
    }

    LaunchedEffect(Unit) {
        historyViewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(activity) {
        val lifecycleOwner = activity ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAppBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun closeThen(action: () -> Unit) {
        drawerSessionActive = true
        scope.launch {
            withTimeoutOrNull(600) { drawerState.close() }
            drawerState.snapTo(DrawerValue.Closed)
            drawerSessionActive = false
            drawerContentResetGeneration += 1
            delay(80)
            action()
        }
    }

    val interfaceEffects = LocalInterfaceEffects.current
    val chatBackdropState = rememberChatBackdropState(
        enabled = interfaceEffects.backdropBlurEnabled && LIVE_BACKDROP_BLUR_ENABLED,
    )
    val drawerBackdropBlurRadius = interfaceEffects.resolveBackdropBlurRadius(
        nonGlassRadius = 32.dp,
    )
    val drawerBackdropEnabled =
        interfaceEffects.requiresBackdropSample(drawerBackdropBlurRadius) &&
            LIVE_BACKDROP_BLUR_ENABLED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    DisposableEffect(drawerBackdropSnapshot) {
        val currentSnapshot = drawerBackdropSnapshot
        val retained = currentSnapshot?.retain() == true
        onDispose {
            if (retained) currentSnapshot?.release() else currentSnapshot?.recycleWhenIdle()
        }
    }
    suspend fun refreshDrawerBackdrop() {
        // PixelCopy includes the drawer itself once it is visible, producing blurred text ghosts.
        if (
            drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed
        ) {
            return
        }
        onDrawerBackdropSnapshotChange(if (drawerBackdropEnabled) {
            captureWindowBackdropSnapshot(context)
        } else {
            null
        })
    }
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (
            drawerState.currentValue == DrawerValue.Closed &&
            drawerState.targetValue == DrawerValue.Closed
        ) {
            delay(160)
            if (
                drawerState.currentValue == DrawerValue.Closed &&
                drawerState.targetValue == DrawerValue.Closed
            ) {
                onDrawerBackdropSnapshotChange(null)
            }
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentDrawer = maxWidth >= 840.dp
        CompositionLocalProvider(LocalChatBackdropState provides chatBackdropState) {
        AdaptiveChatLayout(
            usePermanentDrawer = usePermanentDrawer,
            drawerState = drawerState,
            onBeforeDrawerOpen = ::refreshDrawerBackdrop,
            drawerContent = {
                MasonDrawer(
                    permanent = usePermanentDrawer,
                    backdropSnapshot = drawerBackdropSnapshot,
                    resetGeneration = drawerContentResetGeneration,
                    onNewChat = {
                        closeThen {
                            if (!isFreshEmptyChat) onNewChat?.invoke()
                        }
                    },
                    onDevicePairing = { closeThen(onDevicePairing) },
                    remoteConversations = remoteConversations,
                    onOpenRemoteConversations = {
                        closeThen(onOpenRemoteConversations)
                    },
                    conversations = drawerConversations,
                    currentConversationId = uiState.conversationId,
                    onConversationSelected = { conversationId, isRunning ->
                        historyViewModel.markConversationSeen(conversationId)
                        closeThen { onConversationSelected(conversationId, isRunning) }
                    },
                    onOpenWorkbench = onOpenWorkbench,
                    onSettings = onNavigateToSettings,
                    onExportConversations = { ids -> historyViewModel.exportConversations(ids) },
                    onDeleteConversations = { ids -> pendingDrawerDeleteIds = ids },
                )
            },
        ) {
        Scaffold(
            modifier = if (usePermanentDrawer) {
                Modifier.widthIn(max = 920.dp).fillMaxHeight().align(Alignment.Center)
            } else {
                Modifier.fillMaxSize()
            },
            containerColor = pageBackgroundColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBackgroundColor),
            ) {
                val topSafePadding = with(density) {
                    WindowInsets.safeDrawing.getTop(density).toDp()
                }
                val topContentPadding = topSafePadding + 64.dp
                val topFadeHeight = topSafePadding + 30.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .captureChatBackdrop(chatBackdropState),
                    ) {
                        if (!hasMessages) {
                            EmptyChatState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = inputBarHeight),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topContentPadding,
                            bottom = inputBarHeight + 6.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(
                            visibleMessages,
                            key = { index, message -> "$index-${message.role}-${message.timestamp}-${message.content.hashCode()}" },
                        ) { index, message ->
                            val persistedTaskRun = remember(message.content) {
                                extractTaskRunMarker(message.content.orEmpty())
                            }
                            val persistedModelParticipation = remember(message.content) {
                                extractModelParticipation(message.content.orEmpty())
                            }
                            val isCurrentProcess = index == processInsertIndex &&
                                persistedTaskRun?.id == uiState.taskRun?.id
                            if (persistedTaskRun != null && !isCurrentProcess) {
                                MasonProcessPanel(
                                    steps = persistedTaskRun.steps,
                                    isActive = false,
                                    capabilityRequirement = null,
                                    toolCallStatus = null,
                                    hasDraft = false,
                                    modelParticipation = persistedModelParticipation,
                                    toolResultsByCallId = toolResultsByCallId,
                                    onOpenToolDetail = { step, result ->
                                        selectedToolExecution = ToolExecutionDetail(step, result)
                                    },
                                    onRetryStep = viewModel::retryTaskStep,
                                    onPause = viewModel::pauseCurrentTask,
                                    onCancel = viewModel::cancelCurrentTask,
                                )
                            }
                            if (index == processInsertIndex) {
                                MasonProcessPanel(
                                    steps = uiState.taskSteps,
                                    isActive = uiState.isStreaming,
                                    capabilityRequirement = pendingCapabilityRequirement,
                                    toolCallStatus = uiState.toolCallStatus,
                                    hasDraft = uiState.streamingContent.isNotBlank(),
                                    modelParticipation = persistedModelParticipation
                                        ?: uiState.modelContributions.takeIf { it.isNotEmpty() || uiState.isStreaming }
                                            ?.let(::ModelParticipation),
                                    toolResultsByCallId = toolResultsByCallId,
                                    onOpenToolDetail = { step, result ->
                                        selectedToolExecution = ToolExecutionDetail(step, result)
                                    },
                                    onRetryStep = viewModel::retryTaskStep,
                                    onPause = viewModel::pauseCurrentTask,
                                    onCancel = viewModel::cancelCurrentTask,
                                )
                            }
                            Box(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = null,
                                ),
                            ) {
                                MessageBubble(
                                    message = message,
                                    processingMs = if (
                                        message.role == "assistant" &&
                                        message.timestamp == lastAssistantTimestamp
                                    ) {
                                        uiState.lastProcessingMs
                                    } else {
                                        null
                                    },
                                    onRetry = { viewModel.retryLastUserMessage() },
                                    onResendUserMessage = viewModel::sendMessage,
                                    automationApplied = AutomationDraftService
                                        .extractAutomationDraftMarker(message.content.orEmpty())
                                        ?.draftId in appliedAutomationDraftIds,
                                    onApplyAutomationDraft = viewModel::applyAutomationDraft,
                                    onOpenAutomationPermissions = onNavigateToPermission,
                                    onOpenCapabilityConnections = onNavigateToIntegrations,
                                    showModelFooter = persistedTaskRun == null,
                                )
                            }
                        }

                        if (processInsertIndex == visibleMessages.size) {
                            item {
                                MasonProcessPanel(
                                    steps = uiState.taskSteps,
                                    isActive = uiState.isStreaming,
                                    capabilityRequirement = pendingCapabilityRequirement,
                                    toolCallStatus = uiState.toolCallStatus,
                                    hasDraft = uiState.streamingContent.isNotBlank(),
                                    modelParticipation = uiState.modelContributions
                                        .takeIf { it.isNotEmpty() || uiState.isStreaming }
                                        ?.let(::ModelParticipation),
                                    toolResultsByCallId = toolResultsByCallId,
                                    onOpenToolDetail = { step, result ->
                                        selectedToolExecution = ToolExecutionDetail(step, result)
                                    },
                                    onRetryStep = viewModel::retryTaskStep,
                                    onPause = viewModel::pauseCurrentTask,
                                    onCancel = viewModel::cancelCurrentTask,
                                )
                            }
                        }

                        if (uiState.isStreaming && uiState.streamingContent.isNotEmpty()) {
                            item {
                                MessageBubble(
                                    ChatMessage(role = "assistant", content = uiState.streamingContent),
                                    isStreaming = true,
                                    onRetry = { viewModel.retryLastUserMessage() },
                                )
                            }
                        }
                        item(key = "chat-bottom-anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(topFadeHeight)
                            .graphicsLayer {
                                val progress = topFadeProgress.value
                                alpha = progress
                                translationY = -topFadeHeight.toPx() * (1f - progress)
                            }
                            .blurLayerOuterEdgeFeather(
                                edge = ProgressiveBlurEdge.Bottom,
                                featherHeight = 12.dp,
                            )
                            .progressiveEdgeBlur(
                                state = chatBackdropState,
                                edge = ProgressiveBlurEdge.Top,
                                backgroundColor = pageBackgroundColor,
                                blurRadius = 15.dp,
                                smoothBoundary = true,
                            )
                            .background(
                                Brush.verticalGradient(
                                    colors = if (interfaceEffects.glassMaterialEnabled) {
                                        listOf(
                                            pageBackgroundColor.copy(alpha = 0.10f),
                                            pageBackgroundColor.copy(alpha = 0.04f),
                                            Color.Transparent,
                                        )
                                    } else {
                                        listOf(
                                            pageBackgroundColor.copy(alpha = 0.98f),
                                            pageBackgroundColor.copy(alpha = 0.72f),
                                            Color.Transparent,
                                        )
                                    },
                                ),
                            ),
                    )
                    if (!usePermanentDrawer) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    WindowInsets.safeDrawing
                                        .only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                                        .asPaddingValues(),
                                )
                                .padding(start = 8.dp, top = 8.dp),
                        ) {
                            GlassIconButton(
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    drawerSessionActive = true
                                    scope.launch {
                                        refreshDrawerBackdrop()
                                        drawerState.open()
                                    }
                                },
                            ) {
                                Box(modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Outlined.Menu,
                                        contentDescription = "打开菜单",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .size(23.dp)
                                            .align(Alignment.Center),
                                    )
                                    if (drawerConversations.any { it.hasUnreadCompletion }) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (topModelSummary.hasConfiguredModel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    WindowInsets.safeDrawing
                                        .only(WindowInsetsSides.Top + WindowInsetsSides.End)
                                        .asPaddingValues(),
                                )
                                .padding(end = 8.dp, top = 8.dp),
                        ) {
                            TopModelMenuButton(
                                summary = topModelSummary,
                                expanded = topModelMenuExpanded,
                                onExpandedChange = { topModelMenuExpanded = it },
                                onOpenSettings = {
                                    topModelMenuExpanded = false
                                    onNavigateToModelSettings()
                                },
                            )
                        }
                    }
                    if (showScrollToBottom) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = inputBarHeight + 8.dp),
                        ) {
                            GlassIconButton(
                                onClick = {
                                    scrollToBottomInProgress = true
                                    followLatestMessages = true
                                    scope.launch {
                                        try {
                                            scrollListToBottom(animate = false)
                                        } finally {
                                            scrollToBottomInProgress = false
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "回到最新消息",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(inputBarHeight)
                        .blurLayerOuterEdgeFeather(
                            edge = ProgressiveBlurEdge.Top,
                            featherHeight = 12.dp,
                        )
                        .progressiveEdgeBlur(
                            state = chatBackdropState,
                            edge = ProgressiveBlurEdge.Bottom,
                            backgroundColor = pageBackgroundColor,
                            blurRadius = 15.dp,
                            smoothBoundary = true,
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = if (interfaceEffects.glassMaterialEnabled) {
                                    listOf(
                                        Color.Transparent,
                                        pageBackgroundColor.copy(alpha = 0.02f),
                                        pageBackgroundColor.copy(alpha = 0.08f),
                                    )
                                } else {
                                    listOf(
                                        Color.Transparent,
                                        pageBackgroundColor.copy(alpha = 0.08f),
                                        pageBackgroundColor.copy(alpha = 0.28f),
                                    )
                                },
                            ),
                        ),
                )
                if (!interfaceEffects.glassMaterialEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(inputBarHeight)
                            .background(
                                Brush.verticalGradient(
                                    0f to pageBackgroundColor.copy(alpha = 0.28f),
                                    0.22f to pageBackgroundColor.copy(alpha = 0.88f),
                                    1f to pageBackgroundColor,
                                ),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size -> inputBarHeightPx = size.height },
                ) {
                    InputBar(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            val outgoing = buildOutgoingMessage(inputText, pendingAttachments, selectedSkill)
                            viewModel.sendMessage(outgoing)
                            inputText = ""
                            pendingAttachments = emptyList()
                            selectedSkill = null
                            composerExpanded = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        },
                        onStop = viewModel::stopGeneration,
                        isGenerating = uiState.isStreaming,
                        enabled = uiState.pendingToolApproval == null,
                        expanded = composerExpanded,
                        onExpandedChange = { composerExpanded = it },
                        attachments = pendingAttachments,
                        selectedSkill = selectedSkill,
                        onAddImage = { imagePicker.launch(arrayOf("image/*")) },
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onUseSkill = {
                            viewModel.refreshInstalledSkills()
                            showSkillPicker = true
                        },
                        apiWarning = apiWarning,
                        pendingApproval = uiState.pendingToolApproval,
                        onOpenApprovalDetails = { showApprovalDetails = true },
                        onApproveOnce = { viewModel.approvePendingToolCall() },
                        onOpenSettings = onNavigateToModelSettings,
                        modelSwitchModels = modeSwitchModels,
                        currentModelId = configuredChatRef?.modelId.orEmpty(),
                        currentProviderName = currentProviderName,
                        canSendToModel = canSendToModel,
                        onSelectModel = viewModel::selectChatModel,
                        onRemoveAttachment = { index ->
                            pendingAttachments = pendingAttachments.filterIndexed { itemIndex, _ ->
                                itemIndex != index
                            }
                        },
                        onClearSkill = { selectedSkill = null },
                    )
                }
            }
        }
        }
        }
    }

    if (showSkillPicker) {
        SkillPickerSheet(
            skills = installedSkills.map { installed ->
                SkillOption(
                    name = installed.manifest.name,
                    description = installed.manifest.description,
                    path = installed.path,
                    instructions = installed.instructions,
                    parameters = installed.manifest.parameters,
                )
            },
            onDismiss = { showSkillPicker = false },
            onSelect = { skill ->
                showSkillPicker = false
                if (skill.parameters.any { !it.secret }) {
                    parameterizingSkill = skill
                } else {
                    selectedSkill = skill
                }
            },
        )
    }

    parameterizingSkill?.let { skill ->
        SkillParameterDialog(
            skill = skill,
            onDismiss = { parameterizingSkill = null },
            onConfirm = { values ->
                selectedSkill = skill.copy(parameterValues = values)
                parameterizingSkill = null
            },
        )
    }

    selectedToolExecution?.let { detail ->
        ToolExecutionDetailSheet(
            detail = detail,
            onDismiss = { selectedToolExecution = null },
            onRetry = {
                selectedToolExecution = null
                viewModel.retryTaskStep(detail.step.id)
            },
        )
    }

    if (showApprovalDetails) {
        uiState.pendingToolApproval?.let { approval ->
            ToolApprovalDetailSheet(
            approval = approval,
            onDismiss = { showApprovalDetails = false },
            onApprove = {
                showApprovalDetails = false
                viewModel.approvePendingToolCall()
            },
            onAlwaysApprove = {
                showApprovalDetails = false
                viewModel.approvePendingToolCall(alwaysAllow = true)
            },
            onReject = {
                showApprovalDetails = false
                viewModel.rejectPendingToolCall()
            },
            onOpenConnections = onNavigateToIntegrations,
        )
        }
    }

    if (pendingDrawerDeleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDrawerDeleteIds = emptySet() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除选中对话") },
            text = { Text("将删除 ${pendingDrawerDeleteIds.size} 个对话及其中消息，删除后不能恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyViewModel.deleteConversations(pendingDrawerDeleteIds)
                        pendingDrawerDeleteIds = emptySet()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDrawerDeleteIds = emptySet() }) {
                    Text("取消")
                }
            },
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("退出 MASON？") },
            text = { Text("退出后将返回系统桌面，未完成的任务可以稍后继续。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        activity?.moveTaskToBack(true)
                    },
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolApprovalDetailSheet(
    approval: ToolApprovalRequest,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onAlwaysApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenConnections: () -> Unit,
) {
    val riskLabel = when (approval.riskLevel) {
        ToolRiskLevel.Low -> "低风险"
        ToolRiskLevel.Medium -> "需要确认"
        ToolRiskLevel.High -> "高风险"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 2.dp,
                bottomPadding = 0.dp,
            )
            Text(
                if (approval.integrationProtocol == "A2A") "允许应用协作？" else "风险确认",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = displayApprovalAction(approval),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (approval.actionSummary.isNotBlank()) {
                Text(approval.actionSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            approval.integrationProtocol?.let { protocol ->
                Text(
                    text = "协作方式：$protocol",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
            Text("级别：$riskLabel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("影响范围：${approval.reason}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = when {
                    approval.integrationProtocol == "A2A" ->
                        "这次只允许当前任务。以后再次委派敏感操作时，Mason 仍会询问。"
                    !approval.allowPersistentGrant ->
                        "这次只允许当前任务。以后再次保存敏感信息时，Mason 仍会询问。"
                    else ->
                        "允许一次只继续本轮任务；总是允许会记住该工具，之后可在设置中撤销。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (approval.integrationProtocol != null) {
                TextButton(onClick = onOpenConnections) {
                    Text("查看连接")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReject) { Text("拒绝") }
                if (approval.allowPersistentGrant) {
                    TextButton(onClick = onAlwaysApprove) {
                        Text("总是允许")
                    }
                }
                TextButton(onClick = onApprove) {
                    Text("允许一次", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SkillParameterDialog(
    skill: SkillOption,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val parameters = skill.parameters.filterNot(MasonSkillParameter::secret)
    var values by remember(skill.path) {
        mutableStateOf(parameters.associate { it.key to it.defaultValue })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(skill.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parameters.forEach { parameter ->
                    OutlinedTextField(
                        value = values[parameter.key].orEmpty(),
                        onValueChange = { value -> values = values + (parameter.key to value) },
                        label = { Text(parameter.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (skill.parameters.any(MasonSkillParameter::secret)) {
                    Text(
                        "敏感参数不会写入对话，请在 Skill 的安全配置中提供。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(values) },
                enabled = parameters.none { it.required && values[it.key].isNullOrBlank() },
            ) { Text("使用 Skill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MasonDrawer(
    permanent: Boolean = false,
    backdropSnapshot: WindowBackdropSnapshot? = null,
    resetGeneration: Int,
    onNewChat: () -> Unit,
    onDevicePairing: () -> Unit,
    remoteConversations: RemoteConversationListUiState,
    onOpenRemoteConversations: () -> Unit,
    conversations: List<ConversationListItem>,
    currentConversationId: Long?,
    onConversationSelected: (Long, Boolean) -> Unit,
    onOpenWorkbench: () -> Unit,
    onSettings: () -> Unit,
    onExportConversations: (Set<Long>) -> Unit,
    onDeleteConversations: (Set<Long>) -> Unit,
) {
    var selectionMode by remember(resetGeneration) { mutableStateOf(false) }
    var selectedConversationIds by remember(resetGeneration) {
        mutableStateOf<Set<Long>>(emptySet())
    }
    var searchActive by remember(resetGeneration) { mutableStateOf(false) }
    var searchQuery by remember(resetGeneration) { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filteredConversations = remember(conversations, searchQuery) {
        val keyword = searchQuery.trim()
        if (keyword.isBlank()) {
            conversations
        } else {
            conversations.filter { item ->
                item.conversation.title.contains(keyword, ignoreCase = true) ||
                    item.searchableText.contains(keyword, ignoreCase = true)
            }
        }
    }
    val visibleConversations = filteredConversations.take(30)
    val allVisibleIds = remember(visibleConversations) {
        visibleConversations.map { it.conversation.id }.toSet()
    }
    val drawerListState = rememberLazyListState()
    val drawerTopFadeHeight = 44.dp
    val drawerBottomFadeHeight = 44.dp
    val drawerBlurOuterFeather = 6.dp
    val drawerFadeRevealDistancePx = with(LocalDensity.current) { 24.dp.toPx() }
    val drawerTopFadeProgress = remember(drawerListState, drawerFadeRevealDistancePx) {
        derivedStateOf {
            if (drawerListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (drawerListState.firstVisibleItemScrollOffset / drawerFadeRevealDistancePx)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val drawerBottomFadeProgress by animateFloatAsState(
        targetValue = if (drawerListState.canScrollForward) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "drawer_bottom_fade",
    )
    val interfaceEffects = LocalInterfaceEffects.current
    val drawerBackdropBlurRadius = interfaceEffects.resolveBackdropBlurRadius(
        nonGlassRadius = 32.dp,
    )
    val drawerBackdropRequired = interfaceEffects.requiresBackdropSample(
        blurRadius = drawerBackdropBlurRadius,
    )
    var drawerWindowPosition by remember { mutableStateOf(IntOffset.Zero) }
    val drawerSurfaceAlpha by animateFloatAsState(
        targetValue = if (
            !permanent &&
            drawerBackdropRequired &&
            backdropSnapshot == null
        ) {
            1f
        } else {
            interfaceEffects.largeSurfaceAlpha
        },
        animationSpec = tween(durationMillis = 120),
        label = "drawer_surface_alpha",
    )
    val drawerSurface = drawerGlassSurface(drawerSurfaceAlpha)
    val drawerEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = LocalInterfaceEffects.current.progressiveEdgeBlurEnabled,
    )

    LaunchedEffect(allVisibleIds) {
        selectedConversationIds = selectedConversationIds.intersect(allVisibleIds)
    }

    LaunchedEffect(searchActive, selectionMode, conversations.isEmpty()) {
        if (conversations.isEmpty()) {
            searchQuery = ""
            searchActive = false
        }
        if (searchActive && !selectionMode) {
            withFrameNanos { }
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = selectionMode || searchActive) {
        if (selectionMode) {
            selectedConversationIds = emptySet()
            selectionMode = false
        } else {
            searchQuery = ""
            searchActive = false
        }
    }

    fun toggleConversationSelection(id: Long) {
        val nextIds = if (id in selectedConversationIds) {
            selectedConversationIds - id
        } else {
            selectedConversationIds + id
        }
        selectedConversationIds = nextIds
    }

    val content: @Composable () -> Unit = {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                .padding(top = 14.dp, bottom = 10.dp),
        ) {
            if (!selectionMode) {
                DrawerPrimaryAction(
                    label = "新对话",
                    selected = false,
                    onClick = onNewChat,
                    icon = Icons.Outlined.Add,
                )
                if (remoteConversations.connector == null) {
                    DrawerPrimaryAction(
                        label = "设备扫码配对",
                        selected = false,
                        onClick = onDevicePairing,
                        icon = ImageVector.vectorResource(R.drawable.ic_device_scan),
                    )
                } else {
                    RemoteConversationDrawerGroup(
                        state = remoteConversations,
                        onOpen = onOpenRemoteConversations,
                    )
                }
            }

            if (conversations.isNotEmpty()) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchActive && !selectionMode) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "搜索对话和内容",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchActive = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Text(
                        if (selectionMode) "已选 ${selectedConversationIds.size}" else "最近对话",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectionMode) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                selectedConversationIds = emptySet()
                                selectionMode = false
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                    Text(
                        "全选",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                selectedConversationIds = allVisibleIds
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                } else if (!searchActive) {
                    IconButton(
                        onClick = { searchActive = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索对话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = drawerListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawerListEdgeFadeMask(
                            topProgress = drawerTopFadeProgress.value,
                            bottomProgress = drawerBottomFadeProgress,
                            topFadeHeight = drawerTopFadeHeight,
                            bottomFadeHeight = drawerBottomFadeHeight,
                        )
                        .captureProgressiveEdgeBlur(drawerEdgeBlurState),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (visibleConversations.isEmpty() && conversations.isNotEmpty()) {
                        item {
                            Text(
                                "没有匹配的对话",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        items(
                            items = visibleConversations,
                            key = { it.conversation.id },
                        ) { item ->
                            Box(modifier = Modifier.animateItem()) {
                                DrawerConversationItem(
                                    item = item,
                                    selected = if (selectionMode) {
                                        item.conversation.id in selectedConversationIds
                                    } else {
                                        item.conversation.id == currentConversationId
                                    },
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) {
                                            toggleConversationSelection(item.conversation.id)
                                        } else {
                                            onConversationSelected(item.conversation.id, item.isRunning)
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedConversationIds = selectedConversationIds + item.conversation.id
                                    },
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(drawerTopFadeHeight + drawerBlurOuterFeather)
                        .graphicsLayer {
                            val progress = drawerTopFadeProgress.value
                            alpha = progress
                            translationY = -drawerBlurOuterFeather.toPx()
                        }
                        .blurLayerOuterEdgeFeather(
                            edge = ProgressiveBlurEdge.Top,
                            featherHeight = drawerBlurOuterFeather,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .progressiveEdgeBlur(
                                state = drawerEdgeBlurState,
                                edge = ProgressiveBlurEdge.Top,
                                backgroundColor = Color.Transparent,
                                smoothBoundary = true,
                                gradientStartY = drawerBlurOuterFeather,
                                gradientEndY = drawerBlurOuterFeather + drawerTopFadeHeight,
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(drawerBottomFadeHeight + drawerBlurOuterFeather)
                        .graphicsLayer {
                            alpha = drawerBottomFadeProgress
                            translationY = drawerBlurOuterFeather.toPx()
                        }
                        .blurLayerOuterEdgeFeather(
                            edge = ProgressiveBlurEdge.Bottom,
                            featherHeight = drawerBlurOuterFeather,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .progressiveEdgeBlur(
                                state = drawerEdgeBlurState,
                                edge = ProgressiveBlurEdge.Bottom,
                                backgroundColor = Color.Transparent,
                                smoothBoundary = true,
                                gradientStartY = 0.dp,
                                gradientEndY = drawerBottomFadeHeight,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(17.dp))
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawerSelectionAction(
                        label = "导出",
                        icon = Icons.Outlined.FileDownload,
                        enabled = selectedConversationIds.isNotEmpty(),
                        onClick = {
                            onExportConversations(selectedConversationIds)
                            selectedConversationIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerSelectionAction(
                        label = "删除",
                        icon = Icons.Outlined.Delete,
                        enabled = selectedConversationIds.isNotEmpty(),
                        destructive = true,
                        onClick = { onDeleteConversations(selectedConversationIds) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                DrawerFooterDock(
                    onOpenWorkbench = onOpenWorkbench,
                    onSettings = onSettings,
                )
            }
        }
    }
    if (permanent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerSurface)
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start).asPaddingValues()),
        ) {
            content()
        }
    } else {
        ModalDrawerSheet(
            modifier = Modifier
                .width(292.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    drawerWindowPosition = IntOffset(
                        x = position.x.roundToInt(),
                        y = position.y.roundToInt(),
                    )
                }
                .windowBackdrop(
                    snapshot = backdropSnapshot,
                    windowPosition = drawerWindowPosition,
                    blurRadius = drawerBackdropBlurRadius,
                    effectAlpha = interfaceEffects.backdropEffectAlpha,
                    allowZeroPosition = true,
                )
                .background(drawerSurface),
            drawerShape = RectangleShape,
            drawerContainerColor = Color.Transparent,
            windowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun AdaptiveChatLayout(
    usePermanentDrawer: Boolean,
    drawerState: DrawerState,
    onBeforeDrawerOpen: suspend () -> Unit,
    drawerContent: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (usePermanentDrawer) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(292.dp).fillMaxHeight()) { drawerContent() }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    } else {
        val drawerGestureScope = rememberCoroutineScope()
        val edgeWidthPx = with(LocalDensity.current) { 32.dp.toPx() }
        val openGestureModifier = Modifier.pointerInput(drawerState, edgeWidthPx) {
            awaitEachGesture {
                // Keep the drawer recognizer on the system edge. A consumed down
                // belongs to a horizontal content control (table, carousel, etc.).
                val down = awaitFirstDown(requireUnconsumed = true)
                val start = down.position
                if (!isDrawerGestureStartWithinEdge(start.x, edgeWidthPx)) {
                    return@awaitEachGesture
                }

                var change = down
                while (change.pressed) {
                    val event = awaitPointerEvent()
                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val drag = change.position - start
                    if (shouldOpenDrawerFromGesture(
                            horizontalDrag = drag.x,
                            verticalDrag = drag.y,
                            touchSlop = viewConfiguration.touchSlop,
                        )
                    ) {
                        change.consume()
                        drawerGestureScope.launch {
                            onBeforeDrawerOpen()
                            drawerState.open()
                        }
                        break
                    }
                    if (drag.x < -viewConfiguration.touchSlop ||
                        abs(drag.y) > viewConfiguration.touchSlop
                    ) {
                        break
                    }
                }
            }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.currentValue == DrawerValue.Open,
            scrimColor = Color.Transparent,
            drawerContent = drawerContent,
            content = {
                Box(modifier = Modifier.fillMaxSize().then(openGestureModifier)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                    if (
                        drawerState.currentValue == DrawerValue.Open ||
                            drawerState.targetValue == DrawerValue.Open
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f))
                                .clickable {
                                    drawerGestureScope.launch { drawerState.close() }
                                },
                        )
                    }
                }
            },
        )
    }
}

internal fun shouldOpenDrawerFromGesture(
    horizontalDrag: Float,
    verticalDrag: Float,
    touchSlop: Float,
): Boolean = horizontalDrag > touchSlop && horizontalDrag > abs(verticalDrag)

internal fun isDrawerGestureStartWithinEdge(
    startX: Float,
    edgeWidth: Float,
): Boolean = startX >= 0f && edgeWidth > 0f && startX <= edgeWidth

@Composable
private fun TopModelMenuButton(
    summary: ChatModelMenuSummary,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box {
        GlassIconButton(
            onClick = { onExpandedChange(!expanded) },
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_model_switch),
                contentDescription =
                    "当前模型：聊天 ${summary.chatModelName}，识图 ${summary.visionModelName}，" +
                        "图片生成 ${summary.imageModelName}",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(23.dp),
            )
        }
        ChatGlassDropdown(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            width = 180.dp,
            cornerRadius = 12.dp,
            alignEnd = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前模型",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .offset(x = 8.dp)
                            .size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "打开模型配置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFDEE0E6).copy(alpha = 0.50f))
                CurrentModelMenuRow(label = "聊天", modelName = summary.chatModelName)
                CurrentModelMenuRow(label = "识图", modelName = summary.visionModelName)
                CurrentModelMenuRow(label = "图片生成", modelName = summary.imageModelName)
            }
        }
    }
}

@Composable
private fun CurrentModelMenuRow(
    label: String,
    modelName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = modelName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun drawerGlassSurface(alpha: Float): Color = MaterialTheme.colorScheme.surface.copy(
    alpha = alpha,
)

@Composable
private fun DrawerPrimaryAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    trailingIcon: ImageVector? = null,
) {
    val glassMaterialEnabled = LocalInterfaceEffects.current.glassMaterialEnabled
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else if (glassMaterialEnabled) {
                    // The drawer is the single glass layer. Its rows stay
                    // transparent so a second translucent panel is not formed.
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                },
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else if (glassMaterialEnabled) Color.Transparent
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingIcon?.let {
            Spacer(Modifier.width(8.dp))
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RemoteConversationDrawerGroup(
    state: RemoteConversationListUiState,
    onOpen: () -> Unit,
) {
    val connector = state.connector ?: return
    DrawerPrimaryAction(
        label = if (state.isReachable == false) {
            "离线 · ${connector.displayName}"
        } else {
            "已连接 · ${connector.displayName}"
        },
        selected = false,
        onClick = onOpen,
        icon = Icons.Outlined.Computer,
        trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    )
}

@Composable
private fun DrawerSelectionAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (enabled) 0.12f else 0.06f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DrawerFooterDock(
    onOpenWorkbench: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerFooterAction(
            label = "工作台",
            icon = Icons.Outlined.Folder,
            onClick = onOpenWorkbench,
            modifier = Modifier.weight(1f),
        )
        DrawerFooterAction(
            label = "设置",
            icon = Icons.Outlined.Settings,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawerFooterAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DrawerConversationItem(
    item: ConversationListItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.46f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.conversation.title,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.lastMessage ?: "还没有消息",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selectionMode) {
            when {
                item.isRunning -> {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 1.5.dp,
                    )
                }
                item.hasUnreadCompletion -> {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    var variantIndex by rememberSaveable { mutableIntStateOf(nextEmptyChatVariantIndex()) }
    val variant = emptyChatVariants[variantIndex]
    val primaryMotion = remember { Animatable(0f) }
    val followMotion = remember { Animatable(0f) }
    val bodyX = remember { Animatable(0f) }
    val bodyY = remember { Animatable(0f) }
    val bodyRotation = remember { Animatable(0f) }
    val bodyScale = remember { Animatable(1f) }
    val bodyOpacity = remember { Animatable(0f) }
    var interacting by remember { mutableStateOf(false) }
    val motionScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motionEaseOut = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val motionEaseInOut = remember { CubicBezierEasing(0.77f, 0f, 0.175f, 1f) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = backgroundColor.luminance() < 0.5f

    suspend fun playExpressionMotion() {
        primaryMotion.snapTo(0f)
        followMotion.snapTo(0f)
        coroutineScope {
            launch {
                primaryMotion.animateTo(-0.18f, tween(120, easing = motionEaseInOut))
                primaryMotion.animateTo(1f, tween(260, easing = motionEaseOut))
                primaryMotion.animateTo(-0.07f, tween(210, easing = motionEaseInOut))
                primaryMotion.animateTo(0f, tween(220, easing = motionEaseOut))
            }
            launch {
                delay(65)
                followMotion.animateTo(0.78f, tween(250, easing = motionEaseOut))
                followMotion.animateTo(-0.04f, tween(210, easing = motionEaseInOut))
                followMotion.animateTo(0f, tween(220, easing = motionEaseOut))
            }
        }
    }

    LaunchedEffect(Unit) {
        bodyY.snapTo(with(density) { (-8).dp.toPx() })
        bodyRotation.snapTo(-1.5f)
        bodyScale.snapTo(0.97f)
        coroutineScope {
            launch { bodyOpacity.animateTo(1f, tween(260, easing = motionEaseOut)) }
            launch { bodyY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 180f)) }
            launch { bodyRotation.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 150f)) }
            launch {
                bodyScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
        }
    }

    LaunchedEffect(variantIndex, interacting) {
        primaryMotion.stop()
        followMotion.stop()
        primaryMotion.snapTo(0f)
        followMotion.snapTo(0f)
        if (interacting) return@LaunchedEffect
        delay(260)
        var firstMotion = true
        while (true) {
            playExpressionMotion()
            if (!firstMotion) {
                val direction = if (variantIndex % 2 == 0) 1f else -1f
                val idleShift = with(density) { 1.5.dp.toPx() }
                coroutineScope {
                    launch {
                        bodyX.animateTo(direction * idleShift, tween(240, easing = motionEaseInOut))
                        bodyX.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                    launch {
                        bodyRotation.animateTo(
                            direction * 1.1f,
                            tween(240, easing = motionEaseInOut),
                        )
                        bodyRotation.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 120f))
                    }
                }
            }
            firstMotion = false
            delay(variant.idleDelayMs)
        }
    }

    fun settleAfterDrag(velocityX: Float, velocityY: Float, width: Float) {
        motionScope.launch {
            coroutineScope {
                launch {
                    bodyX.animateTo(
                        0f,
                        spring(dampingRatio = 0.82f, stiffness = 160f),
                        initialVelocity = velocityX.coerceIn(-1_800f, 1_800f),
                    )
                }
                launch {
                    bodyY.animateTo(
                        0f,
                        spring(dampingRatio = 0.9f, stiffness = 180f),
                        initialVelocity = velocityY.coerceIn(-1_400f, 1_400f),
                    )
                }
                launch {
                    bodyRotation.animateTo(
                        0f,
                        spring(dampingRatio = 0.82f, stiffness = 150f),
                        initialVelocity = (velocityX / width * 28f).coerceIn(-48f, 48f),
                    )
                }
                launch {
                    bodyScale.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
            }
        }
    }

    fun changeExpressionWithRock() {
        val direction = if (variantIndex % 2 == 0) 1f else -1f
        val rockDistance = with(density) { 5.dp.toPx() }
        val counterDistance = with(density) { 1.5.dp.toPx() }
        variantIndex = (variantIndex + 1) % emptyChatVariants.size
        motionScope.launch {
            coroutineScope {
                launch {
                    bodyX.animateTo(direction * rockDistance, tween(110, easing = motionEaseOut))
                    bodyX.animateTo(
                        -direction * counterDistance,
                        tween(170, easing = motionEaseInOut),
                    )
                    bodyX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 150f))
                }
                launch {
                    bodyRotation.animateTo(direction * 4.2f, tween(110, easing = motionEaseOut))
                    bodyRotation.animateTo(
                        -direction * 1.2f,
                        tween(170, easing = motionEaseInOut),
                    )
                    bodyRotation.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 150f))
                }
                launch {
                    bodyY.animateTo(
                        with(density) { (-2).dp.toPx() },
                        tween(100, easing = motionEaseOut),
                    )
                    bodyY.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 180f))
                }
                launch {
                    bodyScale.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        val computerSize = (minOf(
            maxWidth - 32.dp,
            maxHeight * 0.72f,
            400.dp,
        ) * 0.5f).coerceAtLeast(96.dp)
        val faceCenterX = if (isDark) 0.583f else 0.579f
        val faceCenterY = if (isDark) 0.528f else 0.485f
        val faceWidth = computerSize * 0.16f
        val faceHeight = faceWidth / variant.faceAspectRatio
        val shadowTilt = (abs(bodyRotation.value) / 6f).coerceIn(0f, 1f)
        val shadowLateral = (
            abs(bodyX.value) / with(density) { 72.dp.toPx() }
        ).coerceIn(0f, 1f)
        val shadowLift = (
            -bodyY.value / with(density) { 48.dp.toPx() }
        ).coerceIn(0f, 1f)
        val shadowCompression = (
            bodyY.value / with(density) { 48.dp.toPx() }
        ).coerceIn(0f, 1f)
        val shadowScaleX = 1f + shadowTilt * 0.10f + shadowLateral * 0.06f - shadowLift * 0.05f
        val shadowScaleY = 1f + shadowCompression * 0.08f - shadowLift * 0.18f
        val shadowAlpha = (
            (if (isDark) 0.72f else 0.90f) *
                (1f - shadowLift * 0.42f + shadowCompression * 0.10f) *
                bodyOpacity.value
        ).coerceIn(0f, 1f)

        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(computerSize)
                    .aspectRatio(1f),
            ) {
                Image(
                    painter = painterResource(R.drawable.mason_empty_chat_shadow),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = shadowScaleX
                            scaleY = shadowScaleY
                            alpha = shadowAlpha
                            transformOrigin = TransformOrigin(0.5f, 0.85f)
                        },
                    contentScale = ContentScale.Fit,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = bodyX.value
                            translationY = bodyY.value
                            rotationZ = bodyRotation.value
                            scaleX = bodyScale.value
                            scaleY = bodyScale.value
                            alpha = bodyOpacity.value
                            transformOrigin = TransformOrigin(0.5f, 0.78f)
                        }
                        .pointerInput(variantIndex) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                interacting = true
                                motionScope.launch {
                                    bodyX.stop()
                                    bodyY.stop()
                                    bodyRotation.stop()
                                    bodyScale.stop()
                                    bodyScale.snapTo(0.985f)
                                }

                                var change = down
                                var dragged = false
                                while (change.pressed) {
                                    val event = awaitPointerEvent()
                                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val dragOffset = change.position - start
                                    if (!dragged && dragOffset.getDistance() >= viewConfiguration.touchSlop) {
                                        dragged = true
                                    }
                                    if (dragged) {
                                        change.consume()
                                        val targetX = emptyChatRubberBand(
                                            dragOffset.x,
                                            size.width * 0.32f,
                                        )
                                        val targetY = emptyChatRubberBand(
                                            dragOffset.y,
                                            size.height * 0.22f,
                                        )
                                        val targetRotation = (
                                            dragOffset.x / size.width * 16f
                                        ).coerceIn(-6f, 6f)
                                        motionScope.launch {
                                            bodyX.snapTo(targetX)
                                            bodyY.snapTo(targetY)
                                            bodyRotation.snapTo(targetRotation)
                                            bodyScale.snapTo(0.99f)
                                        }
                                    }
                                }

                                interacting = false
                                if (dragged) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    settleAfterDrag(velocity.x, velocity.y, size.width.toFloat())
                                } else {
                                    changeExpressionWithRock()
                                }
                            }
                        },
                ) {
                    Image(
                        painter = painterResource(
                            if (isDark) {
                                R.drawable.mason_empty_chat_stone_dark
                            } else {
                                R.drawable.mason_empty_chat_computer
                            },
                        ),
                        contentDescription = "石头屏幕助手，${variant.prompt}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Box(
                        modifier = Modifier
                            .offset(
                                x = computerSize * faceCenterX - faceWidth / 2,
                                y = computerSize * faceCenterY - faceHeight / 2,
                            )
                            .width(faceWidth)
                            .aspectRatio(variant.faceAspectRatio),
                    ) {
                        variant.layers.forEachIndexed { layerIndex, resourceId ->
                            Image(
                                painter = painterResource(resourceId),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val primary = primaryMotion.value
                                        val follow = followMotion.value
                                        when (variant.expression) {
                                            EmptyChatExpression.Tilted -> if (layerIndex == 2) {
                                                translationX = primary * 2.8.dp.toPx()
                                                translationY = follow * 0.4.dp.toPx()
                                                rotationZ = primary * 1.4f
                                            }
                                            EmptyChatExpression.Surprised -> when (layerIndex) {
                                                0 -> {
                                                    translationY = -primary * 3.2.dp.toPx()
                                                    rotationZ = -primary * 4f
                                                }
                                                1 -> {
                                                    translationY = -follow * 3.dp.toPx()
                                                    rotationZ = follow * 4f
                                                }
                                                2 -> {
                                                    translationY = follow * 1.4.dp.toPx()
                                                    scaleX = 1f + follow * 0.06f
                                                    scaleY = scaleX
                                                }
                                            }
                                            EmptyChatExpression.Smile -> if (layerIndex == 2) {
                                                translationY = -primary * 2.3.dp.toPx()
                                                scaleY = 1f + primary * 0.07f
                                            } else {
                                                translationY = -follow * 0.7.dp.toPx()
                                            }
                                            EmptyChatExpression.Frown -> if (layerIndex == 2) {
                                                translationY = primary * 1.7.dp.toPx()
                                                scaleX = 1f - primary * 0.04f
                                            } else {
                                                translationY = -follow * 0.6.dp.toPx()
                                            }
                                            EmptyChatExpression.Question -> if (layerIndex == 0) {
                                                translationY = -primary * 2.8.dp.toPx()
                                                rotationZ = primary * 2f
                                            } else {
                                                translationY = -follow * 0.6.dp.toPx()
                                            }
                                        }
                                    },
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = variant.prompt,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun ToolCallStatusCard(toolName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation_angle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                toolIcon(toolName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(17.dp)
                    .rotate(rotation),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 11.dp, vertical = 8.dp),
        ) {
            Text(
                text = toolName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MasonProcessPanel(
    steps: List<TaskStep>,
    isActive: Boolean,
    capabilityRequirement: CapabilityRequirement?,
    toolCallStatus: String?,
    hasDraft: Boolean,
    modelParticipation: ModelParticipation?,
    toolResultsByCallId: Map<String, ChatMessage>,
    onOpenToolDetail: (TaskStep, ChatMessage?) -> Unit,
    onRetryStep: (String) -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    val boilerplateIds = setOf("plan", "prepare-inputs", "execute", "review", "summary")
    val executionSteps = steps.filter { step ->
        step.toolCall != null ||
            step.kind == TaskStepKind.Tool ||
            step.kind == TaskStepKind.Skill ||
            step.id !in boilerplateIds ||
            step.status == TaskStepStatus.Failed ||
            step.status == TaskStepStatus.WaitingForUser
    }
    if (steps.isEmpty() && !isActive && capabilityRequirement == null && toolCallStatus == null) return

    val toolRunning = executionSteps.any { it.status == TaskStepStatus.Running }
    val thoughtRunning = isActive && !toolRunning && capabilityRequirement == null
    val thoughtFailed = steps.any { it.toolCall == null && it.status == TaskStepStatus.Failed }
    val thoughtCancelled = steps.any { it.toolCall == null && it.status == TaskStepStatus.Cancelled }
    val thoughtTitle = when {
        capabilityRequirement != null -> "等待连接 ${capabilityRequirement.displayName}"
        thoughtFailed -> "思考遇到问题"
        thoughtCancelled -> "思考已停止"
        thoughtRunning && hasDraft -> "正在组织回答"
        thoughtRunning -> "正在思考"
        else -> "已思考"
    }
    val thoughtDetail = when {
        capabilityRequirement != null -> capabilityRequirement.detail
        thoughtFailed -> steps.lastOrNull { it.toolCall == null && it.status == TaskStepStatus.Failed }
            ?.let { it.error ?: it.detail }
            .orEmpty()
        else -> steps.lastOrNull { it.toolCall == null && it.detail.isNotBlank() }?.detail
            ?: toolCallStatus
            ?: "已完成目标识别和回答规划"
    }
    var thoughtExpanded by remember(steps.map { it.status }, capabilityRequirement?.id) { mutableStateOf(false) }
    val thoughtCanExpand = thoughtDetail.isNotBlank() || isActive || modelParticipation != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                RoundedCornerShape(8.dp),
            ),
    ) {
        ActivitySummaryRow(
            icon = Icons.Outlined.Lightbulb,
            title = thoughtTitle,
            status = if (thoughtRunning) "进行中" else if (thoughtFailed) "失败" else "",
            active = thoughtRunning,
            expanded = thoughtExpanded,
            onClick = if (thoughtCanExpand) { { thoughtExpanded = !thoughtExpanded } } else null,
        )
        if (thoughtExpanded && thoughtCanExpand) {
            Column(
                modifier = Modifier.padding(
                    start = 42.dp,
                    top = 6.dp,
                    end = 12.dp,
                    bottom = 10.dp,
                ),
            ) {
                Text(
                    thoughtDetail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                ModelParticipationDetails(modelParticipation)
                if (isActive) {
                    Row(modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onPause) { Text("暂停") }
                        TextButton(onClick = onCancel) { Text("停止") }
                    }
                }
            }
        }
        executionSteps.forEach { step ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.09f))
            val result = step.toolCall?.id?.let(toolResultsByCallId::get)
            TaskProcessLine(
                step = step,
                result = result,
                allowWaitingResume = capabilityRequirement == null,
                onOpenDetail = { onOpenToolDetail(step, result) },
                onRetry = { onRetryStep(step.id) },
            )
        }
    }
}

@Composable
private fun ModelParticipationDetails(participation: ModelParticipation?) {
    Text(
        "参与模型",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(3.dp))
    if (participation == null) {
        Text(
            "该回答生成于模型记录功能启用前",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        return
    }
    if (participation.contributions.isEmpty()) {
        Text(
            "未调用模型，本轮由 Mason 本地逻辑完成",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        return
    }
    participation.contributions.forEach { contribution ->
        Text(
            "${modelContributionName(contribution)}：${contribution.parts.joinToString("、")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ModelParticipationFooter(contributions: List<ModelContribution>) {
    Text(
        text = modelParticipationSummary(contributions),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
}

internal fun modelParticipationSummary(contributions: List<ModelContribution>): String =
    if (contributions.isEmpty()) {
        "未调用模型，本回答由 Mason 本地逻辑完成"
    } else {
        "模型参与：" + contributions.joinToString("；") { contribution ->
            "${modelContributionName(contribution)}（${contribution.parts.joinToString("、")}）"
        }
    }

private fun modelContributionName(contribution: ModelContribution): String {
    val modelName = LocalModelCatalog.get(contribution.modelId)?.name ?: contribution.modelId
    val source = when (contribution.engineId) {
        "llama-cpp", "litert-lm" -> "本地"
        "openai-compatible" -> "远端"
        else -> ""
    }
    return if (source.isBlank()) modelName else "$modelName · $source"
}

@Composable
internal fun ActivitySummaryRow(
    icon: ImageVector,
    title: String,
    status: String,
    active: Boolean,
    expanded: Boolean,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .activityShimmer(active)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (status.isNotBlank()) {
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (onClick != null) {
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskProcessLine(
    step: TaskStep,
    result: ChatMessage?,
    allowWaitingResume: Boolean,
    onOpenDetail: () -> Unit,
    onRetry: () -> Unit,
) {
    val active = step.status == TaskStepStatus.Running
    val toolName = step.toolCall?.function?.name.orEmpty()
    val taskIconSize = when {
        step.kind == TaskStepKind.Understand -> 18.dp
        toolName.contains("memory") -> 18.dp
        toolName.startsWith("file_") || toolName in setOf("storage", "file_list") -> 17.dp
        else -> 16.dp
    }
    val canResume = step.status == TaskStepStatus.WaitingForUser && allowWaitingResume
    val canRetry = step.status == TaskStepStatus.Failed && step.retryable
    val isDocumentKnowledgeFailure = canRetry && isDocumentKnowledgeStep(step)
    val canOpenDetail = step.toolCall != null
    val canInteract = canOpenDetail || canResume || canRetry
    var showRetryConfirmation by remember(step.id, step.status) { mutableStateOf(false) }
    val statusLabel = when (step.status) {
        TaskStepStatus.Pending -> "待处理"
        TaskStepStatus.Running -> "进行中"
        TaskStepStatus.WaitingForUser -> "待确认"
        TaskStepStatus.Completed -> "完成"
        TaskStepStatus.Failed -> "失败"
        TaskStepStatus.Cancelled -> "取消"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .activityShimmer(active)
            .clickable(enabled = canInteract) {
                when {
                    isDocumentKnowledgeFailure -> showRetryConfirmation = true
                    canResume || (canRetry && !canOpenDetail) -> onRetry()
                    canOpenDetail -> onOpenDetail()
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    taskStepIcon(step),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(taskIconSize),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                displayTaskStepTitle(step),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.width(52.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    statusLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (canInteract) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "查看执行详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.width(24.dp))
            }
        }
    }

    if (showRetryConfirmation) {
        AlertDialog(
            onDismissRequest = { showRetryConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("确认重试") },
            text = { Text("文档知识处理失败，是否重新尝试？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRetryConfirmation = false
                        onRetry()
                    },
                ) {
                    Text("重试", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetryConfirmation = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

private fun isDocumentKnowledgeStep(step: TaskStep): Boolean {
    val searchable = buildString {
        append(step.title)
        append('\n')
        append(step.detail)
        append('\n')
        append(step.error.orEmpty())
        append('\n')
        append(step.toolCall?.function?.name.orEmpty())
        append('\n')
        append(step.toolCall?.function?.arguments.orEmpty())
    }.lowercase(Locale.ROOT)
    return listOf("文档", "文本", "知识", "document", "text", "knowledge", "file_read")
        .any(searchable::contains)
}

@Composable
internal fun Modifier.activityShimmer(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "activity_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "activity_shimmer_progress",
    )
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    return drawWithContent {
        drawContent()
        val bandWidth = 64.dp.toPx()
        val startX = -bandWidth + (size.width + bandWidth * 2f) * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(startX, 0f),
                end = Offset(startX + bandWidth, size.height),
            ),
        )
    }
}

@Composable
private fun taskStepIcon(step: TaskStep): ImageVector {
    val toolName = step.toolCall?.function?.name.orEmpty()
    if (toolName.isNotBlank()) return toolIcon(toolName)
    return when (step.kind) {
        TaskStepKind.Understand -> Icons.Outlined.Lightbulb
        TaskStepKind.PrepareInputs -> Icons.Outlined.AttachFile
        TaskStepKind.Model -> Icons.Outlined.Description
        TaskStepKind.Skill -> Icons.Outlined.Extension
        TaskStepKind.Tool -> Icons.Outlined.Terminal
        TaskStepKind.Review -> Icons.Outlined.CheckCircle
        TaskStepKind.Deliver -> ImageVector.vectorResource(R.drawable.ic_layers)
    }
}

private fun toolIcon(toolName: String): ImageVector = when {
    toolName == "run_shell" -> Icons.Outlined.Terminal
    toolName in setOf("http_request", "dns_lookup", "network_info", "get_wifi_info", "geocoding") ->
        Icons.Outlined.Language
    toolName.startsWith("file_") -> Icons.Outlined.Description
    toolName in setOf("camera", "screenshot", "audio_record") -> Icons.Outlined.ImageIcon
    toolName.contains("memory") -> Icons.Outlined.Memory
    toolName in setOf("calendar", "alarm") -> Icons.Outlined.Timer
    toolName in setOf("contacts", "call_log", "sms", "notification") -> Icons.Outlined.Forum
    toolName in setOf("storage", "file_list") -> Icons.Outlined.Folder
    toolName in setOf("launch_app", "app_manager", "system_setting", "battery_opt") ->
        Icons.Outlined.Settings
    toolName in setOf("observe", "click_node", "tap", "swipe", "scroll", "set_text", "global_action") ->
        Icons.Outlined.Visibility
    else -> Icons.Outlined.Terminal
}

private fun taskStepMeta(step: TaskStep): String? {
    val parts = mutableListOf<String>()
    if (step.attempt > 1) parts += "第 ${step.attempt} 次尝试"
    val startedAt = step.startedAt
    val finishedAt = step.finishedAt
    if (startedAt != null && finishedAt != null && finishedAt >= startedAt) {
        val durationMs = finishedAt - startedAt
        parts += if (durationMs < 1_000L) {
            "${durationMs}ms"
        } else {
            "%.1f 秒".format(Locale.US, durationMs / 1_000.0)
        }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

private fun displayTaskStepTitle(step: TaskStep): String {
    val toolName = step.toolCall?.function?.name ?: return step.title
    return when (toolName) {
        "get_battery_info" -> "读取电池信息"
        "get_device_info" -> "读取设备信息"
        "get_cpu_info" -> "读取处理器信息"
        "get_gpu_info" -> "读取图形处理器信息"
        "get_memory_info" -> "读取内存信息"
        "get_wifi_info" -> "读取 Wi-Fi 信息"
        "get_bluetooth_info" -> "读取蓝牙信息"
        "get_sensor_info" -> "读取传感器信息"
        "network_info" -> "读取网络信息"
        "storage" -> "读取存储信息"
        "file_list" -> "查看文件"
        "file_read" -> "读取文件"
        "file_write" -> "写入文件"
        "file_delete" -> "删除文件"
        "http_request" -> "访问网络"
        "dns_lookup" -> "查询域名"
        "launch_app" -> "打开应用"
        "app_manager" -> "管理应用"
        "system_setting" -> "修改系统设置"
        "battery_opt" -> "调整电池优化"
        "audio_record" -> "录制音频"
        "call_log" -> "读取通话记录"
        "calendar" -> "处理日历"
        "alarm" -> "处理闹钟"
        "clipboard" -> "读取剪贴板"
        "camera" -> "使用相机"
        "contacts" -> "读取联系人"
        "location" -> "读取位置"
        "geocoding" -> "查询地点"
        "notification" -> "发送通知"
        "sms" -> "处理短信"
        "screenshot" -> "截取屏幕"
        "run_shell" -> "执行命令"
        "process" -> "查看进程"
        "hotspot" -> "管理热点"
        "skill__activate" -> "运行 Skill"
        else -> step.title
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolExecutionDetailSheet(
    detail: ToolExecutionDetail,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val detailScrollState = rememberScrollState()
    val detailEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = LocalInterfaceEffects.current.progressiveEdgeBlurEnabled,
    )
    val requestText = remember(detail.step.toolCall) { formatToolRequest(detail.step) }
    val responseText = remember(detail.result?.content, detail.step.error) {
        redactSensitiveToolText(detail.result?.content ?: detail.step.error ?: "暂无返回内容")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .masonSheetSurface()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 2.dp,
                bottomPadding = 0.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTaskStepTitle(detail.step),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(toolStepStatusLabel(detail.step.status), taskStepMeta(detail.step))
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureProgressiveEdgeBlur(detailEdgeBlurState)
                        .verticalScroll(detailScrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ExecutionDetailSection(
                        label = "请求",
                        content = requestText,
                        onCopy = { clipboard.setText(AnnotatedString(requestText)) },
                    )
                    ExecutionDetailSection(
                        label = "响应",
                        content = responseText,
                        onCopy = { clipboard.setText(AnnotatedString(responseText)) },
                    )
                }
                ChatSheetEdgeFades(
                    scrollState = detailScrollState,
                    blurState = detailEdgeBlurState,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                )
            }
            if (detail.step.status == TaskStepStatus.Failed && detail.step.retryable) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重试")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ExecutionDetailSection(
    label: String,
    content: String,
    onCopy: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制$label",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ExecutionDetailContent(content)
    }
}

@Composable
private fun ExecutionDetailContent(content: String) {
    Text(
        text = content,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(14.dp),
    )
}

private fun toolStepStatusLabel(status: TaskStepStatus): String = when (status) {
    TaskStepStatus.Pending -> "待处理"
    TaskStepStatus.Running -> "进行中"
    TaskStepStatus.WaitingForUser -> "待确认"
    TaskStepStatus.Completed -> "已完成"
    TaskStepStatus.Failed -> "执行失败"
    TaskStepStatus.Cancelled -> "已停止"
}

private fun formatToolRequest(step: TaskStep): String {
    val call = step.toolCall ?: return step.detail
    val arguments = call.function.arguments.ifBlank { return "${call.function.name}\n无参数" }
    val formattedArguments = runCatching {
        val parsed = TOOL_DETAIL_JSON.parseToJsonElement(arguments)
        TOOL_DETAIL_JSON.encodeToString(JsonElement.serializer(), redactSensitiveToolJson(parsed))
    }.getOrElse { redactSensitiveToolText(arguments) }
    return "${call.function.name}\n\n$formattedArguments"
}

private fun redactSensitiveToolJson(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (key, value) ->
            if (isSensitiveToolKey(key)) JsonPrimitive("••••••••") else redactSensitiveToolJson(value)
        },
    )
    is JsonArray -> JsonArray(element.map(::redactSensitiveToolJson))
    else -> element
}

private fun isSensitiveToolKey(key: String): Boolean {
    val normalized = key.lowercase(Locale.US).replace("-", "_")
    return normalized.contains("password") ||
        normalized.contains("secret") ||
        normalized.contains("token") ||
        normalized.contains("api_key") ||
        normalized.contains("apikey") ||
        normalized.contains("authorization")
}

private fun redactSensitiveToolText(text: String): String {
    val keyedSecret = Regex(
        pattern = "(?i)(api[_-]?key|token|password|secret|authorization)(\\s*[:=]\\s*)([^\\s,;]+)",
    )
    val bearerSecret = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+")
    return text
        .replace(keyedSecret) { match -> "${match.groupValues[1]}${match.groupValues[2]}••••••••" }
        .replace(bearerSecret, "Bearer ••••••••")
}

@Composable
private fun ExpandableMessageContent(
    content: String,
    isStreaming: Boolean,
    contentColor: Color,
) {
    FormattedMessageText(
        content = content + if (isStreaming) "\n|" else "",
        contentColor = contentColor,
        actionColor = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AssistantAnswerCard(
    message: ChatMessage,
    isStreaming: Boolean = false,
    processingMs: Long? = null,
    onRetry: () -> Unit = {},
    onOpenCapabilityConnections: () -> Unit = {},
    showModelFooter: Boolean = true,
) {
    val rawContent = message.content.orEmpty()
    val artifacts = remember(rawContent) { extractArtifactMetadata(rawContent) }
    val capabilityRequirement = remember(rawContent) { extractCapabilityRequirementMarker(rawContent) }
    val modelParticipation = remember(rawContent) { extractModelParticipation(rawContent) }
    val content = remember(rawContent) {
        stripTaskRunMarkers(
            stripArtifactMarkers(
                stripCapabilityRequirementMarkers(stripModelParticipationMarkers(rawContent)),
            ),
        )
            .let { visible -> visible.substringAfter("</think>", visible).trim() }
    }
    val isStopped = remember(content) { content.contains("已停止生成") }
    val sections = remember(content, isStreaming) { parseAnswerSections(content, isStreaming) }
    val references = remember(content) { extractReferenceUrls(content) }
    val outputs = remember(content) { extractOutputMentions(content) }
    var showActions by remember(message.timestamp, content) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (!isStreaming && content.isNotBlank()) showActions = true },
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (isStreaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Mason",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        isStopped -> "已停止"
                        capabilityRequirement != null -> "待连接"
                        else -> "进行中"
                    },
                    color = if (isStopped) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (processingMs != null) {
                    Spacer(Modifier.width(8.dp))
                    ProcessingTimePill(processingMs)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            AnswerSectionBlock(
                text = section.text,
                isStreaming = isStreaming && index == sections.lastIndex,
            )
        }

        if (outputs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OutputMentionStrip(outputs)
        }

        if (artifacts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ArtifactMentionStrip(artifacts)
        }

        if (references.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ReferenceStrip(references)
        }

        capabilityRequirement?.let { requirement ->
            Spacer(Modifier.height(10.dp))
            CapabilityRequirementBlock(requirement, onOpenCapabilityConnections)
        }

        if (showModelFooter && modelParticipation != null) {
            Spacer(Modifier.height(8.dp))
            ModelParticipationFooter(modelParticipation.contributions)
        }

    }

    if (showActions) {
        AssistantActionSheet(
            content = content,
            onRetry = onRetry,
            onDismiss = { showActions = false },
        )
    }
}

@Composable
private fun AnswerSectionBlock(
    text: String,
    isStreaming: Boolean,
) {
    ExpandableMessageContent(
        content = text,
        isStreaming = isStreaming,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CapabilityRequirementBlock(
    requirement: CapabilityRequirement,
    onOpenConnections: () -> Unit,
) {
    val status = when (requirement.status) {
        CapabilityRequirementStatus.NotInstalled -> "未安装"
        CapabilityRequirementStatus.NeedsAuthorization -> "待授权"
        CapabilityRequirementStatus.WaitingForOfficialAccess -> "等待官方接入"
        CapabilityRequirementStatus.NeedsConnection -> "需要连接"
        CapabilityRequirementStatus.Unavailable -> "当前不可用"
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                requirement.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$status · ${requirement.capabilities.joinToString("、")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOpenConnections) {
            Text(if (requirement.status == CapabilityRequirementStatus.NeedsAuthorization) "去授权" else "查看")
        }
    }
}

@Composable
private fun ToolResultWorkCard(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            ) {
            }
            Spacer(Modifier.width(8.dp))
            Text(
                message.name?.let { "工具结果 · $it" } ?: "工具结果",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "进行中",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))
        AnswerSectionBlock(
            text = message.content.orEmpty().ifBlank { "工具没有返回可展示内容" },
            isStreaming = false,
        )
    }
}

@Composable
private fun AutomationDraftCard(
    draft: AutomationDraft,
    applied: Boolean,
    onApply: (AutomationDraft, Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    var detailsExpanded by remember(draft.draftId, applied) { mutableStateOf(!applied) }
    val operationTitle = when (draft.operation) {
        AutomationDraftService.OPERATION_UPDATE -> "更新自动化"
        AutomationDraftService.OPERATION_ENABLE -> "恢复自动化"
        AutomationDraftService.OPERATION_DISABLE -> "暂停自动化"
        AutomationDraftService.OPERATION_ARCHIVE -> "删除自动化"
        else -> "自动化草稿"
    }
    val triggerLabel = remember(draft.triggerType, draft.triggerValue) {
        when (draft.triggerType) {
            "manual" -> "手动运行"
            "interval" -> "每 ${draft.triggerValue} 分钟"
            "daily" -> "每天 ${draft.triggerValue}"
            "weekdays" -> com.denggl2.mason.automation.AutomationScheduler.describeWeekdays(draft.triggerValue)
            "charging" -> "接上充电器时"
            "wifi" -> "连接 WiFi：${draft.triggerValue}"
            "bluetooth" -> "连接蓝牙：${draft.triggerValue}"
            "notification" -> "收到通知：${draft.triggerValue}"
            "location" -> "进入指定位置（约每 15 分钟检查）"
            else -> draft.triggerValue
        }
    }
    val conditionLabel = remember(draft.constraints) {
        buildList {
            when (draft.constraints.network) {
                "connected" -> add("需要联网")
                "unmetered" -> add("仅非计费网络")
            }
            if (draft.constraints.requiresCharging) add("充电时")
            if (draft.constraints.requiresBatteryNotLow) add("电量充足")
        }.ifEmpty { listOf("无额外条件") }.joinToString(" · ")
    }
    val readsCalendar = draft.arguments["context_tool"] == "calendar"
    val actionLabel = remember(draft.summary, readsCalendar) {
        val base = draft.summary.substringAfter('；', draft.summary)
        if (readsCalendar) "读取今日日历 -> $base" else base
    }
    val safetyLabel = if (readsCalendar) {
        "运行时读取今日日历，需要日历读取权限；确认前不会创建或运行"
    } else {
        "确认前不会创建或运行；后台运行受设置总开关控制"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                operationTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (applied) "已创建" else "待确认",
                color = if (applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(draft.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (applied && !detailsExpanded) {
            Spacer(Modifier.height(5.dp))
            Text(
                "查看自动化详情",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.clickable { detailsExpanded = true },
            )
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        AutomationDraftField("运行时间", triggerLabel)
        AutomationDraftField("执行内容", actionLabel)
        AutomationDraftField("执行条件", conditionLabel)
        AutomationDraftField("安全说明", safetyLabel)
        if (draft.warnings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            draft.warnings.forEach { warning ->
                Text(
                    warning,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Text(
                "去授权",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenPermissions)
                    .padding(top = 4.dp),
            )
        }
        if (draft.actions.size > 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "执行步骤",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            draft.actions.forEachIndexed { index, action ->
                Text(
                    "${index + 1}. ${action.title.ifBlank { action.type }}",
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                )
            }
        }
        if (!applied) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (draft.operation == AutomationDraftService.OPERATION_CREATE ||
                    draft.operation == AutomationDraftService.OPERATION_UPDATE
                ) {
                    TextButton(onClick = { onApply(draft, false) }) {
                        Text(if (draft.operation == AutomationDraftService.OPERATION_CREATE) "仅创建" else "确认更新")
                    }
                    TextButton(onClick = { onApply(draft, true) }) {
                        Text(if (draft.operation == AutomationDraftService.OPERATION_CREATE) "创建并测试" else "更新并测试")
                    }
                } else {
                    TextButton(onClick = { onApply(draft, false) }) {
                        Text("确认$operationTitle")
                    }
                }
            }
        } else {
            Text(
                "收起详情",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { detailsExpanded = false }
                    .padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AutomationDraftField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(68.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun AutomationApplyResultCard(result: AutomationApplyResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (result.status == "success") Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (result.status == "success") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (result.status == "success") "自动化已保存" else "自动化测试未通过",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(result.message, fontSize = 12.sp, lineHeight = 17.sp)
        if (result.scheduleActive) {
            Spacer(Modifier.height(4.dp))
            Text("定时调度已生效", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

private fun parseAnswerSections(content: String, isStreaming: Boolean): List<AnswerSection> {
    if (content.isBlank()) {
        return listOf(
            AnswerSection(
                label = "回复",
                text = if (isStreaming) "正在组织回复..." else "暂无内容",
            ),
        )
    }

    val labels = setOf("思考", "进行中", "引导", "最终总结", "最后总结")
    val visibleText = content.lines().joinToString("\n") { rawLine ->
        detectAnswerLabel(rawLine.trim(), labels)?.second ?: rawLine
    }.trim()
    return listOf(AnswerSection("回复", visibleText.ifBlank { if (isStreaming) "正在组织回复..." else "暂无内容" }))
}

private fun detectAnswerLabel(line: String, labels: Set<String>): Pair<String, String>? {
    val normalized = line
        .removePrefix("#")
        .removePrefix("#")
        .removePrefix("#")
        .trim()
        .removePrefix("[")
        .removePrefix("**")

    labels.forEach { label ->
        val candidates = listOf("$label]", "$label：", "$label:", "$label -", "$label ")
        candidates.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return label to normalized.removePrefix(prefix).trim().removePrefix("**").trim()
        }
        if (normalized == label) return label to ""
    }
    return null
}

private fun isVisibleToolPresentation(message: ChatMessage): Boolean {
    val content = message.content.orEmpty()
    return AutomationDraftService.extractAutomationDraftMarker(content) != null ||
        AutomationDraftService.extractAutomationApplyMarker(content) != null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    processingMs: Long? = null,
    onRetry: () -> Unit = {},
    onResendUserMessage: (String) -> Unit = {},
    automationApplied: Boolean = false,
    onApplyAutomationDraft: (AutomationDraft, Boolean) -> Unit = { _, _ -> },
    onOpenAutomationPermissions: () -> Unit = {},
    onOpenCapabilityConnections: () -> Unit = {},
    showModelFooter: Boolean = true,
) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    if (isTool) {
        AutomationDraftService.extractAutomationDraftMarker(message.content.orEmpty())?.let { draft ->
            AutomationDraftCard(
                draft,
                automationApplied,
                onApplyAutomationDraft,
                onOpenAutomationPermissions,
            )
            return
        }
        AutomationDraftService.extractAutomationApplyMarker(message.content.orEmpty())?.let { result ->
            AutomationApplyResultCard(result)
            return
        }
        ToolResultWorkCard(message)
        return
    }
    if (!isUser && !isTool) {
        AssistantAnswerCard(
            message = message,
            isStreaming = isStreaming,
            processingMs = processingMs,
            onRetry = onRetry,
            onOpenCapabilityConnections = onOpenCapabilityConnections,
            showModelFooter = showModelFooter,
        )
        return
    }
    val presentation = remember(message.content) {
        parseUserMessagePresentation(message.content.orEmpty())
    }
    var showUserActions by remember(message.timestamp, message.content) { mutableStateOf(false) }

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        isTool -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                Avatar(label = if (isTool) "T" else "M", isTool = isTool)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .then(if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth(0.86f))
                    .background(
                        color = bubbleColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showUserActions = true },
                    )
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Column {
                    if (isTool) {
                        Text(
                            text = message.name?.let { "工具结果 · $it" } ?: "工具结果",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message.content ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    } else {
                        UserMessageContent(
                            presentation = presentation,
                            contentColor = contentColor,
                        )
                    }
                }
            }

        }
    }
    if (showUserActions) {
        UserMessageActionSheet(
            copyContent = presentation.body.ifBlank { message.content.orEmpty() },
            onResend = { onResendUserMessage(message.content.orEmpty()) },
            onDismiss = { showUserActions = false },
        )
    }
}

@Composable
private fun Avatar(
    label: String,
    isTool: Boolean,
    isUser: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                when {
                    isUser -> MaterialTheme.colorScheme.onSurfaceVariant
                    isTool -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isUser) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun UserMessageContent(
    presentation: UserMessagePresentation,
    contentColor: Color,
) {
    val hasContext = presentation.skill != null || presentation.attachments.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (presentation.body.isNotBlank()) {
            Text(
                text = presentation.body,
                color = contentColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        if (presentation.body.isNotBlank() && hasContext) {
            HorizontalDivider(color = contentColor.copy(alpha = 0.10f))
        }
        presentation.skill?.let { skill ->
            UserContextRow(
                icon = Icons.Outlined.Extension,
                label = "Skill · ${skill.name}",
                contentColor = contentColor,
            )
        }
        presentation.attachments.forEach { attachment ->
            UserContextRow(
                icon = if (attachment.kind == AttachmentKind.Image) Icons.Outlined.ImageIcon else Icons.Outlined.Description,
                label = "${attachment.kind.label} · ${attachment.name}",
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun UserContextRow(
    icon: ImageVector,
    label: String,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.66f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = contentColor.copy(alpha = 0.72f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessingTimePill(processingMs: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "用时 ${formatDuration(processingMs)}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserMessageActionSheet(
    copyContent: String,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 4.dp,
                bottomPadding = 2.dp,
            )
            AssistantActionSheetRow(
                icon = Icons.Outlined.ContentCopy,
                label = "复制消息",
                onClick = {
                    clipboard.setText(AnnotatedString(copyContent))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )
            AssistantActionSheetRow(
                icon = Icons.Outlined.Refresh,
                label = "重发",
                onClick = {
                    onDismiss()
                    onResend()
                },
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantActionSheet(
    content: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 4.dp,
                bottomPadding = 2.dp,
            )
            AssistantActionSheetRow(
                icon = Icons.Outlined.ContentCopy,
                label = "复制回答",
                onClick = {
                    clipboard.setText(AnnotatedString(content))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )
            AssistantActionSheetRow(
                icon = Icons.Outlined.Refresh,
                label = "重新生成",
                onClick = {
                    onDismiss()
                    onRetry()
                },
            )
            AssistantActionSheetRow(
                icon = Icons.Outlined.Share,
                label = "分享回答",
                onClick = {
                    onDismiss()
                    shareText(context, content)
                },
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AssistantActionSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
internal fun FormattedMessageText(
    content: String,
    contentColor: Color,
    actionColor: Color,
) {
    val blocks = remember(content) { parseMessageBlocks(content) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block.kind) {
                MessageBlockKind.Heading -> InlineMarkdownText(
                    text = block.text,
                    color = contentColor,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                MessageBlockKind.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                MessageBlockKind.Bullet -> LabeledTextLine(
                    label = "•",
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Numbered -> LabeledTextLine(
                    label = block.meta ?: "1.",
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Code -> CodeBlock(
                    code = block.text,
                    language = block.meta,
                    contentColor = contentColor,
                    actionColor = actionColor,
                )
                MessageBlockKind.Quote -> QuoteBlock(
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Divider -> HorizontalDivider(
                    color = contentColor.copy(alpha = 0.16f),
                )
                MessageBlockKind.Table -> block.table?.let { table ->
                    MarkdownTableBlock(
                        table = table,
                        contentColor = contentColor,
                        actionColor = actionColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MessageTable,
    contentColor: Color,
    actionColor: Color,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showFullScreen by remember(table) { mutableStateOf(false) }
    val copyTable = {
        clipboard.setText(AnnotatedString(messageTableToTsv(table)))
        Toast.makeText(context, "表格已复制", Toast.LENGTH_SHORT).show()
    }
    val downloadTable = {
        val savedName = saveMessageTableCsv(context, table)
        Toast.makeText(
            context,
            savedName?.let { "已下载 $it" } ?: "表格下载失败",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.045f))
            .border(1.dp, contentColor.copy(alpha = 0.11f), RoundedCornerShape(8.dp)),
    ) {
        TableToolbar(
            actionColor = actionColor,
            onExpand = { showFullScreen = true },
            onCopy = copyTable,
            onDownload = downloadTable,
        )
        HorizontalDivider(color = contentColor.copy(alpha = 0.10f))
        MessageTableGrid(table = table, contentColor = contentColor)
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showFullScreen = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭表格")
                    }
                    Text(
                        "表格",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = copyTable) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制表格")
                    }
                    IconButton(onClick = downloadTable) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "下载表格")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    MessageTableGrid(
                        table = table,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableToolbar(
    actionColor: Color,
    onExpand: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.TableChart,
            contentDescription = null,
            tint = actionColor,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "表格",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExpand, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.OpenInFull, contentDescription = "全屏查看", tint = actionColor, modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制表格", tint = actionColor, modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.FileDownload, contentDescription = "下载表格", tint = actionColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MessageTableGrid(
    table: MessageTable,
    contentColor: Color,
) {
    val columnWidths = remember(table) {
        table.headers.indices.map { column ->
            val longest = (listOf(table.headers[column]) + table.rows.map { it.getOrNull(column).orEmpty() })
                .maxOfOrNull(String::length)
                ?: 0
            (longest.coerceIn(8, 24) * 7 + 24).dp
        }
    }
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        MessageTableRow(
            cells = table.headers,
            columnWidths = columnWidths,
            contentColor = contentColor,
            header = true,
        )
        table.rows.forEach { row ->
            HorizontalDivider(color = contentColor.copy(alpha = 0.08f))
            MessageTableRow(
                cells = row,
                columnWidths = columnWidths,
                contentColor = contentColor,
                header = false,
            )
        }
    }
}

@Composable
private fun MessageTableRow(
    cells: List<String>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    contentColor: Color,
    header: Boolean,
) {
    Row(
        modifier = Modifier.background(
            if (header) contentColor.copy(alpha = 0.055f) else Color.Transparent,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        columnWidths.forEachIndexed { index, width ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .heightIn(min = 42.dp)
                        .background(contentColor.copy(alpha = 0.08f)),
                )
            }
            InlineMarkdownText(
                text = cells.getOrNull(index).orEmpty(),
                color = contentColor,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = if (header) FontWeight.SemiBold else null,
                modifier = Modifier
                    .width(width)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

private fun saveMessageTableCsv(context: Context, table: MessageTable): String? {
    val displayName = "mason_table_${System.currentTimeMillis()}.csv"
    val csv = "\uFEFF" + messageTableToCsv(table)
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Mason")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入下载文件")
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mason")
            if (!directory.exists() && !directory.mkdirs()) error("无法创建下载目录")
            File(directory, displayName).writeText(csv, Charsets.UTF_8)
        }
        displayName
    }.getOrNull()
}

@Composable
private fun LabeledTextLine(
    label: String,
    text: String,
    contentColor: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = contentColor.copy(alpha = 0.76f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.width(24.dp),
        )
        InlineMarkdownText(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeBlock(
    code: String,
    language: String?,
    contentColor: Color,
    actionColor: Color,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.07f))
            .border(1.dp, contentColor.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.ifBlank { null } ?: "代码",
                color = contentColor.copy(alpha = 0.70f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = actionColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Text(
            text = code,
            color = contentColor,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun QuoteBlock(
    text: String,
    contentColor: Color,
) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.28f)),
        )
        Spacer(Modifier.width(9.dp))
        InlineMarkdownText(
            text = text,
            color = contentColor.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val annotated = remember(text, color) { formatInlineMarkdown(text, color) }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

private fun formatInlineMarkdown(text: String, contentColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val markers = listOf("**", "__", "`")
            .mapNotNull { marker ->
                text.indexOf(marker, cursor).takeIf { it >= 0 }?.let { it to marker }
            }
        val next = markers.minByOrNull { it.first }
        if (next == null) {
            append(text.substring(cursor))
            break
        }

        val (start, marker) = next
        if (start > cursor) append(text.substring(cursor, start))
        val end = text.indexOf(marker, start + marker.length)
        if (end < 0 || end == start + marker.length) {
            append(text.substring(start))
            break
        }

        val value = text.substring(start + marker.length, end)
        val style = if (marker == "`") {
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = contentColor.copy(alpha = 0.08f),
            )
        } else {
            SpanStyle(fontWeight = FontWeight.Bold)
        }
        withStyle(style) { append(value) }
        cursor = end + marker.length
    }
}

@Composable
private fun OutputMentionStrip(outputs: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(outputs) { output ->
            InfoChip(
                icon = Icons.Outlined.Description,
                label = output.substringAfterLast('/').substringAfterLast('\\').take(28),
                detail = "产出",
            )
        }
    }
}

@Composable
internal fun ArtifactMentionStrip(artifacts: List<ArtifactMetadata>) {
    val context = LocalContext.current
    var previewArtifact by remember { mutableStateOf<ArtifactMetadata?>(null) }
    Column {
        artifacts.forEachIndexed { index, artifact ->
            if (index > 0) {
                if (
                    isPreviewableImageArtifact(
                        artifact,
                        platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    ) ||
                    isPreviewableImageArtifact(
                        artifacts[index - 1],
                        platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                ) {
                    Spacer(Modifier.height(12.dp))
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                }
            }
            if (
                isPreviewableImageArtifact(
                    artifact,
                    platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
            ) {
                ArtifactImageThumbnail(
                    artifact = artifact,
                    onClick = { previewArtifact = artifact },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            artifact.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "产出 · ${formatArtifactSize(artifact.bytes)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    MessageActionButton(
                        icon = Icons.Outlined.Visibility,
                        contentDescription = "预览产出",
                        onClick = { previewArtifact = artifact },
                    )
                    MessageActionButton(
                        icon = Icons.Outlined.FileDownload,
                        contentDescription = "打开产出",
                        onClick = { openArtifact(context, artifact, edit = false) },
                    )
                    MessageActionButton(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "编辑产出",
                        onClick = { openArtifact(context, artifact, edit = true) },
                    )
                    MessageActionButton(
                        icon = Icons.Outlined.Share,
                        contentDescription = "分享产出",
                        onClick = { shareArtifact(context, artifact) },
                    )
                }
            }
        }
    }

    previewArtifact?.let { artifact ->
        if (
            isPreviewableImageArtifact(
                artifact,
                platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        ) {
            ArtifactImagePreviewDialog(
                artifact = artifact,
                onDismiss = { previewArtifact = null },
                onShare = { shareArtifact(context, artifact) },
            )
        } else {
            ArtifactPreviewDialog(
                artifact = artifact,
                onDismiss = { previewArtifact = null },
                onOpen = { openArtifact(context, artifact, edit = false) },
                onEdit = { openArtifact(context, artifact, edit = true) },
                onShare = { shareArtifact(context, artifact) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtifactPreviewDialog(
    artifact: ArtifactMetadata,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val previewText = remember(artifact.path, artifact.bytes) {
        buildArtifactPreviewText(artifact)
    }
    val previewScrollState = rememberScrollState()
    val previewEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = LocalInterfaceEffects.current.progressiveEdgeBlurEnabled,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 2.dp,
                bottomPadding = 0.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        artifact.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${artifact.mimeType} · ${formatArtifactSize(artifact.bytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭预览",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureProgressiveEdgeBlur(previewEdgeBlurState)
                        .verticalScroll(previewScrollState)
                        .padding(12.dp),
                ) {
                    Text(
                        previewText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                ChatSheetEdgeFades(
                    scrollState = previewScrollState,
                    blurState = previewEdgeBlurState,
                    surfaceColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
            Row(modifier = Modifier.fillMaxWidth()) {
                ArtifactSheetAction(
                    icon = Icons.Outlined.FileDownload,
                    label = "打开",
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
                ArtifactSheetAction(
                    icon = Icons.Outlined.Edit,
                    label = "编辑",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
                ArtifactSheetAction(
                    icon = Icons.Outlined.Share,
                    label = "分享",
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ArtifactSheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReferenceStrip(references: List<String>) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(references) { index, reference ->
            Box(
                modifier = Modifier.clickable { openUrl(context, reference) },
            ) {
                InfoChip(
                    icon = Icons.Outlined.CheckCircle,
                    label = "来源 ${index + 1}",
                    detail = Uri.parse(reference).host ?: "链接",
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun InputContextStrip(
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
) {
    val listState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 2.dp, end = 22.dp),
        ) {
            selectedSkill?.let { skill ->
                item {
                    SkillContextChip(
                        skill = skill,
                        onClear = onClearSkill,
                    )
                }
            }
            itemsIndexed(attachments) { index, attachment ->
                AttachmentContextChip(
                    attachment = attachment,
                    onRemove = { onRemoveAttachment(index) },
                )
            }
        }
        if (listState.canScrollBackward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(26.dp)
                    .height(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent),
                        ),
                    ),
            )
        }
        if (listState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(26.dp)
                    .height(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun SkillContextChip(
    skill: SkillOption,
    onClear: () -> Unit,
) {
    val chipShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), chipShape)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            skill.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 176.dp),
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除 Skill",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun AttachmentContextChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    val chipShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), chipShape)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (attachment.kind == AttachmentKind.Image) Icons.Outlined.ImageIcon else Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            attachment.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 154.dp),
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除附件",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onUseSkill: () -> Unit,
    apiWarning: String?,
    pendingApproval: ToolApprovalRequest?,
    onOpenApprovalDetails: () -> Unit,
    onApproveOnce: () -> Unit,
    onOpenSettings: () -> Unit,
    modelSwitchModels: List<AiModelPreset>,
    currentModelId: String,
    currentProviderName: String,
    canSendToModel: Boolean,
    onSelectModel: (String) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
) {
    val panelShape = RoundedCornerShape(18.dp)
    val active = enabled && canSendToModel && (
        text.isNotBlank() ||
            attachments.isNotEmpty() ||
            selectedSkill != null
    )
    var addMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var restoreFocusAfterExpansion by remember { mutableStateOf(false) }
    var inputWasFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val inputKeyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(expanded, restoreFocusAfterExpansion) {
        if (expanded && restoreFocusAfterExpansion) {
            withFrameNanos { }
            inputFocusRequester.requestFocus()
            inputKeyboardController?.show()
            restoreFocusAfterExpansion = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (pendingApproval != null) {
            RiskApprovalPill(
                approval = pendingApproval,
                onOpenDetails = onOpenApprovalDetails,
                onApproveOnce = onApproveOnce,
            )
            Spacer(Modifier.height(7.dp))
        }
        if (apiWarning != null) {
            ApiAttentionPill(
                message = apiWarning,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.height(7.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .masonGlassShadow(cornerRadius = 18.dp)
                .clip(panelShape)
                .animateContentSize(animationSpec = tween(durationMillis = 180)),
        ) {
            ChatGlassMaterial(
                shape = panelShape,
                cornerRadius = 18.dp,
                role = ChatSurfaceRole.Large,
                blur = ChatBackdropBlur.Soft,
                refraction = true,
                blurredAlpha = 0.80f,
                fallbackAlpha = 0.99f,
            )
            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
            if (expanded && (attachments.isNotEmpty() || selectedSkill != null)) {
                InputContextStrip(
                    attachments = attachments,
                    selectedSkill = selectedSkill,
                    onRemoveAttachment = onRemoveAttachment,
                    onClearSkill = onClearSkill,
                )
                Spacer(Modifier.height(7.dp))
            }

            @Composable
            fun AddButton() {
                Box {
                    ComposerIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "添加",
                        enabled = enabled,
                        selected = addMenuExpanded,
                        onClick = {
                            if (addMenuExpanded) {
                                addMenuExpanded = false
                            } else {
                                onExpandedChange(true)
                                addMenuExpanded = true
                            }
                        },
                    )
                    ChatGlassDropdown(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                        width = 180.dp,
                        cornerRadius = 14.dp,
                        alignEnd = false,
                    ) {
                        AttachmentMenuRow(
                            label = "添加图片",
                            onClick = {
                                addMenuExpanded = false
                                onAddImage()
                            },
                        )
                        AttachmentMenuRow(
                            label = "添加文件",
                            onClick = {
                                addMenuExpanded = false
                                onAddFile()
                            },
                        )
                        AttachmentMenuRow(
                            label = "使用 Skill",
                            onClick = {
                                addMenuExpanded = false
                                onUseSkill()
                            },
                        )
                    }
                }
            }

            @Composable
            fun TextInput(modifier: Modifier, isExpanded: Boolean) {
                val textIsBlank = text.isBlank()
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    modifier = modifier
                        .heightIn(min = if (isExpanded) 52.dp else 31.dp)
                        .then(
                            if (isExpanded && textIsBlank) {
                                Modifier.heightIn(max = 76.dp)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 5.dp)
                        .focusRequester(inputFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                inputWasFocused = true
                                if (!isExpanded) {
                                    restoreFocusAfterExpansion = true
                                    onExpandedChange(true)
                                }
                            } else if (inputWasFocused) {
                                inputWasFocused = false
                                if (isExpanded && !restoreFocusAfterExpansion) {
                                    onExpandedChange(false)
                                }
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = !isExpanded,
                    maxLines = if (!isExpanded) 1 else if (textIsBlank) 3 else Int.MAX_VALUE,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            if (text.isBlank()) {
                                Text(
                                    "跟MASON聊聊",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            if (expanded) {
                TextInput(
                    modifier = Modifier.fillMaxWidth(),
                    isExpanded = true,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddButton()

                    Spacer(Modifier.weight(1f))

                    if (modelSwitchModels.size > 1) {
                        ModelModeSwitcher(
                            models = modelSwitchModels,
                            currentModelId = currentModelId,
                            providerName = currentProviderName,
                            expanded = modelMenuExpanded,
                            onExpandedChange = { modelMenuExpanded = it },
                            onSelect = { modelId ->
                                modelMenuExpanded = false
                                onSelectModel(modelId)
                            },
                        )
                        Spacer(Modifier.width(7.dp))
                    }

                    ComposerSendButton(
                        action = composerPrimaryAction(
                            isGenerating = isGenerating,
                            hasSendableInput = active,
                        ),
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddButton()
                    Spacer(Modifier.width(4.dp))
                    TextInput(
                        modifier = Modifier.weight(1f),
                        isExpanded = false,
                    )
                    Spacer(Modifier.width(7.dp))

                    ComposerSendButton(
                        action = composerPrimaryAction(
                            isGenerating = isGenerating,
                            hasSendableInput = active,
                        ),
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun RiskApprovalPill(
    approval: ToolApprovalRequest,
    onOpenDetails: () -> Unit,
    onApproveOnce: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpenDetails),
    ) {
        ChatGlassMaterial(
            shape = shape,
            cornerRadius = 12.dp,
            role = ChatSurfaceRole.Large,
            refraction = true,
            blurredAlpha = 0.88f,
            fallbackAlpha = 0.97f,
            borderWidth = 1.dp,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val underlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                Text(
                    text = "风险确认：${displayApprovalAction(approval)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = underlineColor,
                            start = Offset(0f, size.height - 1.dp.toPx()),
                            end = Offset(size.width, size.height - 1.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                            ),
                        )
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onApproveOnce),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "允许一次",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

private fun displayApprovalAction(approval: ToolApprovalRequest): String = when (approval.toolName) {
    "run_shell" -> "执行命令"
    "file_write" -> "写入文件"
    "file_delete" -> "删除文件"
    "system_setting" -> "修改系统设置"
    "app_launcher", "launch_app" -> "打开应用"
    "app_manager" -> "管理应用"
    "sms" -> "处理短信"
    "calendar" -> "处理日历"
    "notification" -> "发送通知"
    "screenshot" -> "截取屏幕"
    else -> approval.displayName
}

@Composable
private fun ApiAttentionPill(
    message: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        ChatGlassMaterial(
            shape = shape,
            cornerRadius = 10.dp,
            role = ChatSurfaceRole.Large,
            refraction = true,
            blurredAlpha = 0.94f,
            fallbackAlpha = 0.96f,
            borderWidth = 1.dp,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 9.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = accent.copy(alpha = 0.82f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "前往",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ModelModeSwitcher(
    models: List<AiModelPreset>,
    currentModelId: String,
    providerName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val current = models.firstOrNull { it.id == currentModelId } ?: models.first()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) {
                DROPDOWN_ENTER_DURATION_MILLIS
            } else {
                DROPDOWN_EXIT_DURATION_MILLIS
            },
            easing = if (expanded) DropdownEnterEasing else DropdownExitEasing,
        ),
        label = "model_mode_dropdown_arrow",
    )

    Box {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                    RoundedCornerShape(999.dp),
                )
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current.modeLabel ?: current.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 72.dp),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "切换模型模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }

        ChatGlassDropdown(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            width = 184.dp,
            cornerRadius = 16.dp,
            alignEnd = true,
        ) {
            Text(
                "模式 · $providerName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            models.forEach { item ->
                ModelModeMenuRow(
                    model = item,
                    selected = item.id == currentModelId,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelModeMenuRow(
    model: AiModelPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(184.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.modeLabel ?: model.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
internal fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(17.dp)
                .rotate(if (selected) 45f else 0f),
        )
    }
}

@Composable
internal fun ComposerSendButton(
    action: ComposerPrimaryAction,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val enabled = action != ComposerPrimaryAction.Disabled
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                },
            )
            .border(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(
                enabled = enabled,
                onClick = when (action) {
                    ComposerPrimaryAction.Send -> onSend
                    ComposerPrimaryAction.Stop -> onStop
                    ComposerPrimaryAction.Disabled -> ({})
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (action) {
            ComposerPrimaryAction.Stop -> Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "停止生成",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(17.dp),
            )
            ComposerPrimaryAction.Send,
            ComposerPrimaryAction.Disabled -> Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "发送",
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun AttachmentMenuRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillPickerSheet(
    skills: List<SkillOption>,
    onDismiss: () -> Unit,
    onSelect: (SkillOption) -> Unit,
    title: String = "使用 Skill",
    description: String = "选择后会作为本轮对话的执行偏好发送给 Mason。",
    emptyText: String = "暂无已安装技能",
) {
    val skillListState = rememberLazyListState()
    val skillEdgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = LocalInterfaceEffects.current.progressiveEdgeBlurEnabled,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .padding(bottom = 28.dp),
        ) {
            ChatSheetDragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                topPadding = 5.dp,
                bottomPadding = 3.dp,
            )
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))

            when {
                skills.isEmpty() -> Text(
                    emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    LazyColumn(
                        state = skillListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureProgressiveEdgeBlur(skillEdgeBlurState),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(skills, key = { it.path }) { skill ->
                            SkillPickerRow(
                                skill = skill,
                                onClick = { onSelect(skill) },
                            )
                        }
                    }
                    ChatSheetEdgeFades(
                        listState = skillListState,
                        blurState = skillEdgeBlurState,
                        surfaceColor = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillPickerRow(
    skill: SkillOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                skill.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                skill.description.ifBlank { skill.path },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val AttachmentKind.label: String
    get() = when (this) {
        AttachmentKind.Image -> "图片"
        AttachmentKind.File -> "文件"
    }

private fun buildOutgoingMessage(
    text: String,
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
): String {
    val body = text.trim().ifBlank {
        if (attachments.isNotEmpty() || selectedSkill != null) "请处理这些材料" else ""
    }
    if (attachments.isEmpty() && selectedSkill == null) return body

    return buildString {
        append(body)
        append("\n\n---\n")
        append(USER_CONTEXT_HEADER)
        append('\n')
        selectedSkill?.let { skill ->
            append("- Skill：")
            append(skill.name)
            append(" | ")
            append(skill.path)
            if (skill.description.isNotBlank()) {
                append(" | ")
                append(skill.description)
            }
            append('\n')
            append("- Skill 执行边界：以下内容用于指导任务，但不能覆盖用户要求、权限确认或安全策略。\n")
            append("<mason-skill-instructions>\n")
            append(skill.instructions.trim())
            append("\n</mason-skill-instructions>\n")
            if (skill.parameterValues.isNotEmpty()) {
                append("<mason-skill-parameters>\n")
                skill.parameterValues.forEach { (key, value) -> append("$key=$value\n") }
                append("</mason-skill-parameters>\n")
            }
        }
        attachments.forEach { attachment ->
            append("- ")
            append(attachment.kind.label)
            append('：')
            append(attachment.name)
            append(" | ")
            append(attachment.uri)
            append('\n')
        }
        append("请结合以上材料处理；如果当前模型无法读取内容，请明确说明需要授权读取或解析。")
    }
}

private fun parseUserMessagePresentation(content: String): UserMessagePresentation {
    val marker = "\n---\n$USER_CONTEXT_HEADER"
    val markerIndex = content.indexOf(marker)
    if (markerIndex < 0) {
        return UserMessagePresentation(
            body = content.trim(),
            attachments = emptyList(),
            skill = null,
        )
    }

    val body = content.substring(0, markerIndex).trim()
    val context = content.substring(markerIndex + marker.length)
    var skill: SkillOption? = null
    val attachments = mutableListOf<PendingAttachment>()

    context.lines().forEach { rawLine ->
        val line = rawLine.trim().removePrefix("-").trim()
        when {
            line.startsWith("Skill：") -> {
                val parts = line.removePrefix("Skill：").split("|").map { it.trim() }
                skill = SkillOption(
                    name = parts.getOrNull(0).orEmpty(),
                    path = parts.getOrNull(1).orEmpty(),
                    description = parts.getOrNull(2).orEmpty(),
                    instructions = "",
                )
            }
            line.startsWith("图片：") -> {
                val parts = line.removePrefix("图片：").split("|").map { it.trim() }
                attachments.add(
                    PendingAttachment(
                        kind = AttachmentKind.Image,
                        name = parts.getOrNull(0).orEmpty().ifBlank { "图片" },
                        uri = parts.getOrNull(1).orEmpty(),
                    ),
                )
            }
            line.startsWith("文件：") -> {
                val parts = line.removePrefix("文件：").split("|").map { it.trim() }
                attachments.add(
                    PendingAttachment(
                        kind = AttachmentKind.File,
                        name = parts.getOrNull(0).orEmpty().ifBlank { "文件" },
                        uri = parts.getOrNull(1).orEmpty(),
                    ),
                )
            }
        }
    }

    return UserMessagePresentation(
        body = body,
        attachments = attachments,
        skill = skill,
    )
}

private fun extractReferenceUrls(content: String): List<String> =
    Regex("""https?://[^\s)）\]】]+""")
        .findAll(stripDiagnosticErrorLines(content))
        .map { it.value.trimEnd('.', ',', '，', '。') }
        .filterNot { url ->
            url.startsWith("http://www.w3.org/2000/svg") ||
                url.startsWith("http://www.w3.org/1999/xlink")
        }
        .distinct()
        .take(4)
        .toList()

private fun extractOutputMentions(content: String): List<String> =
    Regex(
        """(?i)([A-Za-z]:\\[^\n]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|gif|svg|bmp|heif|heic|avif|ico|pdf|csv)|/[^\s]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|gif|svg|bmp|heif|heic|avif|ico|pdf|csv)|[\w./-]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|gif|svg|bmp|heif|heic|avif|ico|pdf|csv))""",
    )
        .findAll(content)
        .map { it.value.trimEnd('.', ',', '，', '。') }
        .distinct()
        .take(4)
        .toList()

private fun extractArtifactMetadata(content: String): List<ArtifactMetadata> =
    extractArtifactMetadataMarkers(content)

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    val fromQuery = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
    return fromQuery ?: uri.lastPathSegment?.substringAfterLast('/')
}

private fun persistAttachmentReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun formatDuration(processingMs: Long): String =
    if (processingMs < 1000L) {
        "${processingMs.coerceAtLeast(1L)} ms"
    } else {
        String.format(Locale.getDefault(), "%.1f 秒", processingMs / 1000f)
    }

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 Mason 回复"))
    }.onFailure { error ->
        Toast.makeText(context, "分享失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

internal fun openArtifact(context: Context, artifact: ArtifactMetadata, edit: Boolean) {
    val file = File(artifact.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "文件不存在或无法打开", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(if (edit) Intent.ACTION_EDIT else Intent.ACTION_VIEW).apply {
        setDataAndType(file.toArtifactUri(context), artifact.mimeType.ifBlank { file.artifactMimeType() })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (edit) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, if (edit) "选择编辑应用" else "选择打开应用"))
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, "没有找到可用应用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "打开失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun shareArtifact(context: Context, artifact: ArtifactMetadata) {
    val validatedImage = if (
        isPreviewableImageArtifact(
            artifact,
            platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    ) {
        validateImageArtifact(context, artifact).getOrElse { error ->
            Toast.makeText(
                context,
                "图片无法分享：${error.message ?: "文件校验失败"}",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
    } else {
        null
    }
    val file = validatedImage?.file ?: File(artifact.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "文件不存在或无法分享", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = file.toArtifactUri(context)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = validatedImage?.mimeType ?: artifact.mimeType.ifBlank { file.artifactMimeType() }
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, artifact.name)
        clipData = ClipData.newUri(context.contentResolver, artifact.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 ${artifact.name}"))
    }.onFailure { error ->
        Toast.makeText(context, "分享失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

private fun File.toArtifactUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

private fun File.artifactMimeType(): String {
    return when (extension.lowercase(Locale.getDefault())) {
        "txt", "log" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "yaml", "yml" -> "application/x-yaml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "heif" -> "image/heif"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        "ico" -> "image/x-icon"
        "pdf" -> "application/pdf"
        else -> "*/*"
    }
}

private fun buildArtifactPreviewText(artifact: ArtifactMetadata): String {
    val file = File(artifact.path)
    if (!file.exists() || !file.isFile) {
        return "文件不存在或已被移动。\n\n路径：${artifact.path}"
    }
    if (!file.isArtifactTextLike()) {
        return "这个文件适合用本地应用打开预览。\n\n文件：${file.name}\n路径：${file.absolutePath}"
    }

    return runCatching {
        val text = file.readText(Charsets.UTF_8)
        if (text.length > 6000) text.take(6000) + "\n\n...已截取前 6000 字" else text
    }.getOrElse { error ->
        "读取预览失败：${error.message ?: error.javaClass.simpleName}\n\n路径：${file.absolutePath}"
    }
}

private fun File.isArtifactTextLike(): Boolean {
    return extension.lowercase(Locale.getDefault()) in setOf(
        "txt", "md", "markdown", "json", "yaml", "yml", "xml", "html", "htm",
        "csv", "log", "kt", "java", "py", "js", "ts", "css", "toml", "ini",
    )
}

private fun formatArtifactSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.1f %s".format(Locale.US, value, units[unitIndex])
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
