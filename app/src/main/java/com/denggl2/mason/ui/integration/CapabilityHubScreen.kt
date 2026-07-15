package com.denggl2.mason.ui.integration

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.integration.CapabilityConnectionStatus
import com.denggl2.mason.integration.CapabilityProviderCatalog
import com.denggl2.mason.integration.CapabilityProviderState
import com.denggl2.mason.integration.IntegrationConnectionPhase
import com.denggl2.mason.integration.IntegrationConnectionState
import com.denggl2.mason.integration.McpServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    viewModel: IntegrationsViewModel = hiltViewModel(),
) {
    var showManualConfiguration by remember { mutableStateOf(false) }
    if (showManualConfiguration) {
        BackHandler { showManualConfiguration = false }
        ManualIntegrationsScreen(
            onBack = { showManualConfiguration = false },
            viewModel = viewModel,
        )
        return
    }

    val context = LocalContext.current
    val providers by viewModel.appProviders.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val mcpStates by viewModel.mcpStates.collectAsState()
    val a2aStates by viewModel.a2aStates.collectAsState()
    var pendingProviderId by remember { mutableStateOf<String?>(null) }
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pendingProviderId?.let { providerId ->
            viewModel.completeAuthorization(providerId, result.resultCode == Activity.RESULT_OK)
        }
        pendingProviderId = null
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAppProviders()
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("扩展能力") },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CapabilitySectionHeader(
                    title = "应用协作（A2A）",
                    description = "让 Mason 把任务交给具体 App。首次连接在目标 App 内授权，发送消息、通话或支付等操作仍会再次确认。",
                )
            }
            items(providers, key = { it.provider.id }) { providerState ->
                AppCollaborationRow(
                    state = providerState,
                    onConnect = {
                        val intent: Intent? = viewModel.authorizationIntent(providerState.provider.id)
                        if (intent == null) {
                            Toast.makeText(context, providerState.detail, Toast.LENGTH_SHORT).show()
                        } else {
                            pendingProviderId = providerState.provider.id
                            authorizationLauncher.launch(intent)
                        }
                    },
                )
            }
            if (snapshot.a2aAgents.isNotEmpty()) {
                item { Text("自定义 A2A 服务", style = MaterialTheme.typography.titleSmall) }
                items(snapshot.a2aAgents, key = { "a2a-${it.id}" }) { agent ->
                    ConnectedServiceRow(
                        name = agent.name,
                        purpose = "可接收 Mason 委派的任务",
                        enabled = agent.enabled,
                        state = a2aStates[agent.id],
                    )
                }
            }

            item { Spacer(Modifier.height(6.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp)) }
            item {
                CapabilitySectionHeader(
                    title = "工具扩展（MCP）",
                    description = "给 Mason 增加搜索、文件、地图、GitHub 和办公系统等工具。连接后，实际调用仍受工具确认和审计保护。",
                )
            }
            if (snapshot.mcpServers.isEmpty()) {
                item {
                    EmptyToolServices(onConnect = { showManualConfiguration = true })
                }
            } else {
                items(snapshot.mcpServers, key = McpServerConfig::id) { server ->
                    ConnectedServiceRow(
                        name = server.name,
                        purpose = inferMcpPurpose(server.name),
                        enabled = server.enabled,
                        state = mcpStates[server.id],
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showManualConfiguration = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Build, contentDescription = null)
                        Text("管理工具服务", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                TextButton(
                    onClick = { showManualConfiguration = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("手动配置 MCP / A2A")
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun CapabilitySectionHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppCollaborationRow(state: CapabilityProviderState, onConnect: () -> Unit) {
    val statusLabel = when (state.status) {
        CapabilityConnectionStatus.NotInstalled -> "未安装"
        CapabilityConnectionStatus.NeedsAuthorization -> "待授权"
        CapabilityConnectionStatus.Connected -> "已连接"
        CapabilityConnectionStatus.WaitingForOfficialAccess -> "等待官方接入"
        CapabilityConnectionStatus.Unavailable -> "当前不可用"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(state.provider.displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.provider.capabilities.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor(state.status))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.detail,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.status == CapabilityConnectionStatus.NeedsAuthorization) {
                    Button(onClick = onConnect) {
                        Text("连接${state.provider.displayName}")
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedServiceRow(
    name: String,
    purpose: String,
    enabled: Boolean,
    state: IntegrationConnectionState?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state?.detail ?: if (enabled) "等待连接检查" else "已停用",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state?.phase) {
                        IntegrationConnectionPhase.Online -> MaterialTheme.colorScheme.primary
                        IntegrationConnectionPhase.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                when (state?.phase) {
                    IntegrationConnectionPhase.Online -> "在线"
                    IntegrationConnectionPhase.Connecting -> "连接中"
                    IntegrationConnectionPhase.Error -> "异常"
                    else -> if (enabled) "待检查" else "已停用"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun EmptyToolServices(onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "当前未连接工具服务。支持的用途：${CapabilityProviderCatalog.mcpPurposes.joinToString("、")}。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Build, contentDescription = null)
            Text("连接工具服务", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun statusColor(status: CapabilityConnectionStatus) = when (status) {
    CapabilityConnectionStatus.Connected -> MaterialTheme.colorScheme.primary
    CapabilityConnectionStatus.NeedsAuthorization -> MaterialTheme.colorScheme.tertiary
    CapabilityConnectionStatus.Unavailable -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun inferMcpPurpose(name: String): String {
    val normalized = name.lowercase()
    return when {
        "github" in normalized -> "代码仓库和研发协作"
        listOf("map", "地图", "高德").any(normalized::contains) -> "地图和位置服务"
        listOf("file", "文件").any(normalized::contains) -> "文件读取和处理"
        listOf("search", "搜索").any(normalized::contains) -> "联网搜索和资料检索"
        listOf("office", "办公", "飞书", "钉钉").any(normalized::contains) -> "办公系统协作"
        else -> "为 Mason 提供外部工具"
    }
}
