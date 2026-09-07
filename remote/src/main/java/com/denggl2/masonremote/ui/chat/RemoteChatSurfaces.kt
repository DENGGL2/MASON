package com.denggl2.masonremote.ui.chat

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.denggl2.masonremote.ui.theme.LocalInterfaceEffects
import com.denggl2.masonremote.ui.theme.floatingSurfaceEdge
import com.denggl2.masonremote.ui.theme.floatingSurfaceShadowColor
import com.denggl2.masonremote.ui.theme.glassRefraction
import com.denggl2.masonremote.ui.theme.rememberWindowBackdropSnapshot
import com.denggl2.masonremote.ui.theme.requiresBackdropSample
import com.denggl2.masonremote.ui.theme.resolveBackdropBlurRadius
import com.denggl2.masonremote.ui.theme.resolveBackdropCaptureScale
import com.denggl2.masonremote.ui.theme.windowBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay

internal enum class ChatBackdropBlur { Strong, Soft, Drawer }
internal enum class ChatSurfaceRole { Compact, Large }
val LocalChatBackdropState = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

private const val CHAT_POPUP_BACKDROP_WAIT_MILLIS = 300L
private const val DROPDOWN_ENTER_DURATION_MILLIS = 180
private const val DROPDOWN_EXIT_DURATION_MILLIS = 120
private val DropdownEnterEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val DropdownExitEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun rememberChatBackdropState(enabled: Boolean): HazeState? {
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
    return remember { HazeState() }
}

fun Modifier.captureChatBackdrop(state: HazeState?): Modifier =
    if (state == null) this else haze(state)

internal fun Modifier.glassClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val effects = LocalInterfaceEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = if (effects.glassMaterialEnabled) null else LocalIndication.current,
        role = role,
        onClick = onClick,
    )
}

