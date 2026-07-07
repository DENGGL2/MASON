package com.denggl2.mason.tool

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_bluetooth_info"
    override val description = "获取蓝牙信息：适配器状态、名称、地址、已配对设备列表、信号强度（如可读取）"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (btManager == null) {
            info["status"] = "设备不支持蓝牙"
            return ToolResult(success = true, data = info)
        }

        val adapter = btManager.adapter
        if (adapter == null) {
            info["status"] = "无蓝牙适配器"
            return ToolResult(success = true, data = info)
        }

        info["enabled"] = if (adapter.isEnabled) "已开启" else "已关闭"
        info["name"] = adapter.name ?: "未知"
        info["address"] = adapter.address ?: "未知"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info["discovering"] = if (adapter.isDiscovering) "正在搜索" else "未搜索"
        }

        // 已配对设备
        val pairedDevices: Set<BluetoothDevice> = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            info["paired_error"] = "需要蓝牙权限"
            return ToolResult(success = true, data = info)
        }

        if (pairedDevices.isNotEmpty()) {
            pairedDevices.forEachIndexed { index, device ->
                info["paired_${index + 1}"] = "${device.name ?: "未知"} (${device.address})"
            }
            info["paired_count"] = "${pairedDevices.size} 台"
        } else {
            info["paired_count"] = "0 台"
        }

        return ToolResult(success = true, data = info)
    }
}
