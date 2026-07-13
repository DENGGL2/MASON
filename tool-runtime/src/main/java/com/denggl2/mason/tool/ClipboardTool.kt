package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class ClipboardTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "clipboard"
    override val description = "读取或写入剪贴板内容"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "read 或 write", required = true, enum = listOf("read", "write")),
        "text" to ParameterDef("string", "要写入的文本，action=write 时必填"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val action = args["action"] ?: return ToolResult.error("Missing action")
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (action == "read") {
            val clip = cm.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString() ?: ""
            return ToolResult.success(mapOf("text" to text))
        } else if (action == "write") {
            val text = args["text"] ?: return ToolResult.error("Missing text")
            cm.setPrimaryClip(android.content.ClipData.newPlainText("mason", text))
            return ToolResult.success(mapOf("written" to "true"))
        }
        return ToolResult.error("Unknown action: $action")
    }
}
