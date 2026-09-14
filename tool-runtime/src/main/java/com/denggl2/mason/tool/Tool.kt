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
    val securityHints: ToolSecurityHints? get() = null

    suspend fun execute(args: Map<String, String>): ToolResult
    fun observe(): Flow<ToolResult> = kotlinx.coroutines.flow.flow {}
}

data class ToolSecurityHints(
    val readOnlyHint: Boolean? = null,
    val destructiveHint: Boolean? = null,
    val idempotentHint: Boolean? = null,
    val openWorldHint: Boolean? = null,
    val approvalHint: ToolApprovalHint = ToolApprovalHint.Default,
)

enum class ToolApprovalHint {
    Default,
    AlwaysAsk,
    AllowWithoutAsk,
}

data class ParameterDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null,
)
