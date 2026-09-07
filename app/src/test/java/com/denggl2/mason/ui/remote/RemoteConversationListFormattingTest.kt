package com.denggl2.mason.ui.remote

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConversationListFormattingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 12, 17, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun formatsTodayYesterdayAndOlderDates() {
        assertEquals("09:05", formatRemoteConversationTime(timestamp(2026, 8, 12, 9, 5), now, zone))
        assertEquals("昨天", formatRemoteConversationTime(timestamp(2026, 8, 11, 23, 59), now, zone))
        assertEquals("7月3日", formatRemoteConversationTime(timestamp(2026, 7, 3), now, zone))
        assertEquals("2025/12/31", formatRemoteConversationTime(timestamp(2025, 12, 31), now, zone))
    }

    @Test
    fun acceptsAppServerEpochSecondsAndEmptyTimestamps() {
        val seconds = timestamp(2026, 8, 12, 9, 5) / 1_000
        assertEquals("09:05", formatRemoteConversationTime(seconds, now, zone))
        assertEquals("", formatRemoteConversationTime(0, now, zone))
    }

    @Test
    fun formatsEnglishRelativeDates() {
        assertEquals(
            "Yesterday",
            formatRemoteConversationTime(timestamp(2026, 8, 11, 23, 59), now, zone, english = true),
        )
        assertEquals(
            "7/3",
            formatRemoteConversationTime(timestamp(2026, 7, 3), now, zone, english = true),
        )
    }

    @Test
    fun onlyTerminalPairingErrorsCountAsAlreadyRevoked() {
        assertTrue(remoteRevocationAlreadyFinal("DEVICE_REVOKED"))
        assertTrue(remoteRevocationAlreadyFinal("DEVICE_NOT_PAIRED"))
        assertTrue(remoteRevocationAlreadyFinal("SESSION_REVOKED"))
        assertFalse(remoteRevocationAlreadyFinal("SESSION_INVALID"))
        assertFalse(remoteRevocationAlreadyFinal(null))
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
