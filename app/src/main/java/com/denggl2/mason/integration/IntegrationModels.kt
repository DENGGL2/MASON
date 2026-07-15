package com.denggl2.mason.integration

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpoint: String,
    val bearerToken: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class A2aAgentConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cardUrl: String,
    val bearerToken: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class IntegrationConfigSnapshot(
    val mcpServers: List<McpServerConfig> = emptyList(),
    val a2aAgents: List<A2aAgentConfig> = emptyList(),
    val schemaVersion: Int = 1,
)

enum class IntegrationConnectionPhase {
    Disabled,
    Connecting,
    Online,
    Error,
}

data class IntegrationConnectionState(
    val phase: IntegrationConnectionPhase = IntegrationConnectionPhase.Disabled,
    val displayName: String = "",
    val detail: String = "",
    val capabilityCount: Int = 0,
    val checkedAt: Long? = null,
)

internal fun validateRemoteEndpoint(value: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
        ?: return "地址格式不正确"
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        return "仅支持完整的 HTTP 或 HTTPS 地址"
    }
    return null
}

internal fun String.integrationNamespace(): String = lowercase()
    .replace(Regex("[^a-z0-9_-]+"), "_")
    .trim('_')
    .take(32)
    .ifBlank { "remote" }
