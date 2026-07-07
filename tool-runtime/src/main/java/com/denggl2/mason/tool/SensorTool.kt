package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class SensorTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "sensor_list"
    override val description = "列出设备所有传感器"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensors = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
        val list = sensors.joinToString(", ") { it.name }
        return ToolResult.success(mapOf("sensors" to list))
    }
}
