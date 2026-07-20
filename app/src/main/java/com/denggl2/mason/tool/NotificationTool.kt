package com.denggl2.mason.tool

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.denggl2.mason.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "notification"
    override val description = "发送系统通知"
    override val parameters = mapOf(
        "title" to ParameterDef(
            type = "string",
            description = "通知标题",
            required = true,
        ),
        "text" to ParameterDef(
            type = "string",
            description = "通知内容",
            required = true,
        ),
    )

    companion object {
        const val CHANNEL_ID = "mason_tool_notification"
        const val CHANNEL_NAME = "Mason 工具通知"
        const val ISLAND_CHANNEL_ID = "mason_island_notification"
        const val ISLAND_CHANNEL_NAME = "Mason 通知岛"
        const val ACTION_OPEN_TASK = "com.denggl2.mason.action.OPEN_TASK"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_TASK_COMMAND = "task_command"
        const val EXTRA_ARTIFACT_PATH = "artifact_path"
        const val TASK_ACTION_PAUSED = "paused"
        const val TASK_COMMAND_RESUME = "resume"
        const val TASK_COMMAND_CANCEL = "cancel"
        const val TASK_COMMAND_OPEN_ARTIFACT = "open_artifact"
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val title = args["title"] ?: return ToolResult(
            success = false,
            error = "缺少 title 参数",
        )
        val text = args["text"] ?: return ToolResult(
            success = false,
            error = "缺少 text 参数",
        )
        val islandMode = args["delivery_mode"].equals("ISLAND", ignoreCase = true)
        val conversationId = args[EXTRA_CONVERSATION_ID]?.toLongOrNull()?.takeIf { it > 0L }
        val pausedTask = args["task_action"] == TASK_ACTION_PAUSED && conversationId != null
        val artifactPath = args[EXTRA_ARTIFACT_PATH]?.takeIf { it.isNotBlank() }

        return try {
            // Android 13+ 需要 POST_NOTIFICATIONS 权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return ToolResult(
                        success = false,
                        error = "缺少通知权限 (POST_NOTIFICATIONS)，请在设置中授予",
                    )
                }
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_TASK
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                conversationId?.let { putExtra(EXTRA_CONVERSATION_ID, it) }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun taskActionIntent(command: String, requestCodeOffset: Int): PendingIntent =
                PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt() + requestCodeOffset,
                    Intent(contentIntent).apply {
                        putExtra(EXTRA_TASK_COMMAND, command)
                        artifactPath?.let { putExtra(EXTRA_ARTIFACT_PATH, it) }
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification = NotificationCompat.Builder(
                context,
                if (islandMode) ISLAND_CHANNEL_ID else CHANNEL_ID,
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(if (islandMode) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(if (islandMode) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .apply {
                    if (pausedTask) {
                        addAction(
                            android.R.drawable.ic_media_play,
                            "继续任务",
                            taskActionIntent(TASK_COMMAND_RESUME, 1),
                        )
                        addAction(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "取消任务",
                            taskActionIntent(TASK_COMMAND_CANCEL, 2),
                        )
                    }
                    artifactPath?.let { path ->
                        addAction(
                            android.R.drawable.ic_menu_view,
                            artifactNotificationActionLabel(path),
                            taskActionIntent(TASK_COMMAND_OPEN_ARTIFACT, 3),
                        )
                    }
                }
                .build()

            val notificationId = System.currentTimeMillis().toInt()
            nm.notify(notificationId, notification)

            ToolResult(
                success = true,
                data = mapOf(
                    "notification_id" to notificationId.toString(),
                    "status" to "已发送",
                ),
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "发送通知失败: ${e.message}",
            )
        }
    }
}

internal fun artifactNotificationActionLabel(path: String): String = when (
    File(path).extension.lowercase(Locale.ROOT)
) {
    "jpg", "jpeg", "png", "webp", "gif", "svg" -> "查看图片"
    "mp4", "mkv", "mov", "webm" -> "查看视频"
    "mp3", "wav", "m4a", "aac", "ogg", "flac" -> "播放音频"
    "txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "查看文档"
    else -> "查看文件"
}
