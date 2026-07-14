package com.denggl2.mason.ui.collection

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.MasonSkillManifest
import com.denggl2.mason.data.MasonAutomationRunLog
import com.denggl2.mason.data.MasonAutomationSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CollectionKind(
    val routeName: String,
    val title: String,
    val emptyText: String,
) {
    ARTIFACTS("artifacts", "产出", "暂无对话生成文件"),
    SKILLS("skills", "技能", "暂无已安装技能"),
    AUTOMATIONS("automations", "自动化", "暂无已生成自动化");

    companion object {
        fun fromRouteName(value: String?): CollectionKind =
            entries.firstOrNull { it.routeName == value } ?: ARTIFACTS
    }
}

private data class CollectionEntry(
    val name: String,
    val path: String,
    val modifiedAt: Long,
    val sizeLabel: String,
    val isDirectory: Boolean,
    val kind: CollectionKind,
    val typeLabel: String,
    val summary: String,
    val sourceLabel: String,
    val previewFile: File?,
    val skillEnabled: Boolean? = null,
    val automationId: String? = null,
    val automationEnabled: Boolean? = null,
)

private val COLLECTION_JSON = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionListScreen(
    kind: CollectionKind,
    onBack: () -> Unit,
    viewModel: CollectionListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val skillState by viewModel.skillState.collectAsState()
    val automationState by viewModel.automationState.collectAsState()
    var query by remember { mutableStateOf("") }
    var searchActive by remember(kind) { mutableStateOf(false) }
    var loaded by remember(kind) { mutableStateOf(false) }
    var entries by remember(kind) { mutableStateOf<List<CollectionEntry>>(emptyList()) }
    var previewEntry by remember { mutableStateOf<CollectionEntry?>(null) }
    var showInstallDialog by remember(kind) { mutableStateOf(false) }
    var showCreateAutomationDialog by remember(kind) { mutableStateOf(false) }
    var pendingAutomationRun by remember { mutableStateOf<CollectionEntry?>(null) }
    val filteredEntries = remember(entries, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.name.contains(keyword, ignoreCase = true) ||
                    entry.path.contains(keyword, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(kind, skillState.revision, automationState.revision) {
        loaded = false
        entries = withContext(Dispatchers.IO) {
            loadCollectionEntries(context.applicationContext, kind)
        }
        loaded = true
    }

    LaunchedEffect(skillState.message) {
        skillState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(automationState.message) {
        automationState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeAutomationMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        TopBarSearchField(
                            value = query,
                            onValueChange = { query = it },
                        )
                    } else {
                        Text(
                            kind.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (kind == CollectionKind.SKILLS || kind == CollectionKind.AUTOMATIONS) {
                        IconButton(
                            onClick = {
                                if (kind == CollectionKind.SKILLS) {
                                    showInstallDialog = true
                                } else {
                                    showCreateAutomationDialog = true
                                }
                            },
                            enabled = if (kind == CollectionKind.SKILLS) {
                                !skillState.working
                            } else {
                                !automationState.working
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = if (kind == CollectionKind.SKILLS) {
                                    "从 GitHub 安装 Skill"
                                } else {
                                    "创建自动化"
                                },
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (searchActive) {
                                query = ""
                                searchActive = false
                            } else {
                                searchActive = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchActive) "关闭搜索" else "搜索",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                val emptyTopPadding = if (maxHeight > 620.dp) maxHeight * 0.30f else maxHeight * 0.26f
                when {
                    !loaded -> {
                        Text(
                            "正在读取...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = emptyTopPadding),
                        )
                    }
                    filteredEntries.isEmpty() -> {
                        Text(
                            if (entries.isEmpty()) kind.emptyText else "没有匹配结果",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = emptyTopPadding),
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            itemsIndexed(filteredEntries, key = { _, item -> item.path }) { _, entry ->
                                Box(modifier = Modifier.animateItem()) {
                                    CollectionEntryRow(
                                        entry = entry,
                                        icon = kind.iconFor(entry),
                                        onPreview = { previewEntry = entry },
                                        onOpen = { openEntry(context, entry, edit = false) },
                                        onEdit = { openEntry(context, entry, edit = true) },
                                        onShare = { shareEntry(context, entry) },
                                        onToggleSkill = entry.skillEnabled?.let { enabled ->
                                            { viewModel.setSkillEnabled(entry.path, !enabled) }
                                        },
                                        onToggleAutomation = entry.automationId?.let { id ->
                                            { viewModel.setAutomationEnabled(id, entry.automationEnabled != true) }
                                        },
                                        onRunAutomation = entry.automationId?.let {
                                            { pendingAutomationRun = entry }
                                        },
                                        onShowAutomationLogs = entry.automationId?.let { id ->
                                            { viewModel.showAutomationLogs(id, entry.name) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewEntry?.let { entry ->
        EntryPreviewDialog(
            entry = entry,
            onDismiss = { previewEntry = null },
            onOpen = { openEntry(context, entry, edit = false) },
            onEdit = { openEntry(context, entry, edit = true) },
            onShare = { shareEntry(context, entry) },
        )
    }

    if (showInstallDialog) {
        InstallSkillDialog(
            working = skillState.working,
            onDismiss = { if (!skillState.working) showInstallDialog = false },
            onInstall = { url ->
                showInstallDialog = false
                viewModel.installSkillFromGitHub(url)
            },
        )
    }
    if (showCreateAutomationDialog) {
        CreateAutomationDialog(
            working = automationState.working,
            onDismiss = { if (!automationState.working) showCreateAutomationDialog = false },
            onCreate = { name, type, arguments ->
                showCreateAutomationDialog = false
                viewModel.createAutomation(name, type, arguments)
            },
        )
    }
    pendingAutomationRun?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingAutomationRun = null },
            title = { Text("运行 ${entry.name}？") },
            text = { Text(entry.summary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        entry.automationId?.let(viewModel::runAutomation)
                        pendingAutomationRun = null
                    },
                ) { Text("运行") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAutomationRun = null }) { Text("取消") }
            },
        )
    }
    automationState.logTitle?.let { title ->
        AutomationLogsDialog(
            title = title,
            logs = automationState.logs,
            onDismiss = viewModel::closeAutomationLogs,
        )
    }
}

@Composable
private fun TopBarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(
                        "搜索",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionEntryRow(
    entry: CollectionEntry,
    icon: ImageVector,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onToggleSkill: (() -> Unit)?,
    onToggleAutomation: (() -> Unit)?,
    onRunAutomation: (() -> Unit)?,
    onShowAutomationLogs: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(collectionGlassBrush())
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onPreview)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${entry.typeLabel} · ${entry.sourceLabel} · ${formatModifiedTime(entry.modifiedAt)} · ${entry.sizeLabel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EntryActionChip(
                label = "预览",
                icon = Icons.Outlined.Visibility,
                onClick = onPreview,
            )
            EntryActionChip(
                label = if (entry.kind == CollectionKind.ARTIFACTS) "编辑" else "打开",
                icon = if (entry.kind == CollectionKind.ARTIFACTS) Icons.Outlined.Edit else Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = if (entry.kind == CollectionKind.ARTIFACTS) onEdit else onOpen,
            )
            if (!entry.isDirectory) {
                EntryActionChip(
                    label = "分享",
                    icon = Icons.Outlined.Share,
                    onClick = onShare,
                )
            }
            if (onToggleSkill != null) {
                EntryActionChip(
                    label = if (entry.skillEnabled == true) "停用" else "启用",
                    icon = Icons.Outlined.PowerSettingsNew,
                    onClick = onToggleSkill,
                )
            }
            if (onRunAutomation != null) {
                EntryActionChip(
                    label = "运行",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = onRunAutomation,
                )
            }
            if (onShowAutomationLogs != null) {
                EntryActionChip(
                    label = "日志",
                    icon = Icons.Outlined.History,
                    onClick = onShowAutomationLogs,
                )
            }
            if (onToggleAutomation != null) {
                EntryActionChip(
                    label = if (entry.automationEnabled == true) "停用" else "启用",
                    icon = Icons.Outlined.PowerSettingsNew,
                    onClick = onToggleAutomation,
                )
            }
        }
    }
}

@Composable
private fun InstallSkillDialog(
    working: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从 GitHub 安装 Skill") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                enabled = !working,
                singleLine = true,
                label = { Text("公开仓库或 Skill 目录链接") },
                placeholder = { Text("https://github.com/owner/repo") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(url.trim()) },
                enabled = !working && url.isNotBlank(),
            ) {
                Text(if (working) "安装中..." else "安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAutomationDialog(
    working: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Map<String, String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("notification") }
    var notificationTitle by remember { mutableStateOf("Mason") }
    var notificationText by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && when (actionType) {
        "notification" -> notificationTitle.isNotBlank() && notificationText.isNotBlank()
        "launch_app" -> packageName.isNotBlank()
        else -> false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建手动自动化") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !working,
                    singleLine = true,
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("notification" to "通知", "launch_app" to "打开 App")
                        .forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = actionType == option.first,
                                onClick = { actionType = option.first },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                label = { Text(option.second) },
                            )
                        }
                }
                if (actionType == "notification") {
                    OutlinedTextField(
                        value = notificationTitle,
                        onValueChange = { notificationTitle = it },
                        enabled = !working,
                        singleLine = true,
                        label = { Text("通知标题") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notificationText,
                        onValueChange = { notificationText = it },
                        enabled = !working,
                        label = { Text("通知内容") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        enabled = !working,
                        singleLine = true,
                        label = { Text("App 包名") },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val arguments = if (actionType == "notification") {
                        mapOf("title" to notificationTitle.trim(), "text" to notificationText.trim())
                    } else {
                        mapOf("action" to "launch", "package_name" to packageName.trim())
                    }
                    onCreate(name.trim(), actionType, arguments)
                },
                enabled = !working && valid,
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("取消") }
        },
    )
}

@Composable
private fun AutomationLogsDialog(
    title: String,
    logs: List<MasonAutomationRunLog>,
    onDismiss: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title · 运行日志") },
        text = {
            if (logs.isEmpty()) {
                Text("暂无运行记录")
            } else {
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs) { log ->
                        Column {
                            Text(
                                if (log.status == "success") "成功" else "失败",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${formatter.format(Date(log.ranAt))} · ${log.message}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun collectionGlassBrush(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

@Composable
private fun EntryActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(27.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                RoundedCornerShape(8.dp),
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.44f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EntryPreviewDialog(
    entry: CollectionEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val previewText = remember(entry.path, entry.modifiedAt) {
        buildPreviewText(entry)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    entry.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${entry.typeLabel} · ${entry.sourceLabel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column {
                Text(
                    entry.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f))
                        .padding(10.dp),
                ) {
                    Text(
                        previewText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text("打开")
            }
        },
        dismissButton = {
            Row {
                if (entry.kind == CollectionKind.ARTIFACTS && !entry.isDirectory) {
                    TextButton(onClick = onEdit) {
                        Text("编辑")
                    }
                }
                if (!entry.isDirectory) {
                    TextButton(onClick = onShare) {
                        Text("分享")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

private fun CollectionKind.iconFor(entry: CollectionEntry): ImageVector {
    if (entry.isDirectory) return Icons.Outlined.Folder
    return when (this) {
        CollectionKind.ARTIFACTS -> Icons.Outlined.Description
        CollectionKind.SKILLS -> Icons.Outlined.Extension
        CollectionKind.AUTOMATIONS -> Icons.AutoMirrored.Outlined.EventNote
    }
}

private fun loadCollectionEntries(context: Context, kind: CollectionKind): List<CollectionEntry> {
    return collectionRoots(context, kind)
        .distinctBy { it.safePath() }
        .flatMap { root -> collectEntries(root, kind) }
        .distinctBy { it.path }
        .sortedByDescending { it.modifiedAt }
}

private fun collectionRoots(context: Context, kind: CollectionKind): List<File> {
    fun publicDir(type: String, child: String): File =
        File(Environment.getExternalStoragePublicDirectory(type), child)

    val externalRoot = context.getExternalFilesDir(null)
    return when (kind) {
        CollectionKind.ARTIFACTS -> listOfNotNull(
            publicDir(Environment.DIRECTORY_DOWNLOADS, "mason"),
            publicDir(Environment.DIRECTORY_DOCUMENTS, "Mason"),
            publicDir(Environment.DIRECTORY_DCIM, "Mason"),
            publicDir(Environment.DIRECTORY_PICTURES, "Mason"),
            publicDir(Environment.DIRECTORY_MUSIC, "Mason"),
            publicDir(Environment.DIRECTORY_MOVIES, "Mason"),
            externalRoot?.let { File(it, "artifacts") },
            File(context.filesDir, "artifacts"),
        )
        CollectionKind.SKILLS -> listOfNotNull(
            File(context.filesDir, "skills"),
            externalRoot?.let { File(it, "skills") },
            File(publicDir(Environment.DIRECTORY_DOWNLOADS, "mason"), "skills"),
        )
        CollectionKind.AUTOMATIONS -> listOfNotNull(
            File(context.filesDir, "automations"),
            externalRoot?.let { File(it, "automations") },
            File(publicDir(Environment.DIRECTORY_DOWNLOADS, "mason"), "automations"),
        )
    }
}

private fun collectEntries(root: File, kind: CollectionKind): List<CollectionEntry> {
    if (!root.exists() || !root.isDirectory) return emptyList()

    return try {
        val files = if (kind == CollectionKind.ARTIFACTS) {
            root.walkTopDown()
                .maxDepth(4)
                .onFail { _, _ -> }
                .filter { it.isFile }
                .take(240)
                .toList()
        } else {
            root.listFiles()
                ?.filter { it.isDirectory || it.isFile }
                ?.take(240)
                .orEmpty()
        }

        files.map { file -> file.toCollectionEntry(kind, root) }
    } catch (_: SecurityException) {
        emptyList()
    }
}

private fun File.toCollectionEntry(kind: CollectionKind, root: File): CollectionEntry {
    val preview = previewFileFor(kind)
    val skillManifest = if (kind == CollectionKind.SKILLS && isDirectory) readCollectionSkillManifest() else null
    val automation = if (kind == CollectionKind.AUTOMATIONS && isDirectory) readCollectionAutomation() else null
    val lastRun = if (automation != null) readCollectionAutomationLogs().maxByOrNull { it.ranAt } else null
    val automationSummary = automation?.let { spec ->
        buildString {
            append(spec.description.ifBlank { "手动自动化" })
            append(if (spec.enabled) " · 已启用" else " · 已停用")
            lastRun?.let { log ->
                append(if (log.status == "success") " · 上次成功" else " · 上次失败")
            }
        }
    }
    return CollectionEntry(
        name = automation?.name?.takeIf(String::isNotBlank)
            ?: skillManifest?.name?.takeIf(String::isNotBlank)
            ?: displayNameFor(kind),
        path = absolutePath,
        modifiedAt = lastModifiedDeep(preview),
        sizeLabel = if (isDirectory) "文件夹" else formatFileSize(length()),
        isDirectory = isDirectory,
        kind = kind,
        typeLabel = typeLabelFor(kind),
        summary = automationSummary ?: summaryFor(kind, root, preview),
        sourceLabel = sourceLabelFor(root),
        previewFile = preview,
        skillEnabled = if (kind == CollectionKind.SKILLS && isDirectory) {
            skillManifest?.enabled ?: true
        } else {
            null
        },
        automationId = automation?.id,
        automationEnabled = automation?.enabled,
    )
}

private fun File.readCollectionSkillManifest(): MasonSkillManifest? {
    val manifest = File(this, "skill.json")
    if (!manifest.exists() || !manifest.isFile) return null
    return runCatching {
        COLLECTION_JSON.decodeFromString(MasonSkillManifest.serializer(), manifest.readText(Charsets.UTF_8))
    }.getOrNull()
}

private fun File.readCollectionAutomation(): MasonAutomationSpec? {
    val manifest = File(this, "automation.json")
    if (!manifest.exists() || !manifest.isFile) return null
    return runCatching {
        COLLECTION_JSON.decodeFromString(MasonAutomationSpec.serializer(), manifest.readText(Charsets.UTF_8))
    }.getOrNull()
}

private fun File.readCollectionAutomationLogs(): List<MasonAutomationRunLog> {
    val logs = File(this, "runs.json")
    if (!logs.exists() || !logs.isFile) return emptyList()
    return runCatching {
        COLLECTION_JSON.decodeFromString(
            ListSerializer(MasonAutomationRunLog.serializer()),
            logs.readText(Charsets.UTF_8),
        )
    }.getOrDefault(emptyList())
}

private fun File.displayNameFor(kind: CollectionKind): String {
    if (kind != CollectionKind.SKILLS || !isDirectory) return name
    val skillFile = File(this, "SKILL.md")
    val title = skillFile.readFirstHeading()
    return title?.takeIf { it.isNotBlank() } ?: name
}

private fun File.typeLabelFor(kind: CollectionKind): String = when (kind) {
    CollectionKind.ARTIFACTS -> extension.ifBlank { "文件" }.uppercase(Locale.getDefault())
    CollectionKind.SKILLS -> if (isDirectory) "Skill" else "Skill 文件"
    CollectionKind.AUTOMATIONS -> if (isDirectory) "自动化" else extension.ifBlank { "任务" }.uppercase(Locale.getDefault())
}

private fun File.summaryFor(kind: CollectionKind, root: File, preview: File?): String {
    return when (kind) {
        CollectionKind.ARTIFACTS -> {
            if (isDirectory) "对话生成的文件夹"
            else "对话生成文件，可预览、编辑或分享"
        }
        CollectionKind.SKILLS -> {
            val description = preview?.readSkillDescription()
            description ?: "已安装技能，支持自主生成或从 GitHub 拉取"
        }
        CollectionKind.AUTOMATIONS -> {
            val firstLine = preview?.readMeaningfulLine()
            firstLine ?: "自动化任务配置，支持定时、监控和跟进"
        }
    }.let { text ->
        val relative = runCatching { relativeTo(root).path }.getOrNull()
        if (relative.isNullOrBlank() || relative == name) text else "$text · $relative"
    }
}

private fun File.sourceLabelFor(root: File): String {
    val path = root.absolutePath.lowercase(Locale.getDefault())
    return when {
        "/download" in path -> "下载"
        "/documents" in path -> "文档"
        "/pictures" in path -> "图片"
        "/dcim" in path -> "相册"
        "/android/data/" in path -> "Mason"
        "/files/" in path -> "本地"
        else -> "本机"
    }
}

private fun File.previewFileFor(kind: CollectionKind): File? {
    if (isFile) return this

    val candidates = when (kind) {
        CollectionKind.SKILLS -> listOf("SKILL.md", "README.md", "skill.json", "package.json")
        CollectionKind.AUTOMATIONS -> listOf("automation.json", "automation.yaml", "automation.yml", "README.md", "task.md")
        CollectionKind.ARTIFACTS -> listOf("README.md", "index.html")
    }
    return candidates
        .map { File(this, it) }
        .firstOrNull { it.exists() && it.isFile }
}

private fun File.lastModifiedDeep(preview: File?): Long = maxOf(lastModified(), preview?.lastModified() ?: 0L)

private fun File.readFirstHeading(): String? =
    runCatching {
        readLines(Charsets.UTF_8)
            .firstOrNull { it.trimStart().startsWith("#") }
            ?.trim()
            ?.trimStart('#')
            ?.trim()
    }.getOrNull()

private fun File.readSkillDescription(): String? =
    runCatching {
        readLines(Charsets.UTF_8)
            .asSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("#") &&
                    !line.startsWith("---") &&
                    !line.startsWith("name:", ignoreCase = true)
            }
            ?.take(140)
    }.getOrNull()

private fun File.readMeaningfulLine(): String? =
    runCatching {
        readLines(Charsets.UTF_8)
            .asSequence()
            .map { it.trim().trim('"', '\'') }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("#") &&
                    !line.startsWith("{") &&
                    !line.startsWith("}") &&
                    !line.startsWith("---")
            }
            ?.take(140)
    }.getOrNull()

private fun buildPreviewText(entry: CollectionEntry): String {
    val file = entry.previewFile ?: return if (entry.isDirectory) {
        "这个文件夹里暂时没有可预览的说明文件。\n\n路径：${entry.path}"
    } else {
        "暂时无法预览这个文件。\n\n路径：${entry.path}"
    }

    if (!file.isTextLike()) {
        return "这个文件适合用本地软件打开预览。\n\n文件：${file.name}\n路径：${file.absolutePath}"
    }

    return runCatching {
        val text = file.readText(Charsets.UTF_8)
        if (text.length > 6000) text.take(6000) + "\n\n…已截取前 6000 字" else text
    }.getOrElse { error ->
        "读取预览失败：${error.message ?: error.javaClass.simpleName}\n\n路径：${file.absolutePath}"
    }
}

private fun File.isTextLike(): Boolean {
    val ext = extension.lowercase(Locale.getDefault())
    return ext in setOf(
        "txt", "md", "markdown", "json", "yaml", "yml", "xml", "html", "htm",
        "csv", "log", "kt", "java", "py", "js", "ts", "css", "toml", "ini",
    )
}

private fun openEntry(context: Context, entry: CollectionEntry, edit: Boolean) {
    val file = entry.previewFile ?: File(entry.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "暂时没有可打开的文件", Toast.LENGTH_SHORT).show()
        return
    }

    val action = if (edit) Intent.ACTION_EDIT else Intent.ACTION_VIEW
    val intent = Intent(action).apply {
        setDataAndType(file.toShareUri(context), file.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (edit) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, if (edit) "选择编辑应用" else "选择打开应用"))
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, "没有找到可用应用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "打开失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareEntry(context: Context, entry: CollectionEntry) {
    val file = entry.previewFile ?: File(entry.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "暂时没有可分享的文件", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType()
        putExtra(Intent.EXTRA_STREAM, file.toShareUri(context))
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
    }.onFailure { error ->
        Toast.makeText(context, "分享失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

private fun File.toShareUri(context: Context) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

private fun File.mimeType(): String {
    return when (extension.lowercase(Locale.getDefault())) {
        "txt", "log" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "yaml", "yml" -> "application/x-yaml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        else -> "*/*"
    }
}

private fun File.safePath(): String = try {
    canonicalPath
} catch (_: Exception) {
    absolutePath
}

private fun formatModifiedTime(value: Long): String {
    if (value <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.1f %s".format(Locale.US, value, units[unitIndex])
}
