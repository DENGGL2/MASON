package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class AppLauncherTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "app_launch"
    override val description = "启动指定包名的应用"
    override val parameters: Map<String, ParameterDef> = mapOf(        "package" to ParameterDef("string", "要启动的应用包名", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val pkg = args["package"] ?: return ToolResult.error("Missing package")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ToolResult.error("App not found: $pkg")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult.success(mapOf("launched" to pkg))
    }
}
