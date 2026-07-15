package com.denggl2.mason.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.denggl2.mason.data.MasonAutomationAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationCapabilityIssue(
    val code: String,
    val message: String,
)

@Singleton
class AutomationCapabilityInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun inspect(triggerType: String, actions: List<MasonAutomationAction>): List<AutomationCapabilityIssue> =
        buildList {
            if (triggerType == AutomationScheduler.TRIGGER_LOCATION ||
                actions.any { it.type == AutomationRunner.ACTION_TOOL && it.arguments["tool_name"] == "location" }
            ) {
                requireRuntimePermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    "location",
                    "缺少精确定位权限，位置自动化无法运行",
                )
            }
            if (triggerType == AutomationScheduler.TRIGGER_BLUETOOTH) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requireRuntimePermission(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        "bluetooth",
                        "缺少蓝牙连接权限，无法识别目标设备",
                    )
                }
            }
            if (actions.any { action ->
                    action.type == AutomationRunner.ACTION_TOOL && action.arguments["tool_name"] == "calendar"
                }
            ) {
                requireRuntimePermission(
                    Manifest.permission.READ_CALENDAR,
                    "calendar",
                    "缺少日历读取权限，无法读取日程",
                )
            }
            if (triggerType == AutomationScheduler.TRIGGER_NOTIFICATION &&
                context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)
            ) {
                add(
                    AutomationCapabilityIssue(
                        "notification_listener",
                        "尚未授予通知使用权，通知触发器不会生效",
                    ),
                )
            }
            if (actions.any { it.type == "notification" } &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                requireRuntimePermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                    "post_notifications",
                    "缺少通知权限，完成提醒可能无法送达",
                )
            }
        }.distinctBy(AutomationCapabilityIssue::code)

    private fun MutableList<AutomationCapabilityIssue>.requireRuntimePermission(
        permission: String,
        code: String,
        message: String,
    ) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            add(AutomationCapabilityIssue(code, message))
        }
    }
}
