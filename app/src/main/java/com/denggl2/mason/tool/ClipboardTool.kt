package com.denggl2.mason.tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "clipboard"
    override val description = "读写系统剪贴板"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作类型：read（读取剪贴板）或 write（写入剪贴板）",
            required = true,
            enum = listOf("read", "write"),
        ),
        "text" to ParameterDef(
            type = "string",
            description = "要写入的文本内容（action=write 时必填）",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return when (action) {
            "read" -> readClipboard()
            "write" -> writeClipboard(args["text"])
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun readClipboard(): ToolResult {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm == null) {
                return ToolResult(success = false, error = "无法获取 ClipboardManager")
            }
            val clip = cm.primaryClip
            if (clip == null || clip.itemCount == 0) {
                return ToolResult(
                    success = true,
                    data = mapOf("text" to "", "has_content" to "false"),
                )
            }

            val text = clip.getItemAt(0).text?.toString() ?: ""
            ToolResult(
                success = true,
                data = mapOf(
                    "text" to text,
                    "has_content" to text.isNotEmpty().toString(),
                ),
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "读取剪贴板失败: ${e.message}",
            )
        }
    }

    private fun writeClipboard(text: String?): ToolResult {
        if (text == null) {
            return ToolResult(success = false, error = "text 参数不能为空")
        }

        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm == null) {
                return ToolResult(success = false, error = "无法获取 ClipboardManager")
            }
            val clip = ClipData.newPlainText("mason_clipboard", text)
            cm.setPrimaryClip(clip)
            ToolResult(
                success = true,
                data = mapOf("status" to "已写入剪贴板"),
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "写入剪贴板失败: ${e.message}",
            )
        }
    }
}
