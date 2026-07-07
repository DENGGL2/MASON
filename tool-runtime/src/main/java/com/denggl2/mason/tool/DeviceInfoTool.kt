package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class DeviceInfoTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "device_info"
    override val description = "获取设备型号、系统版本等信息"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.success(mapOf(
            "model" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "sdk" to android.os.Build.VERSION.SDK_INT.toString(),
            "release" to android.os.Build.VERSION.RELEASE
        ))
    }
}
