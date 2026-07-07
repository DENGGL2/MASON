package com.denggl2.mason.tool

import kotlinx.coroutines.flow.Flow

interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, ParameterDef>

    suspend fun execute(args: Map<String, String>): ToolResult
    fun observe(): Flow<ToolResult> = kotlinx.coroutines.flow.flow {}
}

data class ParameterDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null,
)
