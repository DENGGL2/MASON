package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class SystemSettingTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "system_setting"
    override val description = "查询或修改系统设置"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "get 或 set", required = true, enum = listOf("get", "set")),
        "key" to ParameterDef("string", "设置项名称：brightness/volume/airplane/wifi/bluetooth", required = true),
        "value" to ParameterDef("string", "要设置的值，action=set 时必填"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val action = args["action"] ?: return ToolResult.error("Missing action")
        val key = args["key"] ?: return ToolResult.error("Missing key")
        if (action == "get") {
            return when (key) {
                "brightness" -> ToolResult.success(mapOf("value" to android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 0).toString()))
                "airplane" -> ToolResult.success(mapOf("value" to android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0).toString()))
                else -> ToolResult.error("Unknown key: $key")
            }
        }
        return ToolResult.error("set not supported in this demo")
    }
}
