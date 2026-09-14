package com.denggl2.masonremote.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import com.denggl2.masonremote.ui.settings.RemoteInterfaceStyle

internal const val GLASS_COMPONENT_MATERIAL_ENABLED = true
internal val GLASS_FROST_MAX_BLUR_RADIUS = 40.dp
internal const val DEFAULT_GLASS_TRANSPARENCY = 0.90f
internal const val DEFAULT_GLASS_FROST = 0.10f

data class InterfaceEffects(
    val requestedStyle: RemoteInterfaceStyle,
    val effectiveStyle: RemoteInterfaceStyle,
    val backdropBlurEnabled: Boolean,
    val progressiveEdgeBlurEnabled: Boolean,
    val glassMaterialEnabled: Boolean,
    val glassRefractionEnabled: Boolean,
    val glassFrost: Float,
    val backdropEffectAlpha: Float,
    val compactSurfaceAlpha: Float,
    val largeSurfaceAlpha: Float,
)

val LocalInterfaceEffects = staticCompositionLocalOf {
    resolveInterfaceEffects(
        requestedStyle = RemoteInterfaceStyle.NATIVE,
        requestedGlassRefraction = false,
        sdkInt = android.os.Build.VERSION.SDK_INT,
    )
}

internal fun resolveInterfaceEffects(
    requestedStyle: RemoteInterfaceStyle,
    requestedGlassRefraction: Boolean,
    requestedGlassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    requestedGlassFrost: Float = DEFAULT_GLASS_FROST,
    sdkInt: Int,
): InterfaceEffects {
    val backdropSupported = sdkInt >= 31
    val glassSupported = requestedStyle == RemoteInterfaceStyle.GLASS && backdropSupported
    val effectiveStyle = if (requestedStyle == RemoteInterfaceStyle.GLASS && !backdropSupported) {
        RemoteInterfaceStyle.NATIVE
    } else {
        requestedStyle
    }
    val transparency = requestedGlassTransparency.takeIf(Float::isFinite)?.coerceIn(0f, 1f)
        ?: DEFAULT_GLASS_TRANSPARENCY
    val frost = requestedGlassFrost.takeIf(Float::isFinite)?.coerceIn(0f, 1f)
        ?: DEFAULT_GLASS_FROST
    val compactGlassAlpha = 1f - transparency
    val defaultCompactGlassAlpha = 1f - DEFAULT_GLASS_TRANSPARENCY
    val largeGlassAlpha = if (defaultCompactGlassAlpha > 0f) {
        (compactGlassAlpha * (0.72f / defaultCompactGlassAlpha)).coerceIn(0f, 1f)
    } else {
        compactGlassAlpha
    }
    return InterfaceEffects(
        requestedStyle = requestedStyle,
        effectiveStyle = effectiveStyle,
        backdropBlurEnabled = backdropSupported && requestedStyle != RemoteInterfaceStyle.NATIVE,
        progressiveEdgeBlurEnabled = backdropSupported && requestedStyle != RemoteInterfaceStyle.NATIVE,
        glassMaterialEnabled = glassSupported && GLASS_COMPONENT_MATERIAL_ENABLED,
        glassRefractionEnabled = glassSupported && requestedGlassRefraction && sdkInt >= 33,
        glassFrost = frost,
        backdropEffectAlpha = if (glassSupported || (backdropSupported && requestedStyle != RemoteInterfaceStyle.NATIVE)) 1f else 0f,
        compactSurfaceAlpha = when {
            glassSupported -> compactGlassAlpha
            requestedStyle == RemoteInterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
        largeSurfaceAlpha = when {
            glassSupported -> largeGlassAlpha
            requestedStyle == RemoteInterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
    )
}

internal fun InterfaceEffects.resolveBackdropBlurRadius(nonGlassRadius: Dp): Dp =
    if (glassMaterialEnabled) GLASS_FROST_MAX_BLUR_RADIUS * glassFrost else nonGlassRadius

internal fun InterfaceEffects.requiresBackdropSample(
    blurRadius: Dp,
    includeRefraction: Boolean = false,
): Boolean = backdropBlurEnabled && (blurRadius.value > 0f || (includeRefraction && glassRefractionEnabled))

internal fun InterfaceEffects.resolveBackdropCaptureScale(includeRefraction: Boolean = false): Float =
    if (includeRefraction && glassRefractionEnabled) 1f else 0.5f
