package com.denggl2.mason.integration

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: IntegrationSecretStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(readAndMigrateSnapshot())
    val snapshot = _snapshot.asStateFlow()

    suspend fun upsertMcp(config: McpServerConfig) = update { current ->
        val validationError = validateRemoteEndpoint(config.endpoint)
        require(validationError == null) { validationError ?: "MCP 地址不正确" }
        val old = current.mcpServers.firstOrNull { it.id == config.id }
        val secret = config.bearerToken.takeIf(String::isNotBlank)
        val credentialRef = when {
            secret != null -> secrets.put(secret, config.credentialRef ?: old?.credentialRef)
            old != null && old.authType != config.authType -> null
            config.credentialRef != null -> config.credentialRef
            else -> old?.credentialRef
        }
        if (old?.credentialRef != null && credentialRef == null) secrets.remove(old.credentialRef)
        val safe = config.copy(
            authType = when {
                config.authType != McpAuthType.NONE -> config.authType
                credentialRef != null -> McpAuthType.BEARER_TOKEN
                else -> McpAuthType.NONE
            },
            credentialRef = credentialRef,
            bearerToken = "",
        )
        current.copy(mcpServers = current.mcpServers.upsert(safe) { it.id })
    }

    suspend fun removeMcp(id: String) = update { current ->
        secrets.remove(current.mcpServers.firstOrNull { it.id == id }?.credentialRef)
        current.copy(mcpServers = current.mcpServers.filterNot { it.id == id })
    }

    suspend fun upsertA2a(config: A2aAgentConfig) = update { current ->
        val validationError = validateRemoteEndpoint(config.cardUrl)
        require(validationError == null) { validationError ?: "A2A 地址不正确" }
        val old = current.a2aAgents.firstOrNull { it.id == config.id }
        val credentialRef = config.bearerToken.takeIf(String::isNotBlank)
            ?.let { secrets.put(it, config.credentialRef ?: old?.credentialRef) }
            ?: config.credentialRef
            ?: old?.credentialRef
        val safe = config.copy(credentialRef = credentialRef, bearerToken = "")
        current.copy(a2aAgents = current.a2aAgents.upsert(safe) { it.id })
    }

    suspend fun removeA2a(id: String) = update { current ->
        secrets.remove(current.a2aAgents.firstOrNull { it.id == id }?.credentialRef)
        current.copy(a2aAgents = current.a2aAgents.filterNot { it.id == id })
    }

    fun resolve(config: McpServerConfig): McpServerConfig = config.copy(
        bearerToken = secrets.get(config.credentialRef),
    )

    fun resolve(config: A2aAgentConfig): A2aAgentConfig = config.copy(
        bearerToken = secrets.get(config.credentialRef),
    )

    suspend fun updateMcpCredential(id: String, accessToken: String) = update { current ->
        val config = current.mcpServers.firstOrNull { it.id == id }
            ?: error("MCP 配置不存在")
        val credentialRef = secrets.put(accessToken, config.credentialRef)
        current.copy(mcpServers = current.mcpServers.upsert(
            config.copy(authType = McpAuthType.OAUTH, credentialRef = credentialRef, bearerToken = ""),
        ) { it.id })
    }

    private suspend fun update(transform: (IntegrationConfigSnapshot) -> IntegrationConfigSnapshot) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = transform(_snapshot.value)
                configFile().writeText(json.encodeToString(next), Charsets.UTF_8)
                _snapshot.value = next
            }
        }
    }

    private fun readAndMigrateSnapshot(): IntegrationConfigSnapshot {
        val file = configFile()
        if (!file.exists()) return IntegrationConfigSnapshot()
        val loaded = runCatching {
            json.decodeFromString<IntegrationConfigSnapshot>(file.readText(Charsets.UTF_8))
        }.getOrDefault(IntegrationConfigSnapshot())
        val migrated = migrateIntegrationSnapshot(loaded, secrets::put)
        if (migrated != loaded) file.writeText(json.encodeToString(migrated), Charsets.UTF_8)
        return migrated
    }

    private fun configFile(): File = File(context.filesDir, "integrations/connections.json").also {
        it.parentFile?.mkdirs()
    }
}

internal fun migrateIntegrationSnapshot(
    loaded: IntegrationConfigSnapshot,
    storeSecret: (String, String?) -> String,
): IntegrationConfigSnapshot = loaded.copy(
    mcpServers = loaded.mcpServers.map { config ->
        if (config.bearerToken.isBlank()) config else config.copy(
            authType = if (config.authType == McpAuthType.NONE) McpAuthType.BEARER_TOKEN else config.authType,
            credentialRef = storeSecret(config.bearerToken, config.credentialRef),
            bearerToken = "",
        )
    },
    a2aAgents = loaded.a2aAgents.map { config ->
        if (config.bearerToken.isBlank()) config else config.copy(
            credentialRef = storeSecret(config.bearerToken, config.credentialRef),
            bearerToken = "",
        )
    },
    schemaVersion = 2,
)

private fun <T> List<T>.upsert(value: T, id: (T) -> String): List<T> {
    val index = indexOfFirst { id(it) == id(value) }
    return if (index < 0) this + value else toMutableList().apply { set(index, value) }
}
