package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class CpuTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "cpu_info"
    override val description = "获取 CPU 信息，包括型号、核心数、当前使用率"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val cpuInfo = System.getProperty("os.arch") ?: "unknown"
        val cores = Runtime.getRuntime().availableProcessors()
        return ToolResult.success(mapOf("arch" to cpuInfo, "cores" to cores.toString()))
    }
}
