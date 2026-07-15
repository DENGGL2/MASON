package com.denggl2.mason.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionItem(
    val permission: String,
    val label: String,
    val group: PermissionGroup,
    val isGranted: Boolean,
    val settingsIntent: Intent?,
)

enum class PermissionGroup(val label: String) {
    HARDWARE("硬件"),
    COMMUNICATION("通讯"),
    SYSTEM("系统"),
    NETWORK("网络"),
}

@HiltViewModel
class PermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _permissions.value = listOf(
                // ── 硬件 ──
                permissionItem(
                    Manifest.permission.CAMERA,
                    "相机",
                    PermissionGroup.HARDWARE,
                ),
                permissionItem(
                    Manifest.permission.RECORD_AUDIO,
                    "麦克风",
                    PermissionGroup.HARDWARE,
                ),

                // ── 通讯 ──
                permissionItem(
                    Manifest.permission.READ_CONTACTS,
                    "通讯录",
                    PermissionGroup.COMMUNICATION,
                ),
                permissionItem(
                    Manifest.permission.READ_PHONE_STATE,
                    "电话状态",
                    PermissionGroup.COMMUNICATION,
                ),
                permissionItem(
                    Manifest.permission.READ_CALL_LOG,
                    "通话记录",
                    PermissionGroup.COMMUNICATION,
                ),
                permissionItem(
                    Manifest.permission.SEND_SMS,
                    "发送短信",
                    PermissionGroup.COMMUNICATION,
                ),
                permissionItem(
                    Manifest.permission.READ_SMS,
                    "读取短信",
                    PermissionGroup.COMMUNICATION,
                ),
                permissionItem(
                    Manifest.permission.READ_CALENDAR,
                    "读取日历",
                    PermissionGroup.COMMUNICATION,
                ),

                // ── 系统 ──
                PermissionItem(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    label = "通知",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.areNotificationsEnabled()
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    },
                    settingsIntent = appSettingsIntent(),
                ),
                PermissionItem(
                    permission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                    label = "通知使用权（用于自动化触发）",
                    group = PermissionGroup.SYSTEM,
                    isGranted = context.packageName in
                        NotificationManagerCompat.getEnabledListenerPackages(context),
                    settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                ),
                PermissionItem(
                    permission = "android.settings.MANAGE_OVERLAY_PERMISSION",
                    label = "悬浮窗",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else true,
                    settingsIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                ),
                PermissionItem(
                    permission = "android.settings.USAGE_STATS_SETTINGS",
                    label = "使用情况访问",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                            val mode = appOps.checkOpNoThrow(
                                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                                android.os.Process.myUid(),
                                context.packageName,
                            )
                            mode == android.app.AppOpsManager.MODE_ALLOWED
                        } catch (e: Exception) {
                            false
                        }
                    } else true,
                    settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                ),
                PermissionItem(
                    permission = Manifest.permission.WRITE_SETTINGS,
                    label = "修改系统设置",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.System.canWrite(context)
                    } else true,
                    settingsIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                ),
                PermissionItem(
                    permission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    label = "忽略电池优化",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        pm.isIgnoringBatteryOptimizations(context.packageName)
                    } else true,
                    settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                ),
                PermissionItem(
                    permission = Manifest.permission.SCHEDULE_EXACT_ALARM,
                    label = "精确闹钟",
                    group = PermissionGroup.SYSTEM,
                    isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        am.canScheduleExactAlarms()
                    } else true,
                    settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                ),

                // ── 网络 ──
                permissionItem(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    "精确定位",
                    PermissionGroup.NETWORK,
                ),
                permissionItem(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "粗略定位",
                    PermissionGroup.NETWORK,
                ),
                permissionItem(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    "蓝牙连接",
                    PermissionGroup.NETWORK,
                ),
            )
        }
    }

    private fun permissionItem(
        permission: String,
        label: String,
        group: PermissionGroup,
    ): PermissionItem {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        return PermissionItem(
            permission = permission,
            label = label,
            group = group,
            isGranted = granted,
            settingsIntent = if (!granted) appSettingsIntent() else null,
        )
    }

    private fun appSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}
