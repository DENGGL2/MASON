package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class NotificationTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "notification"
    override val description = "发送系统通知"
    override val parameters: Map<String, ParameterDef> = mapOf(        "title" to ParameterDef("string", "通知标题", required = true),
        "content" to ParameterDef("string", "通知内容", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val title = args["title"] ?: return ToolResult.error("Missing title")
        val content = args["content"] ?: return ToolResult.error("Missing content")
        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("mason", "Mason", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val nb = android.app.Notification.Builder(context, "mason")
            .setContentTitle(title).setContentText(content).setSmallIcon(android.R.drawable.ic_dialog_info)
        nm.notify(System.currentTimeMillis().toInt(), nb.build())
        return ToolResult.success(mapOf("sent" to "true"))
    }
}
