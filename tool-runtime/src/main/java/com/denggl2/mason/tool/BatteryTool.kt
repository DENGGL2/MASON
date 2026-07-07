package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class BatteryTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "battery_info"
    override val description = "获取电池电量、充电状态、温度等信息"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return ToolResult.success(mapOf("level" to level.toString()))
    }
}
