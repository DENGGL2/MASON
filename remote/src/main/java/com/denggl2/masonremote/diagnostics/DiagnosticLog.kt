package com.denggl2.masonremote.diagnostics

import android.content.Context
import android.net.Uri
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small private rolling log used to diagnose device-only failures. */
object DiagnosticLog {
    private const val FILE_NAME = "cloudx-diagnostic.log"
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val KEEP_FILE_BYTES = 384 * 1024L
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        record("APP_START version=${com.denggl2.masonremote.BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT}")
    }

    fun record(event: String) {
        val context = appContext ?: return
        val line = synchronized(timestampFormat) {
            "${timestampFormat.format(Date())} [${Thread.currentThread().name}] ${sanitize(event)}\n"
        }
        synchronized(lock) {
            runCatching {
                val file = context.filesDir.resolve(FILE_NAME)
                file.appendText(line, Charsets.UTF_8)
                trimIfNeeded(file)
            }
        }
    }

    fun recordException(event: String, error: Throwable) {
        val stack = StringWriter()
        error.printStackTrace(PrintWriter(stack))
        record("$event ${sanitize(stack.toString())}")
    }

    fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordException("UNCAUGHT_EXCEPTION thread=${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun suggestedFileName(): String = synchronized(timestampFormat) {
        "cloudx-diagnostic-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.log"
    }

    fun exportTo(context: Context, destination: Uri): Result<Unit> = runCatching {
        val log = synchronized(lock) {
            appContext?.filesDir?.resolve(FILE_NAME)?.takeIf { it.isFile }?.readText(Charsets.UTF_8)
                ?: "${timestampFormat.format(Date())} [${Thread.currentThread().name}] LOG_EMPTY\n"
        }
        context.contentResolver.openOutputStream(destination)?.use { output ->
            output.write(log.toByteArray(Charsets.UTF_8))
        } ?: error("无法打开导出文件")
        record("DIAGNOSTIC_EXPORT_SUCCESS")
    }

    private fun trimIfNeeded(file: java.io.File) {
        if (file.length() <= MAX_FILE_BYTES) return
        val bytes = file.readBytes()
        val start = (bytes.size - KEEP_FILE_BYTES.toInt()).coerceAtLeast(0)
        file.writeBytes(bytes.copyOfRange(start, bytes.size))
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)Bearer\\s+[^\\s]+"), "Bearer [redacted]")
        .replace(Regex("(?i)(token|oneTimeToken|authorization)=([^,\\s]+)"), "$1=[redacted]")
        .replace("\u0000", "")
}
