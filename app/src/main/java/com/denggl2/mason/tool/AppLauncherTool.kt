package com.denggl2.mason.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLauncherTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "launch_app"
    override val description = "启动应用或获取已安装应用列表"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作类型：launch（启动应用）或 list（列出已安装应用）",
            required = true,
            enum = listOf("launch", "list"),
        ),
        "package_name" to ParameterDef(
            type = "string",
            description = "要启动的应用包名（action=launch 时必填）",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return when (action) {
            "launch" -> launchApp(args["package_name"])
            "list" -> listApps()
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun launchApp(packageName: String?): ToolResult {
        if (packageName.isNullOrBlank()) {
            return ToolResult(success = false, error = "package_name 不能为空")
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult(
                    success = true,
                    data = mapOf("package_name" to packageName, "status" to "已启动"),
                )
            } else {
                ToolResult(
                    success = false,
                    error = "无法启动应用 $packageName：未找到启动入口",
                )
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "启动失败: ${e.message}",
            )
        }
    }

    private fun listApps(): ToolResult {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val sb = StringBuilder()
            packages.sortedBy { app ->
                app.loadLabel(pm).toString().lowercase()
            }.forEach { app ->
                val label = app.loadLabel(pm).toString()
                val pkgName = app.packageName
                sb.appendLine("$label | $pkgName")
            }
            ToolResult(
                success = true,
                data = mapOf(
                    "count" to packages.size.toString(),
                    "apps" to sb.toString(),
                ),
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "获取应用列表失败: ${e.message}",
            )
        }
    }
}
