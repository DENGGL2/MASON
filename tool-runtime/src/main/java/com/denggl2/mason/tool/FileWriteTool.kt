package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class FileWriteTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "file_write"
    override val description = "写入文件内容"
    override val parameters: Map<String, ParameterDef> = mapOf(        "path" to ParameterDef("string", "文件路径", required = true),
        "content" to ParameterDef("string", "要写入的内容", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val path = args["path"] ?: return ToolResult.error("Missing path")
        val content = args["content"] ?: return ToolResult.error("Missing content")
        val file = java.io.File(path)
        if (!file.canonicalPath.startsWith(context.filesDir.canonicalPath) && !file.canonicalPath.startsWith(context.externalCacheDir?.canonicalPath ?: ""))
            return ToolResult.error("Path traversal denied")
        file.writeText(content)
        return ToolResult.success(mapOf("written" to "true"))
    }
}
