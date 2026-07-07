package com.denggl2.mason.tool

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "alarm"
    override val description = "闹钟管理：设置、取消闹钟/定时器。闹钟触发时通过通知提醒，不做全屏闹钟界面"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：set（设置闹钟）、cancel（取消闹钟）、list（列出所有闹钟）",
            required = true,
            enum = listOf("set", "cancel", "list"),
        ),
        "label" to ParameterDef(
            type = "string",
            description = "闹钟标签/名称",
            required = false,
        ),
        "time" to ParameterDef(
            type = "string",
            description = "闹钟时间 (ISO 8601 格式)，action=set 时必填，如 2026-07-07T14:00:00",
            required = false,
        ),
        "alarm_id" to ParameterDef(
            type = "string",
            description = "闹钟 ID，action=cancel 时必填，从 list 获取",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false, error = "缺少 action 参数"
        )

        return when (action) {
            "set" -> setAlarm(args["label"], args["time"])
            "cancel" -> cancelAlarm(args["alarm_id"])
            "list" -> listAlarms()
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun setAlarm(label: String?, timeStr: String?): ToolResult {
        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                return ToolResult(
                    success = false,
                    error = "缺少精确闹钟权限 (SCHEDULE_EXACT_ALARM)，请在系统设置中授予「闹钟和提醒」权限",
                )
            }
        }

        if (timeStr.isNullOrBlank()) {
            return ToolResult(success = false, error = "set 操作需要 time 参数 (ISO 8601)")
        }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        isoFormat.timeZone = TimeZone.getDefault()
        val triggerTime: Long = try {
            val date = isoFormat.parse(timeStr)
            if (date == null) {
                return ToolResult(success = false, error = "无法解析时间: $timeStr")
            }
            date.time
        } catch (e: Exception) {
            return ToolResult(success = false, error = "时间格式错误，请使用 ISO 8601 格式 (如 2026-07-07T14:00:00): ${e.message}")
        }

        if (triggerTime <= System.currentTimeMillis()) {
            return ToolResult(success = false, error = "闹钟时间已过，请设置未来的时间")
        }

        val alarmId = UUID.randomUUID().toString().take(8)
        val alarmLabel = label ?: "闹钟"

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, alarmLabel)
        }

        val requestCode = alarmId.hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent, flags,
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
                )
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getDefault()

            // Store alarm info
            alarms[alarmId] = AlarmInfo(
                id = alarmId,
                label = alarmLabel,
                triggerTime = triggerTime,
                pendingIntent = pendingIntent,
            )

            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "set",
                    "alarm_id" to alarmId,
                    "label" to alarmLabel,
                    "trigger_time" to dateFormat.format(Date(triggerTime)),
                    "trigger_timestamp_ms" to triggerTime.toString(),
                ),
            )
        } catch (e: Exception) {
            ToolResult(success = false, error = "设置闹钟失败: ${e.message}")
        }
    }

    private fun cancelAlarm(alarmId: String?): ToolResult {
        if (alarmId.isNullOrBlank()) {
            return ToolResult(success = false, error = "cancel 操作需要 alarm_id 参数")
        }

        val alarmInfo = alarms[alarmId]
            ?: return ToolResult(success = false, error = "未找到闹钟 ID: $alarmId")

        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(alarmInfo.pendingIntent)
            alarms.remove(alarmId)

            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "cancelled",
                    "alarm_id" to alarmId,
                    "label" to alarmInfo.label,
                ),
            )
        } catch (e: Exception) {
            ToolResult(success = false, error = "取消闹钟失败: ${e.message}")
        }
    }

    private fun listAlarms(): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()

        if (alarms.isEmpty()) {
            return ToolResult(success = true, data = mapOf("message" to "没有已设置的闹钟"))
        }

        val list = alarms.values
            .sortedBy { it.triggerTime }
            .joinToString("\n") { info ->
                val countdown = (info.triggerTime - now) / 1000
                buildString {
                    append("ID: ${info.id}")
                    append(" | 标签: ${info.label}")
                    append(" | 触发时间: ${dateFormat.format(Date(info.triggerTime))}")
                    if (countdown > 0) {
                        val hours = countdown / 3600
                        val mins = (countdown % 3600) / 60
                        append(" | 倒计时: ${hours}h${mins}m")
                    } else {
                        append(" | 已触发")
                    }
                }
            }

        return ToolResult(
            success = true,
            data = mapOf(
                "count" to alarms.size.toString(),
                "alarms" to list,
            ),
        )
    }

    data class AlarmInfo(
        val id: String,
        val label: String,
        val triggerTime: Long,
        val pendingIntent: PendingIntent,
    )

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"

        val alarms = ConcurrentHashMap<String, AlarmInfo>()
    }
}

/**
 * BroadcastReceiver for alarm trigger. Shows a notification when alarm fires.
 * Must be declared in AndroidManifest.xml.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmTool.EXTRA_ALARM_ID) ?: return
        val alarmLabel = intent.getStringExtra(AlarmTool.EXTRA_ALARM_LABEL) ?: "闹钟"

        // Remove from active alarms
        AlarmTool.alarms.remove(alarmId)

        // Show notification
        val notificationId = alarmId.hashCode()
        val channelId = "mason_alarm_channel"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Mason 闹钟")
            .setContentText(alarmLabel)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(notificationId, notification)
    }
}
