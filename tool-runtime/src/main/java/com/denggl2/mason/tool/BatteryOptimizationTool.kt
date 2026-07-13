package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class BatteryOptimizationTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "battery_optimization"
    override val description = "管理电池优化白名单"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "check 或 request", required = true, enum = listOf("check", "request")))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.success(mapOf("status" to "unknown", "note" to "Battery optimization check requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
    }
}
