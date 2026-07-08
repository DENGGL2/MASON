package com.denggl2.mason.tool

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Singleton
class AppManagerTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "app_manager"
    override val description = "应用管理：卸载、强制停止、清除数据、查看详情。卸载和清除数据需用户手动确认。"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：uninstall（卸载）/ force_stop（强制停止）/ clear_data（清除数据）/ details（查看详情）",
            required = true,
            enum = listOf("uninstall", "force_stop", "clear_data", "details"),
        ),
        "package_name" to ParameterDef(
            type = "string",
            description = "目标应用的包名，如 com.example.app",
            required = true,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )
        val packageName = args["package_name"] ?: return ToolResult(
            success = false,
            error = "缺少 package_name 参数",
        )

        return try {
            // Validate package exists
            try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return ToolResult(
                    success = false,
                    error = "未找到包名为 $packageName 的应用",
                )
            }

            when (action) {
                "uninstall" -> uninstallApp(packageName)
                "force_stop" -> forceStopApp(packageName)
                "clear_data" -> clearAppData(packageName)
                "details" -> getAppDetails(packageName)
                else -> ToolResult(success = false, error = "未知 action: $action")
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "操作失败: ${e.message}")
        }
    }

    private fun uninstallApp(packageName: String): ToolResult {
        if (isSystemApp(packageName)) {
            return ToolResult(
                success = false,
                error = "无法卸载系统应用: $packageName。系统应用受保护。",
            )
        }

        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult(
                success = true,
                data = mapOf(
                    "action" to "uninstall",
                    "package_name" to packageName,
                    "status" to "已启动卸载界面，请在系统对话框中确认卸载",
                ),
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                error = "无法启动卸载界面: ${e.message}",
            )
        }
    }

    private fun forceStopApp(packageName: String): ToolResult {
        if (isSystemApp(packageName)) {
            return ToolResult(
                success = false,
                error = "无法强制停止系统应用: $packageName。系统应用受保护。",
            )
        }

        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            return ToolResult(
                success = true,
                data = mapOf(
                    "action" to "force_stop",
                    "package_name" to packageName,
                    "status" to "已终止 $packageName 的后台进程",
                ),
            )
        } catch (e: SecurityException) {
            return ToolResult(
                success = false,
                error = "权限不足，无法强制停止应用: ${e.message}",
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                error = "强制停止失败: ${e.message}",
            )
        }
    }

    private fun clearAppData(packageName: String): ToolResult {
        if (isSystemApp(packageName)) {
            return ToolResult(
                success = false,
                error = "无法清除系统应用数据: $packageName。系统应用受保护。",
            )
        }

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult(
                success = true,
                data = mapOf(
                    "action" to "clear_data",
                    "package_name" to packageName,
                    "status" to "已跳转至应用详情页，请在系统设置中手动清除数据",
                ),
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                error = "无法跳转至应用详情页: ${e.message}",
            )
        }
    }

    private fun getAppDetails(packageName: String): ToolResult {
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val appInfo = pi.applicationInfo ?: return ToolResult(
                success = false,
                error = "Application info is unavailable for $packageName",
            )

            val appName = pm.getApplicationLabel(appInfo).toString()
            val versionName = pi.versionName ?: "未知"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toString()
            }
            val targetSdk = appInfo.targetSdkVersion.toString()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val firstInstallTime = dateFormat.format(Date(pi.firstInstallTime))
            val lastUpdateTime = dateFormat.format(Date(pi.lastUpdateTime))

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Permissions
            val permissions = pi.requestedPermissions?.toList() ?: emptyList()
            val permCount = permissions.size

            // Build summary
            val summary = buildString {
                appendLine("应用详情: $appName")
                appendLine("包名: $packageName")
                appendLine("版本: $versionName ($versionCode)")
                appendLine("Target SDK: $targetSdk")
                appendLine("首次安装: $firstInstallTime")
                appendLine("最后更新: $lastUpdateTime")
                appendLine("系统应用: ${if (isSystem) "是" else "否"}")
                if (isUpdatedSystem) appendLine("  (系统应用已更新)")
                appendLine("权限数量: $permCount")
                if (permissions.isNotEmpty()) {
                    appendLine()
                    appendLine("权限列表:")
                    permissions.forEachIndexed { index, perm ->
                        appendLine("  ${index + 1}. $perm")
                    }
                }
            }

            return ToolResult(
                success = true,
                data = mapOf(
                    "app_name" to appName,
                    "package_name" to packageName,
                    "version_name" to versionName,
                    "version_code" to versionCode,
                    "target_sdk" to targetSdk,
                    "first_install_time" to firstInstallTime,
                    "last_update_time" to lastUpdateTime,
                    "is_system" to (isSystem || isUpdatedSystem).toString(),
                    "permission_count" to permCount.toString(),
                    "details" to summary,
                ),
            )
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult(
                success = false,
                error = "未找到包名为 $packageName 的应用",
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                error = "获取应用详情失败: ${e.message}",
            )
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(packageName, 0)
            val appInfo = pi.applicationInfo ?: return false
            val isSystemFlag = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isLowUid = appInfo.uid < 10000
            isSystemFlag || isUpdatedSystem || isLowUid
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
