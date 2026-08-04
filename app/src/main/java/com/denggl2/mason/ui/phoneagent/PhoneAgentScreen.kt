package com.denggl2.mason.ui.phoneagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val logs by viewModel.logs.collectAsState()
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
                        title = "简述",
                        text = "屏幕助手可以帮你点击屏幕、输入文字和滑动页面。它使用 Android 系统能力，不需要 Root。",
                    )
                    PhoneAgentInfoLine(
                        title = "能力",
                        text = "它能读取当前页面上的文字和按钮，也能点击按钮、输入文字、上下滑动、返回上一页或回到桌面。需要截图时，会先向你确认。",
                    )
                    PhoneAgentInfoLine(
                        title = "策略",
                        text = "查看页面和每次操作都会记入日志。点击、输入、滑动、返回等操作会先向你确认。任务中断后会重新读取当前页面，不会重复执行已经完成的操作。",
                    )
                    PhoneAgentInfoLine(
                        title = "权限",
                        text = "需要无障碍权限才能读取和操作屏幕；需要悬浮窗权限才能显示正在执行的操作和停止按钮。两项权限没有全部开启时，功能开关不可用。截图只保存在本机缓存，不会自动发送给模型。",
                    )
                }
            }

            item { PhoneAgentSectionLabel("功能开关") }
            item {
                PhoneAgentSwitchRow(
                    title = "开启屏幕助手",
                    description = when {
                        !permissions.accessibilityEnabled && !permissions.overlayEnabled ->
                            "请先开启无障碍权限和悬浮窗权限"
                        !permissions.accessibilityEnabled -> "请先开启无障碍权限"
                        !permissions.overlayEnabled -> "请先开启悬浮窗权限"
                        config.phoneToolsEnabled -> "已开启，Mason 会在你确认后帮你操作屏幕"
                        else -> "开启后，Mason 才能读取和操作屏幕"
                    },
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
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Text("恢复")
                        }
                    }
                }
            }

            item { PhoneAgentSectionLabel("权限设置") }
            item {
                PhoneAgentPermissionRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "无障碍服务",
                    description = "读取界面控件并执行点击、输入、滚动和系统导航",
                    granted = permissions.accessibilityEnabled,
                    detail = when {
                        runtime.serviceConnected -> "服务已连接"
                        permissions.accessibilityEnabled -> "已开启，等待服务连接"
                        else -> "未开启"
                    },
                    onClick = viewModel::openAccessibilitySettings,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)) }
            item {
                PhoneAgentPermissionRow(
                    icon = Icons.Outlined.Layers,
                    title = "悬浮窗",
                    description = "显示正在执行的动作，并提供立即停止入口",
                    granted = permissions.overlayEnabled,
                    detail = if (permissions.overlayEnabled) "已开启" else "未开启",
                    onClick = viewModel::openOverlaySettings,
                )
            }

            item { PhoneAgentSectionLabel("记录") }
            item {
                PhoneAgentNavigationRow(
                    icon = Icons.Outlined.History,
                    title = "最近使用日志",
                    description = if (logs.isEmpty()) "暂无执行记录" else "最近 ${logs.size} 条执行记录",
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
                    PhoneAgentLogRow(entry)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
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
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
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
        content = content,
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
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
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
    icon: ImageVector,
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                detail,
                color = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun PhoneAgentNavigationRow(
    icon: ImageVector,
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun PhoneAgentLogRow(entry: PhoneAgentLogEntry) {
    val detailText = entry.details.entries.joinToString("\n") { (key, value) ->
        "${phoneAgentDetailLabel(key)}：$value"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.action,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (entry.success) "成功" else "失败",
                color = if (entry.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            formatPhoneAgentLogTime(entry.timestamp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Text(entry.summary, fontSize = 13.sp, lineHeight = 19.sp)
        if (entry.packageName?.isNotBlank() == true) {
            Text(
                "应用：${entry.packageName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (detailText.isNotBlank()) {
            Text(
                detailText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        entry.error?.takeIf(String::isNotBlank)?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
        }
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
