package com.denggl2.mason

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.denggl2.mason.crashguard.CrashGuard
import com.denggl2.mason.tool.NotificationTool
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MasonApp : Application() {

    @Inject
    lateinit var crashGuard: CrashGuard

    override fun onCreate() {
        super.onCreate()
        crashGuard.init()
        createNotificationChannel()
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
