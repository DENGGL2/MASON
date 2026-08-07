package com.denggl2.mason.ui.phoneagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.denggl2.mason.phoneagent.PhoneAgentLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAgentScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: PhoneAgentViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val runtime by viewModel.runtime.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val allPermissionsGranted = permissions.accessibilityEnabled && permissions.overlayEnabled

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PhoneAgentScaffold(title = "屏幕助手", onBack = onBack) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhoneAgentInfoLine(
                        title = "能力",
                        text = "通过读取屏幕并操作按钮、滑动、打开、关闭等交互完成提出的任务，敏感操作会提前申请",
                    )
                    PhoneAgentInfoLine(
                        title = "权限",
                        text = "需要授权系统的无障碍和悬浮窗功能权限，每次行为都会保存到最近使用记录中",
                    )
                }
            }

            item { PhoneAgentSectionLabel("功能开关") }
            item {
                PhoneAgentSwitchRow(
                    title = "开启屏幕助手",
                    description = "",
                    checked = config.phoneToolsEnabled && allPermissionsGranted,
                    enabled = allPermissionsGranted,
                    onCheckedChange = viewModel::setEnabled,
                )
            }
            if (runtime.paused) {
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text("执行已暂停", fontWeight = FontWeight.Medium)
                            Text(
                                "你通过悬浮窗暂停了屏幕助手",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(onClick = viewModel::resumeExecution) {
                            Text("恢复")
                        }
                    }
                }
            }

            item { PhoneAgentSectionLabel("权限设置") }
            item {
                PhoneAgentPermissionRow(
                    title = "无障碍服务",
                    description = "",
                    granted = permissions.accessibilityEnabled,
                    detail = if (permissions.accessibilityEnabled) "已授权" else "未授权",
                    onClick = viewModel::openAccessibilitySettings,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)) }
            item {
                PhoneAgentPermissionRow(
                    title = "悬浮窗",
                    description = "",
                    granted = permissions.overlayEnabled,
                    detail = if (permissions.overlayEnabled) "已授权" else "未授权",
                    onClick = viewModel::openOverlaySettings,
                )
            }

            item { PhoneAgentSectionLabel("记录") }
            item {
                PhoneAgentNavigationRow(
                    title = "最近使用日志",
                    description = "",
                    onClick = onOpenLogs,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAgentLogScreen(
    onBack: () -> Unit,
    viewModel: PhoneAgentViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var selectedLog by remember { mutableStateOf<PhoneAgentLogEntry?>(null) }

    PhoneAgentScaffold(title = "屏幕助手日志", onBack = onBack) { contentPadding ->
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("暂无屏幕助手使用记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(logs.asReversed(), key = PhoneAgentLogEntry::id) { entry ->
                    PhoneAgentLogRow(entry, onClick = { selectedLog = entry })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    selectedLog?.let { entry ->
        PhoneAgentLogDetailSheet(
            entry = entry,
            onDismiss = { selectedLog = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneAgentLogDetailSheet(
    entry: PhoneAgentLogEntry,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(entry.action, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    formatPhoneAgentLogTime(entry.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    if (entry.success) "成功" else "未成功",
                    color = if (entry.success) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            item {
                Text(entry.summary, fontSize = 14.sp, lineHeight = 20.sp)
                entry.packageName?.takeIf(String::isNotBlank)?.let { packageName ->
                    Text(
                        "应用：$packageName",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            items(entry.details.entries.toList()) { (key, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(phoneAgentDetailLabel(key), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            entry.error?.takeIf(String::isNotBlank)?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneAgentScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                content(contentPadding)
            }
        },
    )
}

@Composable
private fun PhoneAgentInfoLine(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun PhoneAgentSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 7.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PhoneAgentSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PhoneAgentPermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                detail,
                color = if (granted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PhoneAgentNavigationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) {
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun PhoneAgentLogRow(entry: PhoneAgentLogEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.action,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (entry.success) "成功" else "未成功",
                color = if (entry.success) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            formatPhoneAgentLogTime(entry.timestamp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

internal fun formatPhoneAgentLogTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun phoneAgentDetailLabel(key: String): String = when (key) {
    "path" -> "截图路径"
    "width" -> "宽度"
    "height" -> "高度"
    "storage" -> "存储位置"
    "node_count" -> "节点数"
    "truncated" -> "是否截断"
    "method" -> "执行方式"
    "character_count" -> "字符数"
    "duration_ms" -> "持续时间"
    else -> key
}
