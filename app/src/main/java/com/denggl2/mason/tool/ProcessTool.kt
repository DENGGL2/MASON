package com.denggl2.mason.tool

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "process"
    override val description = "进程管理：列出运行中进程、终止后台进程。系统关键进程受保护。"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：list（列出运行中进程）/ kill（终止指定包名的后台进程）",
            required = true,
            enum = listOf("list", "kill"),
        ),
        "package_name" to ParameterDef(
            type = "string",
            description = "目标应用的包名，kill 时必填",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return try {
            when (action) {
                "list" -> listProcesses()
                "kill" -> {
                    val packageName = args["package_name"]
                    if (packageName.isNullOrBlank()) {
                        return ToolResult(
                            success = false,
                            error = "kill 操作需要提供 package_name 参数",
                        )
                    }
                    killProcess(packageName)
                }
                else -> ToolResult(success = false, error = "未知 action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult(success = false, error = "权限不足: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "操作失败: ${e.message}")
        }
    }

    private fun listProcesses(): ToolResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses ?: emptyList()

        if (runningProcesses.isEmpty()) {
            return ToolResult(
                success = true,
                data = mapOf(
                    "process_count" to "0",
                    "summary" to "当前无运行中的第三方进程",
                ),
            )
        }

        val pm = context.packageManager
        val processList = mutableListOf<Map<String, String>>()
        val summaryLines = mutableListOf<String>()

        summaryLines.add("运行中进程列表 (共 ${runningProcesses.size} 个):")
        summaryLines.add("")

        // Header
        summaryLines.add(
            "%-4s %-30s %-8s %-8s %-10s %s".format(
                "序号", "进程名", "PID", "重要性", "内存(KB)", "应用名"
            )
        )
        summaryLines.add("-".repeat(90))

        runningProcesses.forEachIndexed { index, procInfo ->
            val processName = procInfo.processName ?: "未知"
            val pid = procInfo.pid
            val importance = importanceToString(procInfo.importance)
            val importanceCode = procInfo.importance.toString()
            val isSystem = isSystemProcess(procInfo)

            // Get application label
            val appName = try {
                val appInfo = pm.getApplicationInfo(processName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                // Try extracting package name from process name
                val pkg = processName.substringBeforeLast(":")
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    processName
                }
            }

            // RSS estimation via Debug.MemoryInfo
            val memoryKb = try {
                val memInfoArray = am.getProcessMemoryInfo(intArrayOf(pid))
                if (memInfoArray.isNotEmpty()) {
                    memInfoArray[0].totalPss
                } else {
                    0
                }
            } catch (_: Exception) {
                0
            }

            processList.add(
                mapOf(
                    "index" to (index + 1).toString(),
                    "process_name" to processName,
                    "pid" to pid.toString(),
                    "importance" to importance,
                    "importance_code" to importanceCode,
                    "memory_kb" to memoryKb.toString(),
                    "memory_mb" to "%.1f".format(memoryKb / 1024.0),
                    "app_name" to appName,
                    "is_system" to isSystem.toString(),
                )
            )

            summaryLines.add(
                "%-4d %-30s %-8d %-8s %-10d %s".format(
                    index + 1,
                    processName.take(30),
                    pid,
                    importance.take(8),
                    memoryKb,
                    appName.take(30),
                )
            )
        }

        // Build result data
        val resultData = mutableMapOf<String, String>()
        processList.forEachIndexed { index, proc ->
            val prefix = "process_${index}_"
            proc.forEach { (key, value) ->
                resultData["${prefix}$key"] = value
            }
        }
        resultData["process_count"] = runningProcesses.size.toString()
        resultData["summary"] = summaryLines.joinToString("\n")

        return ToolResult(success = true, data = resultData)
    }

    private fun killProcess(packageName: String): ToolResult {
        // Check if it's a system process
        if (isSystemPackage(packageName)) {
            return ToolResult(
                success = false,
                error = "无法终止系统进程: $packageName。系统进程受保护。",
            )
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses ?: emptyList()

        // Find all processes belonging to this package
        val targetProcesses = runningProcesses.filter { procInfo ->
            val procName = procInfo.processName ?: ""
            procName == packageName || procName.startsWith("$packageName:")
        }

        if (targetProcesses.isEmpty()) {
            return ToolResult(
                success = true,
                data = mapOf(
                    "action" to "kill",
                    "package_name" to packageName,
                    "status" to "$packageName 当前无运行中的后台进程",
                ),
            )
        }

        // Double-check: verify none are system processes
        val systemProcs = targetProcesses.filter { isSystemProcess(it) }
        if (systemProcs.isNotEmpty()) {
            return ToolResult(
                success = false,
                error = "检测到系统进程，已拒绝终止 (${systemProcs.map { it.processName }.joinToString(", ")})",
            )
        }

        // Kill background processes
        try {
            am.killBackgroundProcesses(packageName)
            val killedPids = targetProcesses.map { it.pid }
            val killedNames = targetProcesses.map { it.processName ?: "unknown" }

            return ToolResult(
                success = true,
                data = mapOf(
                    "action" to "kill",
                    "package_name" to packageName,
                    "killed_count" to killedPids.size.toString(),
                    "killed_pids" to killedPids.joinToString(", "),
                    "killed_processes" to killedNames.joinToString(", "),
                    "status" to "已终止 ${killedPids.size} 个后台进程: ${killedNames.joinToString(", ")}",
                ),
            )
        } catch (e: SecurityException) {
            return ToolResult(
                success = false,
                error = "权限不足，无法终止进程: ${e.message}",
            )
        }
    }

    private fun isSystemProcess(procInfo: ActivityManager.RunningAppProcessInfo): Boolean {
        // System processes have importance >= IMPORTANCE_SERVICE or uid < 10000
        if (procInfo.uid < 10000) return true
        if (procInfo.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) return true

        // Check FLAG_SYSTEM via PackageManager
        val procName = procInfo.processName ?: return false
        val pkg = procName.substringBeforeLast(":")
        return isSystemPackage(pkg)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(packageName, 0)
            val appInfo = pi.applicationInfo
            val isSystemFlag = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isLowUid = appInfo.uid < 10000
            isSystemFlag || isUpdatedSystem || isLowUid
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun importanceToString(importance: Int): String {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "前台"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "可见"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "可感知"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "服务"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "睡眠前台"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "前台服务"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "缓存"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "无法保存状态"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "已退出"
            else -> "其他($importance)"
        }
    }
}
