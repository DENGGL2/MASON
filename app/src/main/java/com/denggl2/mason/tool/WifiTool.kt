package com.denggl2.mason.tool

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_wifi_info"
    override val description = "获取网络和WiFi信息：WiFi状态、SSID、信号强度、网络类型（5G/2.4G）、IP地址"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            info["wifi_enabled"] = if (wifiManager.isWifiEnabled) "已开启" else "已关闭"

            val wifiInfo: WifiInfo = wifiManager.connectionInfo
            if (wifiInfo.networkId != -1) {
                val ssid = wifiInfo.ssid.removeSurrounding("\"")
                info["ssid"] = ssid.ifEmpty { "未知" }

                val rssi = wifiInfo.rssi
                info["signal_dbm"] = "$rssi dBm"
                info["signal_level"] = WifiManager.calculateSignalLevel(rssi, 5).toString() + "/5"

                // 频率判断 2.4G vs 5G
                val freq = wifiInfo.frequency
                info["frequency"] = if (freq > 4000) "5GHz" else "2.4GHz"

                // IP
                val ip = wifiInfo.ipAddress
                info["ip"] = "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"

                val linkSpeed = wifiInfo.linkSpeed
                info["link_speed"] = "$linkSpeed Mbps"
            } else {
                info["wifi_connected"] = "未连接WiFi"
            }
        }

        // 网络类型（WiFi / 蜂窝）
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null) {
                info["network_type"] = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                    else -> "未知"
                }
            }
        }

        return ToolResult(success = true, data = info)
    }
}
