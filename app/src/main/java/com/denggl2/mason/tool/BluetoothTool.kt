package com.denggl2.mason.tool

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_bluetooth_info"
    override val description = "获取蓝牙信息：适配器状态、名称和已配对设备列表"
    override val parameters = emptyMap<String, ParameterDef>()

    @SuppressLint("MissingPermission")
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(success = false, error = "缺少蓝牙连接权限")
        }

        return try {
            info["enabled"] = if (adapter.isEnabled) "已开启" else "已关闭"
            info["name"] = adapter.name ?: "未知"

            val canReadDiscoveryState = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canReadDiscoveryState) {
                info["discovering"] = if (adapter.isDiscovering) "正在搜索" else "未搜索"
            }

            val pairedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
            if (pairedDevices.isNotEmpty()) {
                pairedDevices.forEachIndexed { index, device ->
                    info["paired_${index + 1}"] = "${device.name ?: "未知"} (${device.address})"
                }
                info["paired_count"] = "${pairedDevices.size} 台"
            } else {
                info["paired_count"] = "0 台"
            }

            ToolResult(success = true, data = info)
        } catch (_: SecurityException) {
            ToolResult(success = false, error = "蓝牙权限已被撤销")
        }
    }
}
