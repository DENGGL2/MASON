package com.denggl2.mason.tool

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutor @Inject constructor(
    private val registry: ToolRegistry,
) {
    suspend fun execute(name: String, args: Map<String, String>): ToolResult {
        val tool = registry.get(name) ?: return ToolResult(
            success = false,
            error = "未找到工具: $name",
        )
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "工具执行失败: ${e.message}",
            )
        }
    }
}
