package com.denggl2.mason.tool

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileReadTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "file_read"
    override val description = "读取文本文件内容。仅允许读取安全目录下的文本文件（<1MB）。"
    override val parameters = mapOf(
        "path" to ParameterDef(
            type = "string",
            description = "文件绝对路径",
            required = true,
        ),
        "offset" to ParameterDef(
            type = "integer",
            description = "起始行（0-based），默认 0",
            required = false,
        ),
        "limit" to ParameterDef(
            type = "integer",
            description = "最大行数，默认 100",
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
        private const val MAX_FILE_SIZE = 1024L * 1024L // 1 MB
        private const val DEFAULT_LIMIT = 100

        // 常见文本文件扩展名
        private val TEXT_EXTENSIONS = setOf(
            "txt", "log", "csv", "json", "xml", "html", "htm", "md",
            "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
            "sh", "bash", "zsh", "py", "js", "ts", "java", "kt", "kts",
            "c", "cpp", "h", "hpp", "css", "scss", "sql", "gradle",
            "svg", "text", "rst",
        )

        // 编码检测顺序
        private val ENCODINGS = listOf(
            Charsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("ISO-8859-1"),
            Charsets.UTF_16,
            Charset.forName("windows-1252"),
        )
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult(
            success = false,
            error = "缺少 path 参数",
        )
        val offset = args["offset"]?.toIntOrNull() ?: 0
        val limit = args["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT

        // 路径穿越检查
        if (containsPathTraversal(path)) {
            return ToolResult(
                success = false,
                error = "禁止路径穿越：路径中不允许包含 ../ 或 ..\\",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)

                // 安全检查
                if (!file.exists()) {
                    return@withContext ToolResult(
                        success = false,
                        error = "文件不存在: $path",
                    )
                }

                if (!file.isFile) {
                    return@withContext ToolResult(
                        success = false,
                        error = "路径不是文件: $path",
                    )
                }

                if (!isInSafeDirectory(file)) {
                    return@withContext ToolResult(
                        success = false,
                        error = "不允许读取此目录下的文件。允许的目录: ${safeDirectories.joinToString(", ") { it.absolutePath }}",
                    )
                }

                // 大小检查
                if (file.length() > MAX_FILE_SIZE) {
                    return@withContext ToolResult(
                        success = false,
                        error = "文件过大（${file.length()} 字节），仅允许读取小于 1MB 的文件",
                    )
                }

                // 扩展名检查
                val extension = file.extension.lowercase()
                if (extension.isNotEmpty() && extension !in TEXT_EXTENSIONS) {
                    return@withContext ToolResult(
                        success = false,
                        error = "不支持的文件类型: .$extension。仅允许文本文件。",
                    )
                }

                // 自动检测编码并读取
                val rawBytes = FileInputStream(file).use { it.readBytes() }
                val encoding = detectEncoding(rawBytes)
                val content = rawBytes.toString(encoding)

                // 分行
                val lines = content.lines()
                val totalLines = lines.size

                // 应用 offset 和 limit
                val fromIndex = offset.coerceIn(0, totalLines)
                val toIndex = (fromIndex + limit).coerceAtMost(totalLines)
                val selectedLines = lines.subList(fromIndex, toIndex)
                val resultContent = selectedLines.joinToString("\n")

                // 格式化修改时间
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val modTime = dateFormat.format(Date(file.lastModified()))

                ToolResult(
                    success = true,
                    data = mapOf(
                        "content" to resultContent,
                        "path" to file.absolutePath,
                        "size_bytes" to file.length().toString(),
                        "modify_time" to modTime,
                        "encoding" to encoding.name(),
                        "total_lines" to totalLines.toString(),
                        "offset" to fromIndex.toString(),
                        "limit" to limit.toString(),
                        "returned_lines" to selectedLines.size.toString(),
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
                    error = "读取文件失败: ${e.message}",
                )
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

    private fun detectEncoding(bytes: ByteArray): Charset {
        for (encoding in ENCODINGS) {
            try {
                val test = bytes.toString(encoding)
                // 检查是否有过多替换字符（U+FFFD）
                val replacementCount = test.count { it == '\uFFFD' }
                if (replacementCount.toDouble() / test.length.coerceAtLeast(1) < 0.01) {
                    return encoding
                }
            } catch (e: Exception) {
                continue
            }
        }
        return Charsets.UTF_8
    }
}
