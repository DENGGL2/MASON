package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class FileReadTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "file_read"
    override val description = "读取文件内容"
    override val parameters: Map<String, ParameterDef> = mapOf(        "path" to ParameterDef("string", "文件路径", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val path = args["path"] ?: return ToolResult.error("Missing path")
        val file = java.io.File(path)
        if (!file.canonicalPath.startsWith(context.filesDir.canonicalPath) && !file.canonicalPath.startsWith(context.externalCacheDir?.canonicalPath ?: ""))
            return ToolResult.error("Path traversal denied")
        val content = file.readText()
        return ToolResult.success(mapOf("content" to content))
    }
}
