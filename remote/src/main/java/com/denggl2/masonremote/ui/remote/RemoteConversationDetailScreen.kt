package com.denggl2.masonremote.ui.remote

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denggl2.masonremote.R
import com.denggl2.masonremote.ui.chat.ChatBackdropBlur
import com.denggl2.masonremote.ui.chat.ChatGlassControl
import com.denggl2.masonremote.ui.chat.ChatGlassDropdown
import com.denggl2.masonremote.ui.chat.ChatGlassMaterial
import com.denggl2.masonremote.ui.chat.ChatSurfaceRole
import com.denggl2.masonremote.ui.chat.LocalChatBackdropState
import com.denggl2.masonremote.ui.chat.captureChatBackdrop
import com.denggl2.masonremote.ui.chat.glassClickable
import com.denggl2.masonremote.ui.chat.masonGlassShadow
import com.denggl2.masonremote.ui.chat.rememberChatBackdropState
import com.denggl2.masonremote.ui.theme.LocalInterfaceEffects
import com.denggl2.masonremote.ui.theme.MASON_SHEET_SCRIM_ALPHA
import com.denggl2.masonremote.ui.theme.MasonSheetShape
import com.denggl2.masonremote.ui.theme.masonOverlayWindowInsets
import com.denggl2.masonremote.ui.theme.masonSheetContainerColor
import com.denggl2.masonremote.ui.theme.masonSheetSurface
import com.denggl2.masonremote.data.AndroidDeviceIdentityStore
import com.denggl2.masonremote.diagnostics.DiagnosticLog
import com.denggl2.masonremote.transport.ConnectorConversationActivityKind
import com.denggl2.masonremote.transport.ConnectorConversationActivityPayload
import com.denggl2.masonremote.transport.ConnectorConversationActivityStatus
import com.denggl2.masonremote.transport.ConnectorConversationAttachmentPayload
import com.denggl2.masonremote.transport.ConnectorApprovalRequest
import com.denggl2.masonremote.transport.ConnectorConversationDetailPayload
import com.denggl2.masonremote.transport.ConnectorConversationRole
import com.denggl2.masonremote.transport.ConnectorAttachmentKind
import com.denggl2.masonremote.transport.ConnectorComposerOptions
import com.denggl2.masonremote.transport.ConnectorModelOption
import com.denggl2.masonremote.transport.ConnectorMessageRequest
import com.denggl2.masonremote.transport.ConnectorPermissionProfileOption
import com.denggl2.masonremote.transport.ConnectorReasoningEffortOption
import com.denggl2.masonremote.transport.ConnectorSkillOption
import com.denggl2.masonremote.transport.ConnectorSkillSelection
import com.denggl2.masonremote.transport.ConnectorExecutionStatus
import com.denggl2.masonremote.transport.ConnectorMessageDeliveryMode
import com.denggl2.masonremote.transport.PairedConnector
import com.denggl2.masonremote.transport.RemoteConnectorClient
import com.denggl2.masonremote.transport.displayName
import com.denggl2.masonremote.transport.isCloudXVisible
import com.denggl2.masonremote.ui.settings.RemoteMessageSendMode
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.WithRemoteMaterialResources
import com.denggl2.masonremote.ui.RemoteStrings
import com.denggl2.masonremote.ui.localizedDuration
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DetailMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val attachments: List<ConnectorConversationAttachmentPayload> = emptyList(),
)

private enum class DetailActivityKind {
    THINKING,
    COMMAND,
    WEB_SEARCH,
    TOOL,
    FILE_CHANGE,
    COMMENTARY,
    PLAN,
    IMAGE,
    OTHER,
}

private enum class DetailActivityStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

