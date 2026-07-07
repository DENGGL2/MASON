package com.denggl2.mason.tool

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HotspotTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "hotspot"
    override val description = "读取/控制热点状态"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作类型：status（查看状态）、enable（开启热点）、disable（关闭热点）",
            required = true,
            enum = listOf("status", "enable", "disable"),
        ),
    )

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return when (action) {
            "status" -> getHotspotStatus()
            "enable" -> enableHotspot()
            "disable" -> disableHotspot()
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private suspend fun getHotspotStatus(): ToolResult {
        return withContext(Dispatchers.Main) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                return@withContext ToolResult(success = false, error = "无法获取 WifiManager")
            }

            val info = mutableMapOf<String, String>()

            // 检查 WiFi 状态
            val wifiState = wifiManager.wifiState
            info["wifi_state"] = when (wifiState) {
                WifiManager.WIFI_STATE_ENABLED -> "已开启"
                WifiManager.WIFI_STATE_ENABLING -> "正在开启"
                WifiManager.WIFI_STATE_DISABLED -> "已关闭"
                WifiManager.WIFI_STATE_DISABLING -> "正在关闭"
                else -> "未知"
            }

            // 检测热点是否活跃（通过 AP 状态）
            info["hotspot_active"] = if (isHotspotActive(wifiManager)) "已开启" else "未开启"

            if (isHotspotActive(wifiManager)) {
                try {
                    val config = getWifiApConfiguration(wifiManager)
                    if (config != null) {
                        info["hotspot_ssid"] = config.SSID.removeSurrounding("\"")
                        info["hotspot_security"] = when (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
                            true -> "无密码"
                            else -> when {
                                config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK) -> "WPA2-PSK"
                                config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA2_PSK) -> "WPA2-PSK"
                                else -> "其他"
                            }
                        }
                    }
                } catch (e: Exception) {
                    info["hotspot_detail_error"] = e.message ?: "未知错误"
                }
            }

            ToolResult(success = true, data = info)
        }
    }

    private fun enableHotspot(): ToolResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult(success = false, error = "无法获取 WifiManager")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用 LocalOnlyHotspot
            try {
                wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        hotspotReservation = reservation
                    }

                    override fun onStopped() {
                        hotspotReservation = null
                    }

                    override fun onFailed(reason: Int) {
                        hotspotReservation = null
                    }
                }, null)
                return ToolResult(
                    success = true,
                    data = mapOf("status" to "热点已请求开启（LocalOnlyHotspot）"),
                )
            } catch (e: Exception) {
                return ToolResult(
                    success = false,
                    error = "开启热点失败: ${e.message}（可能需要定位权限）",
                )
            }
        } else {
            // 旧版本通过反射
            return try {
                val method = wifiManager.javaClass.getMethod(
                    "setWifiApEnabled",
                    WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                val config = WifiConfiguration().apply {
                    SSID = "MasonHotspot"
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                method.invoke(wifiManager, config, true)
                ToolResult(
                    success = true,
                    data = mapOf("status" to "热点已开启"),
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    error = "开启热点失败: ${e.message}",
                )
            }
        }
    }

    private fun disableHotspot(): ToolResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult(success = false, error = "无法获取 WifiManager")

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hotspotReservation?.close()
                hotspotReservation = null
                ToolResult(
                    success = true,
                    data = mapOf("status" to "热点已关闭"),
                )
            } else {
                val method = wifiManager.javaClass.getMethod(
                    "setWifiApEnabled",
                    WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                method.invoke(wifiManager, null, false)
                ToolResult(
                    success = true,
                    data = mapOf("status" to "热点已关闭"),
                )
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "关闭热点失败: ${e.message}",
            )
        }
    }

    private fun isHotspotActive(wifiManager: WifiManager): Boolean {
        return try {
            val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wifiManager) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getWifiApConfiguration(wifiManager: WifiManager): WifiConfiguration? {
        return try {
            val method = wifiManager.javaClass.getMethod("getWifiApConfiguration")
            method.invoke(wifiManager) as? WifiConfiguration
        } catch (e: Exception) {
            null
        }
    }
}
