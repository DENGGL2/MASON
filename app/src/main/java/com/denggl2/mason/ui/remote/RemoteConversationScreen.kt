package com.denggl2.mason.ui.remote

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.WithRemoteMaterialResources
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.protocol.RemoteConversationActivity
import com.denggl2.mason.protocol.RemoteConversationActivityKind
import com.denggl2.mason.protocol.RemoteConversationActivityStatus
import com.denggl2.mason.protocol.RemoteConversationMessage
import com.denggl2.mason.protocol.RemoteConversationRole
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteModelOption
import com.denggl2.mason.protocol.RemotePermissionProfileOption
import com.denggl2.mason.protocol.RemoteReasoningEffortOption
import com.denggl2.mason.ui.chat.ActivitySummaryRow
import com.denggl2.mason.ui.chat.ArtifactImagePreviewDialog
import com.denggl2.mason.ui.chat.ArtifactPreviewDialog
import com.denggl2.mason.ui.chat.AttachmentKind
import com.denggl2.mason.ui.chat.AttachmentMenuRow
import com.denggl2.mason.ui.chat.ChatBackdropBlur
import com.denggl2.mason.ui.chat.ChatGlassDropdown
import com.denggl2.mason.ui.chat.ChatGlassMaterial
import com.denggl2.mason.ui.chat.ChatSurfaceRole
import com.denggl2.mason.ui.chat.ComposerIconButton
import com.denggl2.mason.ui.chat.ComposerPrimaryAction
import com.denggl2.mason.ui.chat.ComposerSendButton
import com.denggl2.mason.ui.chat.FormattedMessageText
import com.denggl2.mason.ui.chat.InputContextStrip
import com.denggl2.mason.ui.chat.LocalChatBackdropState
import com.denggl2.mason.ui.chat.SkillOption
import com.denggl2.mason.ui.chat.SkillPickerSheet
import com.denggl2.mason.ui.chat.blurLayerOuterEdgeFeather
import com.denggl2.mason.ui.chat.captureChatBackdrop
import com.denggl2.mason.ui.chat.masonGlassShadow
import com.denggl2.mason.ui.chat.rememberChatBackdropState
import com.denggl2.mason.ui.chat.shouldShowScrollToBottom
import com.denggl2.mason.ui.chat.isPreviewableImageArtifact
import com.denggl2.mason.ui.chat.openArtifact
import com.denggl2.mason.ui.chat.shareArtifact
import com.denggl2.mason.ui.theme.LocalInterfaceEffects
import com.denggl2.mason.ui.theme.ProgressiveBlurEdge
import com.denggl2.mason.ui.theme.progressiveEdgeBlur
import kotlinx.coroutines.launch

private val RemoteDetailTopFadeContentHeight = 30.dp