private data class DetailActivity(
    val id: String,
    val kind: DetailActivityKind,
    val title: String,
    val text: String,
    val status: DetailActivityStatus,
    val command: String? = null,
    val output: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

private data class DetailTranscriptItem(
    val id: String,
    val message: DetailMessage? = null,
    val activity: DetailActivity? = null,
    val activityGroup: List<DetailActivity>? = null,
)

private data class DetailDemo(
    val messages: List<DetailMessage>,
    val activities: List<DetailActivity>,
    val running: Boolean,
    val executionStatus: ConnectorExecutionStatus? = null,
    val activeActivityTitle: String? = null,
    val activeActivityText: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMillis: Long? = null,
)

private data class DetailSelectorItem(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
)

private data class PendingDetailAttachment(
    val kind: ConnectorAttachmentKind,
    val name: String,
    val uri: Uri,
    val mimeType: String?,
)

private val DetailTopFadeHeight = 30.dp
private val DetailComposerSeparation = 28.dp
private val DetailActivityToggleHeight = 52.dp
private const val MaxVisibleDetailActivities = 9
private const val MaxVisibleDetailMessages = 40
private const val MaxDetailMessageCharacters = 4_000
private const val MaxPendingDetailAttachments = 5
private const val MaxDetailAttachmentBytes = 20L * 1024L * 1024L
private const val MaxRemoteFilePreviewBytes = 1L * 1024L * 1024L
private const val DETAIL_ACTIVE_POLL_INTERVAL_MILLIS = 750L
private const val DETAIL_IDLE_POLL_INTERVAL_MILLIS = 1_500L

private val RemoteCodePreviewExtensions = setOf(
    "bash",
    "bat",
    "c",
    "cc",
    "conf",
    "cpp",
    "cs",
    "css",
    "csv",
    "dockerfile",
    "gradle",
    "h",
    "hpp",
    "htm",
    "html",
    "ini",
    "java",
    "js",
    "json",
    "jsx",
    "kt",
    "kts",
    "less",
    "log",
    "md",
    "mjs",
    "properties",
    "ps1",
    "py",
    "rb",
    "rs",
    "scss",
    "sh",
    "sql",
    "swift",
    "toml",
    "ts",
    "tsx",
    "txt",
    "xml",
    "yaml",
    "yml",
    "zsh",
)

private val RemoteCodePreviewMimeTypes = setOf(
    "application/graphql",
    "application/javascript",
    "application/json",
    "application/sql",
    "application/typescript",
    "application/x-javascript",
    "application/x-sh",
    "application/x-yaml",
    "application/xml",
    "application/yaml",
)

@Composable
internal fun RemoteConversationDetailScreen(
    threadId: String,
    pairedConnector: PairedConnector? = null,
    defaultMessageSendMode: RemoteMessageSendMode = RemoteMessageSendMode.QUEUE,
    draft: String = "",
    onDraftChange: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(threadId) {
        DiagnosticLog.record("DETAIL_SCREEN_OPEN threadId=$threadId")
    }
    val context = LocalContext.current
    val strings = LocalRemoteStrings.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val remoteClient = remember(pairedConnector) {
        pairedConnector?.let {
            RemoteConnectorClient(it, AndroidDeviceIdentityStore(), context.applicationContext)
        }
    }
    val agentLabel = pairedConnector?.agentKind?.displayName()?.let(strings::displayText) ?: strings.t("远程 Agent")
    var remoteDetail by remember(threadId) { mutableStateOf<ConnectorConversationDetailPayload?>(null) }
    var remoteError by remember(threadId) { mutableStateOf<String?>(null) }
    var loadingAttachmentId by remember(threadId) { mutableStateOf<String?>(null) }
    var previewImage by remember(threadId) { mutableStateOf<RemotePreviewImage?>(null) }
    var fileAttachment by remember(threadId) { mutableStateOf<ConnectorConversationAttachmentPayload?>(null) }
    var filePreviewState by remember(threadId) {
        mutableStateOf<RemoteFilePreviewState>(RemoteFilePreviewState.Unsupported)
    }
    var commandActivity by remember(threadId) { mutableStateOf<DetailActivity?>(null) }
    var fileDownloadBusy by remember(threadId) { mutableStateOf(false) }
    var fileDownloadError by remember(threadId) { mutableStateOf<String?>(null) }
    var composerOptions by remember(threadId) { mutableStateOf<ConnectorComposerOptions?>(null) }
    var optionsError by remember(threadId) { mutableStateOf<String?>(null) }
    var optionsLoading by remember(threadId) { mutableStateOf(false) }
    LaunchedEffect(remoteClient, threadId) {
        if (remoteClient == null || threadId.startsWith("demo-")) return@LaunchedEffect
        while (isActive) {
            var nextPollDelayMillis = DETAIL_IDLE_POLL_INTERVAL_MILLIS
            runCatching {
                remoteClient.readConversation(checkNotNull(pairedConnector).deviceId, threadId)
            }.onSuccess {
                DiagnosticLog.record(
                    "DETAIL_READ_SUCCESS threadId=$threadId messages=${it.messages.size} activities=${it.activities.size} " +
                        "attachments=${it.messages.sumOf { message -> message.attachments.size }} status=${it.executionStatus}",
                )
                remoteDetail = it
                remoteError = null
                if (
                    it.executionStatus == ConnectorExecutionStatus.RUNNING ||
                    it.executionStatus == ConnectorExecutionStatus.WAITING_FOR_APPROVAL
                ) {
                    nextPollDelayMillis = DETAIL_ACTIVE_POLL_INTERVAL_MILLIS
                }
            }.onFailure {
                DiagnosticLog.recordException("DETAIL_READ_FAILURE threadId=$threadId", it)
                if (remoteDetail == null) {
                    remoteError = strings.displayText(it.message ?: strings.t("无法读取电脑端会话"))
                }
            }
            delay(nextPollDelayMillis)
        }
    }
    LaunchedEffect(remoteClient, threadId) {
        if (remoteClient == null || pairedConnector == null || threadId.startsWith("demo-")) return@LaunchedEffect
        optionsLoading = true
        runCatching {
            remoteClient.composerOptions(checkNotNull(pairedConnector).deviceId, threadId)
        }.onSuccess {
            DiagnosticLog.record("COMPOSER_OPTIONS_SUCCESS threadId=$threadId models=${it.models.size} skills=${it.skills.size}")
            composerOptions = it.copy(models = it.models.filter(ConnectorModelOption::isCloudXVisible))
            optionsError = null
        }.onFailure {
            DiagnosticLog.recordException("COMPOSER_OPTIONS_FAILURE threadId=$threadId", it)
            optionsError = it.message ?: strings.text("无法读取 $agentLabel 配置", "Unable to read $agentLabel options")
        }
        optionsLoading = false
    }
    var pendingApproval by remember(threadId) {
        mutableStateOf(
            if (threadId == "demo-waiting") {
                ConnectorApprovalRequest(
                    threadId = threadId,
                    requestId = "demo-approval",
                    method = "command/execute",
                    title = "权限确认",
                    detail = "电脑端请求执行一项需要确认的操作。",
                )
            } else {
                null
            },
        )
    }
    var approvalBusy by remember(threadId) { mutableStateOf(false) }
    var approvalError by remember(threadId) { mutableStateOf<String?>(null) }
    LaunchedEffect(remoteClient, pairedConnector, threadId) {
        if (remoteClient == null || pairedConnector == null || threadId.startsWith("demo-")) return@LaunchedEffect
        while (isActive) {
            runCatching {
                remoteClient.pendingApprovals(checkNotNull(pairedConnector).deviceId, threadId)
            }.onSuccess { approvals ->
                pendingApproval = approvals.firstOrNull()
                if (approvals.isEmpty()) approvalError = null
            }.onFailure { error ->
                approvalError = error.message ?: "无法读取电脑端确认请求"
            }
            delay(if (pendingApproval == null) 1_000L else 500L)
        }
    }
    val detailLoading = !threadId.startsWith("demo-") && remoteDetail == null && remoteError == null
    val demo = when {
        threadId.startsWith("demo-") -> remember(threadId) { demoRemoteDetail(threadId) }
        remoteDetail != null -> runCatching {
            remoteDetail!!.toDetailDemo()
        }.onFailure {
            DiagnosticLog.recordException("DETAIL_MODEL_CONVERSION_FAILURE threadId=$threadId", it)
        }.getOrElse {
            DetailDemo(
                messages = listOf(DetailMessage("remote-model-error", false, "电脑端记录暂时无法显示。")),
                activities = emptyList(),
                running = false,
            )
        }
        else -> DetailDemo(
            messages = remoteError?.let {
                listOf(DetailMessage("remote-error", false, it))
            }.orEmpty(),
            activities = emptyList(),
            running = false,
        )
    }
    var messages by remember(threadId) { mutableStateOf(demo.messages) }
    val demoOptions = remember(threadId) { demoComposerOptions() }
    val activeComposerOptions = composerOptions ?: if (threadId.startsWith("demo-")) demoOptions else ConnectorComposerOptions()
    var selectedModelId by remember(threadId) { mutableStateOf<String?>(null) }
    var selectedReasoningEffort by remember(threadId) { mutableStateOf<String?>(null) }
    var selectedPermissionProfileId by remember(threadId) { mutableStateOf<String?>(null) }
    var selectedSkill by remember(threadId) { mutableStateOf<ConnectorSkillOption?>(null) }
    var deliveryMode by remember(threadId, defaultMessageSendMode) {
        mutableStateOf(
            when (defaultMessageSendMode) {
                RemoteMessageSendMode.STEER -> ConnectorMessageDeliveryMode.STEER
                RemoteMessageSendMode.QUEUE -> ConnectorMessageDeliveryMode.QUEUE
            },
        )
    }
    var composerOptionsChanged by remember(threadId) { mutableStateOf(false) }
    var attachments by remember(threadId) { mutableStateOf(emptyList<PendingDetailAttachment>()) }
    var isSending by remember(threadId) { mutableStateOf(false) }
    var localSendingStartedAt by remember(threadId) { mutableStateOf<Long?>(null) }
    var isInterrupting by remember(threadId) { mutableStateOf(false) }
    var sendError by remember(threadId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollToBottomInProgress by remember(threadId) { mutableStateOf(false) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safeDrawingPadding.calculateTopPadding()
    var composerHostHeightPx by remember { mutableIntStateOf(0) }
    val composerHeight = if (composerHostHeightPx > 0) {
        with(androidx.compose.ui.platform.LocalDensity.current) { composerHostHeightPx.toDp() }
    } else {
        118.dp
    }
    val interfaceEffects = LocalInterfaceEffects.current
    val backdropState = rememberChatBackdropState(interfaceEffects.backdropBlurEnabled)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val detailRunning = demo.running || isSending
    val activityStartedAt = demo.startedAt ?: localSendingStartedAt
    val transcriptItems = buildDetailTranscript(messages, demo, isSending)
    val lastTranscriptItemIndex = transcriptItems.lastIndex
    val showScrollToBottom by remember(
        listState,
        transcriptItems.isNotEmpty(),
        scrollToBottomInProgress,
        imeBottom,
    ) {
        derivedStateOf {
            transcriptItems.isNotEmpty() &&
                (listState.canScrollForward || imeBottom > 0) &&
                !scrollToBottomInProgress
        }
    }
    LaunchedEffect(fileAttachment?.attachmentId, remoteClient, pairedConnector?.deviceId, threadId) {
        val attachment = fileAttachment ?: return@LaunchedEffect
        if (!isRemoteCodePreviewable(attachment) || attachment.sizeBytes > MaxRemoteFilePreviewBytes) {
            filePreviewState = RemoteFilePreviewState.Unsupported
            return@LaunchedEffect
        }
        val client = remoteClient
        val deviceId = pairedConnector?.deviceId
        if (client == null || deviceId.isNullOrBlank() || threadId.startsWith("demo-")) {
            filePreviewState = RemoteFilePreviewState.Unsupported
            return@LaunchedEffect
        }
        filePreviewState = RemoteFilePreviewState.Loading
        val attachmentId = attachment.attachmentId
        val previewState = try {
            withContext(Dispatchers.IO) {
                val bytes = client.downloadConversationAttachment(
                    deviceId = deviceId,
                    threadId = threadId,
                    attachmentId = attachmentId,
                )
                if (bytes.size.toLong() > MaxRemoteFilePreviewBytes) {
                    null
                } else {
                    decodeRemoteText(bytes)
                }
            }?.let(RemoteFilePreviewState::Code) ?: RemoteFilePreviewState.Unsupported
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            RemoteFilePreviewState.Unsupported
        }
        if (fileAttachment?.attachmentId == attachmentId) {
            filePreviewState = previewState
        }
    }

    LaunchedEffect(activeComposerOptions) {
        val model = activeComposerOptions.models.firstOrNull { it.id == selectedModelId }
            ?: activeComposerOptions.models.firstOrNull { it.id == activeComposerOptions.currentModelId }
            ?: activeComposerOptions.models.firstOrNull { it.isDefault }
            ?: activeComposerOptions.models.firstOrNull()
        if (selectedModelId == null || activeComposerOptions.models.none { it.id == selectedModelId }) {
            selectedModelId = model?.id
        }
        val selectedModel = model ?: activeComposerOptions.models.firstOrNull { it.id == selectedModelId }
        if (
            selectedReasoningEffort == null ||
                selectedModel?.supportedReasoningEfforts?.none { it.id == selectedReasoningEffort } == true
        ) {
            selectedReasoningEffort = activeComposerOptions.currentReasoningEffort
                ?.takeIf { effort -> selectedModel?.supportedReasoningEfforts?.any { it.id == effort } == true }
                ?: selectedModel?.defaultReasoningEffort
                    ?.takeIf { effort -> selectedModel.supportedReasoningEfforts.any { it.id == effort } }
                ?: selectedModel?.supportedReasoningEfforts?.firstOrNull()?.id
        }
        if (
            selectedPermissionProfileId == null ||
                activeComposerOptions.permissionProfiles.none { it.id == selectedPermissionProfileId && it.allowed }
        ) {
            selectedPermissionProfileId = activeComposerOptions.currentPermissionProfileId
                ?.takeIf { id -> activeComposerOptions.permissionProfiles.any { it.id == id && it.allowed } }
                ?: activeComposerOptions.permissionProfiles.firstOrNull { it.allowed }?.id
        }
        selectedSkill = selectedSkill?.let { skill ->
            activeComposerOptions.skills.firstOrNull { it.path == skill.path }
        }
    }

    fun addAttachment(kind: ConnectorAttachmentKind, uri: Uri) {
        if (attachments.size >= MaxPendingDetailAttachments) {
            sendError = strings.text(
                "每次最多添加 $MaxPendingDetailAttachments 个附件",
                "You can add up to $MaxPendingDetailAttachments attachments at a time",
            )
            return
        }
        if (attachments.any { it.uri == uri }) return
        val size = queryAttachmentSize(context, uri)
        if (size != null && size > MaxDetailAttachmentBytes) {
            sendError = strings.t("单个附件不能超过 20 MB")
            return
        }
        attachments = attachments + PendingDetailAttachment(
            kind = kind,
            name = queryAttachmentName(context, uri),
            uri = uri,
            mimeType = context.contentResolver.getType(uri),
        )
        sendError = null
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addAttachment(ConnectorAttachmentKind.IMAGE, it)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addAttachment(ConnectorAttachmentKind.FILE, it)
        }
    }

    LaunchedEffect(remoteDetail, remoteError) {
        if (!threadId.startsWith("demo-")) messages = demo.messages
    }

    LaunchedEffect(transcriptItems.size) {
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0) listState.scrollToItem(target)
    }

    LaunchedEffect(imeBottom > 0, composerHeight, lastTranscriptItemIndex) {
        if (imeBottom <= 0 || lastTranscriptItemIndex < 0) return@LaunchedEffect
        // adjustNothing keeps the window stable; after the IME settles, reveal the
        // latest message above the composer instead of leaving it under the selectors.
        delay(80L)
        listState.animateScrollToItem(lastTranscriptItemIndex)
    }

    LaunchedEffect(detailRunning) {
        if (!detailRunning) localSendingStartedAt = null
    }

    fun sendMessage(requestedDeliveryMode: ConnectorMessageDeliveryMode = deliveryMode) {
        val text = draft.trim()
        if (
            (text.isBlank() && attachments.isEmpty() && selectedSkill == null) ||
                isSending
        ) return
        val pendingAttachments = attachments
        val pendingSkill = selectedSkill
        val requestText = text.ifBlank { strings.t("已添加内容") }
        val effectiveDeliveryMode = requestedDeliveryMode
        localSendingStartedAt = System.currentTimeMillis()
        isSending = true
        messages = messages + DetailMessage(
            id = "user-${messages.size}-${requestText.hashCode()}",
            isUser = true,
            text = requestText,
        )
        sendError = null
        if (remoteClient == null || pairedConnector == null || threadId.startsWith("demo-")) {
            scope.launch {
                kotlinx.coroutines.delay(550)
                messages = messages + DetailMessage(
                    id = "assistant-${messages.size}",
                    isUser = false,
                    text = strings.t("已收到这条消息。电脑端返回内容后，会在这里显示完整结果。"),
                )
                onDraftChange("")
                attachments = emptyList()
                selectedSkill = null
                isSending = false
                localSendingStartedAt = null
            }
            return
        }
        scope.launch {
            runCatching {
                val uploadedAttachments = pendingAttachments.map { pending ->
                    remoteClient.uploadAttachment(
                        deviceId = pairedConnector.deviceId,
                        kind = pending.kind,
                        name = pending.name,
                        mimeType = pending.mimeType,
                        bytes = readAttachmentBytes(context, pending.uri),
                    )
                }
                remoteClient.sendMessage(
                    deviceId = pairedConnector.deviceId,
                    threadId = threadId,
                    request = ConnectorMessageRequest(
                        text = text,
                        attachmentIds = uploadedAttachments.map { it.attachmentId },
                        skill = pendingSkill?.let { ConnectorSkillSelection(it.name, it.path) },
                        modelId = selectedModelId.takeIf { composerOptionsChanged },
                        reasoningEffort = selectedReasoningEffort.takeIf { composerOptionsChanged },
                        permissionProfileId = selectedPermissionProfileId.takeIf { composerOptionsChanged },
                        deliveryMode = effectiveDeliveryMode,
                    ),
                )
            }.onSuccess {
                onDraftChange("")
                attachments = emptyList()
                selectedSkill = null
                composerOptionsChanged = false
                runCatching {
                    remoteClient.readConversation(pairedConnector.deviceId, threadId)
                }.onSuccess { remoteDetail = it }
                    .onFailure { remoteError = it.message ?: strings.t("消息已发送，但无法刷新对话") }
            }.onFailure {
                sendError = remoteSendErrorMessage(it)
            }
            isSending = false
        }
    }

    fun openRemoteImage(attachment: ConnectorConversationAttachmentPayload) {
        if (attachment.kind != ConnectorAttachmentKind.IMAGE || loadingAttachmentId != null) return
        val client = remoteClient
        val connector = pairedConnector
        if (client == null || connector == null || threadId.startsWith("demo-")) {
            Toast.makeText(context, strings.t("当前对话没有可读取的电脑端图片"), Toast.LENGTH_SHORT).show()
            return
        }
        loadingAttachmentId = attachment.attachmentId
        scope.launch {
            runCatching {
                client.downloadConversationAttachment(
                    deviceId = connector.deviceId,
                    threadId = threadId,
                    attachmentId = attachment.attachmentId,
                )
            }.onSuccess { bytes ->
                previewImage = RemotePreviewImage(
                    attachmentId = attachment.attachmentId,
                    name = attachment.name,
                    mimeType = attachment.mimeType,
                    bytes = bytes,
                )
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    strings.displayText("图片读取失败：${error.message ?: strings.t("电脑端附件不可用")}"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            loadingAttachmentId = null
        }
    }

    fun openRemoteFile(attachment: ConnectorConversationAttachmentPayload) {
        fileDownloadError = null
        filePreviewState = if (
            isRemoteCodePreviewable(attachment) && attachment.sizeBytes <= MaxRemoteFilePreviewBytes
        ) {
            RemoteFilePreviewState.Loading
        } else {
            RemoteFilePreviewState.Unsupported
        }
        fileAttachment = attachment
    }

    fun downloadRemoteFile(attachment: ConnectorConversationAttachmentPayload) {
        val client = remoteClient
        val connector = pairedConnector
        if (client == null || connector == null || threadId.startsWith("demo-")) {
            fileDownloadError = strings.t("当前对话没有可读取的电脑端文件")
            return
        }
        scope.launch {
            fileDownloadBusy = true
            fileDownloadError = null
            runCatching {
                val downloaded = client.downloadConversationAttachment(
                    deviceId = connector.deviceId,
                    threadId = threadId,
                    attachmentId = attachment.attachmentId,
                )
                saveRemoteConversationFile(context, attachment.name, attachment.mimeType, downloaded)
            }.onSuccess { path ->
                fileAttachment = null
                Toast.makeText(context, strings.displayText("已下载到 $path"), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                fileDownloadError = error.message ?: strings.t("文件下载失败")
            }
            fileDownloadBusy = false
        }
    }

    fun resolveApproval(decision: String) {
        val approval = pendingApproval ?: return
        if (threadId.startsWith("demo-")) {
            pendingApproval = null
            return
        }
        val client = remoteClient ?: return
        val connector = pairedConnector ?: return
        scope.launch {
            approvalBusy = true
            approvalError = null
            runCatching {
                client.resolveApproval(
                    deviceId = connector.deviceId,
                    threadId = threadId,
                    requestId = approval.requestId,
                    decision = decision,
                )
            }.onSuccess {
                pendingApproval = null
                runCatching { client.readConversation(connector.deviceId, threadId) }
                    .onSuccess { remoteDetail = it }
            }.onFailure { error ->
                approvalError = error.message ?: strings.t("无法提交确认结果")
            }
            approvalBusy = false
        }
    }

    fun interruptConversation() {
        val client = remoteClient ?: return
        val connector = pairedConnector ?: return
        if (threadId.startsWith("demo-") || isInterrupting) return
        scope.launch {
            isInterrupting = true
            runCatching {
                client.interruptConversation(
                    deviceId = connector.deviceId,
                    threadId = threadId,
                )
            }.onSuccess {
                runCatching { client.readConversation(connector.deviceId, threadId) }
                    .onSuccess { remoteDetail = it }
            }.onFailure { error ->
                sendError = error.message ?: strings.t("无法停止电脑端任务")
            }
            isInterrupting = false
        }
    }

    CompositionLocalProvider(LocalChatBackdropState provides backdropState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureChatBackdrop(backdropState)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Final,
                            )
                            val up = waitForUpOrCancellation(PointerEventPass.Final)
                            val tappedTranscript = up?.let { change ->
                                listState.layoutInfo.visibleItemsInfo.any { item ->
                                    change.position.y >= item.offset &&
                                        change.position.y < item.offset + item.size
                                }
                            } == true
                            if (up != null && !up.isConsumed && !tappedTranscript) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = topInset + 76.dp,
                        end = 16.dp,
                        bottom = composerHeight + DetailComposerSeparation,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!detailLoading) {
                        transcriptItems.forEach { transcriptItem ->
                            item(key = transcriptItem.id) {
                                transcriptItem.activity?.let { activity ->
                                    DetailTranscriptActivityRow(
                                        activity = activity,
                                        onOpenCommand = { commandActivity = it },
                                    )
                                }
                                transcriptItem.activityGroup?.let { activities ->
                                    DetailTranscriptActivityGroup(
                                        activities = activities,
                                        running = detailRunning,
                                        executionStatus = demo.executionStatus,
                                        startedAt = activityStartedAt,
                                        durationMillis = demo.durationMillis,
                                        onOpenCommand = { commandActivity = it },
                                    )
                                }
                                transcriptItem.message?.let { message ->
                                    if (transcriptItem.activity != null || transcriptItem.activityGroup != null) {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    DetailMessageRow(
                                        message = message,
                                        deviceId = pairedConnector?.deviceId,
                                        loadingAttachmentId = loadingAttachmentId,
                                        remoteClient = remoteClient,
                                        threadId = threadId,
                                        onPreviewAttachment = ::openRemoteImage,
                                        onOpenFile = ::openRemoteFile,
                                    )
                                }
                            }
                        }
                    }
                    sendError?.let { error ->
                        item(key = "send-error") {
            Text(
                text = strings.displayText("发送失败：$error"),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }

            if (!detailLoading) {
                DetailEdgeFades(
                    topInset = topInset,
                    bottomHeight = composerHeight,
                    background = MaterialTheme.colorScheme.background,
                )
            }

            RemoteBackButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp, top = topInset + 8.dp),
            )

            if (showScrollToBottom) {
                DetailScrollToBottomButton(
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
                        .padding(end = 8.dp, bottom = composerHeight + DetailComposerSeparation),
                )
            }

            if (detailLoading) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + 8.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.7.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = strings.t("加载中"),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .union(WindowInsets.ime)
                                    .only(WindowInsetsSides.Bottom),
                            )
                        // The composer host owns the IME inset. The list uses its
                        // measured height as bottom content padding.
                        .onSizeChanged { composerHostHeightPx = it.height },
                ) {
                    DetailComposer(
                        draft = draft,
                        model = activeComposerOptions.models.firstOrNull { it.id == selectedModelId },
                        agentLabel = agentLabel,
                        reasoning = selectedReasoningEffort,
                        permission = selectedPermissionProfileId,
                        options = activeComposerOptions,
                        optionsLoading = optionsLoading,
                        optionsError = optionsError,
                        attachments = attachments,
                        selectedSkill = selectedSkill,
                        pendingApproval = pendingApproval,
                        approvalBusy = approvalBusy,
                        approvalError = approvalError,
                        onResolveApproval = ::resolveApproval,
                        running = detailRunning,
                        sending = isSending,
                        onDraftChange = onDraftChange,
                        onSend = ::sendMessage,
                        onInterrupt = ::interruptConversation,
                        interrupting = isInterrupting,
                        onAddImage = { imagePicker.launch(arrayOf("image/*")) },
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onRemoveAttachment = { index -> attachments = attachments.filterIndexed { i, _ -> i != index } },
                        onClearSkill = { selectedSkill = null },
                        onSkillSelected = { selectedSkill = it },
                        onModelChange = { id ->
                            composerOptionsChanged = true
                            selectedModelId = id
                            val model = activeComposerOptions.models.firstOrNull { it.id == id }
                            selectedReasoningEffort = model?.defaultReasoningEffort
                                ?.takeIf { effort -> model.supportedReasoningEfforts.any { it.id == effort } }
                                ?: model?.supportedReasoningEfforts?.firstOrNull()?.id
                        },
                        onReasoningChange = {
                            composerOptionsChanged = true
                            selectedReasoningEffort = it
                        },
                        onPermissionChange = {
                            composerOptionsChanged = true
                            selectedPermissionProfileId = it
                        },
                    )
                }
            }
            previewImage?.let { image ->
                RemoteImagePreviewDialog(
                    image = image,
                    onDismiss = { previewImage = null },
                    onShare = { shareRemoteImage(context, image, strings) },
                )
            }
            fileAttachment?.let { attachment ->
                RemoteFileAttachmentSheet(
                    attachment = attachment,
                    previewState = filePreviewState,
                    downloadBusy = fileDownloadBusy,
                    downloadError = fileDownloadError,
                    onDismiss = { if (!fileDownloadBusy) fileAttachment = null },
                    onDownload = { downloadRemoteFile(attachment) },
                )
            }
            commandActivity?.let { activity ->
                DetailCommandSheet(activity = activity, onDismiss = { commandActivity = null })
            }
        }
    }
}

