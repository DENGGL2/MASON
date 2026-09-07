package com.denggl2.masonremote.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.denggl2.masonremote.R
import com.denggl2.masonremote.ui.remote.RemoteTaskNotificationEvent
import com.denggl2.masonremote.ui.settings.TaskNotificationMode

data class RemoteNotificationResult(
    val sent: Boolean,
    val needsPermission: Boolean = false,
    val message: String? = null,
)

/** Notification behavior kept aligned with MASON's computer-side task notifications. */
class RemoteNotificationManager(
    private val context: Context,
    private val english: Boolean = false,
    private val activityClass: Class<*>? = null,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeNotificationIds = linkedSetOf<Int>()

    init {
        createChannels()
    }

    fun shouldRequestPostNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

    fun preview(mode: TaskNotificationMode): RemoteNotificationResult {
        if (mode == TaskNotificationMode.DISABLED) {
            cancelAll()
            return RemoteNotificationResult(sent = true)
        }
        if (shouldRequestPostNotificationPermission()) {
            return RemoteNotificationResult(
                sent = false,
                needsPermission = true,
                message = text(
                    "缺少通知权限，请在系统设置中允许 CloudX 发送通知",
                    "Notification permission is missing. Allow CloudX notifications in system settings",
                ),
            )
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return RemoteNotificationResult(
                sent = false,
                message = text(
                    "系统通知已关闭，请在系统设置中允许 CloudX 发送通知",
                    "System notifications are disabled. Allow CloudX notifications in system settings",
                ),
            )
        }

        val useIsland = canUseNotificationIsland(mode)
        val notificationId = PREVIEW_NOTIFICATION_ID
        mainHandler.removeCallbacksAndMessages(PREVIEW_TOKEN)
        notificationManager.cancel(PREVIEW_NOTIFICATION_ID)
        val previewText = text(
            "CloudX 会在电脑端任务状态变化时通知你",
            "CloudX will notify you when a task changes on the computer",
        )
        val islandFallback = mode == TaskNotificationMode.ISLAND && !useIsland
        val text = if (islandFallback) {
            this.text(
                "$previewText；当前系统不支持 Android 16 岛通知，已回退为常规任务通知",
                "$previewText. Android 16 live update notifications are unavailable, so regular task notifications will be used",
            )
        } else {
            previewText
        }
        post(
            notificationId = notificationId,
            title = when {
                islandFallback -> this.text("岛通知不可用，已回退", "Live update unavailable; using regular notifications")
                mode == TaskNotificationMode.ISLAND -> this.text("岛通知已选择", "Live update notifications selected")
                else -> this.text("常规通知已启用", "Regular notifications enabled")
            },
            text = text,
            liveUpdate = useIsland,
            progress = 35,
            shortText = if (useIsland) this.text("岛通知", "Live") else null,
            final = false,
            threadId = null,
        )
        if (useIsland) {
            mainHandler.postAtTime(
                {
                    post(
                        notificationId = PREVIEW_NOTIFICATION_ID,
                        title = text("岛通知已选择", "Live update notifications selected"),
                        text = text(
                            "CloudX 会在电脑端任务状态变化时通知你",
                            "CloudX will notify you when a task changes on the computer",
                        ),
                        liveUpdate = true,
                        progress = 100,
                        shortText = text("完成", "Done"),
                        final = true,
                        threadId = null,
                    )
                },
                PREVIEW_TOKEN,
                System.currentTimeMillis() + 3_000L,
            )
        }
        return RemoteNotificationResult(sent = true)
    }

    fun notifyTaskEvent(
        event: RemoteTaskNotificationEvent,
        mode: TaskNotificationMode,
    ): RemoteNotificationResult {
        if (mode == TaskNotificationMode.DISABLED) return RemoteNotificationResult(sent = true)
        if (shouldRequestPostNotificationPermission()) {
            return RemoteNotificationResult(sent = false, needsPermission = true)
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return RemoteNotificationResult(
                sent = false,
                message = text(
                    "系统通知已关闭，请在系统设置中允许 CloudX 发送通知",
                    "System notifications are disabled. Allow CloudX notifications in system settings",
                ),
            )
        }

        val useIsland = canUseNotificationIsland(mode)
        val notificationId = stableNotificationId(event.threadId)
        val (title, text, progress, shortText, final) = when (event.kind) {
            RemoteTaskNotificationEvent.Kind.RUNNING -> NotificationContent(
                title = text("CloudX 正在处理任务", "CloudX is working on a task"),
                text = text(
                    "${event.conversationTitle} 正在电脑端执行，可点按返回 CloudX 查看进度",
                    "${event.conversationTitle} is running on the computer. Tap to view progress in CloudX",
                ),
                progress = 5,
                shortText = text("执行中", "Running"),
                final = false,
            )
            RemoteTaskNotificationEvent.Kind.WAITING_FOR_PERMISSION -> NotificationContent(
                title = text("CloudX 需要你的确认", "CloudX needs your approval"),
                text = text(
                    "${event.conversationTitle} 等待你批准电脑端权限请求",
                    "${event.conversationTitle} is waiting for you to approve a computer permission request",
                ),
                progress = 50,
                shortText = text("需确认", "Approve"),
                final = false,
            )
            RemoteTaskNotificationEvent.Kind.COMPLETED -> NotificationContent(
                title = text("CloudX 已完成", "CloudX task completed"),
                text = event.detail ?: text(
                    "${event.conversationTitle} 已处理完成，可点按返回查看结果",
                    "${event.conversationTitle} is complete. Tap to view the result",
                ),
                progress = 100,
                shortText = text("已完成", "Done"),
                final = true,
            )
            RemoteTaskNotificationEvent.Kind.FAILED -> NotificationContent(
                title = text("CloudX 任务失败", "CloudX task failed"),
                text = event.detail ?: text(
                    "${event.conversationTitle} 未能完成，可返回 CloudX 查看原因",
                    "${event.conversationTitle} could not be completed. Return to CloudX for details",
                ),
                progress = 100,
                shortText = text("失败", "Failed"),
                final = true,
            )
        }
        val sent = post(
            notificationId = notificationId,
            title = title,
            text = text,
            liveUpdate = useIsland,
            progress = progress,
            shortText = shortText,
            final = final,
            threadId = event.threadId,
        )
        return RemoteNotificationResult(sent = sent)
    }

    fun cancelAll() {
        mainHandler.removeCallbacksAndMessages(PREVIEW_TOKEN)
        notificationManager.cancelAll()
        activeNotificationIds.forEach(notificationManager::cancel)
        activeNotificationIds.clear()
    }

    private fun canUseNotificationIsland(mode: TaskNotificationMode): Boolean =
        mode == TaskNotificationMode.ISLAND && Build.VERSION.SDK_INT >= 36 &&
            notificationManager.canPostPromotedNotifications()

    private fun post(
        notificationId: Int,
        title: String,
        text: String,
        liveUpdate: Boolean,
        progress: Int,
        shortText: String?,
        final: Boolean,
        threadId: String?,
    ): Boolean {
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, activityClass ?: resolveLaunchActivity(context)).apply {
                action = ACTION_OPEN_TASK
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                threadId?.let { putExtra(EXTRA_THREAD_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(
            context,
            if (liveUpdate) LIVE_UPDATE_CHANNEL_ID else CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_mason)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(
                if (liveUpdate) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(if (liveUpdate) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(contentIntent)
            .setAutoCancel(!liveUpdate || final)

        if (liveUpdate) {
            builder
                .setOngoing(!final)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setProgress(100, progress.coerceIn(0, 100), false)
                .setShortCriticalText(shortText?.take(7).orEmpty().ifBlank { "$progress%" })
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setStyledByProgress(true)
                        .setProgress(progress.coerceIn(0, 100)),
                )
            if (!final && Build.VERSION.SDK_INT >= 36) builder.setRequestPromotedOngoing(true)
        }
        notificationManager.notify(notificationId, builder.build())
        activeNotificationIds += notificationId
        return true
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Android keeps channel records across upgrades. Remove old branded
        // channels, including renamed variants.
        notificationManager.notificationChannels
            .filter { channel ->
                sequenceOf(channel.id, channel.name?.toString(), channel.description)
                    .filterNotNull()
                    .any { value -> value.contains("mason", ignoreCase = true) }
            }
            .forEach { channel -> notificationManager.deleteNotificationChannel(channel.id) }
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(LEGACY_LIVE_UPDATE_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(PREVIOUS_LIVE_UPDATE_CHANNEL_ID)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                text("CloudX 工具通知", "CloudX task notifications"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = text(
                    "CloudX 发送的任务状态通知",
                    "Task status notifications from CloudX",
                )
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                LIVE_UPDATE_CHANNEL_ID,
                text("CloudX 任务实时状态", "CloudX live task updates"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = text(
                    "用于 Android 16 的任务实时通知",
                    "Live task update notifications for Android 16",
                )
            },
        )
    }

    private fun resolveLaunchActivity(context: Context): Class<*> = runCatching {
        Class.forName("${context.packageName}.MainActivity")
    }.getOrDefault(context.javaClass)

    private fun text(chinese: String, englishText: String): String =
        if (english) englishText else chinese

    private fun stableNotificationId(threadId: String): Int =
        (threadId.hashCode() and 0x7fffffff).coerceAtLeast(47010)

    private data class NotificationContent(
        val title: String,
        val text: String,
        val progress: Int,
        val shortText: String,
        val final: Boolean,
    )

    companion object {
        const val CHANNEL_ID = "cloudx_tool_notification"
        const val LIVE_UPDATE_CHANNEL_ID = "cloudx_task_live_update_v2"
        const val ACTION_OPEN_TASK = "com.denggl2.masonremote.action.OPEN_TASK"
        const val EXTRA_THREAD_ID = "thread_id"
        private const val PREVIEW_NOTIFICATION_ID = 47002
        private const val LEGACY_CHANNEL_ID = "mason_tool_notification"
        private const val LEGACY_LIVE_UPDATE_CHANNEL_ID = "mason_task_live_update"
        private const val PREVIOUS_LIVE_UPDATE_CHANNEL_ID = "cloudx_task_live_update"
        private val PREVIEW_TOKEN = Any()
    }
}
