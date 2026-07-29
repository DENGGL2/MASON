package com.denggl2.mason.tool

import com.denggl2.mason.data.legacyNotificationsEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryPolicyTest {

    @Test
    fun legacyEnabledModeMigratesToRegularNotifications() {
        assertTrue(legacyNotificationsEnabled(storedMode = "REGULAR", legacyEnabled = true))
    }

    @Test
    fun legacyDisabledModeStaysDisabled() {
        assertFalse(legacyNotificationsEnabled(storedMode = "DISABLED", legacyEnabled = true))
        assertFalse(legacyNotificationsEnabled(storedMode = null, legacyEnabled = false))
    }

    @Test
    fun liveUpdateRequiresAndroid16OrNewer() {
        assertFalse(
            shouldRequestLiveUpdate(
                isLiveUpdate = true,
                islandNotificationsEnabled = true,
                sdkInt = 35,
                promotionAllowed = true,
            ),
        )
    }

    @Test
    fun liveUpdateRequiresSystemPromotionApproval() {
        assertFalse(
            shouldRequestLiveUpdate(
                isLiveUpdate = true,
                islandNotificationsEnabled = true,
                sdkInt = 36,
                promotionAllowed = false,
            ),
        )
    }

    @Test
    fun enabledIslandRequestsLiveUpdateOnEligibleSystem() {
        assertTrue(
            shouldRequestLiveUpdate(
                isLiveUpdate = true,
                islandNotificationsEnabled = true,
                sdkInt = 36,
                promotionAllowed = true,
            ),
        )
    }
}