@Composable
private fun DetailMessageRow(
    message: DetailMessage,
    deviceId: String?,
    loadingAttachmentId: String?,
    remoteClient: RemoteConnectorClient?,
    threadId: String,
    onPreviewAttachment: (ConnectorConversationAttachmentPayload) -> Unit,
    onOpenFile: (ConnectorConversationAttachmentPayload) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .then(
                    if (message.isUser) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                )
            .padding(horizontal = if (message.isUser) 14.dp else 2.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.text.isNotBlank()) RemoteMarkdown(message.text)
                val imageAttachments = message.attachments.filter {
                    it.kind == ConnectorAttachmentKind.IMAGE
                }
                if (imageAttachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = imageAttachments,
                            key = { attachment -> "image-${attachment.attachmentId}" },
                        ) { attachment ->
                            RemoteConversationImageThumbnail(
                                attachment = attachment,
                                deviceId = deviceId,
                                loading = loadingAttachmentId == attachment.attachmentId,
                                onClick = { onPreviewAttachment(attachment) },
                                remoteClient = remoteClient,
                                threadId = threadId,
                            )
                        }
                    }
                }
                message.attachments.filterNot {
                    it.kind == ConnectorAttachmentKind.IMAGE
                }.forEach { attachment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                            .clickable(onClick = { onOpenFile(attachment) })
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                attachment.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "电脑文件 · ${formatRemoteAttachmentSize(attachment.sizeBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTranscriptActivityRow(
    activity: DetailActivity,
    onOpenCommand: (DetailActivity) -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val canOpenCommand = activity.kind == DetailActivityKind.COMMAND &&
        (!activity.command.isNullOrBlank() || !activity.output.isNullOrBlank())
    val hasDetails = activity.text.isNotBlank() || canOpenCommand
    var expanded by remember(activity.id) {
        mutableStateOf(activity.status != DetailActivityStatus.COMPLETED && hasDetails)
    }
    LaunchedEffect(activity.status) {
        if (activity.status == DetailActivityStatus.RUNNING) expanded = true
    }
    val statusColor = when (activity.status) {
        DetailActivityStatus.FAILED -> MaterialTheme.colorScheme.error
        DetailActivityStatus.INTERRUPTED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DetailActivityToggleHeight)
                .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activity.status == DetailActivityStatus.RUNNING) {
                        RemoteShimmerStatusText(
                            text = detailActivityDisplayTitle(activity),
                            baseColor = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Text(
                            detailActivityDisplayTitle(activity),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (
                            activity.status == DetailActivityStatus.FAILED ||
                                activity.status == DetailActivityStatus.INTERRUPTED
                        ) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                detailActivityStatusLabel(activity.status),
                                color = statusColor,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    activity.durationMillis()?.let { durationMillis ->
                        Spacer(Modifier.width(7.dp))
                        Text(
                            strings.localizedDuration(durationMillis),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            if (hasDetails) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        strings.text(
                            "收起${detailActivityDisplayTitle(activity)}",
                            "Collapse ${strings.displayText(detailActivityDisplayTitle(activity))}",
                        )
                    } else {
                        strings.text(
                            "展开${detailActivityDisplayTitle(activity)}",
                            "Expand ${strings.displayText(detailActivityDisplayTitle(activity))}",
                        )
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = expandVertically(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(150)),
        ) {
            Column(
                modifier = Modifier.padding(start = 26.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (canOpenCommand) {
                    DetailCommandOutputLink(
                        activity = activity,
                        onOpenCommand = onOpenCommand,
                    )
                } else {
                    activity.text.takeIf(String::isNotBlank)?.let { text ->
                        RemoteMarkdown(text, compact = true)
                    }
                }
            }
        }
    }
}

private fun DetailActivity.durationMillis(): Long? =
    startedAt?.let { started ->
        completedAt?.let { completed -> (completed - started).coerceAtLeast(0L) }
    }

@Composable
internal fun RemoteShimmerStatusText(
    text: String,
    baseColor: Color,
    fontSize: TextUnit = 11.sp,
    lineHeight: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "detail-status-shimmer")
    val sweep by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350),
            repeatMode = RepeatMode.Restart,
        ),
        label = "detail-status-shimmer-position",
    )
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.45f),
                    baseColor,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f),
                    baseColor,
                    baseColor.copy(alpha = 0.45f),
                ),
                start = androidx.compose.ui.geometry.Offset(sweep * 72f - 34f, 0f),
                end = androidx.compose.ui.geometry.Offset(sweep * 72f + 34f, 0f),
            ),
        ),
    )
}

