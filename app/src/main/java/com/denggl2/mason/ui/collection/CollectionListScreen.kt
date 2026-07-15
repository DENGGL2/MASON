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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.denggl2.mason.automation.AutomationScheduler
import com.denggl2.mason.automation.AutomationRunner
import com.denggl2.mason.data.MasonSkillManifest
import com.denggl2.mason.data.MasonAutomationRunLog
import com.denggl2.mason.data.MasonAutomationConstraints
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationCondition
import com.denggl2.mason.data.MasonAutomationTrigger
import com.denggl2.mason.automation.AutomationWorkflowLogic
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
    val automation: MasonAutomationSpec? = null,
    val skillManifest: MasonSkillManifest? = null,
)

private val COLLECTION_JSON = Json { ignoreUnknownKeys = true }
private val TIMED_TRIGGER_TYPES = setOf(
    AutomationScheduler.TRIGGER_DAILY,
    AutomationScheduler.TRIGGER_WEEKDAYS,
)

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
    val automationCapabilityIssues by viewModel.automationCapabilityIssues.collectAsState()
    val automationPreferences by viewModel.automationPreferences.collectAsState()
    var query by remember { mutableStateOf("") }
    var searchActive by remember(kind) { mutableStateOf(false) }
    var loaded by remember(kind) { mutableStateOf(false) }
    var entries by remember(kind) { mutableStateOf<List<CollectionEntry>>(emptyList()) }
    var previewEntry by remember { mutableStateOf<CollectionEntry?>(null) }
    var showInstallDialog by remember(kind) { mutableStateOf(false) }
    var showCreateAutomationDialog by remember(kind) { mutableStateOf(false) }
    var editingAutomation by remember(kind) { mutableStateOf<MasonAutomationSpec?>(null) }
    var pendingAutomationRun by remember { mutableStateOf<CollectionEntry?>(null) }
    LaunchedEffect(kind, automationState.revision) {
        if (kind == CollectionKind.AUTOMATIONS) viewModel.refreshAutomationCapabilities()
    }
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
                                        onEdit = {
                                            entry.automation?.let { editingAutomation = it }
                                                ?: openEntry(context, entry, edit = true)
                                        },
                                        onShare = { shareEntry(context, entry) },
                                        onToggleSkill = entry.skillEnabled?.let { enabled ->
                                            { viewModel.setSkillEnabled(entry.path, !enabled) }
                                        },
                                        onUpdateSkill = entry.skillManifest
                                            ?.takeIf { it.source.startsWith("https://github.com/") }
                                            ?.let { manifest -> { viewModel.updateSkill(manifest.id) } },
                                        onArchiveSkill = entry.skillManifest?.let { manifest ->
                                            { viewModel.archiveSkill(manifest.id) }
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
                                        automationStatus = entry.automation?.let { spec ->
                                            when {
                                                !spec.enabled -> "已停用"
                                                automationCapabilityIssues[spec.id].orEmpty().isNotEmpty() ->
                                                    "缺权限 · ${automationCapabilityIssues[spec.id].orEmpty().first()}"
                                                spec.trigger.type != "manual" &&
                                                    !automationPreferences.backgroundExecutionEnabled -> "后台关闭"
                                                else -> "可运行"
                                            }
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
            onEdit = {
                entry.automation?.let { editingAutomation = it }
                    ?: openEntry(context, entry, edit = true)
            },
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
            initialSpec = null,
            onDismiss = { if (!automationState.working) showCreateAutomationDialog = false },
            onCreate = { name, type, arguments, triggerType, triggerValue, constraints ->
                showCreateAutomationDialog = false
                viewModel.createAutomation(name, type, arguments, triggerType, triggerValue, constraints)
            },
        )
    }
    editingAutomation?.let { spec ->
        if (spec.requiresWorkflowEditor()) {
            WorkflowAutomationDialog(
                working = automationState.working,
                spec = spec,
                onDismiss = { if (!automationState.working) editingAutomation = null },
                onSave = { name, trigger, actions ->
                    editingAutomation = null
                    viewModel.updateAutomationWorkflow(spec.id, name, trigger, actions)
                },
                onTestThroughStep = { actionId ->
                    viewModel.testAutomationThroughStep(spec.id, actionId)
                },
            )
        } else {
            CreateAutomationDialog(
                working = automationState.working,
                initialSpec = spec,
                onDismiss = { if (!automationState.working) editingAutomation = null },
                onCreate = { name, type, arguments, triggerType, triggerValue, constraints ->
                    editingAutomation = null
                    viewModel.updateAutomation(spec.id, name, type, arguments, triggerType, triggerValue, constraints)
                },
            )
        }
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
    onUpdateSkill: (() -> Unit)?,
    onArchiveSkill: (() -> Unit)?,
    onToggleAutomation: (() -> Unit)?,
    onRunAutomation: (() -> Unit)?,
    onShowAutomationLogs: (() -> Unit)?,
    automationStatus: String?,
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
                automationStatus?.let { status ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        status,
                        color = if (status == "可运行") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                label = if (entry.kind == CollectionKind.ARTIFACTS || entry.automation != null) "编辑" else "打开",
                icon = if (entry.kind == CollectionKind.ARTIFACTS || entry.automation != null) {
                    Icons.Outlined.Edit
                } else {
                    Icons.AutoMirrored.Outlined.OpenInNew
                },
                onClick = if (entry.kind == CollectionKind.ARTIFACTS || entry.automation != null) onEdit else onOpen,
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
            if (onUpdateSkill != null) {
                EntryActionChip(
                    label = "更新",
                    icon = Icons.Outlined.Refresh,
                    onClick = onUpdateSkill,
                )
            }
            if (onArchiveSkill != null) {
                EntryActionChip(
                    label = "卸载",
                    icon = Icons.Outlined.Delete,
                    onClick = onArchiveSkill,
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

private fun MasonAutomationSpec.requiresWorkflowEditor(): Boolean =
    actions.size > 1 || actions.any { action ->
        action.type !in setOf("notification", AutomationRunner.ACTION_MODEL_ARTIFACT, "launch_app") ||
            action.id.isNotBlank() || action.inputKey.isNotBlank() || action.outputKey.isNotBlank() ||
            action.condition != null || action.continueOnFailure
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkflowAutomationDialog(
    working: Boolean,
    spec: MasonAutomationSpec,
    onDismiss: () -> Unit,
    onSave: (String, MasonAutomationTrigger, List<MasonAutomationAction>) -> Unit,
    onTestThroughStep: (String) -> Unit,
) {
    var name by remember(spec.id) { mutableStateOf(spec.name) }
    var triggerType by remember(spec.id) { mutableStateOf(spec.trigger.type) }
    var triggerValue by remember(spec.id) { mutableStateOf(spec.trigger.value) }
    var actions by remember(spec.id) {
        mutableStateOf(AutomationWorkflowLogic.normalizedActions(spec.actions))
    }
    var editingStep by remember(spec.id) { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("编辑多步骤自动化") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = triggerType,
                        onValueChange = { triggerType = it },
                        label = { Text("触发类型") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = triggerValue,
                        onValueChange = { triggerValue = it },
                        label = { Text("触发值") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("执行步骤", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                actions.forEachIndexed { index, action ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { editingStep = index }
                            .padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}. ${action.title}",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) actions = actions.toMutableList().apply {
                                        val item = removeAt(index)
                                        add(index - 1, item)
                                    }
                                },
                                enabled = index > 0 && !working,
                                modifier = Modifier.size(30.dp),
                            ) { Icon(Icons.Outlined.ArrowUpward, "上移", Modifier.size(16.dp)) }
                            IconButton(
                                onClick = {
                                    if (index < actions.lastIndex) actions = actions.toMutableList().apply {
                                        val item = removeAt(index)
                                        add(index + 1, item)
                                    }
                                },
                                enabled = index < actions.lastIndex && !working,
                                modifier = Modifier.size(30.dp),
                            ) { Icon(Icons.Outlined.ArrowDownward, "下移", Modifier.size(16.dp)) }
                            IconButton(
                                onClick = { if (actions.size > 1) actions = actions.filterIndexed { i, _ -> i != index } },
                                enabled = actions.size > 1 && !working,
                                modifier = Modifier.size(30.dp),
                            ) { Icon(Icons.Outlined.Delete, "移除步骤", Modifier.size(16.dp)) }
                        }
                        Text(
                            action.type + buildString {
                                if (action.inputKey.isNotBlank()) append(" · 输入 ${action.inputKey}")
                                if (action.outputKey.isNotBlank()) append(" · 输出 ${action.outputKey}")
                                action.condition?.let { append(" · 条件 ${it.key} ${it.operator}") }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                        TextButton(
                            onClick = { onTestThroughStep(action.id) },
                            enabled = !working,
                        ) { Text("测试到此步骤") }
                    }
                }
                TextButton(
                    onClick = {
                        actions = actions + MasonAutomationAction(
                            id = "action-${System.currentTimeMillis()}",
                            title = "发送通知",
                            type = "notification",
                            arguments = mapOf("title" to name, "text" to "自动化已完成"),
                        )
                    },
                    enabled = !working,
                ) { Text("添加步骤") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        MasonAutomationTrigger(triggerType.trim(), triggerValue.trim()),
                        AutomationWorkflowLogic.normalizedActions(actions),
                    )
                },
                enabled = !working && name.isNotBlank() && triggerType.isNotBlank() && actions.isNotEmpty(),
            ) { Text(if (working) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("取消") } },
    )

    editingStep?.let { index ->
        actions.getOrNull(index)?.let { action ->
            AutomationStepDialog(
                action = action,
                onDismiss = { editingStep = null },
                onSave = { updated ->
                    actions = actions.toMutableList().apply { set(index, updated) }
                    editingStep = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationStepDialog(
    action: MasonAutomationAction,
    onDismiss: () -> Unit,
    onSave: (MasonAutomationAction) -> Unit,
) {
    var title by remember(action.id) { mutableStateOf(action.title) }
    var type by remember(action.id) { mutableStateOf(action.type) }
    var inputKey by remember(action.id) { mutableStateOf(action.inputKey) }
    var outputKey by remember(action.id) { mutableStateOf(action.outputKey) }
    var conditionKey by remember(action.id) { mutableStateOf(action.condition?.key.orEmpty()) }
    var conditionOperator by remember(action.id) { mutableStateOf(action.condition?.operator ?: "not_empty") }
    var conditionValue by remember(action.id) { mutableStateOf(action.condition?.value.orEmpty()) }
    var continueOnFailure by remember(action.id) { mutableStateOf(action.continueOnFailure) }
    var argumentsText by remember(action.id) {
        mutableStateOf(action.arguments.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑步骤") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf(
                    Triple("标题", title, { value: String -> title = value }),
                    Triple("类型", type, { value: String -> type = value }),
                    Triple("输入变量", inputKey, { value: String -> inputKey = value }),
                    Triple("输出变量", outputKey, { value: String -> outputKey = value }),
                    Triple("条件变量", conditionKey, { value: String -> conditionKey = value }),
                    Triple("条件操作", conditionOperator, { value: String -> conditionOperator = value }),
                    Triple("条件值", conditionValue, { value: String -> conditionValue = value }),
                ).forEach { (label, value, setter) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = setter,
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = argumentsText,
                    onValueChange = { argumentsText = it },
                    label = { Text("参数（每行 key=value）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = continueOnFailure,
                    onClick = { continueOnFailure = !continueOnFailure },
                    label = { Text("失败后继续") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        action.copy(
                            title = title.trim(),
                            type = type.trim(),
                            arguments = argumentsText.lines().mapNotNull { line ->
                                val key = line.substringBefore('=', "").trim()
                                if (key.isBlank() || '=' !in line) null
                                else key to line.substringAfter('=').trim()
                            }.toMap(),
                            inputKey = inputKey.trim(),
                            outputKey = outputKey.trim(),
                            condition = conditionKey.trim().takeIf(String::isNotBlank)?.let {
                                MasonAutomationCondition(it, conditionOperator.trim(), conditionValue)
                            },
                            continueOnFailure = continueOnFailure,
                        ),
                    )
                },
                enabled = title.isNotBlank() && type.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateAutomationDialog(
    working: Boolean,
    initialSpec: MasonAutomationSpec?,
    onDismiss: () -> Unit,
    onCreate: (String, String, Map<String, String>, String, String, MasonAutomationConstraints) -> Unit,
) {
    val initialAction = initialSpec?.actions?.firstOrNull()
    val initialTriggerType = initialSpec
        ?.trigger
        ?.type
        ?.takeIf { initialAction?.type != "launch_app" }
        ?: "manual"
    val initialTriggerValue = initialSpec?.trigger?.value.orEmpty()
    val initialTime = AutomationScheduler.scheduledTime(initialTriggerValue).split(':')
    var name by remember(initialSpec?.id) { mutableStateOf(initialSpec?.name.orEmpty()) }
    var actionType by remember(initialSpec?.id) { mutableStateOf(initialAction?.type ?: "notification") }
    var notificationTitle by remember(initialSpec?.id) {
        mutableStateOf(initialAction?.arguments?.get("title") ?: "Mason")
    }
    var notificationText by remember(initialSpec?.id) {
        mutableStateOf(initialAction?.arguments?.get("text").orEmpty())
    }
    var modelPrompt by remember(initialSpec?.id) {
        mutableStateOf(initialAction?.arguments?.get("prompt").orEmpty())
    }
    var artifactFileName by remember(initialSpec?.id) {
        mutableStateOf(initialAction?.arguments?.get("file_name") ?: "automation-output.md")
    }
    var packageName by remember(initialSpec?.id) {
        mutableStateOf(initialAction?.arguments?.get("package_name").orEmpty())
    }
    var triggerType by remember(initialSpec?.id) { mutableStateOf(initialTriggerType) }
    var intervalMinutes by remember(initialSpec?.id) {
        mutableStateOf(
            initialSpec?.trigger?.value
                ?.takeIf { initialTriggerType == AutomationScheduler.TRIGGER_INTERVAL }
                ?: AutomationScheduler.MIN_INTERVAL_MINUTES.toString(),
        )
    }
    var scheduledHour by remember(initialSpec?.id) {
        mutableStateOf(initialTime.getOrNull(0)?.takeIf { initialTriggerType in TIMED_TRIGGER_TYPES } ?: "08")
    }
    var scheduledMinute by remember(initialSpec?.id) {
        mutableStateOf(initialTime.getOrNull(1)?.takeIf { initialTriggerType in TIMED_TRIGGER_TYPES } ?: "00")
    }
    var selectedWeekdays by remember(initialSpec?.id) {
        mutableStateOf(AutomationScheduler.selectedWeekdays(initialTriggerValue))
    }
    var networkRequirement by remember(initialSpec?.id) {
        mutableStateOf(initialSpec?.constraints?.network ?: AutomationScheduler.NETWORK_NONE)
    }
    var requiresCharging by remember(initialSpec?.id) {
        mutableStateOf(initialSpec?.constraints?.requiresCharging ?: false)
    }
    var requiresBatteryNotLow by remember(initialSpec?.id) {
        mutableStateOf(initialSpec?.constraints?.requiresBatteryNotLow ?: false)
    }
    val parsedInterval = intervalMinutes.toLongOrNull()
    val parsedHour = scheduledHour.toIntOrNull()
    val parsedMinute = scheduledMinute.toIntOrNull()
    val fixedTimeValid = parsedHour != null && parsedHour in 0..23 &&
        parsedMinute != null && parsedMinute in 0..59
    val triggerValid = when (triggerType) {
        "manual" -> true
        AutomationScheduler.TRIGGER_INTERVAL -> (parsedInterval ?: 0L) >= AutomationScheduler.MIN_INTERVAL_MINUTES
        AutomationScheduler.TRIGGER_DAILY -> fixedTimeValid
        AutomationScheduler.TRIGGER_WEEKDAYS -> fixedTimeValid && selectedWeekdays.isNotEmpty()
        else -> false
    }
    val valid = name.isNotBlank() && when (actionType) {
        "notification" -> notificationTitle.isNotBlank() && notificationText.isNotBlank() && triggerValid
        AutomationRunner.ACTION_MODEL_ARTIFACT ->
            modelPrompt.isNotBlank() && artifactFileName.isNotBlank() && triggerValid
        "launch_app" -> packageName.isNotBlank()
        else -> false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSpec == null) "创建自动化" else "编辑自动化") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !working,
                    singleLine = true,
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val actionOptions = listOf(
                        "notification" to "通知",
                        AutomationRunner.ACTION_MODEL_ARTIFACT to "AI 产出",
                        "launch_app" to "打开 App",
                    )
                    actionOptions
                        .forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = actionType == option.first,
                                onClick = {
                                    actionType = option.first
                                    if (option.first == "launch_app") triggerType = "manual"
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, actionOptions.size),
                                label = { Text(option.second) },
                            )
                        }
                }
                if (actionType != "launch_app") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "manual" to "手动",
                            AutomationScheduler.TRIGGER_INTERVAL to "间隔",
                            AutomationScheduler.TRIGGER_DAILY to "每天",
                            AutomationScheduler.TRIGGER_WEEKDAYS to "每周",
                        )
                            .forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = triggerType == option.first,
                                    onClick = { triggerType = option.first },
                                    shape = SegmentedButtonDefaults.itemShape(index, 4),
                                    label = { Text(option.second) },
                                )
                            }
                    }
                    if (triggerType == AutomationScheduler.TRIGGER_INTERVAL) {
                        OutlinedTextField(
                            value = intervalMinutes,
                            onValueChange = { value -> intervalMinutes = value.filter(Char::isDigit).take(6) },
                            enabled = !working,
                            singleLine = true,
                            label = { Text("间隔分钟（最少 15）") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (triggerType == AutomationScheduler.TRIGGER_DAILY ||
                        triggerType == AutomationScheduler.TRIGGER_WEEKDAYS
                    ) {
                        if (triggerType == AutomationScheduler.TRIGGER_WEEKDAYS) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf("一", "二", "三", "四", "五", "六", "日")
                                    .forEachIndexed { index, label ->
                                        val day = index + 1
                                        FilterChip(
                                            selected = day in selectedWeekdays,
                                            onClick = {
                                                selectedWeekdays = if (day in selectedWeekdays) {
                                                    selectedWeekdays - day
                                                } else {
                                                    selectedWeekdays + day
                                                }
                                            },
                                            enabled = !working,
                                            label = { Text(label) },
                                        )
                                    }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = scheduledHour,
                                onValueChange = { value -> scheduledHour = value.filter(Char::isDigit).take(2) },
                                enabled = !working,
                                singleLine = true,
                                label = { Text("小时（0-23）") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = scheduledMinute,
                                onValueChange = { value -> scheduledMinute = value.filter(Char::isDigit).take(2) },
                                enabled = !working,
                                singleLine = true,
                                label = { Text("分钟（0-59）") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (triggerType != "manual") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                AutomationScheduler.NETWORK_NONE to "不限",
                                AutomationScheduler.NETWORK_CONNECTED to "联网",
                                AutomationScheduler.NETWORK_UNMETERED to "非计费",
                            ).forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = networkRequirement == option.first,
                                    onClick = { networkRequirement = option.first },
                                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                                    label = { Text(option.second) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = requiresCharging,
                                onClick = { requiresCharging = !requiresCharging },
                                enabled = !working,
                                label = { Text("充电时") },
                            )
                            FilterChip(
                                selected = requiresBatteryNotLow,
                                onClick = { requiresBatteryNotLow = !requiresBatteryNotLow },
                                enabled = !working,
                                label = { Text("电量充足") },
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
                            value = modelPrompt,
                            onValueChange = { modelPrompt = it },
                            enabled = !working,
                            label = { Text("任务内容") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = artifactFileName,
                            onValueChange = { artifactFileName = it },
                            enabled = !working,
                            singleLine = true,
                            label = { Text("产出文件名") },
                            placeholder = { Text("automation-output.md") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                    val arguments = when (actionType) {
                        "notification" -> mapOf(
                            "title" to notificationTitle.trim(),
                            "text" to notificationText.trim(),
                        )
                        AutomationRunner.ACTION_MODEL_ARTIFACT -> mapOf(
                            "prompt" to modelPrompt.trim(),
                            "file_name" to artifactFileName.trim(),
                        )
                        else -> mapOf("action" to "launch", "package_name" to packageName.trim())
                    }
                    val triggerValue = when (triggerType) {
                        AutomationScheduler.TRIGGER_INTERVAL -> parsedInterval?.toString().orEmpty()
                        AutomationScheduler.TRIGGER_DAILY ->
                            "%02d:%02d".format(Locale.ROOT, parsedHour ?: 0, parsedMinute ?: 0)
                        AutomationScheduler.TRIGGER_WEEKDAYS -> AutomationScheduler.encodeWeekdays(
                            selectedWeekdays,
                            "%02d:%02d".format(Locale.ROOT, parsedHour ?: 0, parsedMinute ?: 0),
                        )
                        else -> ""
                    }
                    onCreate(
                        name.trim(),
                        actionType,
                        arguments,
                        triggerType,
                        triggerValue,
                        MasonAutomationConstraints(
                            network = networkRequirement,
                            requiresCharging = requiresCharging,
                            requiresBatteryNotLow = requiresBatteryNotLow,
                        ),
                    )
                },
                enabled = !working && valid,
            ) { Text(if (initialSpec == null) "创建" else "保存") }
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
    var expandedRunAt by remember { mutableStateOf<Long?>(null) }
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = log.steps.isNotEmpty()) {
                                    expandedRunAt = if (expandedRunAt == log.ranAt) null else log.ranAt
                                },
                        ) {
                            Text(
                                if (log.status == "success") "成功" else "失败",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${formatter.format(Date(log.ranAt))} · ${when (log.source) {
                                    "schedule" -> "定时"
                                    "event" -> "事件"
                                    else -> "手动"
                                }} · ${log.message}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            if (log.steps.isNotEmpty()) {
                                Text(
                                    if (expandedRunAt == log.ranAt) "收起步骤" else "查看步骤",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                )
                            }
                            if (expandedRunAt == log.ranAt) {
                                log.steps.forEachIndexed { index, step ->
                                    Text(
                                        "${index + 1}. ${step.title} · ${when (step.status) {
                                            "success" -> "成功"
                                            "skipped" -> "跳过"
                                            else -> "失败"
                                        }}\n${step.message}",
                                        modifier = Modifier.padding(top = 5.dp, start = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
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
        .filterNot { it.skillManifest?.archived == true }
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
        automation = automation,
        skillManifest = skillManifest,
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
