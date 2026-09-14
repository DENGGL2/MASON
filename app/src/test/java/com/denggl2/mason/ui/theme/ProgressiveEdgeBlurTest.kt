package com.denggl2.mason.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveEdgeBlurTest {
    @Test
    fun `Android 12 uses the continuous performance mask`() {
        assertTrue(preferPerformanceProgressiveBlur(sdkInt = 31))
        assertTrue(preferPerformanceProgressiveBlur(sdkInt = 32))
    }

    @Test
    fun `Android 13 and newer use the shader gradient`() {
        assertFalse(preferPerformanceProgressiveBlur(sdkInt = 33))
        assertFalse(preferPerformanceProgressiveBlur(sdkInt = 36))
    }
}