private fun detailActivityKindLabel(kind: DetailActivityKind): String = when (kind) {
    DetailActivityKind.THINKING -> "思考"
    DetailActivityKind.COMMAND -> "执行"
    DetailActivityKind.WEB_SEARCH -> "搜索"
    DetailActivityKind.TOOL -> "工具"
    DetailActivityKind.FILE_CHANGE -> "修改"
    DetailActivityKind.COMMENTARY -> "说明"
    DetailActivityKind.PLAN -> "计划"
    DetailActivityKind.IMAGE -> "图片"
    DetailActivityKind.OTHER -> "其他"
}

private fun normalizeDetailActivityTitle(title: String): String {
    val normalized = title.trim()
    return when (normalized) {
        "命令执行", "执行代码", "执行过程", "执行" -> "运行命令"
        "终端 Web 搜索", "网络搜索", "网页搜索", "搜索网页" -> "搜索网页"
        "工具调用", "调用工具" -> "调用工具"
        "修改文件" -> "修改文件"
        "图片预览", "生成图片", "生成图像" -> "生成图像"
        "查看图片", "查看图像" -> "查看图像"
        "正在组织回复" -> "组织回复"
        "上下文压缩中", "压缩上下文", "上下文压缩" -> "上下文压缩"
        "其他操作", "正在处理", "处理中", "执行说明", "说明" -> ""
        "连接任务" -> "连接"
        "恢复任务" -> "恢复"
        else -> normalized
    }
}

private fun buildDetailTranscript(
    messages: List<DetailMessage>,
    demo: DetailDemo,
    isSending: Boolean,
): List<DetailTranscriptItem> {
    val sourceActivities = demo.activities
        .filterNot { it.kind == DetailActivityKind.COMMENTARY }
        .filterNot(::isPlaceholderDetailActivity)
    val liveActivity = detailLiveActivity(sourceActivities, demo, isSending)
    val visibleActivities = sourceActivities
        .map { activity ->
            if (liveActivity != null && activity.id == liveActivity.id) liveActivity else activity
        }
        .let { activities ->
            if (liveActivity != null && activities.none { it.id == liveActivity.id }) {
                activities + liveActivity
            } else {
                activities
            }
        }
        .takeLast(MaxVisibleDetailActivities)
    val activityItems = buildList {
        if (visibleActivities.isNotEmpty()) {
            add(
                DetailTranscriptItem(
                    id = "activity-group",
                    activityGroup = visibleActivities,
                ),
            )
        }
    }
    val items = buildList {
        if (demo.running || isSending) {
            // While active, keep the live execution group below every message.
            messages.forEach { message ->
                add(DetailTranscriptItem(id = "message-${message.id}", message = message))
            }
            addAll(activityItems)
        } else {
            // Once complete, place the execution group before the final result.
            val lastUserIndex = messages.indexOfLast { it.isUser }
            var activitiesInserted = false
            messages.forEachIndexed { index, message ->
                add(DetailTranscriptItem(id = "message-${message.id}", message = message))
                if (!activitiesInserted && index == lastUserIndex && activityItems.isNotEmpty()) {
                    addAll(activityItems)
                    activitiesInserted = true
                }
            }
            if (!activitiesInserted) {
                addAll(activityItems)
            }
        }
    }
    return items
}

private fun detailLiveActivity(
    activities: List<DetailActivity>,
    demo: DetailDemo,
    isSending: Boolean,
): DetailActivity? {
    if (!demo.running && !isSending) return null
    val current = activities.lastOrNull { it.status == DetailActivityStatus.RUNNING }
    val liveTitle = demo.activeActivityTitle
        ?.let(::normalizeDetailActivityTitle)
        ?.takeIf(::isUsefulDetailActivityTitle)
    val liveText = demo.activeActivityText
        ?.trim()
        ?.takeIf(::isUsefulDetailActivityText)
    if (current != null) {
        val currentTitle = normalizeDetailActivityTitle(current.title)
            .takeIf(::isUsefulDetailActivityTitle)
        val currentText = current.text.takeIf(::isUsefulDetailActivityText)
        return current.copy(
            title = liveTitle ?: currentTitle ?: detailActivityFallbackTitle(current.kind),
            text = liveText ?: currentText.orEmpty(),
        )
    }
    return DetailActivity(
        id = "activity-live-progress",
        kind = DetailActivityKind.OTHER,
        title = liveTitle ?: if (isSending) "发送消息" else if (liveText != null) "任务进展" else "等待电脑反馈",
        text = liveText ?: if (isSending) "正在等待电脑端响应" else "正在等待电脑端活动更新",
        status = DetailActivityStatus.RUNNING,
    )
}

