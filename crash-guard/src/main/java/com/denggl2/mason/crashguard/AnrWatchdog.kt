package com.denggl2.mason.crashguard

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.denggl2.mason.crashguard.data.CrashRecord
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

internal class AnrWatchdog(
    private val anrDir: File,
    private val appVersion: String,
    private val timeoutMs: Long = 3000L,
) {

    companion object {
        private const val TAG = "AnrWatchdog"
        private const val TICK_INTERVAL_MS = 1000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tickCount = 0L
    private var isRunning = false
    private var lastTickProcessed = 0L

    private val tickRunnable: Runnable = Runnable {
        tickCount++
        lastTickProcessed = tickCount
        mainHandler.postDelayed(monitorRunnable, TICK_INTERVAL_MS)
    }

    private val monitorRunnable: Runnable = Runnable {
        val expectedTick = tickCount + 1
        mainHandler.post(tickRunnable)

        if (isRunning && lastTickProcessed < tickCount) {
            // Main thread is blocked
            val blockedDuration = (tickCount - lastTickProcessed) * TICK_INTERVAL_MS + TICK_INTERVAL_MS
            if (blockedDuration >= timeoutMs) {
                onMainThreadBlocked(blockedDuration)
            }
        }
    }

    var onAnrDetected: ((CrashRecord) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        tickCount = 0
        lastTickProcessed = 0
        mainHandler.post(monitorRunnable)
        Log.d(TAG, "ANR watchdog started with timeout ${timeoutMs}ms")
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(monitorRunnable)
        mainHandler.removeCallbacks(tickRunnable)
        Log.d(TAG, "ANR watchdog stopped")
    }

    private fun onMainThreadBlocked(blockedDuration: Long) {
        val timestamp = System.currentTimeMillis()

        // Capture main thread stack trace
        val mainLooper = Looper.getMainLooper()
        val mainThread = mainLooper.thread
        val stackTrace = mainThread.stackTrace

        val stackTraceString = buildString {
            appendLine("Main thread blocked for ${blockedDuration}ms")
            appendLine("Thread: ${mainThread.name} (id=${mainThread.id})")
            appendLine("Stack trace:")
            for (element in stackTrace) {
                appendLine("  at $element")
            }
        }

        val record = CrashRecord(
            timestamp = timestamp,
            threadName = mainThread.name,
            exceptionType = "ANR_DETECTED",
            message = "Main thread blocked for ${blockedDuration}ms",
            stackTrace = stackTraceString,
            appVersion = appVersion,
            isLaunchCrash = false,
        )

        // Write ANR log to file
        writeAnrToFile(timestamp, blockedDuration, stackTraceString)

        onAnrDetected?.invoke(record)
        Log.e(TAG, "ANR detected: main thread blocked for ${blockedDuration}ms")
    }

    private fun writeAnrToFile(
        timestamp: Long,
        blockedDuration: Long,
        stackTrace: String,
    ) {
        try {
            if (!anrDir.exists()) {
                anrDir.mkdirs()
            }

            val fileName = "anr_${timestamp}.txt"
            val file = File(anrDir, fileName)

            val content = buildString {
                appendLine("ANR Detected")
                appendLine("Timestamp: $timestamp")
                appendLine("Blocked Duration: ${blockedDuration}ms")
                appendLine("App Version: $appVersion")
                appendLine()
                appendLine(stackTrace)
            }

            file.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ANR log", e)
        }
    }
}
