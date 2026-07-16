package com.denggl2.mason.ui.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.integration.A2aAgentConfig
import com.denggl2.mason.integration.A2aToolManager
import com.denggl2.mason.integration.AppCapabilityAuthorization
import com.denggl2.mason.integration.CapabilityProviderCatalog
import com.denggl2.mason.integration.CapabilityProviderState
import com.denggl2.mason.integration.IntegrationStore
import com.denggl2.mason.integration.McpServerConfig
import com.denggl2.mason.integration.McpAuthType
import com.denggl2.mason.integration.McpOAuthCoordinator
import com.denggl2.mason.integration.McpOAuthEvent
import com.denggl2.mason.integration.McpServiceCatalogEntry
import com.denggl2.mason.integration.McpToolManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntegrationsViewModel @Inject constructor(
    private val store: IntegrationStore,
    private val mcpToolManager: McpToolManager,
    private val a2aToolManager: A2aToolManager,
    private val appAuthorization: AppCapabilityAuthorization,
    private val mcpOAuthCoordinator: McpOAuthCoordinator,
) : ViewModel() {
    val snapshot = store.snapshot.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        store.snapshot.value,
    )
    val mcpStates = mcpToolManager.states
    val a2aStates = a2aToolManager.states
    private val _appProviders = MutableStateFlow<List<CapabilityProviderState>>(emptyList())
    val appProviders = _appProviders.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    val oauthEvents = mcpOAuthCoordinator.events

    init {
        refreshAppProviders()
    }

    fun refreshAppProviders() {
        _appProviders.value = CapabilityProviderCatalog.appCollaborations.map(appAuthorization::state)
    }

    fun authorizationIntent(providerId: String) = CapabilityProviderCatalog.appCollaborations
        .firstOrNull { it.id == providerId }
        ?.let(appAuthorization::createAuthorizationIntent)

    fun completeAuthorization(providerId: String, authorized: Boolean) {
        if (authorized) appAuthorization.markAuthorized(providerId)
        refreshAppProviders()
        _messages.tryEmit(if (authorized) "应用协作已授权" else "未完成授权")
    }

    fun saveMcp(config: McpServerConfig) = launchAction("MCP 配置已保存") {
        store.upsertMcp(config)
        mcpToolManager.refresh(config.id)
    }

    fun setMcpEnabled(config: McpServerConfig, enabled: Boolean) = launchAction(
        if (enabled) "MCP 已启用" else "MCP 已停用",
    ) {
        store.upsertMcp(config.copy(enabled = enabled))
        mcpToolManager.refreshAll()
    }

    fun removeMcp(id: String) = launchAction("MCP 配置已移除") {
        store.removeMcp(id)
        mcpToolManager.refreshAll()
    }

    fun testMcp(id: String) = launchAction("MCP 连接检查完成") {
        mcpToolManager.refresh(id)
    }

    fun connectCatalogService(
        entry: McpServiceCatalogEntry,
        authType: McpAuthType,
        token: String,
        clientId: String,
    ) = viewModelScope.launch {
        runCatching {
            val existing = store.snapshot.value.mcpServers.firstOrNull { it.catalogId == entry.id }
            val config = existing?.copy(
                name = entry.name,
                endpoint = entry.endpoint,
                authType = authType,
                clientId = clientId,
                scopes = entry.scopes,
                bearerToken = token,
                enabled = true,
            ) ?: McpServerConfig(
                name = entry.name,
                endpoint = entry.endpoint,
                catalogId = entry.id,
                authType = authType,
                clientId = clientId,
                scopes = entry.scopes,
                bearerToken = token,
            )
            store.upsertMcp(config)
            if (authType == McpAuthType.OAUTH) {
                mcpOAuthCoordinator.start(config.id)
            } else {
                mcpToolManager.refresh(config.id)
                _messages.emit("${entry.name} 连接配置已保存")
            }
        }.onFailure { error -> _messages.emit(error.message ?: "连接失败") }
    }

    fun startMcpOAuth(serverId: String) {
        viewModelScope.launch { mcpOAuthCoordinator.start(serverId) }
    }

    fun saveA2a(config: A2aAgentConfig) = launchAction("A2A 配置已保存") {
        store.upsertA2a(config)
        a2aToolManager.refresh(config.id)
    }

    fun setA2aEnabled(config: A2aAgentConfig, enabled: Boolean) = launchAction(
        if (enabled) "A2A Agent 已启用" else "A2A Agent 已停用",
    ) {
        store.upsertA2a(config.copy(enabled = enabled))
        a2aToolManager.refreshAll()
    }

    fun removeA2a(id: String) = launchAction("A2A 配置已移除") {
        store.removeA2a(id)
        a2aToolManager.refreshAll()
    }

    fun testA2a(id: String) = launchAction("A2A Agent 检查完成") {
        a2aToolManager.refresh(id)
    }

    private fun launchAction(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _messages.emit(successMessage) }
                .onFailure { error -> _messages.emit(error.message ?: error.javaClass.simpleName) }
        }
    }
}
