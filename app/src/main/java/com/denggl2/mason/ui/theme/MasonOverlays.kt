package com.denggl2.mason.ui.theme

import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.Role
import com.denggl2.masonremote.ui.LocalRemoteStrings

internal val MasonSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
internal val MasonDialogShape = RoundedCornerShape(8.dp)

/**
 * Action used inside MasonAlertDialog. It keeps the dialog's compact, text-only
 * action row without inheriting Material 3 TextButton's min-size and padding.
 */
@Composable
internal fun MasonDialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        androidx.compose.material3.ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(
                color = if (enabled) color else color.copy(alpha = 0.38f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        ) {
            com.denggl2.masonremote.ui.localizedText(LocalRemoteStrings.current.displayText(label))
        }
    }
}

internal fun masonOverlayWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
internal fun masonSheetContainerColor(): Color = Color.Transparent

internal fun Modifier.masonSheetSurface(
    shape: Shape = MasonSheetShape,
): Modifier = composed {
    val effects = LocalInterfaceEffects.current
    val surface = MaterialTheme.colorScheme.surface
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val view = LocalView.current
    val dialogWindow = generateSequence(view as android.view.ViewParent?) { current ->
        (current as? android.view.View)?.parent
    }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    DisposableEffect(dialogWindow, surface) {
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
        // ModalBottomSheet is hosted in its own edge-to-edge Dialog window. Keep
        // the system bar transparent so the sheet material can paint through the
        // Home indicator inset instead of leaving a separate solid strip.
        if (window != null && decorView != null) {
            // Material3's sheet dialog can inherit the host window's fit-system-
            // windows policy. Make the dialog edge-to-edge as well, otherwise
            // Android may insert a separate navigation-bar plate below the sheet.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
            if (previousNavigationBarColor != null) {
                window.navigationBarColor = previousNavigationBarColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousNavigationBarDividerColor != null) {
                window?.navigationBarDividerColor = previousNavigationBarDividerColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousSystemUiVisibility != null) {
                decorView?.systemUiVisibility = previousSystemUiVisibility
            }
        }
    }
    val material = if (effects.backdropBlurEnabled) {
        Modifier
            .windowBackdropMaterial(
                enabled = true,
                blurRadius = effects.resolveBackdropBlurRadius(nonGlassRadius = 40.dp),
                fallbackColor = surface,
                effectAlpha = effects.backdropEffectAlpha,
            )
            .background(surface.copy(alpha = effects.largeSurfaceAlpha))
    } else {
        Modifier.background(surface)
    }

    this
        // Material must be outside the inset padding. Otherwise the bottom
        // navigation inset is measured as transparent space below the surface.
        .clip(shape)
        .then(material)
        .navigationBarsPadding()
        .floatingSurfaceEdge(
            shape = shape,
            nonGlassWidth = 0.5.dp,
        )
}

@Composable
private fun MasonDialogWindowEffects() {
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
        window?.setDimAmount(MASON_OVERLAY_SCRIM_ALPHA)
        onDispose {
            if (previousDimAmount != null) {
                window?.setDimAmount(previousDimAmount)
            }
            if (!previouslyDimmed) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
            cornerRadius = 8.dp,
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
        MasonDialogWindowEffects()
        Column(
            modifier = materialModifier
                .widthIn(min = 280.dp, max = 360.dp)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            icon?.let { iconContent ->
                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        iconContent()
                    }
                }
            }
            title?.let { titleContent ->
                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                    Box(modifier = Modifier.padding(bottom = if (text == null) 16.dp else 10.dp)) {
                        androidx.compose.material3.ProvideTextStyle(
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                            ),
                        ) {
                            titleContent()
                        }
                    }
                }
            }
            text?.let { textContent ->
                CompositionLocalProvider(LocalContentColor provides textContentColor) {
                    Box(modifier = Modifier.padding(bottom = 18.dp)) {
                        androidx.compose.material3.ProvideTextStyle(
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                        ) {
                            textContent()
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.End),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
