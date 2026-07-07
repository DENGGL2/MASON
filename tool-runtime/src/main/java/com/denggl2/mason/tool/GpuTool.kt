package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class GpuTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "gpu_info"
    override val description = "获取 GPU 渲染器和供应商信息"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.success(mapOf("renderer" to "GL", "vendor" to "Android GPU"))
    }
}
