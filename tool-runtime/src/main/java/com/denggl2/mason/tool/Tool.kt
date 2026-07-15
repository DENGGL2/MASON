package com.denggl2.mason.tool

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface Tool {
    val name: String
    val description: String
    val displayName: String get() = name
    val approvalDescription: String get() = description
    val parameters: Map<String, ParameterDef>
    val inputSchema: JsonObject? get() = null

    suspend fun execute(args: Map<String, String>): ToolResult
    fun observe(): Flow<ToolResult> = kotlinx.coroutines.flow.flow {}
}

data class ParameterDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null,
)
