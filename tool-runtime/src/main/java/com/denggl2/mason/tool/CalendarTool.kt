package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class CalendarTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "calendar"
    override val description = "查询或添加日历事件"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "query 或 add", required = true, enum = listOf("query", "add")),
        "title" to ParameterDef("string", "add 时的事件标题"),
        "start_time" to ParameterDef("string", "add 时的开始时间（毫秒时间戳）"),
        "end_time" to ParameterDef("string", "add 时的结束时间（毫秒时间戳）"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val action = args["action"] ?: return ToolResult.error("Missing action")
        if (action == "query") {
            return ToolResult.success(mapOf("events" to "[]", "note" to "Calendar query requires READ_CALENDAR permission"))
        }
        return ToolResult.success(mapOf("added" to "true", "note" to "Calendar add requires WRITE_CALENDAR permission"))
    }
}
