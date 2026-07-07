package com.denggl2.mason.tool

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@Singleton
class HttpRequestTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "http_request"
    override val description = "发送 HTTP GET/POST 请求，获取网页数据或调用外部 API。禁止访问内网地址。"
    override val parameters = mapOf(
        "method" to ParameterDef(
            type = "string",
            description = "HTTP 方法",
            required = true,
            enum = listOf("GET", "POST"),
        ),
        "url" to ParameterDef(
            type = "string",
            description = "请求 URL",
            required = true,
        ),
        "headers" to ParameterDef(
            type = "string",
            description = "请求头，JSON 格式字符串，如 {\"Content-Type\":\"application/json\"}",
            required = false,
        ),
        "body" to ParameterDef(
            type = "string",
            description = "请求体（POST 时使用）",
            required = false,
        ),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val privateRanges = listOf(
        Regex("^127\\."),
        Regex("^10\\."),
        Regex("^172\\.(1[6-9]|2\\d|3[01])\\."),
        Regex("^192\\.168\\."),
        Regex("^0\\."),
        Regex("^localhost$", RegexOption.IGNORE_CASE),
        Regex("\\blocalhost\\b", RegexOption.IGNORE_CASE),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val method = args["method"]?.uppercase() ?: return ToolResult(
            success = false,
            error = "缺少 method 参数",
        )
        val url = args["url"] ?: return ToolResult(
            success = false,
            error = "缺少 url 参数",
        )
        val headersStr = args["headers"]
        val body = args["body"]

        if (method !in listOf("GET", "POST")) {
            return ToolResult(success = false, error = "不支持的 HTTP 方法: $method")
        }

        // 提取 hostname 检查内网地址
        val hostname = extractHostname(url)
        if (hostname != null && isPrivateAddress(hostname)) {
            return ToolResult(
                success = false,
                error = "禁止访问内网地址: $hostname",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(url)

                // 解析 headers
                if (!headersStr.isNullOrBlank()) {
                    try {
                        val headersJson = org.json.JSONObject(headersStr)
                        val keys = headersJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            requestBuilder.addHeader(key, headersJson.getString(key))
                        }
                    } catch (e: Exception) {
                        return@withContext ToolResult(
                            success = false,
                            error = "headers 解析失败，请使用合法的 JSON 格式: ${e.message}",
                        )
                    }
                }

                when (method) {
                    "GET" -> requestBuilder.get()
                    "POST" -> {
                        val contentType = requestBuilder.build().header("Content-Type")
                            ?: "application/json; charset=utf-8"
                        val requestBody = (body ?: "").toRequestBody(contentType.toMediaType())
                        requestBuilder.post(requestBody)
                    }
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""
                val truncatedBody = if (responseBody.length > 4000) {
                    responseBody.substring(0, 4000) + "\n\n... (响应体已截断，原长度: ${responseBody.length} 字符)"
                } else {
                    responseBody
                }

                ToolResult(
                    success = response.isSuccessful,
                    data = mapOf(
                        "status_code" to response.code.toString(),
                        "body" to truncatedBody,
                        "url" to url,
                        "method" to method,
                    ),
                    error = if (!response.isSuccessful) "HTTP ${response.code}" else null,
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    error = "请求失败: ${e.message}",
                )
            }
        }
    }

    private fun extractHostname(url: String): String? {
        return try {
            val withoutProtocol = url.substringAfter("://")
            val hostPart = withoutProtocol.substringBefore("/").substringBefore(":")
            hostPart.lowercase()
        } catch (e: Exception) {
            null
        }
    }

    private fun isPrivateAddress(hostname: String): Boolean {
        // 先检查 localhost 模式
        for (pattern in privateRanges) {
            if (pattern.containsMatchIn(hostname)) return true
        }
        // 再解析 IP 检查
        return try {
            val addr = InetAddress.getByName(hostname)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
        } catch (e: Exception) {
            // DNS 解析失败，按 hostname 模式判断已足够
            false
        }
    }
}
