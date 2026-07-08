package com.denggl2.mason.tool

data class ToolResult(
    val success: Boolean,
    val data: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    companion object {
        fun success(data: Map<String, String> = emptyMap()) = ToolResult(success = true, data = data)
        fun error(message: String) = ToolResult(success = false, error = message)
    }
}
