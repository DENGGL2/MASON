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
class FileDeleteTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "file_delete"
    override val description = "删除 Download/mason/ 目录下的文件或空目录。"
    override val parameters = mapOf(
        "path" to ParameterDef(
            type = "string",
            description = "要删除的文件路径（必须是 Download/mason/ 下的文件或空目录）",
            required = true,
        ),
    )

    private val allowedDir: File by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, "mason")
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult(
            success = false,
            error = "缺少 path 参数",
        )

        // 路径穿越检查
        if (containsPathTraversal(path)) {
            return ToolResult(
                success = false,
                error = "禁止路径穿越：路径中不允许包含 ../ 或 ..\\",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val target = File(path)

                if (!target.exists()) {
                    return@withContext ToolResult(
                        success = false,
                        error = "文件不存在: $path",
                    )
                }

                // 安全检查：必须在允许的目录内
                val canonicalTarget = try {
                    target.canonicalFile
                } catch (e: Exception) {
                    return@withContext ToolResult(
                        success = false,
                        error = "路径解析失败: ${e.message}",
                    )
                }

                val canonicalBase = try {
                    allowedDir.canonicalFile
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
                        error = "不允许删除此路径。仅允许删除 ${allowedDir.absolutePath} 目录下的文件。",
                    )
                }

                // 禁止删除 allowedDir 本身
                if (canonicalTarget.path == canonicalBase.path) {
                    return@withContext ToolResult(
                        success = false,
                        error = "不允许删除 mason 目录本身。",
                    )
                }

                val itemType: String
                var deleted: Boolean

                if (target.isDirectory) {
                    // 目录：仅允许空目录
                    val contents = target.list()
                    if (contents != null && contents.isNotEmpty()) {
                        return@withContext ToolResult(
                            success = false,
                            error = "目录不为空（包含 ${contents.size} 个条目），仅允许删除空目录: ${target.absolutePath}。请先清空目录内容。",
                        )
                    }
                    deleted = target.delete()
                    itemType = "directory"
                } else {
                    deleted = target.delete()
                    itemType = "file"
                }

                if (deleted) {
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "path" to target.absolutePath,
                            "type" to itemType,
                            "status" to "deleted",
                        ),
                    )
                } else {
                    ToolResult(
                        success = false,
                        error = "删除失败，可能是权限不足或文件被占用: ${target.absolutePath}",
                    )
                }
            } catch (e: SecurityException) {
                ToolResult(
                    success = false,
                    error = "权限不足: ${e.message}",
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    error = "删除失败: ${e.message}",
                )
            }
        }
    }

    private fun containsPathTraversal(path: String): Boolean {
        return path.contains("../") || path.contains("..\\")
    }
}
