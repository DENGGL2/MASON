package com.denggl2.mason.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed interface McpOAuthEvent {
    data class OpenBrowser(val intent: Intent) : McpOAuthEvent
    data class Completed(val serverId: String) : McpOAuthEvent
    data class Failed(val message: String) : McpOAuthEvent
}

@Serializable
private data class PendingMcpOAuth(
    val serverId: String,
    val state: String,
    val verifier: String,
    val clientId: String,
    val tokenEndpoint: String,
    val resource: String,
    val redirectUri: String,
)

@Singleton
class McpOAuthCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val store: IntegrationStore,
    private val secrets: IntegrationSecretStore,
    private val protocol: McpOAuthProtocol,
    private val toolManager: McpToolManager,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val _events = MutableSharedFlow<McpOAuthEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    suspend fun start(serverId: String) {
        runCatching {
            val config = store.snapshot.value.mcpServers.firstOrNull { it.id == serverId }
                ?: error("MCP 配置不存在")
            require(config.clientId.isNotBlank()) { "该服务需要 OAuth Client ID；也可以改用访问令牌连接" }
            val metadata = protocol.discover(config.endpoint)
            val state = randomUrlToken(24)
            val verifier = randomUrlToken(64)
            val redirectUri = REDIRECT_URI
            val requestedScopes = config.scopes.distinct()
            val pending = PendingMcpOAuth(
                serverId = serverId,
                state = state,
                verifier = verifier,
                clientId = config.clientId,
                tokenEndpoint = metadata.tokenEndpoint,
                resource = metadata.resource,
                redirectUri = redirectUri,
            )
            val ref = secrets.put(json.encodeToString(pending))
            preferences.edit().putString(PENDING_PREFIX + state, ref).apply()
            val authorizationUrl = metadata.authorizationEndpoint.toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", config.clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", verifier.sha256Url())
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("resource", metadata.resource)
                .apply { if (requestedScopes.isNotEmpty()) appendQueryParameter("scope", requestedScopes.joinToString(" ")) }
                .build()
            McpOAuthEvent.OpenBrowser(Intent(Intent.ACTION_VIEW, authorizationUrl))
        }.onSuccess { _events.emit(it) }
            .onFailure { _events.emit(McpOAuthEvent.Failed(it.message ?: "无法开始授权")) }
    }

    suspend fun handleCallback(uri: Uri?) {
        if (uri?.scheme != "mason" || uri.host != "oauth" || uri.path != "/mcp") return
        val state = uri.getQueryParameter("state")
        val ref = state?.let { preferences.getString(PENDING_PREFIX + it, null) }
        val pending = ref?.let(secrets::get)?.takeIf(String::isNotBlank)?.let {
            runCatching { json.decodeFromString<PendingMcpOAuth>(it) }.getOrNull()
        }
        if (state.isNullOrBlank() || pending == null || pending.state != state) {
            _events.emit(McpOAuthEvent.Failed("授权回调校验失败，请重新连接"))
            return
        }
        preferences.edit().remove(PENDING_PREFIX + state).apply()
        secrets.remove(ref)
        uri.getQueryParameter("error")?.let { error ->
            _events.emit(McpOAuthEvent.Failed(uri.getQueryParameter("error_description") ?: error))
            return
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _events.emit(McpOAuthEvent.Failed("授权回调中没有 code"))
            return
        }
        runCatching {
            protocol.exchangeCode(
                tokenEndpoint = pending.tokenEndpoint,
                code = code,
                clientId = pending.clientId,
                redirectUri = pending.redirectUri,
                verifier = pending.verifier,
                resource = pending.resource,
            ).also { token -> store.updateMcpCredential(pending.serverId, token) }
            toolManager.refresh(pending.serverId)
        }.onSuccess { _events.emit(McpOAuthEvent.Completed(pending.serverId)) }
            .onFailure { _events.emit(McpOAuthEvent.Failed(it.message ?: "授权失败")) }
    }

    private fun randomUrlToken(size: Int): String = ByteArray(size).also(random::nextBytes).toBase64Url()
    private fun String.sha256Url(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.US_ASCII)).toBase64Url()
    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(
        this,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private companion object {
        const val PREFS_NAME = "mason_mcp_oauth"
        const val PENDING_PREFIX = "pending_"
        const val REDIRECT_URI = "mason://oauth/mcp"
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
