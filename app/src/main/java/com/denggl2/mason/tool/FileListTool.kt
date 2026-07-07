package com.denggl2.mason.tool

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileListTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "file_list"
    override val description = "列出目录内容。仅允许列出安全目录。"
    override val parameters = mapOf(
        "path" to ParameterDef(
            type = "string",
            description = "目录路径，默认 /sdcard/",
            required = false,
        ),
        "filter" to ParameterDef(
            type = "string",
            description = "文件后缀过滤，多个用空格分隔，如 \".pdf .txt\"",
            required = false,
        ),
        "max_depth" to ParameterDef(
            type = "integer",
            description = "最大递归深度，默认 1（仅当前目录）",
            required = false,
        ),
    )

    private val safeDirectories: List<File> by lazy {
        val dirs = mutableListOf<File>()

        // 标准公共目录
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))

        // 应用专属目录
        context.getExternalFilesDir(null)?.let { dirs.add(it) }
        context.filesDir?.let { dirs.add(it) }

        dirs
    }

    companion object {
        private const val DEFAULT_MAX_DEPTH = 1
        private const val MAX_LIST_ITEMS = 500
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: "/sdcard/"
        val filterStr = args["filter"]
        val maxDepth = args["max_depth"]?.toIntOrNull() ?: DEFAULT_MAX_DEPTH

        // 路径穿越检查
        if (containsPathTraversal(path)) {
            return ToolResult(
                success = false,
                error = "禁止路径穿越：路径中不允许包含 ../ 或 ..\\",
            )
        }

        // 解析过滤后缀
        val filters = if (!filterStr.isNullOrBlank()) {
            filterStr.trim().split("\\s+".toRegex()).map { it.lowercase().trimStart('.') }.toSet()
        } else {
            emptySet()
        }

        return withContext(Dispatchers.IO) {
            try {
                val dir = File(path)

                if (!dir.exists()) {
                    return@withContext ToolResult(
                        success = false,
                        error = "目录不存在: $path",
                    )
                }

                if (!dir.isDirectory) {
                    return@withContext ToolResult(
                        success = false,
                        error = "路径不是目录: $path",
                    )
                }

                if (!isInSafeDirectory(dir)) {
                    return@withContext ToolResult(
                        success = false,
                        error = "不允许列出此目录。允许的目录: ${safeDirectories.joinToString(", ") { it.absolutePath }}",
                    )
                }

                val entries = mutableListOf<Map<String, String>>()
                listDirectory(dir, filters, maxDepth, 0, entries)

                if (entries.isEmpty()) {
                    return@withContext ToolResult(
                        success = true,
                        data = mapOf(
                            "path" to dir.absolutePath,
                            "count" to "0",
                            "message" to "目录为空",
                        ),
                    )
                }

                // 构建结果
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val sb = StringBuilder()
                sb.appendLine("Directory: ${dir.absolutePath}")
                sb.appendLine("Total items: ${entries.size}")
                sb.appendLine()
                sb.appendLine(String.format("%-8s %-20s %-10s %s", "Type", "Modified", "Size", "Name"))
                sb.appendLine("-".repeat(70))

                for (entry in entries) {
                    val type = if (entry["is_directory"] == "true") "<DIR>" else "<FILE>"
                    val size = entry["size"] ?: ""
                    val name = entry["name"] ?: ""
                    val modTime = entry["modify_time"] ?: ""
                    sb.appendLine(String.format("%-8s %-20s %-10s %s", type, modTime, size, name))
                }

                ToolResult(
                    success = true,
                    data = mapOf(
                        "path" to dir.absolutePath,
                        "count" to entries.size.toString(),
                        "list" to sb.toString(),
                    ),
                )
            } catch (e: SecurityException) {
                ToolResult(
                    success = false,
                    error = "权限不足: ${e.message}",
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    error = "列出目录失败: ${e.message}",
                )
            }
        }
    }

    private fun listDirectory(
        dir: File,
        filters: Set<String>,
        maxDepth: Int,
        currentDepth: Int,
        result: MutableList<Map<String, String>>,
    ) {
        if (result.size >= MAX_LIST_ITEMS) return

        val files = dir.listFiles() ?: return
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 先列出子目录，再列出文件
        val sortedFiles = files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

        for (file in sortedFiles) {
            if (result.size >= MAX_LIST_ITEMS) break

            val entry = mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "is_directory" to file.isDirectory.toString(),
                "size" to (if (file.isFile) file.length().toString() else "-"),
                "modify_time" to dateFormat.format(Date(file.lastModified())),
            )

            // 后缀过滤仅对文件生效
            if (file.isFile && filters.isNotEmpty()) {
                val ext = file.extension.lowercase()
                if (ext !in filters) continue
            }

            result.add(entry)

            // 递归子目录
            if (file.isDirectory && currentDepth < maxDepth - 1) {
                listDirectory(file, filters, maxDepth, currentDepth + 1, result)
            }
        }
    }

    private fun containsPathTraversal(path: String): Boolean {
        return path.contains("../") || path.contains("..\\")
    }

    private fun isInSafeDirectory(file: File): Boolean {
        val canonicalPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }

        return safeDirectories.any { safeDir ->
            val safePath = try {
                safeDir.canonicalPath
            } catch (e: Exception) {
                safeDir.absolutePath
            }
            canonicalPath.startsWith(safePath + File.separator) || canonicalPath == safePath
        }
    }
}
