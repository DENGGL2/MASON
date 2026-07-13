package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class NetworkInfoTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "network_info"
    override val description = "获取网络连接状态和类型"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val type = if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) "wifi"
            else if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true) "cellular"
            else "unknown"
        return ToolResult.success(mapOf("connected" to (network != null).toString(), "type" to type))
    }
}
