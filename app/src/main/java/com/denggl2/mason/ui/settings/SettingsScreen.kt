package com.denggl2.mason.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.ui.theme.MasonAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPermission: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val apiTestState by viewModel.apiTestState.collectAsState()
    val modelRefreshState by viewModel.modelRefreshState.collectAsState()
    val context = LocalContext.current

    var providerId by remember(config) { mutableStateOf(config.providerId) }
    var url by remember(config) { mutableStateOf(config.apiUrl) }
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var toolsEnabled by remember(config) { mutableStateOf(config.toolsEnabled) }
    var keyVisible by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    val provider = AiProviderCatalog.getProvider(providerId)
        ?: AiProviderCatalog.defaultProvider
    val modelOptions = if (provider.id == "openrouter" && modelRefreshState.models.isNotEmpty()) {
        modelRefreshState.models + provider.modelOptions.filterNot { builtIn ->
            builtIn.isFree || modelRefreshState.models.any { it.id == builtIn.id }
        }
    } else {
        provider.modelOptions
    }
    val selectedModel = modelOptions.firstOrNull { it.id == model }
        ?: AiProviderCatalog.getModel(provider.id, model)

    LaunchedEffect(providerId, modelRefreshState.models) {
        if (providerId == "openrouter" && modelRefreshState.models.isNotEmpty()) {
            val currentStillExists = modelRefreshState.models.any { it.id == model }
            if (!currentStillExists && selectedModel?.isFree == true) {
                model = modelRefreshState.models.first().id
                toolsEnabled = false
            }
        }
    }

    var showClearConversationsDialog by remember { mutableStateOf(false) }
    var showClearCrashDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importConversations(it) }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("设置", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Section: API 配置 ──
            SectionHeader("AI 模型")

            SelectorField(
                label = "服务商",
                value = provider.name,
                description = provider.description,
                expanded = showProviderMenu,
                onExpandedChange = { showProviderMenu = it },
            ) {
                AiProviderCatalog.providers.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(item.name, color = Color.White)
                                Text(
                                    item.description,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        onClick = {
                            providerId = item.id
                            url = item.apiUrl
                            model = item.defaultModel
                            toolsEnabled = item.toolsEnabledByDefault
                            showProviderMenu = false
                        },
                    )
                }
            }

            if (provider.id == "openrouter") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.refreshOpenRouterFreeModels(key) },
                        enabled = !modelRefreshState.isRefreshing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (modelRefreshState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MasonAccent,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MasonAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("刷新免费模型", color = MasonAccent)
                        }
                    }
                }

                modelRefreshState.message?.let { message ->
                    Text(
                        text = message,
                        color = when (modelRefreshState.success) {
                            true -> MasonAccent
                            false -> Color(0xFFEF5350)
                            null -> Color.Gray
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            if (modelOptions.isNotEmpty()) {
                SelectorField(
                    label = "模型预设",
                    value = selectedModel?.let {
                        if (it.isFree) "${it.name} · 免费" else it.name
                    } ?: model.ifBlank { "未选择" },
                    description = selectedModel?.description ?: "可在下方手动填写模型名",
                    expanded = showModelMenu,
                    onExpandedChange = { showModelMenu = it },
                ) {
                    modelOptions.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        if (item.isFree) "${item.name} · 免费" else item.name,
                                        color = Color.White,
                                    )
                                    Text(
                                        item.id,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            onClick = {
                                model = item.id
                                toolsEnabled = item.supportsTools
                                showModelMenu = false
                            },
                        )
                    }
                }
            }

            LabeledInput("API Base URL / Endpoint", url, provider.apiUrl) {
                url = it
            }

            LabeledInput(
                label = "API Key",
                value = key,
                placeholder = "sk-...",
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                            tint = Color.Gray,
                        )
                    }
                },
            ) { key = it }

            LabeledInput("模型名称", model, provider.defaultModel.ifBlank { "model-name" }) {
                model = it
            }

            SwitchRow(
                label = "允许调用手机工具",
                description = "模型支持 function calling 时开启；免费小模型或不兼容中转站可关闭",
                checked = toolsEnabled,
                onCheckedChange = { toolsEnabled = it },
            )

            apiTestState.message?.let { message ->
                Text(
                    text = message,
                    color = when (apiTestState.success) {
                        true -> MasonAccent
                        false -> Color(0xFFEF5350)
                        null -> Color.Gray
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.testApi(
                            ApiConfig(
                                providerId = providerId,
                                apiUrl = url,
                                apiKey = key,
                                model = model,
                                toolsEnabled = toolsEnabled,
                            )
                        )
                    },
                    enabled = !apiTestState.isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (apiTestState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MasonAccent,
                        )
                    } else {
                        Text("测试连接", color = MasonAccent)
                    }
                }

                Button(
                    onClick = {
                        val pendingConfig = ApiConfig(
                            providerId = providerId,
                            apiUrl = url,
                            apiKey = key,
                            model = model,
                            toolsEnabled = toolsEnabled,
                        )
                        val validationMessage = viewModel.validateApiConfig(pendingConfig)
                        if (validationMessage != null) {
                            Toast.makeText(context, validationMessage, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.save(pendingConfig)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MasonAccent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("保存", color = Color.Black)
            }
            }

            Spacer(Modifier.height(32.dp))

            // ── Section: 数据管理 ──
            SectionHeader("数据管理")

            DataActionButton(
                icon = Icons.Default.FileDownload,
                label = "导出对话",
                description = "将所有对话和消息导出为 JSON 文件",
                onClick = { viewModel.exportConversations() },
            )

            DataActionButton(
                icon = Icons.Default.FileUpload,
                label = "导入对话",
                description = "从 JSON 备份文件导入对话",
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )

            DataActionButton(
                icon = Icons.Default.Delete,
                label = "清除所有对话",
                description = "删除所有对话记录和消息",
                destructive = true,
                onClick = { showClearConversationsDialog = true },
            )

            DataActionButton(
                icon = Icons.Default.Warning,
                label = "清除崩溃日志",
                description = "删除所有已记录的崩溃日志",
                destructive = true,
                onClick = { showClearCrashDialog = true },
            )

            Spacer(Modifier.height(32.dp))

            // ── Section: 权限 ──
            SectionHeader("权限")

            DataActionButton(
                icon = Icons.Default.Security,
                label = "权限管理",
                description = "查看和管理已授予的权限（相机、存储、通讯录等）",
                onClick = onNavigateToPermission,
            )

            Spacer(Modifier.height(32.dp))

            // ── Section: 关于 ──
            SectionHeader("关于")

            AboutRow("版本号", viewModel.appVersion)
            AboutRow("项目地址", "github.com/denggl2/Mason")
            AboutRow("开源协议", "MIT License")

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──
    if (showClearConversationsDialog) {
        ConfirmDialog(
            title = "清除所有对话",
            message = "此操作将删除所有对话记录和消息，且不可撤销。确定继续？",
            onConfirm = {
                viewModel.clearAllConversations()
                showClearConversationsDialog = false
            },
            onDismiss = { showClearConversationsDialog = false },
        )
    }

    if (showClearCrashDialog) {
        ConfirmDialog(
            title = "清除崩溃日志",
            message = "此操作将删除所有已记录的崩溃日志。确定继续？",
            onConfirm = {
                viewModel.clearCrashLogs()
                showClearCrashDialog = false
            },
            onDismiss = { showClearCrashDialog = false },
        )
    }
}

// ── Helper Composables ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MasonAccent,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        colors = darkFieldColors(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SelectorField(
    label: String,
    value: String,
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    Box {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    value,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(Color(0xFF1E1E1E)),
        ) {
            menuContent()
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                color = Color.Gray.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun DataActionButton(
    icon: ImageVector,
    label: String,
    description: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) Color(0xFFEF5350) else Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                color = Color.Gray.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.Gray,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = Color(0xFFEF5350))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        },
    )
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MasonAccent,
    unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MasonAccent,
    focusedLabelColor = MasonAccent,
    unfocusedLabelColor = Color.Gray,
)
