package com.denggl2.mason.ui.theme

import com.denggl2.mason.data.InterfaceStyle

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
    sdkInt: Int,
): InterfaceEffects {
    val backdropSupported = sdkInt >= 31
    val glassSupported = requestedStyle == InterfaceStyle.GLASS && backdropSupported
    val effectiveStyle = when {
        requestedStyle == InterfaceStyle.GLASS && !backdropSupported -> InterfaceStyle.NATIVE
        else -> requestedStyle
    }
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
            glassSupported -> 0.42f
            requestedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
        largeSurfaceAlpha = when {
            glassSupported -> 0.72f
            requestedStyle == InterfaceStyle.NATIVE || !backdropSupported -> 1f
            else -> 0.80f
        },
    )
}
