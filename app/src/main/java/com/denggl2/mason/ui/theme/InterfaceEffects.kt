package com.denggl2.mason.ui.theme

import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.DEFAULT_GLASS_TRANSPARENCY
import com.denggl2.mason.data.normalizeGlassTransparency

internal const val GLASS_COMPONENT_MATERIAL_ENABLED = true
internal const val GLASS_PROGRESSIVE_EDGES_ENABLED = true

data class InterfaceEffects(
    val requestedStyle: InterfaceStyle,
    val effectiveStyle: InterfaceStyle,
    val backdropBlurEnabled: Boolean,
    val progressiveEdgeBlurEnabled: Boolean,
    val glassMaterialEnabled: Boolean,
    val glassRefractionEnabled: Boolean,
    val compactSurfaceAlpha: Float,
    val largeSurfaceAlpha: Float,
)

internal fun resolveInterfaceEffects(
    requestedStyle: InterfaceStyle,
    requestedGlassRefraction: Boolean,
    requestedGlassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    sdkInt: Int,
): InterfaceEffects {
    val backdropSupported = sdkInt >= 31
    val glassSupported = requestedStyle == InterfaceStyle.GLASS && backdropSupported
    val effectiveStyle = when {
        requestedStyle == InterfaceStyle.GLASS && !backdropSupported -> InterfaceStyle.NATIVE
        else -> requestedStyle
    }
    val glassTransparency = normalizeGlassTransparency(requestedGlassTransparency)
    val compactGlassAlpha = 1f - glassTransparency
    val largeGlassAlpha = (compactGlassAlpha + 0.30f).coerceAtMost(1f)
    return InterfaceEffects(
        requestedStyle = requestedStyle,
        effectiveStyle = effectiveStyle,
        backdropBlurEnabled = backdropSupported && requestedStyle != InterfaceStyle.NATIVE,
        // Acrylic keeps its current progressive edges; Glass uses the new Open Design treatment.
        progressiveEdgeBlurEnabled = backdropSupported &&
            requestedStyle != InterfaceStyle.NATIVE &&
            (requestedStyle != InterfaceStyle.GLASS || GLASS_PROGRESSIVE_EDGES_ENABLED),
        glassMaterialEnabled = glassSupported && GLASS_COMPONENT_MATERIAL_ENABLED,
        glassRefractionEnabled = glassSupported &&
            requestedGlassRefraction &&
            sdkInt >= 33,
        compactSurfaceAlpha = when {
            glassSupported -> compactGlassAlpha
            requestedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
        largeSurfaceAlpha = when {
            glassSupported -> largeGlassAlpha
            requestedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
    )
}
