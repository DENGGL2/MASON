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
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot = _snapshot.asStateFlow()

    suspend fun upsertMcp(config: McpServerConfig) = update { current ->
        val validationError = validateRemoteEndpoint(config.endpoint)
        require(validationError == null) { validationError ?: "MCP 地址不正确" }
        current.copy(mcpServers = current.mcpServers.upsert(config) { it.id })
    }

    suspend fun removeMcp(id: String) = update { current ->
        current.copy(mcpServers = current.mcpServers.filterNot { it.id == id })
    }

    suspend fun upsertA2a(config: A2aAgentConfig) = update { current ->
        val validationError = validateRemoteEndpoint(config.cardUrl)
        require(validationError == null) { validationError ?: "A2A 地址不正确" }
        current.copy(a2aAgents = current.a2aAgents.upsert(config) { it.id })
    }

    suspend fun removeA2a(id: String) = update { current ->
        current.copy(a2aAgents = current.a2aAgents.filterNot { it.id == id })
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

    private fun readSnapshot(): IntegrationConfigSnapshot {
        val file = configFile()
        if (!file.exists()) return IntegrationConfigSnapshot()
        return runCatching {
            json.decodeFromString<IntegrationConfigSnapshot>(file.readText(Charsets.UTF_8))
        }.getOrDefault(IntegrationConfigSnapshot())
    }

    private fun configFile(): File = File(context.filesDir, "integrations/connections.json").also {
        it.parentFile?.mkdirs()
    }
}

private fun <T> List<T>.upsert(value: T, id: (T) -> String): List<T> {
    val index = indexOfFirst { id(it) == id(value) }
    return if (index < 0) this + value else toMutableList().apply { set(index, value) }
}
