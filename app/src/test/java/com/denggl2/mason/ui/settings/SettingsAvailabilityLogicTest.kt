package com.denggl2.mason.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsAvailabilityLogicTest {
    @Test
    fun `Android 12 requirement is only shown on unsupported devices`() {
        assertEquals("仅安卓12+生效", android12RequirementDescription(30))
        assertNull(android12RequirementDescription(31))
        assertNull(android12RequirementDescription(36))
    }

    @Test
    fun `Android 13 requirement is only shown on unsupported devices`() {
        assertEquals("仅安卓13+生效", android13RequirementDescription(32))
        assertNull(android13RequirementDescription(33))
        assertNull(android13RequirementDescription(36))
    }
}
