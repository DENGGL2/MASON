package com.denggl2.mason.ui.chat

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.extractArtifactMetadataMarkers
import com.denggl2.mason.data.stripArtifactMarkers
import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.MasonSkillManifest
import com.denggl2.mason.agent.TaskStep
import com.denggl2.mason.agent.TaskStepStatus
import com.denggl2.mason.agent.ToolApprovalRequest
import com.denggl2.mason.agent.ToolRiskLevel
import com.denggl2.mason.llm.TokenUsage
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.ui.conversation.ConversationListItem
import com.denggl2.mason.ui.conversation.ConversationListViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
private val SKILL_MANIFEST_JSON = Json { ignoreUnknownKeys = true }
private const val MAX_COLLAPSED_LENGTH = 500
private const val USER_CONTEXT_HEADER = "Mason 附加上下文"
private data class AnswerSection(val label: String, val text: String)
private enum class AttachmentKind { Image, File }
private data class PendingAttachment(
    val kind: AttachmentKind,
    val name: String,
    val uri: String,
)
private data class SkillOption(
    val name: String,
    val description: String,
    val path: String,
)
private data class UserMessagePresentation(
    val body: String,
    val attachments: List<PendingAttachment>,
    val skill: SkillOption?,
)
private enum class MessageBlockKind { Heading, Paragraph, Bullet, Numbered, Code, Quote, Divider }
private data class MessageTextBlock(
    val kind: MessageBlockKind,
    val text: String,
    val meta: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onConversationSelected: (Long) -> Unit,
    onNewChat: (() -> Unit)? = null,
    onOpenArtifacts: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenAutomations: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
    historyViewModel: ConversationListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val conversations by historyViewModel.conversations.collectAsState()
    val drawerConversations = remember(conversations) {
        conversations.filterNot { item ->
            item.lastMessage == null && item.conversation.title == "新对话"
        }
    }
    var inputText by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var selectedSkill by remember { mutableStateOf<SkillOption?>(null) }
    var showSkillPicker by remember { mutableStateOf(false) }
    var pendingDrawerDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pendingAttachments = pendingAttachments + PendingAttachment(
                kind = AttachmentKind.Image,
                name = resolveDisplayName(context, it) ?: "图片",
                uri = it.toString(),
            )
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pendingAttachments = pendingAttachments + PendingAttachment(
                kind = AttachmentKind.File,
                name = resolveDisplayName(context, it) ?: "文件",
                uri = it.toString(),
            )
        }
    }

    val visibleMessages = uiState.messages.filterNot { message ->
        message.role == "assistant" &&
            message.content.isNullOrBlank() &&
            !message.tool_calls.isNullOrEmpty()
    }
    val hasMessages = visibleMessages.isNotEmpty() ||
        uiState.streamingContent.isNotEmpty() ||
        uiState.toolCallStatus != null ||
        uiState.pendingToolApproval != null
    val lastAssistantTimestamp = visibleMessages.lastOrNull { it.role == "assistant" }?.timestamp
    val modeSwitchModels = remember(apiConfig.providerId, apiConfig.model) {
        AiProviderCatalog.quickSwitchModels(apiConfig.providerId, apiConfig.model)
    }
    val currentProviderName = remember(apiConfig.providerId) {
        AiProviderCatalog.getProvider(apiConfig.providerId)?.name.orEmpty()
    }
    val apiWarning = remember(apiConfig) {
        when {
            AiProviderCatalog.requiresApiKey(apiConfig) && apiConfig.apiKey.isBlank() -> {
                if (AiProviderCatalog.isFreeModel(apiConfig.providerId, apiConfig.model)) {
                    "免费模型仍需平台 Key，用来识别账号和限额"
                } else {
                    "当前模型需要 API Key，先去设置里填写"
                }
            }
            AiProviderCatalog.requiresApiKey(apiConfig) && !AiProviderCatalog.isVerified(apiConfig) ->
                "API Key 尚未验证，建议先测试连接"
            else -> null
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.streamingContent, uiState.toolCallStatus) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LaunchedEffect(Unit) {
        historyViewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.30f),
        drawerContent = {
            MasonDrawer(
                onNewChat = { closeThen { onNewChat?.invoke() } },
                conversations = drawerConversations,
                currentConversationId = uiState.conversationId,
                onConversationSelected = { conversationId ->
                    closeThen { onConversationSelected(conversationId) }
                },
                onOpenArtifacts = { closeThen(onOpenArtifacts) },
                onOpenSkills = { closeThen(onOpenSkills) },
                onOpenAutomations = { closeThen(onOpenAutomations) },
                onSettings = { closeThen(onNavigateToSettings) },
                onExportConversations = { ids -> historyViewModel.exportConversations(ids) },
                onDeleteConversations = { ids -> pendingDrawerDeleteIds = ids },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Mason",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "打开菜单",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        uiState.lastUsage?.let { usage ->
                            UsageQuotaText(usage = usage)
                            Spacer(Modifier.width(14.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
            ) {
                if (!hasMessages) {
                    EmptyChatState(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            visibleMessages,
                            key = { message -> "${message.role}-${message.timestamp}-${message.content.hashCode()}" },
                        ) { message ->
                            Box(modifier = Modifier.animateItem()) {
                                MessageBubble(
                                    message = message,
                                    processingMs = if (
                                        message.role == "assistant" &&
                                        message.timestamp == lastAssistantTimestamp
                                    ) {
                                        uiState.lastProcessingMs
                                    } else {
                                        null
                                    },
                                    onRetry = { viewModel.retryLastUserMessage() },
                                )
                            }
                        }

                        if (uiState.isStreaming || uiState.pendingToolApproval != null) {
                            item {
                                MasonProcessPanel(
                                    steps = uiState.taskSteps,
                                    toolCallStatus = uiState.toolCallStatus,
                                    hasDraft = uiState.streamingContent.isNotBlank(),
                                )
                            }
                        }

                        if (uiState.isStreaming && uiState.streamingContent.isNotEmpty()) {
                            item {
                                MessageBubble(
                                    ChatMessage(role = "assistant", content = uiState.streamingContent),
                                    isStreaming = true,
                                    onRetry = { viewModel.retryLastUserMessage() },
                                )
                            }
                        }
                    }
                }

                InputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        val outgoing = buildOutgoingMessage(inputText, pendingAttachments, selectedSkill)
                        viewModel.sendMessage(outgoing)
                        inputText = ""
                        pendingAttachments = emptyList()
                        selectedSkill = null
                        focusManager.clearFocus()
                    },
                    enabled = !uiState.isStreaming,
                    attachments = pendingAttachments,
                    selectedSkill = selectedSkill,
                    onAddImage = { imagePicker.launch("image/*") },
                    onAddFile = { filePicker.launch(arrayOf("*/*")) },
                    onUseSkill = { showSkillPicker = true },
                    apiWarning = apiWarning,
                    onOpenSettings = onNavigateToSettings,
                    modelSwitchModels = modeSwitchModels,
                    currentModelId = apiConfig.model,
                    currentProviderName = currentProviderName,
                    onSelectModel = viewModel::selectChatModel,
                    onRemoveAttachment = { index ->
                        pendingAttachments = pendingAttachments.filterIndexed { itemIndex, _ ->
                            itemIndex != index
                        }
                    },
                    onClearSkill = { selectedSkill = null },
                )
            }
        }
    }

    if (showSkillPicker) {
        SkillPickerSheet(
            onDismiss = { showSkillPicker = false },
            onSelect = { skill ->
                selectedSkill = skill
                showSkillPicker = false
            },
        )
    }

    uiState.pendingToolApproval?.let { approval ->
        ToolApprovalDialog(
            approval = approval,
            onApprove = viewModel::approvePendingToolCall,
            onReject = viewModel::rejectPendingToolCall,
        )
    }

    if (pendingDrawerDeleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDrawerDeleteIds = emptySet() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除选中对话") },
            text = { Text("将删除 ${pendingDrawerDeleteIds.size} 个对话及其中消息，删除后不能恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyViewModel.deleteConversations(pendingDrawerDeleteIds)
                        pendingDrawerDeleteIds = emptySet()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDrawerDeleteIds = emptySet() }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ToolApprovalDialog(
    approval: ToolApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val riskLabel = when (approval.riskLevel) {
        ToolRiskLevel.Low -> "低风险"
        ToolRiskLevel.Medium -> "需要确认"
        ToolRiskLevel.High -> "高风险"
    }
    AlertDialog(
        onDismissRequest = onReject,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Mason 准备执行") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = approval.toolName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("级别：$riskLabel")
                Text("影响范围：${approval.reason}")
                Text(
                    text = "允许一次后只继续本轮任务；拒绝后本轮不会执行这个能力。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) {
                Text("允许一次", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("拒绝")
            }
        },
    )
}

@Composable
private fun UsageQuotaText(
    usage: TokenUsage,
) {
    Text(
        text = "消耗 ${formatTokenCount(usage.totalTokens)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens <= 0L -> "0"
        tokens < 1_000L -> tokens.toString()
        tokens < 1_000_000L -> {
            val value = tokens / 1_000.0
            if (tokens < 10_000L) "%.1fK".format(value) else "${(tokens / 1_000)}K"
        }
        else -> "%.1fM".format(tokens / 1_000_000.0)
    }
}

@Composable
private fun MasonDrawer(
    onNewChat: () -> Unit,
    conversations: List<ConversationListItem>,
    currentConversationId: Long?,
    onConversationSelected: (Long) -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenAutomations: () -> Unit,
    onSettings: () -> Unit,
    onExportConversations: (Set<Long>) -> Unit,
    onDeleteConversations: (Set<Long>) -> Unit,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedConversationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val visibleConversations = conversations.take(30)
    val allVisibleIds = remember(visibleConversations) {
        visibleConversations.map { it.conversation.id }.toSet()
    }

    LaunchedEffect(allVisibleIds) {
        selectedConversationIds = selectedConversationIds.intersect(allVisibleIds)
        if (selectionMode && selectedConversationIds.isEmpty()) {
            selectionMode = false
        }
    }

    fun toggleConversationSelection(id: Long) {
        val nextIds = if (id in selectedConversationIds) {
            selectedConversationIds - id
        } else {
            selectedConversationIds + id
        }
        selectedConversationIds = nextIds
        selectionMode = nextIds.isNotEmpty()
    }

    ModalDrawerSheet(
        modifier = Modifier.width(292.dp),
        drawerContainerColor = drawerGlassSurface(),
        windowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerGlassSurface())
                .padding(top = 14.dp, bottom = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    "Mason",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "AI 对话与手机工具",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            DrawerPrimaryAction(
                label = "新对话",
                selected = currentConversationId == null,
                onClick = onNewChat,
                icon = Icons.Outlined.Add,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectionMode) "已选 ${selectedConversationIds.size}" else "最近对话",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (selectionMode && visibleConversations.isNotEmpty()) {
                    Text(
                        if (selectedConversationIds.size == allVisibleIds.size) "取消全选" else "全选",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                selectedConversationIds = if (selectedConversationIds.size == allVisibleIds.size) {
                                    emptySet()
                                } else {
                                    allVisibleIds
                                }
                                selectionMode = selectedConversationIds.isNotEmpty()
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (conversations.isEmpty()) {
                    item {
                        Text(
                            "还没有历史记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    items(
                        items = visibleConversations,
                        key = { it.conversation.id },
                    ) { item ->
                        Box(modifier = Modifier.animateItem()) {
                            DrawerConversationItem(
                                item = item,
                                selected = if (selectionMode) {
                                    item.conversation.id in selectedConversationIds
                                } else {
                                    item.conversation.id == currentConversationId
                                },
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        toggleConversationSelection(item.conversation.id)
                                    } else {
                                        onConversationSelected(item.conversation.id)
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selectedConversationIds = selectedConversationIds + item.conversation.id
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            )
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawerSelectionAction(
                        label = "导出",
                        icon = Icons.Outlined.FileDownload,
                        enabled = selectedConversationIds.isNotEmpty(),
                        onClick = {
                            onExportConversations(selectedConversationIds)
                            selectedConversationIds = emptySet()
                            selectionMode = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerSelectionAction(
                        label = "删除",
                        icon = Icons.Outlined.Delete,
                        enabled = selectedConversationIds.isNotEmpty(),
                        destructive = true,
                        onClick = { onDeleteConversations(selectedConversationIds) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                DrawerFooterDock(
                    onOpenArtifacts = onOpenArtifacts,
                    onOpenSkills = onOpenSkills,
                    onOpenAutomations = onOpenAutomations,
                    onSettings = onSettings,
                )
            }
        }
    }
}

@Composable
private fun drawerGlassSurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f)

@Composable
private fun DrawerPrimaryAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                },
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerSelectionAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (enabled) 0.12f else 0.06f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DrawerFooterDock(
    onOpenArtifacts: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenAutomations: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerFooterAction(
            label = "产出",
            icon = Icons.Outlined.Folder,
            onClick = onOpenArtifacts,
            modifier = Modifier.weight(1f),
        )
        DrawerFooterAction(
            label = "技能",
            icon = Icons.Outlined.Extension,
            onClick = onOpenSkills,
            modifier = Modifier.weight(1f),
        )
        DrawerFooterAction(
            label = "自动",
            icon = Icons.AutoMirrored.Outlined.EventNote,
            onClick = onOpenAutomations,
            modifier = Modifier.weight(1f),
        )
        DrawerFooterAction(
            label = "设置",
            icon = Icons.Outlined.Settings,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawerFooterAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerNavRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DrawerConversationItem(
    item: ConversationListItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.46f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.conversation.title,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.lastMessage ?: "还没有消息",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier)
}

@Composable
private fun TimestampLabel(timestamp: Long?) {
    if (timestamp == null) return
    val timeText = remember(timestamp) { TIME_FORMAT.format(Date(timestamp)) }
    Text(
        text = timeText,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ToolCallStatusCard(toolName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation_angle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(17.dp)
                    .rotate(rotation),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 11.dp, vertical = 8.dp),
        ) {
            Text(
                text = toolName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MasonProcessPanel(
    steps: List<TaskStep>,
    toolCallStatus: String?,
    hasDraft: Boolean,
) {
    if (steps.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "任务过程",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        processSummaryText(steps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProcessStatusPill(processStatusText(steps), steps.any { it.status == TaskStepStatus.WaitingForUser })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            steps.forEach { step ->
                TaskProcessLine(step)
            }
        }
        return
    }

    val runningText = when {
        toolCallStatus != null -> toolCallStatus
        hasDraft -> "正在整理回答和最终总结"
        else -> "正在理解请求并判断是否需要调用手机工具"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ProcessLine(
            label = "思考",
            text = "整理可见上下文和目标，不展示内部推理",
            active = toolCallStatus == null && !hasDraft,
        )
        ProcessLine(
            label = "进行中",
            text = runningText,
            active = true,
        )
        ProcessLine(
            label = "引导",
            text = "需要选择、授权或补充信息时，会在这里给出清晰选项",
            active = false,
        )
    }
}

@Composable
private fun TaskProcessLine(step: TaskStep) {
    val active = step.status == TaskStepStatus.Running || step.status == TaskStepStatus.WaitingForUser
    val dotColor = when (step.status) {
        TaskStepStatus.Completed -> MaterialTheme.colorScheme.primary
        TaskStepStatus.Running -> MaterialTheme.colorScheme.primary
        TaskStepStatus.WaitingForUser -> MaterialTheme.colorScheme.error
        TaskStepStatus.Failed -> MaterialTheme.colorScheme.error
        TaskStepStatus.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStepStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
    }
    val statusLabel = when (step.status) {
        TaskStepStatus.Pending -> "待处理"
        TaskStepStatus.Running -> "进行中"
        TaskStepStatus.WaitingForUser -> "待确认"
        TaskStepStatus.Completed -> "完成"
        TaskStepStatus.Failed -> "失败"
        TaskStepStatus.Cancelled -> "取消"
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.title,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(dotColor.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        statusLabel,
                        color = dotColor,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                step.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ProcessStatusPill(
    text: String,
    urgent: Boolean,
) {
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun processStatusText(steps: List<TaskStep>): String {
    return when {
        steps.any { it.status == TaskStepStatus.WaitingForUser } -> "待确认"
        steps.any { it.status == TaskStepStatus.Failed } -> "失败"
        steps.any { it.status == TaskStepStatus.Running } -> "进行中"
        steps.all { it.status == TaskStepStatus.Completed } -> "完成"
        steps.any { it.status == TaskStepStatus.Cancelled } -> "已停止"
        else -> "排队中"
    }
}

private fun processSummaryText(steps: List<TaskStep>): String {
    val active = steps.firstOrNull {
        it.status == TaskStepStatus.Running || it.status == TaskStepStatus.WaitingForUser
    }
    return active?.let { "${it.title} · ${it.detail}" } ?: "规划、执行、检查、总结会按本轮任务动态更新"
}

@Composable
private fun ProcessLine(
    label: String,
    text: String,
    active: Boolean,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
                ),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandableMessageContent(
    content: String,
    isStreaming: Boolean,
    contentColor: Color,
    actionColor: Color,
) {
    val isLong = content.length > MAX_COLLAPSED_LENGTH
    var expanded by remember(content) { mutableStateOf(false) }
    val displayText = if (isLong && !expanded) content.take(MAX_COLLAPSED_LENGTH) + "..." else content
    val richText = displayText + if (isStreaming) "\n|" else ""

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FormattedMessageText(
            content = richText,
            contentColor = contentColor,
            actionColor = actionColor,
        )

        if (isLong) {
            Text(
                text = if (expanded) "收起" else "展开全文",
                color = actionColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AssistantAnswerCard(
    message: ChatMessage,
    isStreaming: Boolean = false,
    processingMs: Long? = null,
    onRetry: () -> Unit = {},
) {
    val rawContent = message.content.orEmpty()
    val artifacts = remember(rawContent) { extractArtifactMetadata(rawContent) }
    val content = remember(rawContent) { stripArtifactMarkers(rawContent) }
    val sections = remember(content, isStreaming) { parseAnswerSections(content, isStreaming) }
    val references = remember(content) { extractReferenceUrls(content) }
    val outputs = remember(content) { extractOutputMentions(content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "M",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Mason",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isStreaming) "进行中" else "完成",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!isStreaming && processingMs != null) {
                Spacer(Modifier.width(8.dp))
                ProcessingTimePill(processingMs)
            }
        }

        Spacer(Modifier.height(10.dp))

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            AnswerSectionBlock(
                label = section.label,
                text = section.text,
                isStreaming = isStreaming && index == sections.lastIndex,
            )
        }

        if (outputs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OutputMentionStrip(outputs)
        }

        if (artifacts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ArtifactMentionStrip(artifacts)
        }

        if (references.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ReferenceStrip(references)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimestampLabel(message.timestamp)
            Spacer(Modifier.weight(1f))
            if (!isStreaming && content.isNotBlank()) {
                AssistantActionRow(
                    content = content,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun AnswerSectionBlock(
    label: String,
    text: String,
    isStreaming: Boolean,
) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        ExpandableMessageContent(
            content = text,
            isStreaming = isStreaming,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolResultWorkCard(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                message.name?.let { "工具结果 · $it" } ?: "工具结果",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "进行中",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))
        AnswerSectionBlock(
            label = "进行中",
            text = message.content.orEmpty().ifBlank { "工具没有返回可展示内容" },
            isStreaming = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TimestampLabel(message.timestamp)
        }
    }
}

private fun parseAnswerSections(content: String, isStreaming: Boolean): List<AnswerSection> {
    if (content.isBlank()) {
        return listOf(
            AnswerSection(
                label = if (isStreaming) "进行中" else "最终总结",
                text = if (isStreaming) "正在组织回复..." else "暂无内容",
            ),
        )
    }

    val labels = setOf("思考", "进行中", "引导", "最终总结")
    val sections = mutableListOf<AnswerSection>()
    var currentLabel: String? = null
    val currentText = StringBuilder()

    fun flush() {
        val label = currentLabel
        val text = currentText.toString().trim()
        if (label != null && text.isNotBlank()) {
            sections.add(AnswerSection(label, text))
        }
        currentText.clear()
    }

    content.lines().forEach { rawLine ->
        val line = rawLine.trim()
        val detected = detectAnswerLabel(line, labels)
        if (detected != null) {
            flush()
            currentLabel = detected.first
            if (detected.second.isNotBlank()) {
                currentText.appendLine(detected.second)
            }
        } else {
            if (currentLabel == null) currentLabel = if (isStreaming) "进行中" else "最终总结"
            currentText.appendLine(rawLine)
        }
    }
    flush()

    return sections.ifEmpty {
        listOf(AnswerSection(if (isStreaming) "进行中" else "最终总结", content.trim()))
    }
}

private fun detectAnswerLabel(line: String, labels: Set<String>): Pair<String, String>? {
    val normalized = line
        .removePrefix("#")
        .removePrefix("#")
        .removePrefix("#")
        .trim()
        .removePrefix("[")

    labels.forEach { label ->
        val candidates = listOf("$label]", "$label：", "$label:", "$label -", "$label ")
        candidates.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return label to normalized.removePrefix(prefix).trim()
        }
        if (normalized == label) return label to ""
    }
    return null
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    processingMs: Long? = null,
    onRetry: () -> Unit = {},
) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    if (isTool) {
        ToolResultWorkCard(message)
        return
    }
    if (!isUser && !isTool) {
        AssistantAnswerCard(
            message = message,
            isStreaming = isStreaming,
            processingMs = processingMs,
            onRetry = onRetry,
        )
        return
    }
    val presentation = remember(message.content) {
        parseUserMessagePresentation(message.content.orEmpty())
    }

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isTool -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                Avatar(label = if (isTool) "T" else "M", isTool = isTool)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .then(if (isUser) Modifier.widthIn(max = 292.dp) else Modifier.fillMaxWidth(0.86f))
                    .background(
                        color = bubbleColor,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Column {
                    if (isTool) {
                        Text(
                            text = message.name?.let { "工具结果 · $it" } ?: "工具结果",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message.content ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    } else {
                        UserMessageContent(
                            presentation = presentation,
                            contentColor = contentColor,
                        )
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Avatar(label = "U", isTool = false, isUser = true)
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .then(
                    if (isUser) Modifier.padding(end = 40.dp) else Modifier.padding(start = 40.dp),
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            TimestampLabel(message.timestamp)
        }
    }
}

@Composable
private fun Avatar(
    label: String,
    isTool: Boolean,
    isUser: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                when {
                    isUser -> MaterialTheme.colorScheme.onSurfaceVariant
                    isTool -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isUser) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun UserMessageContent(
    presentation: UserMessagePresentation,
    contentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (presentation.body.isNotBlank()) {
            Text(
                text = presentation.body,
                color = contentColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        presentation.skill?.let { skill ->
            UserContextPill(
                icon = Icons.Outlined.Extension,
                label = "Skill · ${skill.name}",
                contentColor = contentColor,
            )
        }
        presentation.attachments.forEach { attachment ->
            UserContextPill(
                icon = if (attachment.kind == AttachmentKind.Image) Icons.Outlined.ImageIcon else Icons.Outlined.Description,
                label = "${attachment.kind.label} · ${attachment.name}",
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun UserContextPill(
    icon: ImageVector,
    label: String,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessingTimePill(processingMs: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "用时 ${formatDuration(processingMs)}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AssistantActionRow(
    content: String,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MessageActionButton(
            icon = Icons.Outlined.ContentCopy,
            contentDescription = "复制",
            onClick = {
                clipboard.setText(AnnotatedString(content))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
        )
        MessageActionButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = "重新生成",
            onClick = onRetry,
        )
        MessageActionButton(
            icon = Icons.Outlined.Share,
            contentDescription = "分享",
            onClick = { shareText(context, content) },
        )
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun FormattedMessageText(
    content: String,
    contentColor: Color,
    actionColor: Color,
) {
    val blocks = remember(content) { parseMessageBlocks(content) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block.kind) {
                MessageBlockKind.Heading -> Text(
                    text = block.text,
                    color = contentColor,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                MessageBlockKind.Paragraph -> Text(
                    text = block.text,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                MessageBlockKind.Bullet -> LabeledTextLine(
                    label = "•",
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Numbered -> LabeledTextLine(
                    label = block.meta ?: "1.",
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Code -> CodeBlock(
                    code = block.text,
                    language = block.meta,
                    contentColor = contentColor,
                    actionColor = actionColor,
                )
                MessageBlockKind.Quote -> QuoteBlock(
                    text = block.text,
                    contentColor = contentColor,
                )
                MessageBlockKind.Divider -> HorizontalDivider(
                    color = contentColor.copy(alpha = 0.16f),
                )
            }
        }
    }
}

@Composable
private fun LabeledTextLine(
    label: String,
    text: String,
    contentColor: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = contentColor.copy(alpha = 0.76f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text,
            color = contentColor,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeBlock(
    code: String,
    language: String?,
    contentColor: Color,
    actionColor: Color,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.07f))
            .border(1.dp, contentColor.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.ifBlank { null } ?: "代码",
                color = contentColor.copy(alpha = 0.70f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = actionColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Text(
            text = code,
            color = contentColor,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun QuoteBlock(
    text: String,
    contentColor: Color,
) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.28f)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            color = contentColor.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OutputMentionStrip(outputs: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(outputs) { output ->
            InfoChip(
                icon = Icons.Outlined.Description,
                label = output.substringAfterLast('/').substringAfterLast('\\').take(28),
                detail = "产出",
            )
        }
    }
}

@Composable
private fun ArtifactMentionStrip(artifacts: List<ArtifactMetadata>) {
    val context = LocalContext.current
    var previewArtifact by remember { mutableStateOf<ArtifactMetadata?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(artifacts, key = { it.path }) { artifact ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.widthIn(max = 150.dp)) {
                    Text(
                        artifact.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatArtifactSize(artifact.bytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                MessageActionButton(
                    icon = Icons.Outlined.Visibility,
                    contentDescription = "预览产出",
                    onClick = { previewArtifact = artifact },
                )
                MessageActionButton(
                    icon = Icons.Outlined.FileDownload,
                    contentDescription = "打开产出",
                    onClick = { openArtifact(context, artifact, edit = false) },
                )
                MessageActionButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑产出",
                    onClick = { openArtifact(context, artifact, edit = true) },
                )
                MessageActionButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = "分享产出",
                    onClick = { shareArtifact(context, artifact) },
                )
            }
        }
    }

    previewArtifact?.let { artifact ->
        ArtifactPreviewDialog(
            artifact = artifact,
            onDismiss = { previewArtifact = null },
            onOpen = { openArtifact(context, artifact, edit = false) },
            onEdit = { openArtifact(context, artifact, edit = true) },
            onShare = { shareArtifact(context, artifact) },
        )
    }
}

@Composable
private fun ArtifactPreviewDialog(
    artifact: ArtifactMetadata,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val previewText = remember(artifact.path, artifact.bytes) {
        buildArtifactPreviewText(artifact)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    artifact.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${artifact.mimeType} · ${formatArtifactSize(artifact.bytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text("打开")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onShare) {
                    Text("分享")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

@Composable
private fun ReferenceStrip(references: List<String>) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(references) { index, reference ->
            Box(
                modifier = Modifier.clickable { openUrl(context, reference) },
            ) {
                InfoChip(
                    icon = Icons.Outlined.CheckCircle,
                    label = "来源 ${index + 1}",
                    detail = Uri.parse(reference).host ?: "链接",
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InputContextStrip(
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        selectedSkill?.let { skill ->
            item {
                SkillContextChip(
                    skill = skill,
                    onClear = onClearSkill,
                )
            }
        }
        itemsIndexed(attachments) { index, attachment ->
            AttachmentContextChip(
                attachment = attachment,
                onRemove = { onRemoveAttachment(index) },
            )
        }
    }
}

@Composable
private fun SkillContextChip(
    skill: SkillOption,
    onClear: () -> Unit,
) {
    val chipShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), chipShape)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            skill.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 176.dp),
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除 Skill",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun AttachmentContextChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    val chipShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), chipShape)
            .padding(start = 4.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentPreview(attachment)
        Spacer(Modifier.width(4.dp))
        Text(
            attachment.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 154.dp),
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除附件",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: PendingAttachment) {
    val context = LocalContext.current
    val bitmap = remember(attachment.uri) {
        if (attachment.kind == AttachmentKind.Image) {
            loadAttachmentBitmap(context, Uri.parse(attachment.uri))
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                if (attachment.kind == AttachmentKind.Image) Icons.Outlined.ImageIcon else Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onUseSkill: () -> Unit,
    apiWarning: String?,
    onOpenSettings: () -> Unit,
    modelSwitchModels: List<AiModelPreset>,
    currentModelId: String,
    currentProviderName: String,
    onSelectModel: (String) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
) {
    val panelShape = RoundedCornerShape(18.dp)
    val active = enabled && (
        text.isNotBlank() ||
            attachments.isNotEmpty() ||
            selectedSkill != null
        )
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.11f)
    }
    val panelSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    var addMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (apiWarning != null) {
            ApiAttentionPill(
                message = apiWarning,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.height(7.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(panelSurface)
                .border(1.dp, borderColor, panelShape)
                .padding(horizontal = 7.dp, vertical = 5.dp),
        ) {
            if (attachments.isNotEmpty() || selectedSkill != null) {
                InputContextStrip(
                    attachments = attachments,
                    selectedSkill = selectedSkill,
                    onRemoveAttachment = onRemoveAttachment,
                    onClearSkill = onClearSkill,
                )
                Spacer(Modifier.height(7.dp))
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 31.dp, max = 118.dp)
                    .padding(horizontal = 5.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isBlank()) {
                            Text(
                                "询问 Mason，或添加材料...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ComposerIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "添加",
                        enabled = enabled,
                        selected = addMenuExpanded,
                        onClick = { addMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
                        tonalElevation = 2.dp,
                        shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        ),
                    ) {
                        AttachmentMenuRow(
                            icon = Icons.Outlined.ImageIcon,
                            label = "添加图片",
                            onClick = {
                                addMenuExpanded = false
                                onAddImage()
                            },
                        )
                        AttachmentMenuRow(
                            icon = Icons.Outlined.AttachFile,
                            label = "添加文件",
                            onClick = {
                                addMenuExpanded = false
                                onAddFile()
                            },
                        )
                        AttachmentMenuRow(
                            icon = Icons.Outlined.Extension,
                            label = "使用 Skill",
                            onClick = {
                                addMenuExpanded = false
                                onUseSkill()
                            },
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (modelSwitchModels.size > 1) {
                    ModelModeSwitcher(
                        models = modelSwitchModels,
                        currentModelId = currentModelId,
                        providerName = currentProviderName,
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it },
                        onSelect = { modelId ->
                            modelMenuExpanded = false
                            onSelectModel(modelId)
                        },
                    )
                    Spacer(Modifier.width(7.dp))
                }

                ComposerSendButton(
                    active = active,
                    onSend = onSend,
                )
            }
        }
    }
}

@Composable
private fun ApiAttentionPill(
    message: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = accent.copy(alpha = 0.82f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "配置",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ModelModeSwitcher(
    models: List<AiModelPreset>,
    currentModelId: String,
    providerName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val current = models.firstOrNull { it.id == currentModelId } ?: models.first()

    Box {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                    RoundedCornerShape(999.dp),
                )
                .clickable { onExpandedChange(true) }
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current.modeLabel ?: current.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 72.dp),
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "切换模型模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
            ),
        ) {
            Text(
                "模式 · $providerName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            models.forEach { item ->
                ModelModeMenuRow(
                    model = item,
                    selected = item.id == currentModelId,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelModeMenuRow(
    model: AiModelPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(184.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.modeLabel ?: model.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(17.dp)
                .rotate(if (selected) 45f else 0f),
        )
    }
}

@Composable
private fun ComposerSendButton(
    active: Boolean,
    onSend: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                },
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(enabled = active, onClick = onSend),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowUpward,
            contentDescription = "发送",
            tint = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AttachmentMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (SkillOption) -> Unit,
) {
    val context = LocalContext.current
    var skills by remember { mutableStateOf<List<SkillOption>?>(null) }

    LaunchedEffect(Unit) {
        skills = loadSkillOptions(context.applicationContext)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "使用 Skill",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "选择后会作为本轮对话的执行偏好发送给 Mason。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))

            when {
                skills == null -> Text(
                    "正在读取技能...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
                skills.orEmpty().isEmpty() -> Text(
                    "暂无已安装技能",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(skills.orEmpty(), key = { it.path }) { skill ->
                        SkillPickerRow(
                            skill = skill,
                            onClick = { onSelect(skill) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillPickerRow(
    skill: SkillOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                skill.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                skill.description.ifBlank { skill.path },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val AttachmentKind.label: String
    get() = when (this) {
        AttachmentKind.Image -> "图片"
        AttachmentKind.File -> "文件"
    }

private fun buildOutgoingMessage(
    text: String,
    attachments: List<PendingAttachment>,
    selectedSkill: SkillOption?,
): String {
    val body = text.trim().ifBlank {
        if (attachments.isNotEmpty() || selectedSkill != null) "请处理这些材料" else ""
    }
    if (attachments.isEmpty() && selectedSkill == null) return body

    return buildString {
        append(body)
        append("\n\n---\n")
        append(USER_CONTEXT_HEADER)
        append('\n')
        selectedSkill?.let { skill ->
            append("- Skill：")
            append(skill.name)
            append(" | ")
            append(skill.path)
            if (skill.description.isNotBlank()) {
                append(" | ")
                append(skill.description)
            }
            append('\n')
        }
        attachments.forEach { attachment ->
            append("- ")
            append(attachment.kind.label)
            append('：')
            append(attachment.name)
            append(" | ")
            append(attachment.uri)
            append('\n')
        }
        append("请结合以上材料处理；如果当前模型无法读取内容，请明确说明需要授权读取或解析。")
    }
}

private fun parseUserMessagePresentation(content: String): UserMessagePresentation {
    val marker = "\n---\n$USER_CONTEXT_HEADER"
    val markerIndex = content.indexOf(marker)
    if (markerIndex < 0) {
        return UserMessagePresentation(
            body = content.trim(),
            attachments = emptyList(),
            skill = null,
        )
    }

    val body = content.substring(0, markerIndex).trim()
    val context = content.substring(markerIndex + marker.length)
    var skill: SkillOption? = null
    val attachments = mutableListOf<PendingAttachment>()

    context.lines().forEach { rawLine ->
        val line = rawLine.trim().removePrefix("-").trim()
        when {
            line.startsWith("Skill：") -> {
                val parts = line.removePrefix("Skill：").split("|").map { it.trim() }
                skill = SkillOption(
                    name = parts.getOrNull(0).orEmpty(),
                    path = parts.getOrNull(1).orEmpty(),
                    description = parts.getOrNull(2).orEmpty(),
                )
            }
            line.startsWith("图片：") -> {
                val parts = line.removePrefix("图片：").split("|").map { it.trim() }
                attachments.add(
                    PendingAttachment(
                        kind = AttachmentKind.Image,
                        name = parts.getOrNull(0).orEmpty().ifBlank { "图片" },
                        uri = parts.getOrNull(1).orEmpty(),
                    ),
                )
            }
            line.startsWith("文件：") -> {
                val parts = line.removePrefix("文件：").split("|").map { it.trim() }
                attachments.add(
                    PendingAttachment(
                        kind = AttachmentKind.File,
                        name = parts.getOrNull(0).orEmpty().ifBlank { "文件" },
                        uri = parts.getOrNull(1).orEmpty(),
                    ),
                )
            }
        }
    }

    return UserMessagePresentation(
        body = body,
        attachments = attachments,
        skill = skill,
    )
}

private fun parseMessageBlocks(content: String): List<MessageTextBlock> {
    val blocks = mutableListOf<MessageTextBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    var codeLanguage: String? = null
    val code = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) blocks.add(MessageTextBlock(MessageBlockKind.Paragraph, text))
        paragraph.clear()
    }

    content.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("```")) {
            if (inCode) {
                blocks.add(MessageTextBlock(MessageBlockKind.Code, code.toString().trimEnd(), codeLanguage))
                code.clear()
                codeLanguage = null
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = line.removePrefix("```").trim().ifBlank { null }
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code.appendLine(rawLine)
            return@forEach
        }

        when {
            line.isBlank() -> flushParagraph()
            line == "---" || line == "***" -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Divider, ""))
            }
            line.startsWith("#") -> {
                flushParagraph()
                blocks.add(
                    MessageTextBlock(
                        MessageBlockKind.Heading,
                        line.trimStart('#').trim(),
                    ),
                )
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks.add(
                    MessageTextBlock(
                        MessageBlockKind.Quote,
                        line.removePrefix(">").trim(),
                    ),
                )
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Bullet, line.drop(2).trim()))
            }
            line.matches(Regex("""\d+[.)]\s+.*""")) -> {
                flushParagraph()
                val label = line.substringBefore(' ').trim()
                blocks.add(
                    MessageTextBlock(
                        MessageBlockKind.Numbered,
                        line.substringAfter(' ').trim(),
                        label,
                    ),
                )
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(rawLine)
            }
        }
    }

    if (inCode) {
        blocks.add(MessageTextBlock(MessageBlockKind.Code, code.toString().trimEnd(), codeLanguage))
    } else {
        flushParagraph()
    }

    return blocks.ifEmpty {
        listOf(MessageTextBlock(MessageBlockKind.Paragraph, content))
    }
}

private fun extractReferenceUrls(content: String): List<String> =
    Regex("""https?://[^\s)）\]】]+""")
        .findAll(content)
        .map { it.value.trimEnd('.', ',', '，', '。') }
        .distinct()
        .take(4)
        .toList()

private fun extractOutputMentions(content: String): List<String> =
    Regex(
        """(?i)([A-Za-z]:\\[^\n]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|pdf|csv)|/[^\s]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|pdf|csv)|[\w./-]+?\.(?:md|txt|json|html|htm|png|jpg|jpeg|webp|pdf|csv))""",
    )
        .findAll(content)
        .map { it.value.trimEnd('.', ',', '，', '。') }
        .distinct()
        .take(4)
        .toList()

private fun extractArtifactMetadata(content: String): List<ArtifactMetadata> =
    extractArtifactMetadataMarkers(content)

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    val fromQuery = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
    return fromQuery ?: uri.lastPathSegment?.substringAfterLast('/')
}

private fun loadAttachmentBitmap(context: Context, uri: Uri): Bitmap? =
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxSide = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = minOf(1f, 240f / maxSide)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
        }
    }.getOrNull()

private fun loadSkillOptions(context: Context): List<SkillOption> {
    val externalRoot = context.getExternalFilesDir(null)
    val roots = listOfNotNull(
        File(context.filesDir, "skills"),
        externalRoot?.let { File(it, "skills") },
        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mason"), "skills"),
    )
    return roots
        .distinctBy { it.absolutePath }
        .flatMap { root ->
            root.listFiles()
                ?.filter { it.isDirectory || it.isFile }
                ?.take(120)
                ?.mapNotNull { file -> file.toSkillOption() }
                .orEmpty()
        }
        .distinctBy { it.path }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun File.toSkillOption(): SkillOption? {
    val manifest = readSkillManifest()
    if (manifest?.enabled == false) return null
    val skillFile = if (isDirectory) {
        listOf("SKILL.md", "README.md", "skill.json")
            .map { File(this, it) }
            .firstOrNull { it.exists() && it.isFile }
    } else {
        this
    }
    return SkillOption(
        name = manifest?.name
            ?.takeIf { it.isNotBlank() }
            ?: skillFile?.readMarkdownHeading().orEmpty().ifBlank { nameWithoutExtension.ifBlank { name } },
        description = manifest?.description
            ?.takeIf { it.isNotBlank() }
            ?: skillFile?.readSkillDescription().orEmpty(),
        path = absolutePath,
    )
}

private fun File.readSkillManifest(): MasonSkillManifest? {
    val manifest = if (isDirectory) File(this, "skill.json") else takeIf { name == "skill.json" }
    if (manifest == null || !manifest.exists() || !manifest.isFile) return null
    return runCatching {
        SKILL_MANIFEST_JSON.decodeFromString(
            MasonSkillManifest.serializer(),
            manifest.readText(Charsets.UTF_8),
        )
    }.getOrNull()
}

private fun File.readMarkdownHeading(): String? =
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
            ?.take(120)
    }.getOrNull()

private fun formatDuration(processingMs: Long): String =
    if (processingMs < 1000L) {
        "${processingMs.coerceAtLeast(1L)} ms"
    } else {
        String.format(Locale.getDefault(), "%.1f 秒", processingMs / 1000f)
    }

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 Mason 回复"))
    }.onFailure { error ->
        Toast.makeText(context, "分享失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

private fun openArtifact(context: Context, artifact: ArtifactMetadata, edit: Boolean) {
    val file = File(artifact.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "文件不存在或无法打开", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(if (edit) Intent.ACTION_EDIT else Intent.ACTION_VIEW).apply {
        setDataAndType(file.toArtifactUri(context), artifact.mimeType.ifBlank { file.artifactMimeType() })
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

private fun shareArtifact(context: Context, artifact: ArtifactMetadata) {
    val file = File(artifact.path)
    if (!file.exists() || file.isDirectory) {
        Toast.makeText(context, "文件不存在或无法分享", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = artifact.mimeType.ifBlank { file.artifactMimeType() }
        putExtra(Intent.EXTRA_STREAM, file.toArtifactUri(context))
        putExtra(Intent.EXTRA_SUBJECT, artifact.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享 ${artifact.name}"))
    }.onFailure { error ->
        Toast.makeText(context, "分享失败：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

private fun File.toArtifactUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

private fun File.artifactMimeType(): String {
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
        else -> "*/*"
    }
}

private fun buildArtifactPreviewText(artifact: ArtifactMetadata): String {
    val file = File(artifact.path)
    if (!file.exists() || !file.isFile) {
        return "文件不存在或已被移动。\n\n路径：${artifact.path}"
    }
    if (!file.isArtifactTextLike()) {
        return "这个文件适合用本地应用打开预览。\n\n文件：${file.name}\n路径：${file.absolutePath}"
    }

    return runCatching {
        val text = file.readText(Charsets.UTF_8)
        if (text.length > 6000) text.take(6000) + "\n\n...已截取前 6000 字" else text
    }.getOrElse { error ->
        "读取预览失败：${error.message ?: error.javaClass.simpleName}\n\n路径：${file.absolutePath}"
    }
}

private fun File.isArtifactTextLike(): Boolean {
    return extension.lowercase(Locale.getDefault()) in setOf(
        "txt", "md", "markdown", "json", "yaml", "yml", "xml", "html", "htm",
        "csv", "log", "kt", "java", "py", "js", "ts", "css", "toml", "ini",
    )
}

private fun formatArtifactSize(bytes: Long): String {
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

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
