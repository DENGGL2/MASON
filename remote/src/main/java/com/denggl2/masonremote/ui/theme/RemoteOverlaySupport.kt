package com.denggl2.masonremote.ui.theme

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb

internal val MasonSheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
internal val MasonDialogShape = RoundedCornerShape(30.dp)
internal const val MASON_OVERLAY_SCRIM_ALPHA = 0.12f
internal const val MASON_SHEET_SCRIM_ALPHA = 0.02f
internal const val MASON_DIALOG_ACTION_ALPHA = 0.90f

@Composable
internal fun masonDialogDismissButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = MASON_DIALOG_ACTION_ALPHA),
    contentColor = MaterialTheme.colorScheme.onSurface,
)

internal fun masonDialogConfirmButtonColor(color: Color): Color =
    color.copy(alpha = MASON_DIALOG_ACTION_ALPHA)

internal fun masonOverlayWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
internal fun masonSheetContainerColor(): Color = Color.Transparent

internal fun Modifier.masonSheetSurface(
    shape: Shape = MasonSheetShape,
    includeNavigationBarPadding: Boolean = true,
    drawEdge: Boolean = false,
): Modifier = composed {
    val surface = MaterialTheme.colorScheme.surface
    val sheetEdgeBlendHeight = with(LocalDensity.current) { 3.dp.toPx() }
    // Keep the matte ramp subtle so the drag handle does not sit in a white band.
    // The sheet remains opaque below the ramp; no blur or refraction is used.
    val sheetSurfaceBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to surface.copy(alpha = 0f),
            0.42f to surface.copy(alpha = 0.30f),
            1f to surface,
        ),
        startY = 0f,
        endY = sheetEdgeBlendHeight,
    )
    val navigationBarColor = surface.toArgb()
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val view = LocalView.current
    val dialogWindow = generateSequence(view as android.view.ViewParent?) { current ->
        (current as? android.view.View)?.parent
    }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    DisposableEffect(dialogWindow, surface, navigationBarColor) {
        val window = dialogWindow
        val decorView = window?.decorView
        val previousNavigationBarColor = window?.navigationBarColor
        val previousNavigationBarDividerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.navigationBarDividerColor
        } else {
            null
        }
        val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        val previousSystemUiVisibility = decorView?.systemUiVisibility
        if (window != null && decorView != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            // The sheet dialog owns the system gesture area. Match its base
            // material there so Android cannot add a differently colored plate
            // below the sheet while the content is animating or blurred.
            window.navigationBarColor = navigationBarColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            WindowInsetsControllerCompat(window, decorView).isAppearanceLightNavigationBars = !darkTheme
        }
        onDispose {
            if (previousNavigationBarColor != null) window.navigationBarColor = previousNavigationBarColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousNavigationBarDividerColor != null) {
                window?.navigationBarDividerColor = previousNavigationBarDividerColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousSystemUiVisibility != null) decorView?.systemUiVisibility = previousSystemUiVisibility
        }
    }
    this
        .clip(shape)
        .background(sheetSurfaceBrush, shape)
        .let { modifier ->
            if (includeNavigationBarPadding) modifier.navigationBarsPadding() else modifier
        }
        .let { modifier ->
            if (drawEdge) modifier.floatingSurfaceEdge(shape = shape, nonGlassWidth = 0.5.dp) else modifier
        }
}

internal enum class ProgressiveBlurEdge { Top, Bottom }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MasonAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = MasonDialogShape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
    scrimAlpha: Float = MASON_OVERLAY_SCRIM_ALPHA,
    customContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val effects = LocalInterfaceEffects.current
    val materialAlpha = when {
        !effects.backdropBlurEnabled -> 1f
        effects.glassMaterialEnabled -> effects.compactSurfaceAlpha
        else -> effects.largeSurfaceAlpha.coerceAtLeast(0.80f)
    }
    val materialModifier = modifier
        .floatingSurfaceEdge(
            shape = shape,
            nonGlassWidth = 0.5.dp,
            nonGlassColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
        )
        .clip(shape)
        .glassRefraction(
            enabled = effects.glassRefractionEnabled,
            cornerRadius = 30.dp,
        )
        .windowBackdropMaterial(
            enabled = effects.backdropBlurEnabled,
            blurRadius = effects.resolveBackdropBlurRadius(nonGlassRadius = 32.dp),
            fallbackColor = containerColor,
            effectAlpha = effects.backdropEffectAlpha,
            useScreenCoordinates = true,
        )
        .background(containerColor.copy(alpha = materialAlpha), shape)

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        MasonDialogWindowEffects(scrimAlpha)
        Column(
            modifier = materialModifier
                .widthIn(min = 280.dp, max = 360.dp)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (customContent != null) {
                customContent()
            } else {
                icon?.let { iconContent ->
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        Box(modifier = Modifier.padding(bottom = 12.dp)) { iconContent() }
                    }
                }
                title?.let { titleContent ->
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        Box(modifier = Modifier.padding(bottom = if (text == null) 16.dp else 10.dp)) {
                            ProvideTextStyle(
                                MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                            ) { titleContent() }
                        }
                    }
                }
                text?.let { textContent ->
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        Box(modifier = Modifier.padding(bottom = 18.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)) {
                                textContent()
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun MasonDialogWindowEffects(scrimAlpha: Float) {
    val view = LocalView.current
    val dialogWindow = generateSequence(view as android.view.ViewParent?) { current ->
        (current as? android.view.View)?.parent
    }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    DisposableEffect(dialogWindow) {
        val window = dialogWindow
        val previousDimAmount = window?.attributes?.dimAmount
        val previouslyDimmed = window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(scrimAlpha.coerceIn(0f, 1f))
        onDispose {
            if (previousDimAmount != null) window?.setDimAmount(previousDimAmount)
            if (!previouslyDimmed) window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
}
