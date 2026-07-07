package com.denggl2.mason.tool

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_gpu_info"
    override val description = "获取GPU渲染器名称和OpenGL版本信息"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        // 通过 ActivityManager 获取 GPU 渲染器
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val config = am.deviceConfigurationInfo
            info["gl_es_version"] = config.reqGlEsVersion.toString()
        }

        // 从 egl 属性获取渲染器名称
        try {
            val egl = Class.forName("android.opengl.EGL14")
            val display = egl.getMethod("eglGetDisplay", Int::class.java).invoke(null, 0)
            val initialize = egl.getMethod("eglInitialize", display.javaClass, IntArray::class.java, Int::class.java, IntArray::class.java, Int::class.java)
            // 简化：通过系统属性获取
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.gpu.renderer"))
            val renderer = process.inputStream.bufferedReader().readText().trim()
            if (renderer.isNotEmpty()) info["renderer"] = renderer
            process.destroy()
        } catch (_: Exception) {}

        // 通过 HardwarePropertiesManager (API 24+)
        try {
            val hpm = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? android.os.HardwarePropertiesManager
            if (hpm != null) {
                val devices = hpm.deviceTemperatures
                info["thermal_sensors_count"] = devices.size.toString()
            }
        } catch (_: Exception) {}

        if (info.isEmpty()) info["note"] = "GPU型号需root权限，当前仅提供OpenGL版本"

        return ToolResult(success = true, data = info)
    }
}
