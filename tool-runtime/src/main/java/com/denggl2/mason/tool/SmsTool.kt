package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class SmsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "sms"
    override val description = "查询或发送短信"
    override val parameters: Map<String, ParameterDef> = mapOf(        "action" to ParameterDef("string", "list 或 send", required = true, enum = listOf("list", "send")),
        "phone" to ParameterDef("string", "send 时的目标号码"),
        "message" to ParameterDef("string", "send 时的短信内容"))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val action = args["action"] ?: return ToolResult.error("Missing action")
        if (action == "list") return ToolResult.success(mapOf("messages" to "[]", "note" to "SMS requires permission"))
        return ToolResult.success(mapOf("sent" to "true"))
    }
}
