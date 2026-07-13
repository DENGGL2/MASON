package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class WifiTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "wifi_info"
    override val description = "获取 Wi-Fi 状态和当前连接信息"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val wm = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wm.connectionInfo
        return ToolResult.success(mapOf("ssid" to (info.ssid ?: "N/A"), "rssi" to info.rssi.toString()))
    }
}
