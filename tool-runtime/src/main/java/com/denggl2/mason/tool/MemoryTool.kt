package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class MemoryTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "memory_info"
    override val description = "获取内存使用情况"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val rt = Runtime.getRuntime()
        return ToolResult.success(mapOf("total" to rt.totalMemory().toString(), "free" to rt.freeMemory().toString(), "max" to rt.maxMemory().toString()))
    }
}
