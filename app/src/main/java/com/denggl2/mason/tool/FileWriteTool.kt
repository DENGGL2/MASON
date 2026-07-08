package com.denggl2.mason.tool

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileWriteTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "file_write"
    override val description = "写入/创建文件到 Download/mason/ 目录下。自动创建父目录。"
    override val parameters = mapOf(
        "path" to ParameterDef(
            type = "string",
            description = "文件相对路径（基于 Download/mason/ 目录），如 'notes/test.txt'",
            required = true,
        ),
        "content" to ParameterDef(
            type = "string",
            description = "要写入的文本内容",
            required = true,
        ),
        "append" to ParameterDef(
            type = "boolean",
            description = "是否追加到文件末尾，默认 false（覆盖写入）",
            required = false,
        ),
    )

    private val baseDir: File by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, "mason").also { it.mkdirs() }
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val relativePath = args["path"] ?: return ToolResult(
            success = false,
            error = "缺少 path 参数",
        )
        val content = args["content"] ?: return ToolResult(
            success = false,
            error = "缺少 content 参数",
        )
        val append = args["append"]?.toBooleanStrictOrNull() ?: false

        // 路径穿越检查
        if (containsPathTraversal(relativePath)) {
            return ToolResult(
                success = false,
                error = "禁止路径穿越：路径中不允许包含 ../ 或 ..\\",
            )
        }

        // 检查是否为空路径或仅根目录
        val trimmedPath = relativePath.trimStart('/', '\\')
        if (trimmedPath.isEmpty()) {
            return ToolResult(
                success = false,
                error = "路径不能为空",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val targetFile = File(baseDir, trimmedPath)

                // 规范化后再次检查是否在允许目录内
                val canonicalTarget = try {
                    targetFile.canonicalFile
                } catch (e: Exception) {
                    return@withContext ToolResult(
                        success = false,
                        error = "路径解析失败: ${e.message}",
                    )
                }

                val canonicalBase = try {
                    baseDir.canonicalFile
                } catch (e: Exception) {
                    return@withContext ToolResult(
                        success = false,
                        error = "基础目录解析失败: ${e.message}",
                    )
                }

                if (!canonicalTarget.path.startsWith(canonicalBase.path + File.separator) &&
                    canonicalTarget.path != canonicalBase.path) {
                    return@withContext ToolResult(
                        success = false,
                        error = "不允许写入此路径。文件必须位于 ${baseDir.absolutePath} 目录下。",
                    )
                }

                // 如果目标是已存在的目录，拒绝
                if (targetFile.exists() && targetFile.isDirectory) {
                    return@withContext ToolResult(
                        success = false,
                        error = "目标路径是一个目录，无法写入: ${targetFile.absolutePath}",
                    )
                }

                // 自动创建父目录
                val parentDir = targetFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    val created = parentDir.mkdirs()
                    if (!created) {
                        return@withContext ToolResult(
                            success = false,
                            error = "无法创建父目录: ${parentDir.absolutePath}",
                        )
                    }
                }

                // 写入文件
                if (append) {
                    targetFile.appendText(content, Charsets.UTF_8)
                } else {
                    targetFile.writeText(content, Charsets.UTF_8)
                }

                val bytesWritten = content.toByteArray(Charsets.UTF_8).size

                ToolResult(
                    success = true,
                    data = mapOf(
                        "path" to targetFile.absolutePath,
                        "bytes_written" to bytesWritten.toString(),
                        "mode" to if (append) "append" else "overwrite",
                        "file_size" to targetFile.length().toString(),
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
                    error = "写入文件失败: ${e.message}",
                )
            }
        }
    }

    private fun containsPathTraversal(path: String): Boolean {
        return path.contains("../") || path.contains("..\\")
    }
}
