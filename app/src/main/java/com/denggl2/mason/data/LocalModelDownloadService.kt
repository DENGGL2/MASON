package com.denggl2.mason.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.denggl2.mason.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocalModelDownloadService : Service() {
    @Inject
    lateinit var coordinator: LocalModelDownloadCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private var currentModelId: String? = null
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (modelId == currentModelId) downloadJob?.cancel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (modelId.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startDownload(modelId, startId)
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(modelId: String, startId: Int) {
        if (downloadJob?.isActive == true) return
        val model = LocalModelCatalog.get(modelId) ?: run {
            stopSelf(startId)
            return
        }
        currentModelId = modelId
        startForegroundCompat(
            buildNotification(
                model,
                LocalModelDownloadState(
                    modelId = model.id,
                    status = LocalModelDownloadStatus.Checking,
                    totalBytes = model.expectedSizeBytes,
                    message = "正在连接模型源",
                ),
                active = true,
            ),
        )
        downloadJob = serviceScope.launch {
            val finalState = coordinator.runDownload(modelId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyFinal(model, finalState)
            currentModelId = null
            stopSelf()
        }

        serviceScope.launch {
            coordinator.states.collect { states ->
                val state = states[modelId] ?: return@collect
                if (state.status in activeStatuses) updateProgressNotification(model, state)
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgressNotification(model: LocalModelPreset, state: LocalModelDownloadState) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < 750L) return
        lastNotificationAt = now
        notificationManager().notify(
            NOTIFICATION_ID,
            buildNotification(model, state, active = true),
        )
    }

    private fun notifyFinal(model: LocalModelPreset, state: LocalModelDownloadState) {
        val notification = when (state.status) {
            LocalModelDownloadStatus.Completed -> buildNotification(model, state, active = false)
            LocalModelDownloadStatus.Failed -> buildNotification(model, state, active = false)
            LocalModelDownloadStatus.Paused -> buildNotification(model, state, active = false)
            else -> null
        }
        if (notification != null) {
            notificationManager().notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        model: LocalModelPreset,
        state: LocalModelDownloadState?,
        active: Boolean,
    ): Notification {
        val current = state ?: LocalModelDownloadState(
            modelId = model.id,
            status = LocalModelDownloadStatus.Checking,
            totalBytes = model.expectedSizeBytes,
        )
        val percent = (current.progress * 100).toInt().coerceIn(0, 100)
        val title = when (current.status) {
            LocalModelDownloadStatus.Completed -> "${model.name} 已可用"
            LocalModelDownloadStatus.Failed -> "${model.name} 下载失败"
            LocalModelDownloadStatus.Paused -> "${model.name} 已暂停"
            LocalModelDownloadStatus.Verifying -> "正在校验 ${model.name}"
            else -> "正在下载 ${model.name}"
        }
        val text = current.message ?: when (current.status) {
            LocalModelDownloadStatus.Downloading ->
                "${formatDownloadBytes(current.downloadedBytes)} / ${formatDownloadBytes(current.totalBytes)}"
            LocalModelDownloadStatus.Completed -> "文件校验通过，可离线使用"
            LocalModelDownloadStatus.Paused -> "打开 Mason 可继续下载"
            LocalModelDownloadStatus.Verifying -> "正在进行 SHA-256 校验"
            else -> "正在连接模型源"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (current.status == LocalModelDownloadStatus.Completed) {
                android.R.drawable.stat_sys_download_done
            } else {
                android.R.drawable.stat_sys_download
            })
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(active)
            .setAutoCancel(!active)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (active && current.status == LocalModelDownloadStatus.Downloading) {
            builder.setProgress(100, percent, false)
        } else if (active) {
            builder.setProgress(0, 0, true)
        }
        if (active) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                pausePendingIntent(model.id),
            )
        }
        return builder.build()
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pausePendingIntent(modelId: String): PendingIntent = PendingIntent.getService(
        this,
        modelId.hashCode(),
        commandIntent(this, ACTION_PAUSE, modelId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager().createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "本地模型下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示本地 AI 模型的后台下载进度"
            },
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val ACTION_START = "com.denggl2.mason.action.START_LOCAL_MODEL_DOWNLOAD"
        private const val ACTION_PAUSE = "com.denggl2.mason.action.PAUSE_LOCAL_MODEL_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "local_model_downloads"
        private const val NOTIFICATION_ID = 4104

        private val activeStatuses = setOf(
            LocalModelDownloadStatus.Checking,
            LocalModelDownloadStatus.Downloading,
            LocalModelDownloadStatus.Verifying,
        )

        fun start(context: Context, modelId: String) {
            context.startForegroundService(commandIntent(context, ACTION_START, modelId))
        }

        fun pause(context: Context, modelId: String) {
            context.startService(commandIntent(context, ACTION_PAUSE, modelId))
        }

        fun clearNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        private fun commandIntent(context: Context, action: String, modelId: String): Intent =
            Intent(context, LocalModelDownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_MODEL_ID, modelId)
            }
    }
}
