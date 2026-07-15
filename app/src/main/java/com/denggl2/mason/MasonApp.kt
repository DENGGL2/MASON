package com.denggl2.mason

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.denggl2.mason.automation.AutomationScheduler
import com.denggl2.mason.automation.AutomationEventMonitor
import com.denggl2.mason.crashguard.CrashGuard
import com.denggl2.mason.integration.IntegrationManager
import com.denggl2.mason.tool.NotificationTool
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MasonApp : Application() {

    @Inject
    lateinit var crashGuard: CrashGuard

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    @Inject
    lateinit var automationEventMonitor: AutomationEventMonitor

    @Inject
    lateinit var integrationManager: IntegrationManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        crashGuard.init()
        createNotificationChannel()
        automationEventMonitor.start(this)
        applicationScope.launch {
            automationScheduler.reconcileAtStartup()
        }
        applicationScope.launch {
            integrationManager.refreshAll()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationTool.CHANNEL_ID,
                NotificationTool.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Mason Tool 发送的通知"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
