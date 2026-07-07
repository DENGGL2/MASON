package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class HotspotTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "hotspot"
    override val description = "检查移动热点状态"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val wm = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        return try {
            val method = wm.javaClass.getMethod("isWifiApEnabled")
            val enabled = method.invoke(wm) as Boolean
            ToolResult.success(mapOf("enabled" to enabled.toString()))
        } catch (e: Exception) {
            ToolResult.error("Unable to check hotspot: ${e.message}")
        }
    }
}