private fun isUsefulDetailActivityTitle(title: String): Boolean =
    title.isNotBlank()

private fun isUsefulDetailActivityText(text: String): Boolean =
    text.isNotBlank() && text !in setOf(
        "正在处理请求",
        "电脑端正在执行任务。",
        "电脑端正在执行任务",
        "处理中",
        "正在处理",
    )

private fun isPlaceholderDetailActivity(activity: DetailActivity): Boolean =
    activity.kind == DetailActivityKind.OTHER &&
        !isUsefulDetailActivityTitle(normalizeDetailActivityTitle(activity.title)) &&
        !isUsefulDetailActivityText(activity.text) &&
        activity.command.isNullOrBlank() &&
        activity.output.isNullOrBlank()

private fun detailActivityFallbackTitle(kind: DetailActivityKind): String = when (kind) {
    DetailActivityKind.THINKING -> "思考"
    DetailActivityKind.COMMAND -> "运行命令"
    DetailActivityKind.WEB_SEARCH -> "搜索网页"
    DetailActivityKind.TOOL -> "调用工具"
    DetailActivityKind.FILE_CHANGE -> "修改文件"
    DetailActivityKind.COMMENTARY -> "说明"
    DetailActivityKind.PLAN -> "更新计划"
    DetailActivityKind.IMAGE -> "图像"
    DetailActivityKind.OTHER -> "任务进展"
}