private fun Modifier.chatBackdrop(
    blur: ChatBackdropBlur = ChatBackdropBlur.Strong,
): Modifier = composed {
    val state = LocalChatBackdropState.current ?: return@composed this
    val interfaceEffects = LocalInterfaceEffects.current
    val blurRadius = interfaceEffects.resolveBackdropBlurRadius(
        nonGlassRadius = when (blur) {
            ChatBackdropBlur.Strong -> 32.dp
            ChatBackdropBlur.Soft -> 20.dp
            ChatBackdropBlur.Drawer -> 32.dp
        },
    )
    hazeChild(
        state = state,
        style = HazeStyle(
            backgroundColor = MaterialTheme.colorScheme.surface,
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

internal fun Modifier.blurLayerOuterEdgeFeather(
    edge: com.denggl2.masonremote.ui.theme.ProgressiveBlurEdge,
    featherHeight: Dp,
): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

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
    val effects = LocalInterfaceEffects.current
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassRefraction(
                    enabled = refraction && effects.glassRefractionEnabled,
                    cornerRadius = cornerRadius,
                )
                .chatBackdrop(blur),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = when {
                            !effects.backdropBlurEnabled -> fallbackAlpha
                            effects.glassMaterialEnabled && role == ChatSurfaceRole.Large -> effects.largeSurfaceAlpha
                            effects.glassMaterialEnabled -> effects.compactSurfaceAlpha
                            else -> blurredAlpha
                        },
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
internal fun ChatGlassControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    cornerRadius: Dp = 22.dp,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .masonGlassShadow(cornerRadius = cornerRadius)
            .clip(shape)
            .glassClickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        if (enabled) {
            ChatGlassMaterial(
                shape = shape,
                cornerRadius = cornerRadius,
                role = ChatSurfaceRole.Compact,
                blur = ChatBackdropBlur.Soft,
                refraction = true,
                blurredAlpha = 0.78f,
                fallbackAlpha = 1f,
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(disabledContainerColor, shape),
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) contentColor else disabledContentColor,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

internal fun Modifier.masonGlassShadow(
    cornerRadius: Dp,
    blurRadius: Dp = 20.dp,
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
    DisposableEffect(graphicsContext, shadowLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(shadowLayer) }
    }
    drawWithContent {
        val blurPx = blurRadius.toPx()
        val cornerPx = cornerRadius.toPx()
        val contentSize = size
        val layerSize = IntSize(
            (contentSize.width + blurPx * 2f).toInt().coerceAtLeast(1),
            (contentSize.height + blurPx * 2f).toInt().coerceAtLeast(1),
        )
        shadowLayer.record(layerSize) {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(blurPx, blurPx),
                size = contentSize,
                cornerRadius = CornerRadius(cornerPx),
            )
        }
        val shadowMask = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, contentSize), CornerRadius(cornerPx, cornerPx)))
        }
        clipPath(shadowMask, clipOp = ClipOp.Difference) {
            translate(left = -blurPx, top = -blurPx) { drawLayer(shadowLayer) }
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
    forceOpenAbove: Boolean = false,
    preserveInputFocus: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var popupMounted by remember { mutableStateOf(expanded) }
    val popupMotion = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
        } else if (popupMounted) {
            popupMotion.animateTo(0f, tween(DROPDOWN_EXIT_DURATION_MILLIS, easing = DropdownExitEasing))
            popupMounted = false
        }
    }
    if (!popupMounted) return

    val density = LocalDensity.current
    val interfaceEffects = LocalInterfaceEffects.current
    var surfacePosition by remember { mutableStateOf(IntOffset.Zero) }
    var opensAbove by remember { mutableStateOf(false) }
    val backdropBlurRadius = if (subduedMaterial) 12.dp else interfaceEffects.resolveBackdropBlurRadius(15.dp)
    val backdropRequired = interfaceEffects.requiresBackdropSample(
        blurRadius = backdropBlurRadius,
        includeRefraction = !subduedMaterial,
    )
    val popupBackdrop = rememberWindowBackdropSnapshot(
        enabled = backdropRequired,
        captureScale = interfaceEffects.resolveBackdropCaptureScale(includeRefraction = !subduedMaterial),
    )
    val shadowGutter = if (subduedMaterial) 12.dp else 24.dp
    val positionProvider = remember(density, alignEnd, forceOpenAbove) {
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
                val desiredSurfaceX = if (alignEnd) anchorBounds.right - surfaceWidth else anchorBounds.left
                val surfaceX = desiredSurfaceX.coerceIn(
                    gutterPx,
                    (windowSize.width - surfaceWidth - gutterPx).coerceAtLeast(gutterPx),
                )
                val belowY = anchorBounds.bottom + gapPx
                val aboveY = anchorBounds.top - gapPx - surfaceHeight
                val nextOpensAbove = forceOpenAbove || belowY + surfaceHeight + gutterPx > windowSize.height
                if (opensAbove != nextOpensAbove) opensAbove = nextOpensAbove
                val surfaceY = (if (nextOpensAbove) aboveY else belowY).coerceIn(
                    gutterPx,
                    (windowSize.height - surfaceHeight - gutterPx).coerceAtLeast(gutterPx),
                )
                val nextPosition = IntOffset(surfaceX, surfaceY)
                if (surfacePosition != nextPosition) surfacePosition = nextPosition
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
    val popupReady = positionReady && (!backdropRequired || useBackdrop || opaqueFallbackLocked)
    val popupSurfaceAlpha = when {
        backdropRequired && !useBackdrop -> 1f
        subduedMaterial -> interfaceEffects.compactSurfaceAlpha.coerceAtLeast(0.82f)
        else -> interfaceEffects.compactSurfaceAlpha
    }
    LaunchedEffect(expanded, popupReady) {
        when {
            !expanded -> Unit
            !popupReady -> popupMotion.snapTo(0f)
            else -> popupMotion.animateTo(1f, tween(DROPDOWN_ENTER_DURATION_MILLIS, easing = DropdownEnterEasing))
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = !preserveInputFocus,
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
                        translationY = (if (opensAbove) 1f else -1f) * hiddenOffset * (1f - progress)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
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
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
                            } else {
                                Modifier.masonGlassShadow(cornerRadius).floatingSurfaceEdge(shape)
                            },
                        )
                        .clip(shape),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .glassRefraction(
                                enabled = useBackdrop && !subduedMaterial && interfaceEffects.glassRefractionEnabled,
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
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = popupSurfaceAlpha), shape),
                    )
                    Column(content = content)
                }
            }
            PopupDismissGutters(shadowGutter, onDismissRequest)
        }
    }
}

@Composable
internal fun BoxScope.PopupDismissGutters(gutter: Dp, onDismissRequest: () -> Unit) {
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val dismissModifier = Modifier.clickable(
        interactionSource = dismissInteractionSource,
        indication = null,
        onClick = onDismissRequest,
    )
    Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(gutter).then(dismissModifier))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(gutter).then(dismissModifier))
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(gutter).then(dismissModifier))
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(gutter).then(dismissModifier))
    }
}
