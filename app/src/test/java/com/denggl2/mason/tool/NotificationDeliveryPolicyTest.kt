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

    @Test
    fun phoneAgentIslandRequiresAllSystemCapabilities() {
        assertFalse(
            shouldUsePromotedNotificationIsland(
                sdkInt = 36,
                notificationsEnabled = true,
                postNotificationPermissionGranted = true,
                promotedAllowed = false,
            ),
        )
        assertFalse(
            shouldUsePromotedNotificationIsland(
                sdkInt = 36,
                notificationsEnabled = false,
                postNotificationPermissionGranted = true,
                promotedAllowed = true,
            ),
        )
        assertTrue(
            shouldUsePromotedNotificationIsland(
                sdkInt = 36,
                notificationsEnabled = true,
                postNotificationPermissionGranted = true,
                promotedAllowed = true,
            ),
        )
    }

    @Test
    fun phoneAgentFallsBackBelowAndroid16() {
        assertFalse(
            shouldUsePromotedNotificationIsland(
                sdkInt = 35,
                notificationsEnabled = true,
                postNotificationPermissionGranted = true,
                promotedAllowed = true,
            ),
        )
    }

    @Test
    fun foregroundNotificationsAreSuppressedExceptForExplicitPreview() {
        assertTrue(shouldSuppressSystemNotification(appForeground = true, allowForeground = false))
        assertFalse(shouldSuppressSystemNotification(appForeground = true, allowForeground = true))
        assertFalse(shouldSuppressSystemNotification(appForeground = false, allowForeground = false))
    }

    @Test
    fun android12DoesNotRequestPostNotificationPermission() {
        assertFalse(
            shouldRequestPostNotificationPermission(
                sdkInt = 32,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun android13RequestsMissingPostNotificationPermission() {
        assertTrue(
            shouldRequestPostNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun grantedPostNotificationPermissionIsNotRequestedAgain() {
        assertFalse(
            shouldRequestPostNotificationPermission(
                sdkInt = 36,
                permissionGranted = true,
            ),
        )
    }
}
