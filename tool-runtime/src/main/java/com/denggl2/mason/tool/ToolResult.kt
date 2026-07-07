package com.denggl2.mason.tool

data class ToolResult(
    val success: Boolean,
    val data: Map<String, String> = emptyMap(),
    val error: String? = null,
)
