package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesLogicTest {
    @Test
    fun `interface style decoder preserves visible styles`() {
        assertEquals(InterfaceStyle.ACRYLIC, decodeInterfaceStyle("ACRYLIC"))
        assertEquals(InterfaceStyle.NATIVE, decodeInterfaceStyle("NATIVE"))
        assertEquals(InterfaceStyle.GLASS, decodeInterfaceStyle("GLASS"))
    }

    @Test
    fun `interface style decoder falls back for legacy and unknown values`() {
        assertEquals(InterfaceStyle.ACRYLIC, decodeInterfaceStyle("LIQUID_GLASS"))
        assertEquals(InterfaceStyle.ACRYLIC, decodeInterfaceStyle("MATERIAL3"))
        assertEquals(InterfaceStyle.ACRYLIC, decodeInterfaceStyle("UNKNOWN"))
        assertEquals(InterfaceStyle.ACRYLIC, decodeInterfaceStyle(null))
    }

    @Test
    fun `glass transparency defaults and clamps invalid stored values`() {
        assertEquals(DEFAULT_GLASS_TRANSPARENCY, UiPreferences().glassTransparency)
        assertEquals(0f, normalizeGlassTransparency(-0.2f))
        assertEquals(1f, normalizeGlassTransparency(1.2f))
        assertEquals(DEFAULT_GLASS_TRANSPARENCY, normalizeGlassTransparency(Float.NaN))
    }

    @Test
    fun `glass frost defaults to Frame 9 and clamps invalid stored values`() {
        assertEquals(DEFAULT_GLASS_FROST, UiPreferences().glassFrost)
        assertEquals(0f, normalizeGlassFrost(-0.2f))
        assertEquals(1f, normalizeGlassFrost(1.2f))
        assertEquals(DEFAULT_GLASS_FROST, normalizeGlassFrost(Float.NaN))
    }
}
