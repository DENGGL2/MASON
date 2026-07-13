package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class FileDeleteTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "file_delete"
    override val description = "删除文件"
    override val parameters: Map<String, ParameterDef> = mapOf(        "path" to ParameterDef("string", "文件路径", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val path = args["path"] ?: return ToolResult.error("Missing path")
        val file = java.io.File(path)
        if (!file.canonicalPath.startsWith(context.filesDir.canonicalPath) && !file.canonicalPath.startsWith(context.externalCacheDir?.canonicalPath ?: ""))
            return ToolResult.error("Path traversal denied")
        val deleted = file.delete()
        return ToolResult.success(mapOf("deleted" to deleted.toString()))
    }
}
