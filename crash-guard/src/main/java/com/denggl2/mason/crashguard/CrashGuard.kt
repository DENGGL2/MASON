package com.denggl2.mason.crashguard

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.crashguard.data.CrashRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashDao: CrashDao,
) {

    companion object {
        private const val TAG = "CrashGuard"
        private const val CRASH_DIR = "crash_logs"
        private const val ANR_DIR = "anr_logs"
        private const val LAST_CRASH_COUNT_KEY = "last_crash_count"
        private const val PREFS_NAME = "crash_guard_prefs"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crashDir: File = File(context.filesDir, CRASH_DIR)
    private val anrDir: File = File(context.filesDir, ANR_DIR)
    private var crashHandler: CrashHandler? = null
    private var anrWatchdog: AnrWatchdog? = null

    private val appVersion: String by lazy {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    fun init() {
        Log.d(TAG, "CrashGuard initializing, app version: $appVersion")

        // Check if last session ended with a crash
        checkPreviousCrash()

        // Setup crash handler
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        crashHandler = CrashHandler(
            crashDir = crashDir,
            appVersion = appVersion,
            originalHandler = originalHandler,
        ).apply {
            onCrashCaught = { record ->
                // Try to save to Room, though process may die before completion
                scope.launch {
                    try {
                        crashDao.insert(record)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert crash record into database", e)
                    }
                }
            }
        }
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        // Start ANR watchdog
        anrWatchdog = AnrWatchdog(
            anrDir = anrDir,
            appVersion = appVersion,
        ).apply {
            onAnrDetected = { record ->
                scope.launch {
                    try {
                        crashDao.insert(record)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert ANR record into database", e)
                    }
                }
            }
        }
        anrWatchdog?.start()

        Log.d(TAG, "CrashGuard initialized successfully")
    }

    private fun checkPreviousCrash() {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val previousCount = prefs.getInt(LAST_CRASH_COUNT_KEY, 0)
                val currentCount = crashDao.getCount()

                if (currentCount > previousCount) {
                    // App crashed in previous session
                    val crashCount = currentCount - previousCount
                    Log.w(TAG, "Previous session crashed $crashCount time(s)")

                    // Update crash records to mark them as launch crashes
                    val allCrashes = crashDao.getAll()
                    for (i in allCrashes.indices.reversed()) {
                        val count = i + 1
                        if (count > crashCount) break
                        // Already marked as launch crash by CrashHandler
                    }
                }

                prefs.edit().putInt(LAST_CRASH_COUNT_KEY, currentCount).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check previous crash", e)
            }
        }
    }

    fun getCrashLogs(): List<File> {
        return crashDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
    }

    fun getAnrLogs(): List<File> {
        return anrDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
    }

    fun getCrashCount(): Int {
        return crashDir.listFiles()?.size ?: 0
    }
}
