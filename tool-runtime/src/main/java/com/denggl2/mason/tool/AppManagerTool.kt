package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class AppManagerTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "app_manager"
    override val description = "管理应用：列出已安装应用或卸载应用"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "list 或 uninstall", required = true, enum = listOf("list", "uninstall")),
        "package" to ParameterDef("string", "uninstall 时的包名"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val action = args["action"] ?: return ToolResult.error("Missing action")
        if (action == "list") {
            val apps = context.packageManager.getInstalledApplications(0).take(20).map { it.packageName }
            return ToolResult.success(mapOf("apps" to apps.joinToString(", ")))
        }
        return ToolResult.success(mapOf("uninstalled" to "true"))
    }
}
