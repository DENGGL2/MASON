package com.denggl2.mason.crashguard

import android.os.Build
import android.os.Looper
import android.util.Log
import com.denggl2.mason.crashguard.data.CrashRecord
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CrashHandler(
    private val crashDir: File,
    private val appVersion: String,
    private val originalHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
    }

    var onCrashCaught: ((CrashRecord) -> Unit)? = null

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val threadName = thread.name
        val exceptionType = throwable.javaClass.name
        val message = throwable.message ?: "No message"
        val stackTrace = getStackTraceString(throwable)

        // Write crash to local file (always works even when process is dying)
        writeCrashToFile(timestamp, threadName, exceptionType, message, stackTrace)

        // Notify listener with crash record (for Room insertion on next launch)
        val record = CrashRecord(
            timestamp = timestamp,
            threadName = threadName,
            exceptionType = exceptionType,
            message = message,
            stackTrace = stackTrace,
            appVersion = appVersion,
            isLaunchCrash = true,
        )
        onCrashCaught?.invoke(record)

        // Delegate to original handler
        originalHandler?.uncaughtException(thread, throwable)
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun writeCrashToFile(
        timestamp: Long,
        threadName: String,
        exceptionType: String,
        message: String,
        stackTrace: String,
    ) {
        try {
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "crash_${dateFormat.format(Date(timestamp))}.txt"
            val file = File(crashDir, fileName)

            val content = buildString {
                appendLine("Timestamp: $timestamp")
                appendLine("App Version: $appVersion")
                appendLine("Thread: $threadName")
                appendLine("Exception: $exceptionType")
                appendLine("Message: $message")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace)
                appendLine()
                appendLine("Device Info:")
                appendLine("  Brand: ${Build.BRAND}")
                appendLine("  Model: ${Build.MODEL}")
                appendLine("  SDK: ${Build.VERSION.SDK_INT}")
                appendLine("  Release: ${Build.VERSION.RELEASE}")
            }

            file.writeText(content)
            Log.e(TAG, "Crash log written to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log to file", e)
        }
    }
}
