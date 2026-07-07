package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class ProcessTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "process"
    override val description = "列出或结束进程"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "list 或 kill", required = true, enum = listOf("list", "kill")),
        "pid" to ParameterDef("string", "kill 时的进程 ID"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.success(mapOf("processes" to "[]", "note" to "Process management limited in non-root mode"))
    }
}
