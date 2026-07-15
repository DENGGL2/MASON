package com.denggl2.mason.ui.integration

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.integration.A2aAgentConfig
import com.denggl2.mason.integration.IntegrationConnectionPhase
import com.denggl2.mason.integration.IntegrationConnectionState
import com.denggl2.mason.integration.McpServerConfig

private enum class IntegrationEditorType { Mcp, A2a }

private data class PendingRemoval(val type: IntegrationEditorType, val id: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualIntegrationsScreen(
    onBack: () -> Unit,
    openMcpEditorOnStart: Boolean = false,
    viewModel: IntegrationsViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val mcpStates by viewModel.mcpStates.collectAsState()
    val a2aStates by viewModel.a2aStates.collectAsState()
    val context = LocalContext.current
    var editorType by remember(openMcpEditorOnStart) {
        mutableStateOf(if (openMcpEditorOnStart) IntegrationEditorType.Mcp else null)
    }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }
    var editingA2a by remember { mutableStateOf<A2aAgentConfig?>(null) }
    var pendingRemoval by remember { mutableStateOf<PendingRemoval?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("手动配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionTitle(
                    title = "MCP Server",
                    actionLabel = "添加",
                    onAdd = {
                        editingMcp = null
                        editorType = IntegrationEditorType.Mcp
                    },
                )
            }
            if (snapshot.mcpServers.isEmpty()) {
                item { EmptyText("暂无 MCP Server") }
            } else {
                items(snapshot.mcpServers, key = McpServerConfig::id) { server ->
                    ConnectionRow(
                        name = server.name,
                        endpoint = server.endpoint,
                        enabled = server.enabled,
                        state = mcpStates[server.id],
                        onEnabledChange = { viewModel.setMcpEnabled(server, it) },
                        onRefresh = { viewModel.testMcp(server.id) },
                        onEdit = {
                            editingMcp = server
                            editorType = IntegrationEditorType.Mcp
                        },
                        onRemove = { pendingRemoval = PendingRemoval(IntegrationEditorType.Mcp, server.id, server.name) },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)) }
            item {
                SectionTitle(
                    title = "A2A Agent",
                    actionLabel = "添加",
                    onAdd = {
                        editingA2a = null
                        editorType = IntegrationEditorType.A2a
                    },
                )
            }
            if (snapshot.a2aAgents.isEmpty()) {
                item { EmptyText("暂无 A2A Agent") }
            } else {
                items(snapshot.a2aAgents, key = A2aAgentConfig::id) { agent ->
                    ConnectionRow(
                        name = agent.name,
                        endpoint = agent.cardUrl,
                        enabled = agent.enabled,
                        state = a2aStates[agent.id],
                        onEnabledChange = { viewModel.setA2aEnabled(agent, it) },
                        onRefresh = { viewModel.testA2a(agent.id) },
                        onEdit = {
                            editingA2a = agent
                            editorType = IntegrationEditorType.A2a
                        },
                        onRemove = { pendingRemoval = PendingRemoval(IntegrationEditorType.A2a, agent.id, agent.name) },
                    )
                }
            }
        }
    }

    editorType?.let { type ->
        ConnectionEditorDialog(
            type = type,
            initialName = if (type == IntegrationEditorType.Mcp) editingMcp?.name.orEmpty() else editingA2a?.name.orEmpty(),
            initialUrl = if (type == IntegrationEditorType.Mcp) editingMcp?.endpoint.orEmpty() else editingA2a?.cardUrl.orEmpty(),
            initialToken = if (type == IntegrationEditorType.Mcp) editingMcp?.bearerToken.orEmpty() else editingA2a?.bearerToken.orEmpty(),
            onDismiss = { editorType = null },
            onSave = { name, url, token ->
                if (type == IntegrationEditorType.Mcp) {
                    viewModel.saveMcp(
                        editingMcp?.copy(name = name, endpoint = url, bearerToken = token)
                            ?: McpServerConfig(name = name, endpoint = url, bearerToken = token),
                    )
                } else {
                    viewModel.saveA2a(
                        editingA2a?.copy(name = name, cardUrl = url, bearerToken = token)
                            ?: A2aAgentConfig(name = name, cardUrl = url, bearerToken = token),
                    )
                }
                editorType = null
            },
        )
    }

    pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("移除连接") },
            text = { Text("将移除 ${pending.name} 的连接配置，不会影响远端服务。") },
            confirmButton = {
                TextButton(onClick = {
                    if (pending.type == IntegrationEditorType.Mcp) viewModel.removeMcp(pending.id)
                    else viewModel.removeA2a(pending.id)
                    pendingRemoval = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String, actionLabel: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun ConnectionRow(
    name: String,
    endpoint: String,
    enabled: Boolean,
    state: IntegrationConnectionState?,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state?.phase == IntegrationConnectionPhase.Connecting) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
            }
            Text(
                state?.detail ?: if (enabled) "尚未检查" else "已停用",
                style = MaterialTheme.typography.bodySmall,
                color = when (state?.phase) {
                    IntegrationConnectionPhase.Online -> MaterialTheme.colorScheme.primary
                    IntegrationConnectionPhase.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = enabled) { Icon(Icons.Outlined.Refresh, "检查连接") }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "移除") }
        }
    }
}

@Composable
private fun ConnectionEditorDialog(
    type: IntegrationEditorType,
    initialName: String,
    initialUrl: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }
    val valid = name.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == IntegrationEditorType.Mcp) "MCP Server" else "A2A Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(
                    url,
                    { url = it },
                    label = { Text(if (type == IntegrationEditorType.Mcp) "MCP Endpoint" else "Agent Card 或服务地址") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
                OutlinedTextField(
                    token,
                    { token = it },
                    label = { Text("Bearer Token（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), url.trim(), token.trim()) }, enabled = valid) { Text("保存并检查") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