@Composable
fun RemoteConversationScreen(
    onBack: () -> Unit,
    viewModel: RemoteConversationViewModel = hiltViewModel(),
) {
    val strings = LocalRemoteStrings.current
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.detail
    val running = detail?.executionStatus == RemoteExecutionStatus.RUNNING
    val activities = detail?.activities.orEmpty()
    val visibleActivities = if (
        running && activities.none { it.status == RemoteConversationActivityStatus.RUNNING }
    ) {
        activities + RemoteConversationActivity(
            id = "active-response-fallback",
            kind = RemoteConversationActivityKind.OTHER,
            title = detail?.activeActivityTitle
                ?.takeIf(String::isNotBlank)
                ?: "正在处理",
            text = detail?.activeActivityText.orEmpty(),
            status = RemoteConversationActivityStatus.RUNNING,
        )
    } else {
        activities
    }
    val trailingAssistantMessage = detail?.messages?.lastOrNull()?.takeIf { message ->
        visibleActivities.isNotEmpty() && message.role == RemoteConversationRole.ASSISTANT
    }
    val messagesBeforeActivities = if (trailingAssistantMessage != null) {
        detail?.messages.orEmpty().dropLast(1)
    } else {
        detail?.messages.orEmpty()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollToBottomInProgress by remember { mutableStateOf(false) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safeDrawingPadding.calculateTopPadding()
    val bottomInset = safeDrawingPadding.calculateBottomPadding()
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val composerHeight = with(density) {
        if (composerHeightPx > 0) composerHeightPx.toDp() else bottomInset + 58.dp
    }
    val topFadeHeight = topInset + RemoteDetailTopFadeContentHeight
    val bottomFadeHeight = composerHeight
    val topFadeRevealDistancePx = with(density) { 24.dp.toPx() }
    val topFadeTarget = remember(listState, topFadeRevealDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / topFadeRevealDistancePx)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val topFadeProgress by animateFloatAsState(
        targetValue = topFadeTarget.value,
        animationSpec = tween(120),
        label = "remote_detail_top_fade",
    )
    val showScrollToBottom by remember(detail, listState, scrollToBottomInProgress) {
        derivedStateOf {
            shouldShowScrollToBottom(
                hasMessages = detail?.messages?.isNotEmpty() == true || visibleActivities.isNotEmpty(),
                canScrollForward = listState.canScrollForward,
                scrollInProgress = scrollToBottomInProgress,
            )
        }
    }
    val interfaceEffects = LocalInterfaceEffects.current
    val backdropState = rememberChatBackdropState(
        enabled = interfaceEffects.backdropBlurEnabled,
    )
    val pageBackground = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    var showSkillPicker by remember { mutableStateOf(false) }
    var expandedActivityIds by remember(detail?.conversation?.threadId) {
        mutableStateOf(emptySet<String>())
    }
    val selectedSkillUi = uiState.selectedSkill?.let { skill ->
        SkillOption(
            name = skill.displayName,
            description = skill.description,
            path = skill.path,
            invocationName = skill.name,
        )
    }
    val skillOptions = uiState.composerOptions.skills.map { skill ->
        SkillOption(
            name = skill.displayName,
            description = skill.description,
            path = skill.path,
            invocationName = skill.name,
        )
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addAttachment(AttachmentKind.Image, it.toString())
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addAttachment(AttachmentKind.File, it.toString())
        }
    }

    LaunchedEffect(
        detail?.messages?.size,
        detail?.messages?.lastOrNull()?.text,
        visibleActivities.size,
        visibleActivities.lastOrNull()?.status,
        visibleActivities.lastOrNull()?.text,
        composerHeightPx,
    ) {
        withFrameNanos { }
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0) listState.scrollToItem(target)
    }

    CompositionLocalProvider(LocalChatBackdropState provides backdropState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                )
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureChatBackdrop(backdropState),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    detail == null && uiState.errorMessage != null -> RemoteConversationError(
                        message = uiState.errorMessage.orEmpty(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    detail != null -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = topInset + 76.dp,
                                end = 16.dp,
                                bottom = composerHeight + 6.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (detail.hasEarlierMessages) {
                                item {
                                    Text(
                                        "仅显示最近 ${detail.messages.size} 条文字消息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (detail.messages.isEmpty() && visibleActivities.isEmpty()) {
                                item {
                                    Text(
                                        "此对话没有可显示的文字消息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 28.dp),
                                    )
                                }
                            } else {
                                items(messagesBeforeActivities) { message ->
                                    RemoteMessage(
                                        message = message,
                                        loadingAttachmentId = uiState.previewAttachmentId,
                                        onPreviewAttachment = viewModel::previewAttachment,
                                    )
                                }
                            }
                            if (visibleActivities.isNotEmpty()) {
                                item {
                                    RemoteProcessPanel(
                                        activities = visibleActivities,
                                        expandedActivityIds = expandedActivityIds,
                                        onToggle = { activityId ->
                                            expandedActivityIds = if (activityId in expandedActivityIds) {
                                                expandedActivityIds - activityId
                                            } else {
                                                expandedActivityIds + activityId
                                            }
                                        },
                                    )
                                }
                            }
                            trailingAssistantMessage?.let { message ->
                                item {
                                    RemoteMessage(
                                        message = message,
                                        loadingAttachmentId = uiState.previewAttachmentId,
                                        onPreviewAttachment = viewModel::previewAttachment,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (detail != null) {
                RemoteDetailEdgeFades(
                    state = backdropState,
                    backgroundColor = pageBackground,
                    topFadeHeight = topFadeHeight,
                    bottomFadeHeight = bottomFadeHeight,
                    topProgress = topFadeProgress,
                )
            }

            RemoteBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = topInset + 8.dp),
            )

            if (showScrollToBottom) {
                RemoteScrollToBottomButton(
                    onClick = {
                        scrollToBottomInProgress = true
                        scope.launch {
                            try {
                                val target = listState.layoutInfo.totalItemsCount - 1
                                if (target >= 0) listState.scrollToItem(target)
                            } finally {
                                scrollToBottomInProgress = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = composerHeight + 8.dp),
                )
            }

            if (detail != null) {
                RemoteConversationComposer(
                    state = uiState,
                    selectedSkill = selectedSkillUi,
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::sendMessage,
                    onInterrupt = viewModel::interrupt,
                    onAddImage = { imagePicker.launch(arrayOf("image/*")) },
                    onAddFile = { filePicker.launch(arrayOf("*/*")) },
                    onUseSkill = {
                        viewModel.refreshComposerOptions()
                        showSkillPicker = true
                    },
                    onRemoveAttachment = viewModel::removeAttachment,
                    onClearSkill = { viewModel.selectSkill(null) },
                    onSelectModel = viewModel::selectModel,
                    onSelectEffort = viewModel::selectReasoningEffort,
                    onSelectPermission = viewModel::selectPermissionProfile,
                    onRefreshOptions = viewModel::refreshComposerOptions,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { composerHeightPx = it.height },
                )
            }
        }
    }

    if (showSkillPicker) {
        SkillPickerSheet(
            skills = skillOptions,
            onDismiss = { showSkillPicker = false },
            onSelect = { selected ->
                viewModel.selectSkill(
                    uiState.composerOptions.skills.firstOrNull { it.path == selected.path },
                )
                showSkillPicker = false
            },
            title = "电脑 Skill",
            description = "${uiState.connectorName} · Codex",
            emptyText = if (uiState.isOptionsLoading) "正在读取电脑 Skill" else "电脑端暂无可用 Skill",
        )
    }

    uiState.previewArtifact?.let { artifact ->
        if (
            isPreviewableImageArtifact(
                artifact,
                platformSupportsAvif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        ) {
            ArtifactImagePreviewDialog(
                artifact = artifact,
                onDismiss = viewModel::dismissPreview,
                onShare = { shareArtifact(context, artifact, strings = strings) },
            )
        } else {
            ArtifactPreviewDialog(
                artifact = artifact,
                onDismiss = viewModel::dismissPreview,
                onOpen = { openArtifact(context, artifact, edit = false, strings = strings) },
                onEdit = { openArtifact(context, artifact, edit = true, strings = strings) },
                onShare = { shareArtifact(context, artifact, strings = strings) },
            )
        }
    }

}

@Composable
private fun RemoteScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteFloatingSurface(
        shape = CircleShape,
        cornerRadius = 24.dp,
        modifier = modifier.size(48.dp),
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = LocalRemoteStrings.current.t("回到最新消息"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RemoteConversationComposer(
    state: RemoteConversationUiState,
    selectedSkill: SkillOption?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onUseSkill: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onSelectPermission: (String?) -> Unit,
    onRefreshOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state.detail?.executionStatus == RemoteExecutionStatus.RUNNING
    val enabled = !state.isSubmitting && !running
    val hasInput = state.draft.isNotBlank() || state.attachments.isNotEmpty() || selectedSkill != null
    val currentModel = state.composerOptions.models.firstOrNull { it.id == state.selectedModelId }
    var expanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var restoreFocusAfterExpansion by remember { mutableStateOf(false) }
    var inputWasFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val inputKeyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(expanded, restoreFocusAfterExpansion) {
        if (expanded && restoreFocusAfterExpansion) {
            withFrameNanos { }
            inputFocusRequester.requestFocus()
            inputKeyboardController?.show()
            restoreFocusAfterExpansion = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            state.optionsErrorMessage?.takeIf(String::isNotBlank)?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "重试",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onRefreshOptions)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .height(40.dp)
                    .padding(bottom = 6.dp)
                    .zIndex(1f),
                contentPadding = PaddingValues(start = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    RemoteSelectorPill(
                        title = "模型",
                        label = currentModel?.displayName ?: "选择模型",
                        items = state.composerOptions.models.map { model ->
                            SelectorItem(model.id, model.displayName)
                        },
                        selectedId = state.selectedModelId,
                        onSelect = { id -> id?.let(onSelectModel) },
                    )
                }
                item {
                    RemoteSelectorPill(
                        title = "推理层级",
                        label = reasoningLabel(state.selectedReasoningEffort),
                        items = currentModel?.supportedReasoningEfforts.orEmpty().map { effort ->
                            SelectorItem(effort.id, reasoningLabel(effort.id))
                        },
                        selectedId = state.selectedReasoningEffort,
                        onSelect = { id -> id?.let(onSelectEffort) },
                    )
                }
                item {
                    RemoteSelectorPill(
                        title = "访问权限",
                        label = if (state.selectedPermissionProfileId == null) {
                            "选择权限"
                        } else {
                            permissionLabel(state.selectedPermissionProfileId)
                        },
                        items = state.composerOptions.permissionProfiles.map { profile ->
                            SelectorItem(
                                profile.id,
                                permissionLabel(profile.id),
                                profile.allowed,
                            )
                        },
                        selectedId = state.selectedPermissionProfileId,
                        onSelect = onSelectPermission,
                    )
                }
            }
            val panelShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .masonGlassShadow(cornerRadius = 18.dp)
                    .clip(panelShape)
                    .animateContentSize(animationSpec = tween(180)),
            ) {
                ChatGlassMaterial(
                    shape = panelShape,
                    cornerRadius = 18.dp,
                    role = ChatSurfaceRole.Large,
                    blur = ChatBackdropBlur.Soft,
                    refraction = true,
                    blurredAlpha = 0.80f,
                    fallbackAlpha = 0.99f,
                )
                Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                    if (expanded && (state.attachments.isNotEmpty() || selectedSkill != null)) {
                        InputContextStrip(
                            attachments = state.attachments,
                            selectedSkill = selectedSkill,
                            onRemoveAttachment = onRemoveAttachment,
                            onClearSkill = onClearSkill,
                        )
                        Spacer(Modifier.height(7.dp))
                    }

                    @Composable
                    fun AddButton() {
                        Box {
                            ComposerIconButton(
                                icon = Icons.Outlined.Add,
                                contentDescription = LocalRemoteStrings.current.t("添加"),
                                enabled = enabled,
                                selected = addMenuExpanded,
                                onClick = {
                                    if (addMenuExpanded) {
                                        addMenuExpanded = false
                                    } else {
                                        expanded = true
                                        addMenuExpanded = true
                                    }
                                },
                            )
                            ChatGlassDropdown(
                                expanded = addMenuExpanded,
                                onDismissRequest = { addMenuExpanded = false },
                                width = 180.dp,
                                cornerRadius = 14.dp,
                                alignEnd = false,
                            ) {
                                AttachmentMenuRow("添加图片") {
                                    addMenuExpanded = false
                                    onAddImage()
                                }
                                AttachmentMenuRow("添加文件") {
                                    addMenuExpanded = false
                                    onAddFile()
                                }
                                AttachmentMenuRow("使用 Skill") {
                                    addMenuExpanded = false
                                    onUseSkill()
                                }
                            }
                        }
                    }

                    @Composable
                    fun TextInput(modifier: Modifier, multiline: Boolean) {
                        val textIsBlank = state.draft.isBlank()
                        BasicTextField(
                            value = state.draft,
                            onValueChange = onDraftChange,
                            enabled = enabled,
                            modifier = modifier
                                .heightIn(min = if (multiline) 52.dp else 31.dp)
                                .then(
                                    if (multiline && textIsBlank) {
                                        Modifier.heightIn(max = 76.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 5.dp)
                                .focusRequester(inputFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        inputWasFocused = true
                                        if (!multiline) {
                                            restoreFocusAfterExpansion = true
                                            expanded = true
                                        }
                                    } else if (inputWasFocused) {
                                        inputWasFocused = false
                                        if (multiline && !restoreFocusAfterExpansion) {
                                            expanded = false
                                        }
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = !multiline,
                            maxLines = if (!multiline) 1 else if (textIsBlank) 3 else Int.MAX_VALUE,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    if (state.draft.isBlank()) {
                                        Text(
                                            when {
                                                running -> "电脑 Codex 正在执行"
                                                state.isSubmitting -> state.submittingLabel ?: "正在发送"
                                                else -> "向电脑 Codex 发送消息"
                                            },
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
                    }

                    if (expanded) {
                        TextInput(Modifier.fillMaxWidth(), multiline = true)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (expanded) 2.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AddButton()
                        if (!expanded) {
                            Spacer(Modifier.width(4.dp))
                            TextInput(Modifier.weight(1f), multiline = false)
                        }
                        Spacer(Modifier.width(7.dp))
                        ComposerSendButton(
                            action = when {
                                running && !state.isSubmitting -> ComposerPrimaryAction.Stop
                                enabled && hasInput -> ComposerPrimaryAction.Send
                                else -> ComposerPrimaryAction.Disabled
                            },
                            onSend = onSend,
                            onStop = onInterrupt,
                        )
                    }
                }
            }
    }
}

@Composable
private fun BoxScope.RemoteDetailEdgeFades(
    state: dev.chrisbanes.haze.HazeState?,
    backgroundColor: Color,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
    topProgress: Float,
) {
    val glassMaterialEnabled = LocalInterfaceEffects.current.glassMaterialEnabled
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(topFadeHeight)
            .graphicsLayer {
                alpha = topProgress
                translationY = -topFadeHeight.toPx() * (1f - topProgress)
            }
            .blurLayerOuterEdgeFeather(
                edge = ProgressiveBlurEdge.Bottom,
                featherHeight = 12.dp,
            )
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Top,
                backgroundColor = backgroundColor,
                blurRadius = 15.dp,
                smoothBoundary = true,
            )
            .background(
                Brush.verticalGradient(
                    colors = if (glassMaterialEnabled) {
                        listOf(
                            backgroundColor.copy(alpha = 0.10f),
                            backgroundColor.copy(alpha = 0.04f),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            backgroundColor.copy(alpha = 0.98f),
                            backgroundColor.copy(alpha = 0.72f),
                            Color.Transparent,
                        )
                    },
                ),
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bottomFadeHeight)
            .blurLayerOuterEdgeFeather(
                edge = ProgressiveBlurEdge.Top,
                featherHeight = 12.dp,
            )
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Bottom,
                backgroundColor = backgroundColor,
                blurRadius = 15.dp,
                smoothBoundary = true,
            )
            .background(
                Brush.verticalGradient(
                    colors = if (glassMaterialEnabled) {
                        listOf(
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.02f),
                            backgroundColor.copy(alpha = 0.08f),
                        )
                    } else {
                        listOf(
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.08f),
                            backgroundColor.copy(alpha = 0.28f),
                        )
                    },
                ),
            ),
    )
    if (!glassMaterialEnabled) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomFadeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.22f to backgroundColor.copy(alpha = 0.88f),
                        1f to backgroundColor,
                    ),
                ),
        )
    }
}

private data class SelectorItem(
    val id: String?,
    val label: String,
    val enabled: Boolean = true,
)

@Composable
private fun RemoteSelectorPill(
    title: String,
    label: String,
    items: List<SelectorItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "remote_selector_arrow",
    )
    Box {
        val pillShape = RoundedCornerShape(999.dp)
        RemoteFloatingSurface(
            shape = pillShape,
            cornerRadius = 17.dp,
            modifier = Modifier.height(34.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(enabled = items.isNotEmpty()) { expanded = !expanded }
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp).rotate(arrowRotation),
                )
            }
        }
        ChatGlassDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = when (title) {
                "模型", "访问权限" -> 150.dp
                else -> 156.dp
            },
            cornerRadius = 16.dp,
            alignEnd = false,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.enabled) {
                                expanded = false
                                onSelect(item.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.label,
                            color = if (item.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = LocalRemoteStrings.current.t("当前选项"),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteProcessPanel(
    activities: List<RemoteConversationActivity>,
    expandedActivityIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .animateContentSize(),
    ) {
        activities.forEachIndexed { index, activity ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.09f))
            }
            val canExpand = activity.text.isNotBlank()
            val expanded = activity.id in expandedActivityIds
            ActivitySummaryRow(
                icon = remoteActivityIcon(activity.kind),
                title = activity.title,
                status = remoteActivityStatusLabel(activity.status),
                active = activity.status == RemoteConversationActivityStatus.RUNNING,
                expanded = expanded,
                onClick = if (canExpand) { { onToggle(activity.id) } } else null,
            )
            if (expanded && canExpand) {
                Column(
                    modifier = Modifier.padding(
                        start = 43.dp,
                        top = 6.dp,
                        end = 12.dp,
                        bottom = 10.dp,
                    ),
                ) {
                    Text(
                        activity.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

private fun remoteActivityStatusLabel(status: RemoteConversationActivityStatus): String = when (status) {
    RemoteConversationActivityStatus.RUNNING -> "进行中"
    RemoteConversationActivityStatus.COMPLETED -> "完成"
    RemoteConversationActivityStatus.INTERRUPTED -> "已停止"
    RemoteConversationActivityStatus.FAILED -> "失败"
}

private fun remoteActivityIcon(kind: RemoteConversationActivityKind): ImageVector = when (kind) {
    RemoteConversationActivityKind.THINKING -> Icons.Outlined.Lightbulb
    RemoteConversationActivityKind.COMMAND -> Icons.Outlined.Terminal
    RemoteConversationActivityKind.WEB_SEARCH -> Icons.Outlined.Language
    RemoteConversationActivityKind.TOOL -> Icons.Outlined.Code
    RemoteConversationActivityKind.FILE_CHANGE -> Icons.Outlined.Edit
    RemoteConversationActivityKind.COMMENTARY -> Icons.Outlined.Description
    RemoteConversationActivityKind.PLAN -> Icons.Outlined.CheckCircle
    RemoteConversationActivityKind.IMAGE -> Icons.Outlined.Image
    RemoteConversationActivityKind.OTHER -> Icons.Outlined.Extension
}

@Composable
private fun RemoteMessage(
    message: RemoteConversationMessage,
    loadingAttachmentId: String?,
    onPreviewAttachment: (com.denggl2.mason.protocol.RemoteConversationAttachment) -> Unit,
) {
    val isUser = message.role == RemoteConversationRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .then(
                    if (isUser) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = if (isUser) 14.dp else 2.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormattedMessageText(
                    content = message.text,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
                message.attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                            .clickable(
                                enabled = loadingAttachmentId == null,
                                onClick = { onPreviewAttachment(attachment) },
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (attachment.kind == com.denggl2.mason.protocol.RemoteAttachmentKind.IMAGE) {
                                Icons.Outlined.Image
                            } else {
                                Icons.Outlined.Description
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                attachment.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "电脑文件 · ${formatRemoteAttachmentSize(attachment.sizeBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                        if (loadingAttachmentId == attachment.attachmentId) {
                            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = LocalRemoteStrings.current.t("从电脑读取并预览"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRemoteAttachmentSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(java.util.Locale.US, bytes / 1024.0 / 1024.0)
}

@Composable
private fun RemoteConversationError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("重试") }
    }
}

private fun reasoningLabel(value: String?): String = when (value?.lowercase()) {
    "none" -> "关闭"
    "minimal" -> "极低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    "max" -> "最高"
    "ultra" -> "超高"
    null, "" -> "默认推理"
    else -> value
}

private fun permissionLabel(value: String?): String {
    val normalized = value?.trim()?.removePrefix(":")?.lowercase()
    return when (normalized) {
        null, "" -> "未读取"
        "read-only", "readonly", "read_only" -> "请求批准"
        "workspace", "workspace-write", "workspacewrite", "workspace_write" -> "帮我批准"
        "danger-full-access", "dangerfullaccess", "danger_full_access" -> "完全访问权限"
        else -> value
    }
}
