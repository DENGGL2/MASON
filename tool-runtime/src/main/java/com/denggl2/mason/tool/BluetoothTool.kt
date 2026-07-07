package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class BluetoothTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "bluetooth_info"
    override val description = "获取蓝牙状态和已配对设备列表"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        return if (adapter != null) ToolResult.success(mapOf("enabled" to adapter.isEnabled.toString(), "name" to (adapter.name ?: "unknown")))
        else ToolResult.error("Bluetooth not available")
    }
}
