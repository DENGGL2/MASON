package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class FileListTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "file_list"
    override val description = "列出目录下的文件"
    override val parameters: Map<String, ParameterDef> = mapOf(        "path" to ParameterDef("string", "目录路径", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val path = args["path"] ?: return ToolResult.error("Missing path")
        val dir = java.io.File(path)
        if (!dir.canonicalPath.startsWith(context.filesDir.canonicalPath) && !dir.canonicalPath.startsWith(context.externalCacheDir?.canonicalPath ?: ""))
            return ToolResult.error("Path traversal denied")
        val files = dir.listFiles()?.map { it.name } ?: emptyList()
        return ToolResult.success(mapOf("files" to files.joinToString(", ")))
    }
}
