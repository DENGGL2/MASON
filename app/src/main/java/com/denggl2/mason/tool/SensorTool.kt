package com.denggl2.mason.tool

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_sensor_info"
    override val description = "获取设备所有传感器清单：名称、类型、厂商、功耗"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) return ToolResult(success = false, error = "无法访问传感器服务")

        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val info = mutableMapOf<String, String>()

        sensors.forEachIndexed { index, sensor ->
            val key = "sensor_${index + 1}"
            val typeStr = when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> "加速度计"
                Sensor.TYPE_GYROSCOPE -> "陀螺仪"
                Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
                Sensor.TYPE_LIGHT -> "光线传感器"
                Sensor.TYPE_PROXIMITY -> "接近传感器"
                Sensor.TYPE_PRESSURE -> "气压计"
                Sensor.TYPE_GRAVITY -> "重力传感器"
                Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度"
                Sensor.TYPE_ROTATION_VECTOR -> "旋转矢量"
                Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度"
                Sensor.TYPE_RELATIVE_HUMIDITY -> "相对湿度"
                Sensor.TYPE_HEART_RATE -> "心率传感器"
                Sensor.TYPE_STEP_COUNTER -> "计步器"
                Sensor.TYPE_STEP_DETECTOR -> "计步检测"
                else -> "类型#${sensor.type}"
            }
            val power = "%.2f mA".format(sensor.power)
            info[key] = "$typeStr | ${sensor.vendor} | 功耗: $power"
        }

        info["total"] = "${sensors.size} 个传感器"

        return ToolResult(success = true, data = info)
    }
}
