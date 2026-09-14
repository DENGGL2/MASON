package com.denggl2.mason.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denggl2.mason.data.DEFAULT_GLASS_FROST
import com.denggl2.mason.data.DEFAULT_GLASS_TRANSPARENCY
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.normalizeGlassFrost
import com.denggl2.mason.data.normalizeGlassTransparency

internal const val GLASS_COMPONENT_MATERIAL_ENABLED = true
internal const val GLASS_PROGRESSIVE_EDGES_ENABLED = true
internal val GLASS_FROST_MAX_BLUR_RADIUS = 40.dp
const val MASON_OVERLAY_SCRIM_ALPHA = 0.12f

data class InterfaceEffects(
    val requestedStyle: InterfaceStyle,
    val effectiveStyle: InterfaceStyle,
    val backdropBlurEnabled: Boolean,
    val progressiveEdgeBlurEnabled: Boolean,
    val glassMaterialEnabled: Boolean,
    val glassRefractionEnabled: Boolean,
    val glassFrost: Float,
    val backdropEffectAlpha: Float,
    val compactSurfaceAlpha: Float,
    val largeSurfaceAlpha: Float,
)

internal fun resolveInterfaceEffects(
    requestedStyle: InterfaceStyle,
    requestedGlassRefraction: Boolean,
    requestedGlassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    requestedGlassFrost: Float = DEFAULT_GLASS_FROST,
    sdkInt: Int,
): InterfaceEffects {
    val backdropSupported = sdkInt >= 31
    val normalizedStyle = when (requestedStyle) {
        InterfaceStyle.GLASS -> InterfaceStyle.GLASS
        else -> InterfaceStyle.NATIVE
    }
    val glassSupported = normalizedStyle == InterfaceStyle.GLASS && backdropSupported
    val effectiveStyle = if (glassSupported) InterfaceStyle.GLASS else InterfaceStyle.NATIVE
    val glassTransparency = normalizeGlassTransparency(requestedGlassTransparency)
    val glassFrost = normalizeGlassFrost(requestedGlassFrost)
    val compactGlassAlpha = 1f - glassTransparency
    val defaultCompactGlassAlpha = 1f - DEFAULT_GLASS_TRANSPARENCY
    val largeGlassAlpha = if (defaultCompactGlassAlpha > 0f) {
        (compactGlassAlpha * (0.72f / defaultCompactGlassAlpha)).coerceIn(0f, 1f)
    } else {
        compactGlassAlpha
    }
    return InterfaceEffects(
        requestedStyle = normalizedStyle,
        effectiveStyle = effectiveStyle,
        backdropBlurEnabled = backdropSupported && normalizedStyle != InterfaceStyle.NATIVE,
        progressiveEdgeBlurEnabled = backdropSupported &&
            normalizedStyle != InterfaceStyle.NATIVE &&
            (normalizedStyle != InterfaceStyle.GLASS || (GLASS_PROGRESSIVE_EDGES_ENABLED && glassFrost > 0f)),
        glassMaterialEnabled = glassSupported && GLASS_COMPONENT_MATERIAL_ENABLED,
        glassRefractionEnabled = glassSupported &&
            requestedGlassRefraction &&
            sdkInt >= 33,
        glassFrost = glassFrost,
        backdropEffectAlpha = when {
            // Transparency controls the material tint only. The sampled backdrop
            // stays available for frost and refraction at every transparency value.
            glassSupported -> 1f
            backdropSupported && normalizedStyle != InterfaceStyle.NATIVE -> 1f
            else -> 0f
        },
        compactSurfaceAlpha = when {
            glassSupported -> compactGlassAlpha
            normalizedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
        largeSurfaceAlpha = when {
            glassSupported -> largeGlassAlpha
            normalizedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
    )
}

internal fun InterfaceEffects.resolveBackdropBlurRadius(
    nonGlassRadius: Dp,
): Dp = if (glassMaterialEnabled) {
    GLASS_FROST_MAX_BLUR_RADIUS * glassFrost
} else {
    nonGlassRadius
}

internal fun InterfaceEffects.requiresBackdropSample(
    blurRadius: Dp,
    includeRefraction: Boolean = false,
): Boolean = backdropBlurEnabled && (
    blurRadius.value > 0f || (includeRefraction && glassRefractionEnabled)
)

internal fun InterfaceEffects.resolveBackdropCaptureScale(
    includeRefraction: Boolean = false,
): Float = if (includeRefraction && glassRefractionEnabled) 1f else 0.5f
