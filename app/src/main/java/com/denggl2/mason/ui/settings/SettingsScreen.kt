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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.ui.theme.MasonAccent

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current

    var url by remember(config) { mutableStateOf(config.apiUrl) }
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var keyVisible by remember { mutableStateOf(false) }

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
            SectionHeader("API 配置")

            LabeledInput("API 端点 URL", url, "https://api.deepseek.com/v1/chat/completions") {
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

            LabeledInput("模型名称", model, "deepseek-chat") { model = it }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.save(ApiConfig(apiUrl = url, apiKey = key, model = model))
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasonAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("保存", color = Color.Black)
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
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
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
            .clip(RoundedCornerShape(12.dp))
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
            .clip(RoundedCornerShape(12.dp))
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
