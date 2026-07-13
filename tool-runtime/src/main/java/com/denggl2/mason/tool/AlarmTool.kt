package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class AlarmTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "alarm"
    override val description = "设置闹钟"
    override val parameters: Map<String, ParameterDef> = mapOf(        "time" to ParameterDef("string", "闹钟时间（HH:mm 格式）", required = true),
        "label" to ParameterDef("string", "闹钟标签", required = false))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val time = args["time"] ?: return ToolResult.error("Missing time")
        val label = args["label"] ?: "Mason Alarm"
        return ToolResult.success(mapOf("set" to "true", "time" to time, "label" to label))
    }
}
