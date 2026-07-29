package com.denggl2.mason.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlamaCppPerformanceMetricsTest {
    @Test
    fun reportsLoadAndVisibleOutputTiming() {
        val metrics = LlamaCppPerformanceMetrics(startedAtMs = 100)

        metrics.markModelReady(atMs = 450)
        metrics.recordVisibleText("你好", atMs = 700)
        metrics.recordVisibleText("世界", atMs = 900)

        val snapshot = metrics.snapshot(endedAtMs = 1_700, outcome = "completed")

        assertEquals(350L, snapshot.loadMs)
        assertEquals(600L, snapshot.ttfvMs)
        assertEquals(1_600L, snapshot.totalMs)
        assertEquals(4, snapshot.visibleChars)
        assertEquals(4.0, snapshot.visibleCharsPerSecond, 0.001)
        assertEquals("completed", snapshot.outcome)
    }

    @Test
    fun reportsNoTtfvWhenNoVisibleTextWasProduced() {
        val metrics = LlamaCppPerformanceMetrics(startedAtMs = 100)

        metrics.markModelReady(atMs = 300)
        val snapshot = metrics.snapshot(endedAtMs = 900, outcome = "cancelled")

        assertEquals(200L, snapshot.loadMs)
        assertNull(snapshot.ttfvMs)
        assertEquals(0, snapshot.visibleChars)
        assertEquals(0.0, snapshot.visibleCharsPerSecond, 0.001)
        assertEquals("cancelled", snapshot.outcome)
    }
}
