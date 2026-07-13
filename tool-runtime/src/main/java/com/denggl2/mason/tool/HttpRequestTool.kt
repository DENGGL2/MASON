package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class HttpRequestTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "http_request"
    override val description = "发起 HTTP 请求"
    override val parameters: Map<String, ParameterDef> = mapOf(        "url" to ParameterDef("string", "请求 URL", required = true),
        "method" to ParameterDef("string", "GET/POST", required = false))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val url = args["url"] ?: return ToolResult.error("Missing url")
        val method = args["method"] ?: "GET"
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return ToolResult.success(mapOf("status" to response.code.toString(), "body" to (response.body?.string() ?: "")))
    }
}
