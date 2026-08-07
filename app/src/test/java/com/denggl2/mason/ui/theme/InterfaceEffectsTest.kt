package com.denggl2.mason.ui.theme

import com.denggl2.mason.data.InterfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceEffectsTest {
    @Test
    fun `glass falls back to opaque native material below Android 12`() {
        val effects = resolveInterfaceEffects(InterfaceStyle.GLASS, true, sdkInt = 30)

        assertEquals(InterfaceStyle.NATIVE, effects.effectiveStyle)
        assertFalse(effects.backdropBlurEnabled)
        assertFalse(effects.progressiveEdgeBlurEnabled)
        assertFalse(effects.glassRefractionEnabled)
        assertEquals(0f, effects.backdropEffectAlpha)
        assertEquals(1f, effects.compactSurfaceAlpha)
    }

    @Test
    fun `glass uses performance blur without refraction on Android 12`() {
        val effects = resolveInterfaceEffects(InterfaceStyle.GLASS, true, sdkInt = 31)

        assertEquals(InterfaceStyle.GLASS, effects.effectiveStyle)
        assertTrue(effects.backdropBlurEnabled)
        assertTrue(effects.progressiveEdgeBlurEnabled)
        assertTrue(effects.glassMaterialEnabled)
        assertFalse(effects.glassRefractionEnabled)
        assertEquals(1f, effects.backdropEffectAlpha, 0.0001f)
        assertEquals(0.42f, effects.compactSurfaceAlpha, 0.0001f)
        assertEquals(0.72f, effects.largeSurfaceAlpha, 0.0001f)
    }

    @Test
    fun `glass transparency preserves the compact and large material relationship`() {
        val clear = resolveInterfaceEffects(
            requestedStyle = InterfaceStyle.GLASS,
            requestedGlassRefraction = false,
            requestedGlassTransparency = 0.75f,
            sdkInt = 31,
        )
        val opaque = resolveInterfaceEffects(
            requestedStyle = InterfaceStyle.GLASS,
            requestedGlassRefraction = false,
            requestedGlassTransparency = -1f,
            sdkInt = 31,
        )

        assertEquals(0.25f, clear.compactSurfaceAlpha, 0.0001f)
        assertEquals(0.42857143f, clear.largeSurfaceAlpha, 0.0001f)
        assertEquals(0.5952381f, clear.backdropEffectAlpha, 0.0001f)
        assertEquals(1f, opaque.compactSurfaceAlpha, 0.0001f)
        assertEquals(1f, opaque.largeSurfaceAlpha, 0.0001f)
        assertEquals(1f, opaque.backdropEffectAlpha, 0.0001f)
    }

    @Test
    fun `fully transparent glass removes both surface and backdrop blur`() {
        val effects = resolveInterfaceEffects(
            requestedStyle = InterfaceStyle.GLASS,
            requestedGlassRefraction = true,
            requestedGlassTransparency = 1f,
            sdkInt = 33,
        )

        assertEquals(0f, effects.compactSurfaceAlpha, 0.0001f)
        assertEquals(0f, effects.largeSurfaceAlpha, 0.0001f)
        assertEquals(0f, effects.backdropEffectAlpha, 0.0001f)
    }

    @Test
    fun `glass refraction requires opt in on Android 13`() {
        assertFalse(
            resolveInterfaceEffects(InterfaceStyle.GLASS, false, sdkInt = 33)
                .glassRefractionEnabled,
        )
        assertTrue(
            resolveInterfaceEffects(InterfaceStyle.GLASS, true, sdkInt = 33)
                .glassRefractionEnabled,
        )
    }

    @Test
    fun `native never enables translucent effects`() {
        val effects = resolveInterfaceEffects(InterfaceStyle.NATIVE, true, sdkInt = 35)

        assertFalse(effects.backdropBlurEnabled)
        assertFalse(effects.progressiveEdgeBlurEnabled)
        assertFalse(effects.glassMaterialEnabled)
        assertFalse(effects.glassRefractionEnabled)
        assertEquals(0f, effects.backdropEffectAlpha)
        assertEquals(1f, effects.largeSurfaceAlpha)
    }

    @Test
    fun `acrylic keeps blur without glass refraction`() {
        val effects = resolveInterfaceEffects(InterfaceStyle.ACRYLIC, true, sdkInt = 35)

        assertTrue(effects.backdropBlurEnabled)
        assertTrue(effects.progressiveEdgeBlurEnabled)
        assertFalse(effects.glassMaterialEnabled)
        assertFalse(effects.glassRefractionEnabled)
        assertEquals(1f, effects.backdropEffectAlpha)
        assertEquals(0.80f, effects.compactSurfaceAlpha)
        assertEquals(0.80f, effects.largeSurfaceAlpha)
    }
}
