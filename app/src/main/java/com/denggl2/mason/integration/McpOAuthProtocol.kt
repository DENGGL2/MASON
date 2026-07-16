package com.denggl2.mason.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class McpOAuthMetadata(
    val resource: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
)

@Singleton
class McpOAuthProtocol @Inject internal constructor(
    private val transport: JsonRpcHttpTransport,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun discover(endpoint: String): McpOAuthMetadata {
        val challenge = runCatching {
            transport.post(
                url = endpoint,
                bearerToken = "",
                protocolVersion = PROTOCOL_VERSION,
                payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "initialize")
                    put("params", buildJsonObject {
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("capabilities", buildJsonObject {})
                        put("clientInfo", buildJsonObject {
                            put("name", "Mason")
                            put("version", "0.1.0")
                        })
                    })
                },
            )
        }.exceptionOrNull() as? RemoteProtocolException
            ?: throw IOException("该 MCP 服务没有返回 OAuth 登录信息")
        if (challenge.statusCode != 401) throw challenge

        val resourceMetadataUrl = challenge.responseHeaders.entries
            .firstOrNull { it.key.equals("WWW-Authenticate", ignoreCase = true) }
            ?.value
            ?.asSequence()
            ?.mapNotNull(::parseResourceMetadataUrl)
            ?.firstOrNull()
            ?: throw IOException("服务需要登录，但没有提供 OAuth 资源元数据")
        val resourceMetadata = getJson(resourceMetadataUrl)
        val resource = resourceMetadata.string("resource") ?: endpoint
        val authorizationServer = resourceMetadata["authorization_servers"]?.jsonArray
            ?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: throw IOException("OAuth 资源元数据缺少授权服务器")
        val authorizationMetadata = getJson(authorizationMetadataUrl(authorizationServer))
        return McpOAuthMetadata(
            resource = resource,
            authorizationEndpoint = authorizationMetadata.string("authorization_endpoint")
                ?: throw IOException("授权服务器没有提供登录地址"),
            tokenEndpoint = authorizationMetadata.string("token_endpoint")
                ?: throw IOException("授权服务器没有提供 Token 地址"),
            scopes = resourceMetadata["scopes_supported"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
        )
    }

    suspend fun exchangeCode(
        tokenEndpoint: String,
        code: String,
        clientId: String,
        redirectUri: String,
        verifier: String,
        resource: String,
    ): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .add("resource", resource)
            .build()
        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(body)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("OAuth 换取 Token 失败：HTTP ${response.code} ${payload.take(300)}")
            }
            val parsed = runCatching { json.parseToJsonElement(payload) as JsonObject }.getOrNull()
            parsed?.string("access_token")?.takeIf(String::isNotBlank)
                ?: throw IOException("OAuth 响应中没有 access_token")
        }
    }

    private suspend fun getJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("读取 OAuth 元数据失败：HTTP ${response.code}")
            runCatching { json.parseToJsonElement(body) as JsonObject }
                .getOrElse { throw IOException("OAuth 元数据格式不正确", it) }
        }
    }

    internal fun authorizationMetadataUrl(issuer: String): String {
        val url = issuer.toHttpUrl()
        val issuerPath = url.encodedPath.trimEnd('/').takeUnless { it == "/" }.orEmpty()
        return url.newBuilder()
            .encodedPath("/.well-known/oauth-authorization-server$issuerPath")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    internal fun parseResourceMetadataUrl(header: String): String? =
        RESOURCE_METADATA.find(header)?.groupValues?.get(1)

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        val RESOURCE_METADATA = Regex("resource_metadata\\s*=\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
    }
}
