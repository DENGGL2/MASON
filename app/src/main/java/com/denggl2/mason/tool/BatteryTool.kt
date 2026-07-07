package com.denggl2.mason.tool

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_battery_info"
    override val description = "获取电池信息：电量、健康状态、充电状态、温度、电压、技术类型"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val info = mutableMapOf<String, String>()

        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                info["level"] = "${level * 100 / scale}%"
            }

            val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
                BatteryManager.BATTERY_STATUS_FULL -> "已充满"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
                else -> "未知"
            }
            info["status"] = status

            val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC电源"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
                else -> "未连接"
            }
            info["power_source"] = plugged

            val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
                BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
                BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> "未知"
                else -> "未知"
            }
            info["health"] = health

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (temp > 0) info["temperature"] = "${temp / 10.0}°C"

            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (voltage > 0) info["voltage"] = "${voltage / 1000.0}V"

            val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            if (technology != null) info["technology"] = technology
        }

        // API 28+ 电池健康度（充电循环次数不可直接获取，这里用容量估算）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                info["design_capacity"] = "$capacity%"
            }
        }

        return ToolResult(success = true, data = info)
    }
}