@Composable
private fun DetailTranscriptActivityGroup(
    activities: List<DetailActivity>,
    running: Boolean,
    executionStatus: ConnectorExecutionStatus?,
    startedAt: Long?,
    durationMillis: Long?,
    onOpenCommand: (DetailActivity) -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val hasDetails = activities.any {
        isUsefulDetailActivityText(it.text) ||
            !it.command.isNullOrBlank() ||
            !it.output.isNullOrBlank()
    } || activities.any { it.status == DetailActivityStatus.RUNNING }
    var expanded by remember(activities.map(DetailActivity::id)) { mutableStateOf(running) }
    LaunchedEffect(running) {
        expanded = running
    }
    val status = when {
        running || executionStatus == ConnectorExecutionStatus.RUNNING ||
            executionStatus == ConnectorExecutionStatus.WAITING_FOR_APPROVAL ->
            DetailActivityStatus.RUNNING
        executionStatus == ConnectorExecutionStatus.COMPLETED -> DetailActivityStatus.COMPLETED
        executionStatus == ConnectorExecutionStatus.INTERRUPTED -> DetailActivityStatus.INTERRUPTED
        executionStatus == ConnectorExecutionStatus.FAILED -> DetailActivityStatus.FAILED
        activities.any { it.status == DetailActivityStatus.FAILED } -> DetailActivityStatus.FAILED
        activities.any { it.status == DetailActivityStatus.INTERRUPTED } -> DetailActivityStatus.INTERRUPTED
        activities.any { it.status == DetailActivityStatus.RUNNING } -> DetailActivityStatus.RUNNING
        else -> DetailActivityStatus.COMPLETED
    }
    val fallbackStartedAt = remember(status) { System.currentTimeMillis() }
    val effectiveStartedAt = startedAt ?: fallbackStartedAt
    var nowMillis by remember(effectiveStartedAt, status) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(effectiveStartedAt, status) {
        if (status != DetailActivityStatus.RUNNING) {
            nowMillis = System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val runningDurationMillis = if (status == DetailActivityStatus.RUNNING) {
        (nowMillis - effectiveStartedAt).coerceAtLeast(0L)
    } else {
        null
    }
    val currentActivity = activities.lastOrNull { it.status == DetailActivityStatus.RUNNING }
    val previousActivities = activities.filter { it.status != DetailActivityStatus.RUNNING }
    val displayedDurationMillis = if (status == DetailActivityStatus.RUNNING) {
        null
    } else {
        durationMillis ?: activities
            .mapNotNull { activity ->
                val started = activity.startedAt ?: return@mapNotNull null
                val completed = activity.completedAt ?: return@mapNotNull null
                started to completed
            }
            .takeIf { it.isNotEmpty() }
            ?.let { ranges ->
                (ranges.maxOf { it.second } - ranges.minOf { it.first }).coerceAtLeast(0L)
            }
    }
    val runningStatusTextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val statusColor = if (status == DetailActivityStatus.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 4.dp, end = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DetailActivityToggleHeight)
                .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status == DetailActivityStatus.RUNNING) {
                    RemoteShimmerStatusText(
                        text = "${strings.t("进行中")} · ${strings.localizedDuration(runningDurationMillis ?: 0L)}",
                        baseColor = MaterialTheme.colorScheme.onSurface,
                        fontSize = runningStatusTextStyle.fontSize,
                        lineHeight = runningStatusTextStyle.lineHeight,
                        modifier = Modifier.alignBy(FirstBaseline),
                    )
                } else {
                    Text(
                        detailActivityStatusLabel(status),
                        color = if (status == DetailActivityStatus.FAILED) {
                            statusColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    displayedDurationMillis?.let { duration ->
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "${strings.t("耗时")}${strings.localizedDuration(duration)}",
                            color = statusColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (!expanded) {
                        Spacer(Modifier.weight(1f))
                        val commandCount = activities.count { it.kind == DetailActivityKind.COMMAND }
                        Text(
                            strings.text(
                                "已执行${commandCount}条",
                                "Executed $commandCount ${if (commandCount == 1) "item" else "items"}",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            if (hasDetails) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = strings.text(
                        if (expanded) "收起执行详情" else "展开执行详情",
                        if (expanded) "Collapse execution details" else "Expand execution details",
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = expandVertically(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(150)),
        ) {
            Column(
                modifier = Modifier.padding(start = 26.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currentActivity?.let { activity ->
                    DetailTranscriptCurrentActivity(
                        activity = activity,
                        onOpenCommand = onOpenCommand,
                    )
                }
                previousActivities.forEach { activity ->
                    DetailTranscriptActivityDetail(
                        activity = activity,
                        onOpenCommand = onOpenCommand,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailTranscriptCurrentActivity(
    activity: DetailActivity,
    onOpenCommand: (DetailActivity) -> Unit,
) {
    val title = detailActivityDisplayTitle(activity)
    val command = activity.command?.trim()?.takeIf(String::isNotBlank)
    val detail = activity.text.trim().takeIf(String::isNotBlank)
    val canOpenCommand = activity.kind == DetailActivityKind.COMMAND &&
        (!activity.command.isNullOrBlank() || !activity.output.isNullOrBlank())
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        RemoteShimmerStatusText(
            text = title,
            baseColor = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        command?.let { value ->
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canOpenCommand) {
            DetailCommandOutputLink(
                activity = activity,
                onOpenCommand = onOpenCommand,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        } else {
            detail?.takeIf { it != command }?.let { value ->
                RemoteMarkdown(value, compact = true)
            }
        }
    }
}

@Composable
private fun DetailTranscriptActivityDetail(
    activity: DetailActivity,
    onOpenCommand: (DetailActivity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            detailActivityDisplayTitle(activity),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (activity.hasCommandDetails()) {
            DetailCommandOutputLink(
                activity = activity,
                onOpenCommand = onOpenCommand,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        } else {
            activity.text.takeIf(::isUsefulDetailActivityText)?.let { text ->
                RemoteMarkdown(text, compact = true)
            }
        }
    }
}

private fun DetailActivity.hasCommandDetails(): Boolean =
    kind == DetailActivityKind.COMMAND &&
        (!command.isNullOrBlank() || !output.isNullOrBlank())

private fun DetailActivity.commandOutputPreview(): String? {
    if (!hasCommandDetails()) return null
    val commandText = command?.trim().orEmpty()
    val outputText = output?.trim().orEmpty()
    val activityText = text.trim()
    return when {
        isUsefulDetailActivityText(activityText) && activityText != commandText -> activityText
        outputText.isNotBlank() -> outputText
        commandText.isNotBlank() -> "命令输出"
        else -> null
    }
}

@Composable
private fun DetailCommandOutputLink(
    activity: DetailActivity,
    onOpenCommand: (DetailActivity) -> Unit,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 19.sp,
) {
    val strings = LocalRemoteStrings.current
    val output = activity.commandOutputPreview() ?: return
    DetailDashedLink(
        text = strings.content(output),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = 3,
        onClick = { onOpenCommand(activity) },
    )
}

@Composable
private fun DetailDashedLink(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 11.sp,
    lineHeight: TextUnit = 14.sp,
    maxLines: Int = 1,
) {
    val linkColor = color
    Text(
        text = text,
        color = linkColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(bottom = 2.dp)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = linkColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                    ),
                )
            }
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailCommandSheet(activity: DetailActivity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalRemoteStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    WithRemoteMaterialResources {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = masonSheetContainerColor(),
        shape = MasonSheetShape,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_SHEET_SCRIM_ALPHA),
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
            )
            Text(strings.t("命令详情"), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            activity.command?.takeIf(String::isNotBlank)?.let { command ->
                DetailCommandSheetBlock(
                    title = strings.t("命令"),
                    value = command,
                    onCopy = {
                        copyRemoteCommand(context, strings.t("命令"), command, strings)
                    },
                )
            }
            activity.output?.takeIf(String::isNotBlank)?.let { output ->
                DetailCommandSheetBlock(
                    title = strings.t("输出"),
                    value = output,
                    scroll = true,
                    onCopy = {
                        copyRemoteCommand(context, strings.t("输出"), output, strings)
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun DetailCommandSheetBlock(
    title: String,
    value: String,
    scroll: Boolean = false,
    onCopy: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
            ChatGlassControl(
                onClick = onCopy,
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                cornerRadius = 15.dp,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = LocalRemoteStrings.current.text(
                        "复制${LocalRemoteStrings.current.displayText(title)}",
                        "Copy ${LocalRemoteStrings.current.displayText(title)}",
                    ),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        val valueModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
        if (scroll) {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = valueModifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            )
        } else {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = valueModifier,
            )
        }
    }
}

private fun copyRemoteCommand(context: Context, label: String, value: String, strings: RemoteStrings) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "${strings.t("已复制")}$label", Toast.LENGTH_SHORT).show()
}

@Composable
private fun RemoteConversationImageThumbnail(
    attachment: ConnectorConversationAttachmentPayload,
    deviceId: String?,
    loading: Boolean,
    onClick: () -> Unit,
    remoteClient: RemoteConnectorClient?,
    threadId: String,
) {
    val thumbnailState by produceState<RemoteThumbnailState>(
        initialValue = RemoteThumbnailState.Loading,
        key1 = remoteClient,
        key2 = deviceId,
        key3 = "$threadId/${attachment.attachmentId}",
    ) {
        val client = remoteClient
        val connectorId = deviceId
        if (client == null || connectorId.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                RemoteThumbnailState.Ready(
                    decodeRemoteBitmap(
                        client.downloadConversationAttachment(
                            deviceId = connectorId,
                            threadId = threadId,
                            attachmentId = attachment.attachmentId,
                        ),
                        maxEdge = 320,
                    ),
                )
            }.getOrElse { error ->
                RemoteThumbnailState.Failed(error.message ?: "图片无法预览")
            }
        }
    }
    val bitmap = (thumbnailState as? RemoteThumbnailState.Ready)?.bitmap
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .clickable(
                enabled = !loading && thumbnailState is RemoteThumbnailState.Ready,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (thumbnailState) {
            RemoteThumbnailState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
            is RemoteThumbnailState.Ready -> Image(
                bitmap = checkNotNull(bitmap).asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            is RemoteThumbnailState.Failed -> Icon(
                Icons.Outlined.Image,
                contentDescription = LocalRemoteStrings.current.t("图片预览失败"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(22.dp),
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private sealed interface RemoteThumbnailState {
    data object Loading : RemoteThumbnailState
    data class Ready(val bitmap: Bitmap) : RemoteThumbnailState
    data class Failed(val message: String) : RemoteThumbnailState
}

private sealed interface RemoteFilePreviewState {
    data object Loading : RemoteFilePreviewState
    data object Unsupported : RemoteFilePreviewState
    data class Code(val content: String) : RemoteFilePreviewState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteFileAttachmentSheet(
    attachment: ConnectorConversationAttachmentPayload,
    previewState: RemoteFilePreviewState,
    downloadBusy: Boolean,
    downloadError: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    WithRemoteMaterialResources {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = masonSheetContainerColor(),
        shape = MasonSheetShape,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_SHEET_SCRIM_ALPHA),
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 650.dp)
                .masonSheetSurface()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (previewState is RemoteFilePreviewState.Code) {
                        Icons.Outlined.Code
                    } else {
                        Icons.Outlined.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.t("文件预览"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ChatGlassControl(
                    onClick = onDismiss,
                    enabled = !downloadBusy,
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    cornerRadius = 17.dp,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = strings.t("关闭文件预览"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = attachment.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings.displayText("电脑文件 · ${formatRemoteAttachmentSize(attachment.sizeBytes)}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                when (previewState) {
                    RemoteFilePreviewState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 1.6.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = strings.t("正在读取文件"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    RemoteFilePreviewState.Unsupported -> Text(
                        text = strings.t("无法预览"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    is RemoteFilePreviewState.Code -> {
                        val verticalScrollState = rememberScrollState()
                        val horizontalScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = previewState.content.ifEmpty { strings.t("（空文件）") },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            downloadError?.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChatGlassControl(
                onClick = onDownload,
                enabled = !downloadBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                cornerRadius = 12.dp,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (downloadBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 1.7.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = strings.t(if (downloadBusy) "下载中" else "下载"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DetailComposer(
    draft: String,
    model: ConnectorModelOption?,
    agentLabel: String,
    reasoning: String?,
    permission: String?,
    options: ConnectorComposerOptions,
    optionsLoading: Boolean,
    optionsError: String?,
    attachments: List<PendingDetailAttachment>,
    selectedSkill: ConnectorSkillOption?,
    pendingApproval: ConnectorApprovalRequest?,
    approvalBusy: Boolean,
    approvalError: String?,
    onResolveApproval: (String) -> Unit,
    running: Boolean,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    interrupting: Boolean,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
    onSkillSelected: (ConnectorSkillOption) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningChange: (String) -> Unit,
    onPermissionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalRemoteStrings.current
    var addMenuExpanded by remember { mutableStateOf(false) }
    var skillMenuExpanded by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val canSend = !sending && (draft.isNotBlank() || attachments.isNotEmpty() || selectedSkill != null)
    val showStop = running && !canSend && !sending
    val composerActionBackground = if (canSend || showStop) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    val composerActionContent = if (canSend || showStop) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        optionsError?.takeIf(String::isNotBlank)?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (running) {
            pendingApproval?.let { approval ->
                PermissionApprovalFloatingBar(
                    approval = approval,
                    busy = approvalBusy,
                    error = approvalError,
                    onResolve = onResolveApproval,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .height(40.dp)
                .padding(bottom = 6.dp),
            contentPadding = PaddingValues(start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                DetailSelectorPill(
                    label = model?.displayName ?: if (optionsLoading) "读取中" else "选择模型",
                    items = options.models.map { DetailSelectorItem(it.id, it.displayName) },
                    selectedId = model?.id,
                    onSelect = { onModelChange(it) },
                )
            }
            item {
                DetailSelectorPill(
                    label = remoteReasoningLabel(reasoning),
                    items = model?.supportedReasoningEfforts?.map {
                        DetailSelectorItem(it.id, remoteReasoningLabel(it.id))
                    }.orEmpty(),
                    selectedId = reasoning,
                    onSelect = { onReasoningChange(it) },
                )
            }
            item {
                DetailSelectorPill(
                    label = remotePermissionLabel(permission),
                    items = options.permissionProfiles.map {
                        DetailSelectorItem(it.id, remotePermissionLabel(it.id), it.allowed)
                    },
                    selectedId = permission,
                    onSelect = { onPermissionChange(it) },
                )
            }
        }

        if (attachments.isNotEmpty() || selectedSkill != null) {
            Spacer(Modifier.height(3.dp))
            // Keep context chips outside the glass composer so their surface does
            // not create a second blurred layer over the input panel.
            DetailComposerContextStrip(
                attachments = attachments,
                selectedSkill = selectedSkill,
                onRemoveAttachment = onRemoveAttachment,
                onClearSkill = onClearSkill,
            )
            Spacer(Modifier.height(3.dp))
        }

        val panelShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .masonGlassShadow(cornerRadius = 18.dp)
                .clip(panelShape),
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
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .masonGlassShadow(cornerRadius = 20.dp, blurRadius = 10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .glassClickable { addMenuExpanded = !addMenuExpanded },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = strings.t("添加"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ChatGlassDropdown(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            width = 160.dp,
                            cornerRadius = 30.dp,
                            alignEnd = false,
                            forceOpenAbove = true,
                            preserveInputFocus = true,
                        ) {
                            DetailComposerMenuRow("添加图片") {
                                addMenuExpanded = false
                                onAddImage()
                            }
                            DetailComposerMenuRow("添加文件") {
                                addMenuExpanded = false
                                onAddFile()
                            }
                            DetailComposerMenuRow("使用 Skill") {
                                addMenuExpanded = false
                                skillMenuExpanded = true
                            }
                        }
                        ChatGlassDropdown(
                            expanded = skillMenuExpanded,
                            onDismissRequest = { skillMenuExpanded = false },
                            width = 250.dp,
                            cornerRadius = 30.dp,
                            alignEnd = false,
                            forceOpenAbove = true,
                            preserveInputFocus = true,
                        ) {
                            when {
                                optionsLoading -> Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "正在读取远程 Skill",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                    )
                                }
                                options.skills.isEmpty() -> Text(
                                    "远程端暂无可用 Skill",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                )
                                else -> options.skills.forEach { skill ->
                                    DetailSkillMenuRow(skill) {
                                        skillMenuExpanded = false
                                        onSkillSelected(skill)
                                    }
                                }
                            }
                        }
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !sending,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 31.dp, max = 100.dp)
                            .focusRequester(inputFocusRequester)
                            .padding(horizontal = 7.dp, vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                if (draft.isBlank()) {
                                    Text(
                                        text = strings.displayText(
                                            if (running) "当前任务进行中，可插入或排队" else "向 $agentLabel 发送消息",
                                        ),
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .masonGlassShadow(cornerRadius = 20.dp, blurRadius = 10.dp)
                            .clip(CircleShape)
                            .background(composerActionBackground)
                            .glassClickable(
                                enabled = if (showStop) !interrupting else canSend,
                                onClick = if (showStop) onInterrupt else onSend,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showStop) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                            contentDescription = strings.t(if (showStop) "停止" else "发送"),
                            tint = composerActionContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailComposerContextStrip(
    attachments: List<PendingDetailAttachment>,
    selectedSkill: ConnectorSkillOption?,
    onRemoveAttachment: (Int) -> Unit,
    onClearSkill: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        selectedSkill?.let { skill ->
            item(key = "skill-${skill.path}") {
                DetailContextChip(
                    icon = Icons.Outlined.Extension,
                    label = skill.displayName,
                    onRemove = onClearSkill,
                )
            }
        }
        itemsIndexed(attachments, key = { _, attachment -> attachment.uri.toString() }) { index, attachment ->
            DetailContextChip(
                icon = if (attachment.kind == ConnectorAttachmentKind.IMAGE) {
                    Icons.Outlined.Image
                } else {
                    Icons.Outlined.Description
                },
                label = attachment.name,
                onRemove = { onRemoveAttachment(index) },
            )
        }
    }
}

@Composable
private fun DetailContextChip(
    icon: ImageVector,
    label: String,
    onRemove: () -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), shape)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 176.dp),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .glassClickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = strings.t("移除"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun DetailSkillMenuRow(skill: ConnectorSkillOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (skill.description.isNotBlank()) {
                Text(
                    text = skill.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailSelectorPill(
    label: String,
    items: List<DetailSelectorItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val strings = LocalRemoteStrings.current
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "remote_detail_selector_arrow",
    )
    Box {
        val shape = RoundedCornerShape(999.dp)
        RemoteFloatingSurface(
            shape = shape,
            cornerRadius = 17.dp,
            modifier = Modifier.height(34.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .glassClickable(enabled = items.isNotEmpty()) { expanded = !expanded }
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = strings.displayText(label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp).rotate(arrowRotation),
                )
            }
        }
        ChatGlassDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = 150.dp,
            cornerRadius = 30.dp,
            alignEnd = false,
            forceOpenAbove = true,
            preserveInputFocus = true,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassClickable(enabled = item.enabled) {
                                expanded = false
                                onSelect(item.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.label,
                            color = if (item.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.id == selectedId) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = strings.t("当前选项"),
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
private fun DetailComposerMenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
private fun BoxScope.DetailEdgeFades(
    topInset: Dp,
    bottomHeight: Dp,
    background: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(topInset + DetailTopFadeHeight)
            .background(
                Brush.verticalGradient(
                    listOf(background.copy(alpha = 0.96f), background.copy(alpha = 0.45f), Color.Transparent),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bottomHeight)
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, background.copy(alpha = 0.10f), background.copy(alpha = 0.32f)),
                ),
            ),
    )
}

@Composable
private fun PermissionApprovalFloatingBar(
    approval: ConnectorApprovalRequest,
    busy: Boolean,
    error: String?,
    onResolve: (String) -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val barShape = RoundedCornerShape(18.dp)
    RemoteFloatingSurface(
        shape = barShape,
        cornerRadius = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .height(48.dp),
        largeSurface = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.displayText(
                    error?.takeIf(String::isNotBlank)
                        ?: approval.title.ifBlank { "权限确认" },
                ),
                color = if (error.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !busy,
                onClick = { onResolve("decline") },
                modifier = Modifier.height(36.dp),
            ) { Text(strings.t("拒绝"), fontSize = 13.sp) }
            MasonBlackConfirmButton(
                label = strings.t("允许"),
                enabled = !busy,
                busy = busy,
                onClick = { onResolve("accept") },
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

@Composable
private fun DetailScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalRemoteStrings.current
    RemoteFloatingSurface(
        shape = CircleShape,
        cornerRadius = 24.dp,
        modifier = modifier.size(48.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = strings.t("回到最新消息"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun detailActivityStatusLabel(status: DetailActivityStatus): String = when (status) {
    DetailActivityStatus.IDLE -> "空闲"
    DetailActivityStatus.RUNNING -> "进行中"
    DetailActivityStatus.COMPLETED -> "已完成"
    DetailActivityStatus.INTERRUPTED -> "已停止"
    DetailActivityStatus.FAILED -> "失败"
}

private fun detailActivityDisplayTitle(activity: DetailActivity): String =
    when (activity.kind) {
        DetailActivityKind.THINKING -> "思考"
        DetailActivityKind.COMMAND -> normalizeDetailActivityTitle(activity.title).ifBlank { "运行命令" }
        DetailActivityKind.WEB_SEARCH -> "搜索网页"
        DetailActivityKind.TOOL -> "调用工具"
        DetailActivityKind.FILE_CHANGE -> "修改文件"
        DetailActivityKind.COMMENTARY -> "执行说明"
        DetailActivityKind.PLAN -> "更新计划"
        DetailActivityKind.IMAGE -> normalizeDetailActivityTitle(activity.title).ifBlank { "图像" }
        DetailActivityKind.OTHER -> normalizeDetailActivityTitle(activity.title)
            .ifBlank { detailActivityKindLabel(activity.kind) }
    }

private fun detailActivityInlineText(activity: DetailActivity): String {
    val title = detailActivityDisplayTitle(activity)
    val detail = activity.text.trim()
    return if (detail.isBlank() || detail == title) title else "$title  $detail"
}

private fun compactDetailActivityTitle(title: String): String {
    return normalizeDetailActivityTitle(title)
}

private fun isRemoteCodePreviewable(attachment: ConnectorConversationAttachmentPayload): Boolean {
    val mimeType = attachment.mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
    val fileName = attachment.name.substringAfterLast('/').lowercase(Locale.ROOT)
    val extension = fileName.substringAfterLast('.', "")
    return mimeType?.startsWith("text/") == true ||
        mimeType in RemoteCodePreviewMimeTypes ||
        extension in RemoteCodePreviewExtensions ||
        fileName == "dockerfile"
}

private fun decodeRemoteText(bytes: ByteArray): String? {
    if (bytes.any { it == 0.toByte() }) return null
    return runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
    }.getOrNull()
}

private fun formatRemoteAttachmentSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / 1024.0 / 1024.0)
}

private fun formatRemoteDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "%d时%02d分".format(Locale.CHINA, hours, minutes % 60L)
        minutes > 0 -> "%d分%02d秒".format(Locale.CHINA, minutes, seconds)
        else -> "%d秒".format(Locale.CHINA, seconds)
    }
}

private fun demoComposerOptions(): ConnectorComposerOptions = ConnectorComposerOptions(
    models = listOf(
        ConnectorModelOption(
            id = "demo-codex",
            model = "codex",
            displayName = "Codex",
            isDefault = true,
            defaultReasoningEffort = "medium",
            supportedReasoningEfforts = listOf(
                ConnectorReasoningEffortOption("low"),
                ConnectorReasoningEffortOption("medium"),
                ConnectorReasoningEffortOption("high"),
            ),
        ),
    ),
    permissionProfiles = listOf(
        ConnectorPermissionProfileOption(
            id = "workspace",
            description = "允许在工作区内读写",
            allowed = true,
        ),
        ConnectorPermissionProfileOption(
            id = "read-only",
            description = "仅读取，不修改文件",
            allowed = true,
        ),
    ),
    currentModelId = "demo-codex",
    currentReasoningEffort = "medium",
    currentPermissionProfileId = "workspace",
)

private fun remoteReasoningLabel(value: String?): String = when (value?.lowercase()) {
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

private fun remotePermissionLabel(value: String?): String = when (
    value?.trim()?.removePrefix(":")?.lowercase()
) {
    "read-only", "readonly", "read_only" -> "请求批准"
    "workspace", "workspace-write", "workspacewrite", "workspace_write" -> "帮我批准"
    "danger-full-access", "danger_full_access", "full-access", "full_access" -> "完全访问权限"
    null, "" -> "未读取"
    else -> value.removePrefix(":")
}

private fun queryAttachmentName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotBlank) else null
    }
}.getOrNull()
    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    ?: "附件"

private fun queryAttachmentSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
}.getOrNull()

private suspend fun readAttachmentBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MaxDetailAttachmentBytes) error("单个附件不能超过 20 MB")
            output.write(buffer, 0, count)
        }
    } ?: error("无法读取附件")
    output.toByteArray()
}

private suspend fun saveRemoteConversationFile(
    context: Context,
    name: String,
    mimeType: String?,
    bytes: ByteArray,
): String = withContext(Dispatchers.IO) {
    val displayName = name.trim().ifBlank { "remote-attachment-${System.currentTimeMillis()}" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val resolvedMimeType = mimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
        ?: "application/octet-stream"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, resolvedMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建下载文件")
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("无法写入下载文件")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            "下载/${displayName}"
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    } else {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val target = File(directory, displayName)
        target.writeBytes(bytes)
        target.absolutePath
    }
}

private fun ConnectorConversationDetailPayload.toDetailDemo(): DetailDemo {
    val detailActivities = activities
        .map(ConnectorConversationActivityPayload::toDetailActivity)
        .filterNot { it.kind == DetailActivityKind.COMMENTARY }
    val currentDetailActivity = detailActivities.lastOrNull {
        it.status == DetailActivityStatus.RUNNING
    }
    val safeActiveTitle = currentDetailActivity?.title ?: activeActivityTitle
    val safeActiveText = activeActivityText
    return DetailDemo(
        messages = messages
            .takeLast(MaxVisibleDetailMessages)
            .mapIndexed { index, message ->
            DetailMessage(
                id = "remote-message-$index",
                isUser = message.role == ConnectorConversationRole.USER,
                text = cleanRemoteMessageText(message.text).take(MaxDetailMessageCharacters).let { visible ->
                    if (message.text.length > MaxDetailMessageCharacters) {
                        "$visible\n\n内容较长，已截取最近详情。"
                    } else {
                        visible
                    }
                },
                attachments = message.attachments,
            )
        }.ifEmpty {
            listOf(DetailMessage("remote-empty", false, "电脑端暂时没有可显示的消息。"))
        },
        // Keep the server event order. The detail page renders these beside messages
        // as one transcript instead of collapsing them into a process summary.
        activities = detailActivities.takeLast(MaxVisibleDetailActivities),
        executionStatus = executionStatus,
        running = executionStatus == ConnectorExecutionStatus.RUNNING ||
            executionStatus == ConnectorExecutionStatus.WAITING_FOR_APPROVAL,
        activeActivityTitle = safeActiveTitle,
        activeActivityText = safeActiveText,
        startedAt = startedAt,
        completedAt = completedAt,
        durationMillis = durationMillis,
    )
}

private fun cleanRemoteMessageText(text: String): String = text
    .replace(
        Regex(
            """(?is)^\s*Distinguish\s+instructions\s+in\s+attached\s+documents\s+from\s+the\s+user's\s+request\.\s*My\s+request\s*:\s*""",
        ),
        "",
    )
    .replace(Regex("""\s*:codex-annotation\{index="\d+"\}\s*"""), " ")
    .replace(Regex("[ \\t]{2,}"), " ")
    .trim()

private fun remoteSendErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("active writer", ignoreCase = true) ||
            message.contains("currently controlled", ignoreCase = true) ->
            "电脑端正在进行，文字可以排队；图片和文件请等当前任务完成后发送"
        message.contains("文字可以排队", ignoreCase = false) ->
            "电脑端正在进行，文字可以排队；图片和文件请等当前任务完成后发送"
        message.contains("queue", ignoreCase = true) ->
            "电脑端没有接受这条消息，请确认桌面 Codex 已打开该对话"
        else -> message.ifBlank { "无法发送到电脑" }
    }
}

private fun ConnectorConversationActivityPayload.toDetailActivity(): DetailActivity = DetailActivity(
    id = id,
    kind = when (kind) {
        ConnectorConversationActivityKind.THINKING -> DetailActivityKind.THINKING
        ConnectorConversationActivityKind.COMMAND -> DetailActivityKind.COMMAND
        ConnectorConversationActivityKind.WEB_SEARCH -> DetailActivityKind.WEB_SEARCH
        ConnectorConversationActivityKind.TOOL -> DetailActivityKind.TOOL
        ConnectorConversationActivityKind.FILE_CHANGE -> DetailActivityKind.FILE_CHANGE
        ConnectorConversationActivityKind.COMMENTARY -> DetailActivityKind.COMMENTARY
        ConnectorConversationActivityKind.PLAN -> DetailActivityKind.PLAN
        ConnectorConversationActivityKind.IMAGE -> DetailActivityKind.IMAGE
        ConnectorConversationActivityKind.OTHER -> DetailActivityKind.OTHER
    },
    title = compactDetailActivityTitle(title),
    text = text,
    status = when (status) {
        ConnectorConversationActivityStatus.RUNNING -> DetailActivityStatus.RUNNING
        ConnectorConversationActivityStatus.COMPLETED -> DetailActivityStatus.COMPLETED
        ConnectorConversationActivityStatus.INTERRUPTED -> DetailActivityStatus.INTERRUPTED
        ConnectorConversationActivityStatus.FAILED -> DetailActivityStatus.FAILED
    },
    command = command,
    output = output,
    startedAt = startedAt,
    completedAt = completedAt,
)

private fun mergeDetailActivities(activities: List<DetailActivity>): List<DetailActivity> {
    if (activities.size < 2) return activities
    val merged = LinkedHashMap<String, DetailActivity>()
    activities.forEach { activity ->
        val key = "${activity.kind.name}:${activity.title.trim().lowercase(Locale.ROOT)}"
        val previous = merged[key]
        merged[key] = if (previous == null) {
            activity
        } else {
            previous.copy(
                // Keep one expandable row per process type and show the latest
                // text/status instead of repeating every streaming update.
                text = activity.text.ifBlank { previous.text },
                status = activity.status,
                command = activity.command ?: previous.command,
                output = activity.output ?: previous.output,
                startedAt = activity.startedAt ?: previous.startedAt,
                completedAt = activity.completedAt ?: previous.completedAt,
            )
        }
    }
    return merged.values.toList()
}

private fun demoRemoteDetail(threadId: String): DetailDemo = when (threadId) {
    "demo-waiting" -> DetailDemo(
        messages = listOf(
            DetailMessage("waiting-user", true, "运行需要确认的远程命令"),
            DetailMessage("waiting-assistant", false, "电脑端正在等待你的允许，确认后会继续执行。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "approval",
                kind = DetailActivityKind.COMMAND,
                title = "确认",
                text = "等待电脑端确认请求。",
                status = DetailActivityStatus.RUNNING,
            ),
        ),
        running = true,
        executionStatus = ConnectorExecutionStatus.WAITING_FOR_APPROVAL,
        startedAt = System.currentTimeMillis(),
    )
    "demo-statuses" -> DetailDemo(
        messages = listOf(
            DetailMessage("status-user", true, "把 Codex 的执行状态整理给我看"),
            DetailMessage("status-assistant", false, "过程会按事件顺序显示，进行中的任务直接跟随对应行更新。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "thinking-1",
                kind = DetailActivityKind.THINKING,
                title = "思考",
                text = "第一次思考更新：分析任务目标。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "thinking-2",
                kind = DetailActivityKind.THINKING,
                title = "思考",
                text = "第二次思考更新：确认执行步骤。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "commentary",
                kind = DetailActivityKind.COMMENTARY,
                title = "执行",
                text = "开始检查电脑端工作区。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "command-1",
                kind = DetailActivityKind.COMMAND,
                title = "执行",
                text = "第一条命令输出。",
                status = DetailActivityStatus.COMPLETED,
                command = "Get-ChildItem -Force",
                output = "README.md\napp\ndesktop-connector",
            ),
            DetailActivity(
                id = "command-2",
                kind = DetailActivityKind.COMMAND,
                title = "执行",
                text = "第二条命令输出：同类命令更新覆盖上一条。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "web-search",
                kind = DetailActivityKind.WEB_SEARCH,
                title = "搜索",
                text = "搜索结果已返回。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "tool-call",
                kind = DetailActivityKind.TOOL,
                title = "工具",
                text = "调用远程工具并等待结果。",
                status = DetailActivityStatus.RUNNING,
            ),
            DetailActivity(
                id = "file-change",
                kind = DetailActivityKind.FILE_CHANGE,
                title = "修改",
                text = "已写入目标文件。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "plan",
                kind = DetailActivityKind.PLAN,
                title = "计划",
                text = "计划已更新。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "image",
                kind = DetailActivityKind.IMAGE,
                title = "图片",
                text = "已生成图片预览。",
                status = DetailActivityStatus.COMPLETED,
            ),
            DetailActivity(
                id = "other",
                kind = DetailActivityKind.OTHER,
                title = "其他",
                text = "其他操作已完成。",
                status = DetailActivityStatus.COMPLETED,
            ),
        ),
        running = true,
        startedAt = System.currentTimeMillis(),
    )
    "demo-richtext" -> DetailDemo(
        messages = listOf(
            DetailMessage("richtext-user", true, "展示手机端富文本效果"),
            DetailMessage(
                "richtext-assistant",
                false,
                """# 任务结果

这是普通文本，支持 **加粗**、`灰底代码` 和 [可点击链接](https://github.com/openai/codex)。
这一行与上一行之间保留换行。

> 这是引用内容。

- 无序列表项目
- 第二个项目
1. 有序列表项目
2. 第二个项目
- [x] 已完成任务
- [ ] 待处理任务

| 状态 | 数量 |
| --- | --- |
| 完成 | 2 |
| 进行中 | 1 |

```powershell
Get-ChildItem -Force
```

```diff
+ 新增行
- 删除行
  保留行
```""",
            ),
        ),
        activities = emptyList(),
        running = false,
    )
    "demo-running" -> DetailDemo(
        messages = listOf(
            DetailMessage("user", true, "整理 CloudX 远程控制项目"),
            DetailMessage("assistant", false, "我正在检查工作区结构，并准备整理远程控制相关模块。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "thinking",
                kind = DetailActivityKind.THINKING,
                title = "思考",
                text = "正在扫描工作区，等待电脑端返回任务进度。",
                status = DetailActivityStatus.RUNNING,
            ),
        ),
        running = true,
        startedAt = System.currentTimeMillis(),
    )
    "demo-completed" -> run {
        val completedAt = System.currentTimeMillis()
        DetailDemo(
            messages = listOf(
                DetailMessage("completed-user", true, "检查远程电脑端工作区"),
                DetailMessage("completed-assistant", false, "任务已完成。执行记录默认收起，点击执行行可以展开查看具体命令。"),
            ),
            activities = listOf(
                DetailActivity(
                    id = "completed-command",
                    kind = DetailActivityKind.COMMAND,
                    title = "执行",
                text = "命令已完成。",
                    status = DetailActivityStatus.COMPLETED,
                    command = "Get-ChildItem -Force",
                    output = "README.md\napp\ndesktop-connector",
                    startedAt = completedAt - 2_400L,
                    completedAt = completedAt,
                ),
                DetailActivity(
                    id = "completed-file-change",
                    kind = DetailActivityKind.FILE_CHANGE,
                    title = "修改",
                    text = "文件修改已完成。",
                    status = DetailActivityStatus.COMPLETED,
                    startedAt = completedAt - 5_800L,
                    completedAt = completedAt - 3_100L,
                ),
            ),
            running = false,
        )
    }
    "demo-failed" -> DetailDemo(
        messages = listOf(
            DetailMessage("user", true, "修复扫码连接超时"),
            DetailMessage("assistant", false, "任务失败：未收到电脑端响应。请确认电脑端项目已经启动。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "connect",
                kind = DetailActivityKind.COMMAND,
                title = "连接",
                text = "未在等待时间内收到电脑端响应。",
                status = DetailActivityStatus.FAILED,
            ),
        ),
        running = false,
    )
    "demo-interrupted" -> DetailDemo(
        messages = listOf(
            DetailMessage("user", true, "测试远程任务恢复"),
            DetailMessage("assistant", false, "任务已中断，可以从最近消息继续。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "resume",
                kind = DetailActivityKind.PLAN,
                title = "恢复",
                text = "任务在电脑端停止，当前页面保留最近消息。",
                status = DetailActivityStatus.INTERRUPTED,
            ),
        ),
        running = false,
    )
    else -> DetailDemo(
        messages = listOf(
            DetailMessage("user", true, demoRemoteTitle(threadId)),
            DetailMessage("assistant", false, "已完成：这是电脑端 Codex 返回的对话内容。你可以展开下面的过程行查看任务详情。"),
        ),
        activities = listOf(
            DetailActivity(
                id = "thinking",
                kind = DetailActivityKind.THINKING,
                title = "思考",
                text = "已完成本次任务。这里会显示电脑端返回的过程和任务结果。",
                status = DetailActivityStatus.COMPLETED,
            ),
        ),
        running = false,
    )
}

private fun demoRemoteTitle(threadId: String): String = when (threadId) {
    "demo-waiting" -> "等待确认远程命令"
    "demo-completed-unread" -> "实现扫码配对流程"
    "demo-idle" -> "检查 Windows Connector 状态"
    "demo-completed" -> "同步设置页面"
    "demo-archived" -> "整理项目文档"
    else -> "远程 Codex 对话"
}
