package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class ContactsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "contacts"
    override val description = "查询通讯录"
    override val parameters: Map<String, ParameterDef> = mapOf(        "query" to ParameterDef("string", "搜索关键词", required = false))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val query = args["query"] ?: ""
        return ToolResult.success(mapOf("contacts" to "[]", "note" to "Contacts requires READ_CONTACTS permission"))
    }
}
