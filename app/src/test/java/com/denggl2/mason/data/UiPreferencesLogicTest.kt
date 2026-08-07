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
}
