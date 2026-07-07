package com.denggl2.mason.tool

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
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
