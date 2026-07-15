package com.denggl2.mason.integration

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationManager @Inject constructor(
    private val mcpToolManager: McpToolManager,
    private val a2aToolManager: A2aToolManager,
) {
    suspend fun refreshAll() = coroutineScope {
        launch { mcpToolManager.refreshAll() }
        launch { a2aToolManager.refreshAll() }
    }
}
