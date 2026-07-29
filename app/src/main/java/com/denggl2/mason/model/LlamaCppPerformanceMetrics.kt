package com.denggl2.mason.model

internal data class LlamaCppPerformanceSnapshot(
    val loadMs: Long,
    val ttfvMs: Long?,
    val totalMs: Long,
    val visibleChars: Int,
    val visibleCharsPerSecond: Double,
    val outcome: String,
)

internal class LlamaCppPerformanceMetrics(
    private val startedAtMs: Long,
) {
    private var modelReadyAtMs: Long? = null
    private var firstVisibleAtMs: Long? = null
    private var visibleChars = 0

    fun markModelReady(atMs: Long) {
        modelReadyAtMs = atMs
    }

    fun recordVisibleText(text: String, atMs: Long) {
        if (text.isEmpty()) return
        if (firstVisibleAtMs == null) firstVisibleAtMs = atMs
        visibleChars += text.length
    }

    fun snapshot(endedAtMs: Long, outcome: String): LlamaCppPerformanceSnapshot {
        val totalMs = (endedAtMs - startedAtMs).coerceAtLeast(0)
        val firstVisibleAt = firstVisibleAtMs
        val generationMs = firstVisibleAt?.let { (endedAtMs - it).coerceAtLeast(1) } ?: 1
        return LlamaCppPerformanceSnapshot(
            loadMs = modelReadyAtMs?.let { (it - startedAtMs).coerceAtLeast(0) } ?: 0,
            ttfvMs = firstVisibleAt?.let { (it - startedAtMs).coerceAtLeast(0) },
            totalMs = totalMs,
            visibleChars = visibleChars,
            visibleCharsPerSecond = visibleChars * 1_000.0 / generationMs,
            outcome = outcome,
        )
    }
}
