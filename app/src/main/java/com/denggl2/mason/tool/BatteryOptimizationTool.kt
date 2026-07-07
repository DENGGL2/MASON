package com.denggl2.mason.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryOptimizationTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "battery_opt"
    override val description = "电池优化设置：检查电池优化状态、请求加入白名单、查看是否已忽略电池优化"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：check（检查电池优化状态）/ request（请求加入电池优化白名单）/ ignore（查看是否已忽略电池优化）",
            required = true,
            enum = listOf("check", "request", "ignore"),
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return try {
            when (action) {
                "check" -> checkBatteryOptimization()
                "request" -> requestIgnoreBatteryOptimization()
                "ignore" -> checkIgnoreStatus()
                else -> ToolResult(success = false, error = "未知 action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult(success = false, error = "权限不足: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "操作失败: ${e.message}")
        }
    }

    private fun checkBatteryOptimization(): ToolResult {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        val isOptimizing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            // Before M, battery optimization was less restrictive
            false
        }

        // Check if device has battery optimization feature
        val hasBatteryOptimization = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

        val status = when {
            !hasBatteryOptimization -> "设备不支持电池优化功能"
            isOptimizing -> "系统正在对 $packageName 进行电池优化（可能会限制后台活动）"
            else -> "$packageName 已在电池优化白名单中，不受电池优化限制"
        }

        return ToolResult(
            success = true,
            data = mapOf(
                "package_name" to packageName,
                "is_optimizing" to isOptimizing.toString(),
                "in_whitelist" to (!isOptimizing).toString(),
                "has_battery_opt_feature" to hasBatteryOptimization.toString(),
                "status" to status,
            ),
        )
    }

    private fun requestIgnoreBatteryOptimization(): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return ToolResult(
                success = true,
                data = mapOf(
                    "status" to "当前 Android 版本 ($Build.VERSION.SDK_INT) 不支持电池优化白名单功能 (需 API 23+)",
                ),
            )
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        // Check if already whitelisted
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            return ToolResult(
                success = true,
                data = mapOf(
                    "package_name" to packageName,
                    "status" to "$packageName 已在电池优化白名单中，无需再次请求",
                ),
            )
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            return ToolResult(
                success = true,
                data = mapOf(
                    "package_name" to packageName,
                    "status" to "已打开电池优化设置页面，请在系统对话框中选择「允许」以将 $packageName 加入白名单",
                ),
            )
        } catch (e: Exception) {
            // Fallback: open general battery optimization settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ToolResult(
                    success = true,
                    data = mapOf(
                        "package_name" to packageName,
                        "status" to "已打开电池优化设置列表，请在列表中找到 $packageName 并将其设为「不优化」",
                    ),
                )
            } catch (e2: Exception) {
                return ToolResult(
                    success = false,
                    error = "无法打开电池优化设置: ${e.message} / ${e2.message}",
                )
            }
        }
    }

    private fun checkIgnoreStatus(): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return ToolResult(
                success = true,
                data = mapOf(
                    "is_ignoring" to "false",
                    "status" to "当前 Android 版本不支持电池优化白名单功能",
                ),
            )
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

        // Additional: check if device is in power save mode
        val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pm.isPowerSaveMode
        } else {
            false
        }

        val status = buildString {
            append("$packageName ")
            if (isIgnoring) {
                append("已忽略电池优化（在白名单中）")
                append("。")
                if (isPowerSaveMode) {
                    append(" 注意：设备当前处于省电模式，可能仍受部分限制。")
                }
            } else {
                append("未忽略电池优化")
                append("。")
                if (isPowerSaveMode) {
                    append(" 且设备处于省电模式，后台活动可能受限。")
                } else {
                    append(" 后台活动可能受电池优化限制。")
                }
                append(" 可使用 request 操作请求加入白名单。")
            }
        }

        return ToolResult(
            success = true,
            data = mapOf(
                "package_name" to packageName,
                "is_ignoring" to isIgnoring.toString(),
                "is_power_save_mode" to isPowerSaveMode.toString(),
                "status" to status,
            ),
        )
    }
}
