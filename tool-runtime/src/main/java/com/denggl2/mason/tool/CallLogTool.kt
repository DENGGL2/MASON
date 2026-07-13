package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class CallLogTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "call_log"
    override val description = "查询通话记录"
    override val parameters: Map<String, ParameterDef> = mapOf(        "limit" to ParameterDef("string", "返回条数", required = false))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.success(mapOf("calls" to "[]", "note" to "Call log requires READ_CALL_LOG permission"))
    }
}
