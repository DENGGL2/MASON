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
class NetworkInfoTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "network_info"
    override val description = "获取设备网络状态信息：网络类型、连接状态、WiFi SSID、信号强度、IP 地址、带宽估算、是否按流量计费"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)

            if (caps != null) {
                info["connected"] = "已连接"
                info["network_type"] = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "蓝牙共享"
                    else -> "其他"
                }

                // 是否按流量计费
                info["metered"] = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    "非计费"
                } else {
                    "按流量计费"
                }

                // 带宽估算
                val downBandwidth = caps.linkDownstreamBandwidthKbps
                val upBandwidth = caps.linkUpstreamBandwidthKbps
                if (downBandwidth > 0) {
                    info["downstream_bandwidth_kbps"] = downBandwidth.toString()
                    info["downstream_bandwidth_mbps"] = "%.1f".format(downBandwidth / 1000.0)
                }
                if (upBandwidth > 0) {
                    info["upstream_bandwidth_kbps"] = upBandwidth.toString()
                    info["upstream_bandwidth_mbps"] = "%.1f".format(upBandwidth / 1000.0)
                }

                // 网络是否已验证（可访问互联网）
                info["internet_validated"] = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    "已验证"
                } else {
                    "未验证"
                }
            } else {
                info["connected"] = "未连接"
                info["network_type"] = "无"
            }
        } else {
            info["connected"] = "未知"
            info["network_type"] = "未知"
        }

        // WiFi 详细信息
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            info["wifi_enabled"] = if (wifiManager.isWifiEnabled) "已开启" else "已关闭"

            val wifiInfo: WifiInfo = wifiManager.connectionInfo
            if (wifiInfo.networkId != -1) {
                val ssid = wifiInfo.ssid.removeSurrounding("\"")
                info["wifi_ssid"] = ssid.ifEmpty { "未知" }

                val rssi = wifiInfo.rssi
                info["wifi_signal_dbm"] = "$rssi dBm"
                info["wifi_signal_level"] = WifiManager.calculateSignalLevel(rssi, 5).toString() + "/5"

                val freq = wifiInfo.frequency
                info["wifi_frequency"] = if (freq > 4000) "5GHz" else "2.4GHz"

                val ip = wifiInfo.ipAddress
                info["ip_address"] = "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"

                val linkSpeed = wifiInfo.linkSpeed
                info["wifi_link_speed"] = "$linkSpeed Mbps"

                val mac = wifiInfo.macAddress ?: "未知"
                info["wifi_mac"] = mac
            }
        }

        return ToolResult(success = true, data = info)
    }
}
