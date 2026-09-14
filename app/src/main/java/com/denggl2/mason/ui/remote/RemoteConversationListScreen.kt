package com.denggl2.mason.ui.remote

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import com.denggl2.mason.ui.theme.MasonAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.WithRemoteMaterialResources
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.denggl2.mason.R
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteModelOption
import com.denggl2.mason.ui.chat.ChatBackdropBlur
import com.denggl2.mason.ui.chat.ChatGlassMaterial
import com.denggl2.mason.ui.chat.ChatGlassDropdown
import com.denggl2.mason.ui.chat.ChatSurfaceRole
import com.denggl2.mason.ui.chat.LocalChatBackdropState
import com.denggl2.mason.ui.chat.blurLayerOuterEdgeFeather
import com.denggl2.mason.ui.chat.captureChatBackdrop
import com.denggl2.mason.ui.chat.masonGlassShadow
import com.denggl2.mason.ui.chat.rememberChatBackdropState
import com.denggl2.mason.ui.theme.LocalInterfaceEffects
import com.denggl2.mason.ui.theme.MASON_OVERLAY_SCRIM_ALPHA
import com.denggl2.mason.ui.theme.MasonSheetShape
import com.denggl2.mason.ui.theme.ProgressiveBlurEdge
import com.denggl2.mason.ui.theme.captureProgressiveEdgeBlur
import com.denggl2.mason.ui.theme.progressiveEdgeBlur
import com.denggl2.mason.ui.theme.rememberProgressiveEdgeBlurState
import com.denggl2.mason.ui.theme.resolveBackdropBlurRadius
import com.denggl2.mason.ui.theme.windowBackdropMaterial
import com.denggl2.mason.ui.theme.masonOverlayWindowInsets
import com.denggl2.mason.ui.theme.masonSheetContainerColor
import com.denggl2.mason.ui.theme.masonSheetSurface
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val RemoteHeaderTopSpacing = 8.dp
private val RemoteHeaderHeight = 48.dp
private val RemoteHeaderBottomSpacing = 12.dp
private val RemoteListTopFadeContentHeight = 30.dp
private val RemoteListBottomFadeContentHeight = 14.dp
private val RemotePullContentDistance = 72.dp
private const val RemoteGestureRefreshMinDurationMillis = 480L
private const val RemotePostPullClickGuardMillis = 240L
private val RemoteConversationActionWidth = 148.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConversationListScreen(
    onBack: () -> Unit,
    onConversationSelected: (String) -> Unit,
    onPairingDisconnected: () -> Unit,
    viewModel: RemoteConversationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewConversationSheet by remember { mutableStateOf(false) }
    var disconnectConfirmationStage by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var gestureRefreshActive by remember { mutableStateOf(false) }
    var lastPullInteractionAt by remember { mutableLongStateOf(0L) }
    var openActionThreadId by remember { mutableStateOf<String?>(null) }
    var confirmingArchiveThreadId by remember { mutableStateOf<String?>(null) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safeDrawingPadding.calculateTopPadding()
    val bottomInset = safeDrawingPadding.calculateBottomPadding()
    val listTopPadding = topInset + RemoteHeaderTopSpacing +
        RemoteHeaderHeight + RemoteHeaderBottomSpacing
    val listTopFadeHeight = topInset + RemoteListTopFadeContentHeight
    val listBottomFadeHeight = bottomInset + RemoteListBottomFadeContentHeight
    val density = LocalDensity.current
    val fadeRevealDistancePx = with(density) { 24.dp.toPx() }
    val pullFraction = pullToRefreshState.distanceFraction.coerceAtLeast(0f)
    val rowInteractionsEnabled = pullFraction <= 0f && !gestureRefreshActive
    val rowInteractionBlocked = rememberUpdatedState(!rowInteractionsEnabled)
    val pullContentOffsetPx = with(density) {
        RemotePullContentDistance.toPx() * pullFraction.coerceAtMost(1.55f)
    }
    val listTopPaddingPx = with(density) { listTopPadding.toPx() }
    val topFadeProgress = remember(listState, fadeRevealDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / fadeRevealDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val bottomFadeTarget = remember(listState, fadeRevealDistancePx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            when {
                layoutInfo.totalItemsCount == 0 || lastVisibleItem == null -> 0f
                lastVisibleItem.index < layoutInfo.totalItemsCount - 1 -> 1f
                else -> {
                    val remaining = lastVisibleItem.offset + lastVisibleItem.size -
                        layoutInfo.viewportEndOffset
                    (remaining / fadeRevealDistancePx).coerceIn(0f, 1f)
                }
            }
        }
    }
    val bottomFadeProgress by animateFloatAsState(
        targetValue = bottomFadeTarget.value,
        animationSpec = tween(180),
        label = "remote_conversation_bottom_fade",
    )
    val interfaceEffects = LocalInterfaceEffects.current
    val edgeBlurState = rememberProgressiveEdgeBlurState(
        enabled = interfaceEffects.progressiveEdgeBlurEnabled,
    )
    val backdropState = rememberChatBackdropState(
        enabled = interfaceEffects.backdropBlurEnabled,
    )
    val pageBackground = MaterialTheme.colorScheme.background

    LaunchedEffect(uiState.createdConversationThreadId) {
        val threadId = uiState.createdConversationThreadId ?: return@LaunchedEffect
        showNewConversationSheet = false
        viewModel.consumeCreatedConversation()
        onConversationSelected(threadId)
    }

    LaunchedEffect(viewModel) {
        viewModel.pairingDisconnected.collect {
            disconnectConfirmationStage = 0
            onPairingDisconnected()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onResume()
                    viewModel.startExecutionEventObservation()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.stopExecutionEventObservation()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onResume()
            viewModel.startExecutionEventObservation()
        }
        onDispose {
            viewModel.stopExecutionEventObservation()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(gestureRefreshActive) {
        if (!gestureRefreshActive) return@LaunchedEffect
        delay(RemoteGestureRefreshMinDurationMillis)
        snapshotFlow { uiState.isRefreshing }.first { refreshing -> !refreshing }
        gestureRefreshActive = false
    }

    LaunchedEffect(pullToRefreshState) {
        snapshotFlow { pullToRefreshState.distanceFraction }
            .collect { fraction ->
                if (fraction > 0f) lastPullInteractionAt = SystemClock.uptimeMillis()
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    openActionThreadId = null
                    confirmingArchiveThreadId = null
                }
            }
    }

    LaunchedEffect(listState, uiState.conversations.size, uiState.nextCursor) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (
                uiState.conversations.isNotEmpty() &&
                uiState.nextCursor != null &&
                lastVisibleIndex >= uiState.conversations.lastIndex - 2
            ) {
                viewModel.loadMore()
            }
        }
    }

    CompositionLocalProvider(LocalChatBackdropState provides backdropState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureChatBackdrop(backdropState),
            ) {
                PullToRefreshBox(
                    isRefreshing = gestureRefreshActive,
                    onRefresh = {
                        if (!gestureRefreshActive) {
                            gestureRefreshActive = true
                            viewModel.refresh()
                        }
                    },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                val indicatorAlpha = if (gestureRefreshActive) {
                    1f
                } else {
                    ((pullFraction - 0.82f) / 0.18f).coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(30.dp)
                        .graphicsLayer {
                            alpha = indicatorAlpha
                            translationY = listTopPaddingPx + pullContentOffsetPx * 0.22f
                            val scale = 0.78f + 0.22f * indicatorAlpha
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (gestureRefreshActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { pullFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                    },
                ) {
                when {
                    uiState.connector == null -> RemoteListEmptyState("未配对远端电脑")
                    uiState.isInitialLoading && uiState.conversations.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(26.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                    uiState.errorMessage != null && uiState.conversations.isEmpty() -> {
                        RemoteListError(
                            message = uiState.errorMessage.orEmpty(),
                            onRetry = viewModel::retry,
                        )
                    }
                    uiState.conversations.isEmpty() -> RemoteListEmptyState("电脑上暂无会话")
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = pullContentOffsetPx }
                            .remoteListEdgeFadeMask(
                                topProgress = topFadeProgress.value,
                                bottomProgress = bottomFadeProgress,
                                topFadeHeight = listTopFadeHeight,
                                bottomFadeHeight = listBottomFadeHeight,
                            )
                            .captureProgressiveEdgeBlur(edgeBlurState),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = listTopPadding + 4.dp,
                            bottom = bottomInset + 12.dp,
                        ),
                    ) {
                        val pinnedConversations = uiState.conversations.filter(RemoteConversationSummary::isPinned)
                        val recentConversations = uiState.conversations.filterNot(RemoteConversationSummary::isPinned)

                        if (pinnedConversations.isNotEmpty()) {
                            remoteConversationSection("置顶")
                            remoteConversationRows(
                                conversations = pinnedConversations,
                                uiState = uiState,
                                rowInteractionsEnabled = rowInteractionsEnabled,
                                openActionThreadId = openActionThreadId,
                                confirmingArchiveThreadId = confirmingArchiveThreadId,
                                onOpenActionThreadChange = { threadId ->
                                    openActionThreadId = threadId
                                    if (confirmingArchiveThreadId != threadId) confirmingArchiveThreadId = null
                                },
                                onConfirmingArchiveChange = { confirmingArchiveThreadId = it },
                                onPinChange = viewModel::setConversationPinned,
                                onArchive = viewModel::archiveConversation,
                                onConversationClick = { conversation ->
                                    val recentlyPulled = SystemClock.uptimeMillis() - lastPullInteractionAt <
                                        RemotePostPullClickGuardMillis
                                    if (!rowInteractionBlocked.value && !recentlyPulled) {
                                        viewModel.markConversationSeen(conversation.threadId)
                                        onConversationSelected(conversation.threadId)
                                    }
                                },
                            )
                        }
                        if (recentConversations.isNotEmpty()) {
                            remoteConversationSection("最近")
                            remoteConversationRows(
                                conversations = recentConversations,
                                uiState = uiState,
                                rowInteractionsEnabled = rowInteractionsEnabled,
                                openActionThreadId = openActionThreadId,
                                confirmingArchiveThreadId = confirmingArchiveThreadId,
                                onOpenActionThreadChange = { threadId ->
                                    openActionThreadId = threadId
                                    if (confirmingArchiveThreadId != threadId) confirmingArchiveThreadId = null
                                },
                                onConfirmingArchiveChange = { confirmingArchiveThreadId = it },
                                onPinChange = viewModel::setConversationPinned,
                                onArchive = viewModel::archiveConversation,
                                onConversationClick = { conversation ->
                                    val recentlyPulled = SystemClock.uptimeMillis() - lastPullInteractionAt <
                                        RemotePostPullClickGuardMillis
                                    if (!rowInteractionBlocked.value && !recentlyPulled) {
                                        viewModel.markConversationSeen(conversation.threadId)
                                        onConversationSelected(conversation.threadId)
                                    }
                                },
                            )
                        }
                        if (uiState.isAppending) {
                            item("remote-append-loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(17.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 1.5.dp,
                                    )
                                }
                            }
                        } else if (uiState.errorMessage != null) {
                            item("remote-list-error") {
                                Text(
                                    text = "${uiState.errorMessage}，点击重试",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = viewModel::retry)
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                )
                            }
                        }
                    }
                    }
                }
            }

            if (uiState.conversations.isNotEmpty()) {
                RemoteListEdgeFades(
                    state = edgeBlurState,
                    backgroundColor = pageBackground,
                    topProgress = topFadeProgress.value,
                    bottomProgress = bottomFadeProgress,
                    topFadeHeight = listTopFadeHeight,
                    bottomFadeHeight = listBottomFadeHeight,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = topInset + RemoteHeaderTopSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteBackButton(onClick = onBack)
                Spacer(Modifier.width(8.dp))
                RemoteFloatingSurface(
                    shape = RoundedCornerShape(24.dp),
                    cornerRadius = 24.dp,
                    modifier = Modifier.height(RemoteHeaderHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(
                                enabled = uiState.connector != null && !uiState.isDisconnecting,
                            ) {
                                viewModel.clearDisconnectError()
                                disconnectConfirmationStage = 1
                            }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                uiState.isDisconnecting -> "断开中"
                                uiState.isInitialLoading && uiState.conversations.isEmpty() -> "连接中"
                                uiState.connector != null -> "已连接"
                                else -> "连接中"
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (uiState.connector != null) {
                RemoteNewConversationButton(
                    onClick = {
                        showNewConversationSheet = true
                        viewModel.loadNewConversationOptions()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = topInset + RemoteHeaderTopSpacing),
                )
            }
        }
    }

    if (showNewConversationSheet) {
        RemoteNewConversationSheet(
                state = uiState,
                onDismiss = { showNewConversationSheet = false },
                onDraftChange = viewModel::updateNewConversationDraft,
                onSelectProject = viewModel::selectNewProject,
                onSelectModel = viewModel::selectNewModel,
                onSelectEffort = viewModel::selectNewReasoningEffort,
                onSelectPermission = viewModel::selectNewPermissionProfile,
                onCreate = viewModel::createConversation,
            )
    }

    when (disconnectConfirmationStage) {
        1 -> AlertDialog(
            onDismissRequest = { disconnectConfirmationStage = 0 },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("取消与此电脑的配对？") },
            text = { Text("取消配对后将断开远端操控。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDisconnectError()
                        disconnectConfirmationStage = 2
                    },
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectConfirmationStage = 0 }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        2 -> AlertDialog(
            onDismissRequest = {
                if (!uiState.isDisconnecting) {
                    viewModel.clearDisconnectError()
                    disconnectConfirmationStage = 0
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("确定断开连接？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("断开后需要重新扫码配对。")
                    uiState.disconnectError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::disconnectPairing,
                    enabled = !uiState.isDisconnecting,
                ) {
                    if (uiState.isDisconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text("断开连接", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDisconnectError()
                        disconnectConfirmationStage = 1
                    },
                    enabled = !uiState.isDisconnecting,
                ) {
                    Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
internal fun RemoteBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteFloatingSurface(
        shape = CircleShape,
        cornerRadius = 24.dp,
        modifier = modifier.size(RemoteHeaderHeight),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = LocalRemoteStrings.current.t("返回"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun RemoteNewConversationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteFloatingSurface(
        shape = CircleShape,
        cornerRadius = 24.dp,
        modifier = modifier.size(RemoteHeaderHeight),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                    R.drawable.ic_remote_new_conversation,
                ),
                contentDescription = LocalRemoteStrings.current.t("新建远端对话"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class RemoteNewSelectorItem(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteNewConversationSheet(
    state: RemoteConversationListUiState,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onSelectPermission: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val selectedModel = state.newConversationOptions.models
        .firstOrNull { it.id == state.selectedNewModelId }
    val selectedProject = state.newConversationOptions.projects
        .firstOrNull { it.path == state.selectedNewProjectPath }
    val canCreate = state.newConversationDraft.isNotBlank() &&
        selectedProject != null &&
        selectedModel != null &&
        state.selectedNewPermissionProfileId != null &&
        !state.isCreatingConversation &&
        !state.isNewConversationOptionsLoading
    val inputFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var expandedSelector by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
        keyboard?.show()
    }

    WithRemoteMaterialResources {
    ModalBottomSheet(
        onDismissRequest = {
            if (expandedSelector != null) expandedSelector = null else onDismiss()
        },
        shape = MasonSheetShape,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_OVERLAY_SCRIM_ALPHA),
        tonalElevation = 0.dp,
        contentWindowInsets = { masonOverlayWindowInsets() },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .masonSheetSurface()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)),
            )
            Text(
                text = "新对话",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.isNewConversationOptionsLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Text("正在读取电脑选项", fontSize = 12.sp)
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item("new-project") {
                    RemoteNewSelector(
                        selectorId = "project",
                        title = "项目",
                        label = selectedProject?.displayName ?: "选择项目",
                        items = state.newConversationOptions.projects.map {
                            RemoteNewSelectorItem(it.path, it.displayName)
                        },
                        selectedId = state.selectedNewProjectPath,
                        onSelect = onSelectProject,
                        expandedSelector = expandedSelector,
                        onExpandedSelectorChange = { expandedSelector = it },
                    )
                }
                item("new-model") {
                    RemoteNewSelector(
                        selectorId = "model",
                        title = "模型",
                        label = selectedModel?.displayName ?: "选择模型",
                        items = state.newConversationOptions.models.map {
                            RemoteNewSelectorItem(it.id, it.displayName)
                        },
                        selectedId = state.selectedNewModelId,
                        onSelect = onSelectModel,
                        expandedSelector = expandedSelector,
                        onExpandedSelectorChange = { expandedSelector = it },
                    )
                }
                item("new-reasoning") {
                    RemoteNewSelector(
                        selectorId = "reasoning",
                        title = "推理层级",
                        label = remoteReasoningLabel(state.selectedNewReasoningEffort),
                        items = selectedModel?.supportedReasoningEfforts.orEmpty().map {
                            RemoteNewSelectorItem(it.id, remoteReasoningLabel(it.id))
                        },
                        selectedId = state.selectedNewReasoningEffort,
                        onSelect = onSelectEffort,
                        expandedSelector = expandedSelector,
                        onExpandedSelectorChange = { expandedSelector = it },
                    )
                }
                item("new-permission") {
                    RemoteNewSelector(
                        selectorId = "permission",
                        title = "访问权限",
                        label = if (state.selectedNewPermissionProfileId == null) {
                            "选择权限"
                        } else {
                            remotePermissionLabel(state.selectedNewPermissionProfileId)
                        },
                        items = state.newConversationOptions.permissionProfiles.map {
                            RemoteNewSelectorItem(it.id, remotePermissionLabel(it.id), it.allowed)
                        },
                        selectedId = state.selectedNewPermissionProfileId,
                        onSelect = onSelectPermission,
                        expandedSelector = expandedSelector,
                        onExpandedSelectorChange = { expandedSelector = it },
                    )
                }
            }
            val composerShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .masonGlassShadow(18.dp)
                    .clip(composerShape)
                    .animateContentSize(animationSpec = tween(180)),
            ) {
                ChatGlassMaterial(
                    shape = composerShape,
                    cornerRadius = 18.dp,
                    role = ChatSurfaceRole.Large,
                    blur = ChatBackdropBlur.Soft,
                    refraction = true,
                    blurredAlpha = 0.80f,
                    fallbackAlpha = 0.99f,
                )
                BasicTextField(
                    value = state.newConversationDraft,
                    onValueChange = onDraftChange,
                    enabled = !state.isCreatingConversation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp, max = 180.dp)
                        .focusRequester(inputFocusRequester)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (state.newConversationDraft.isBlank()) {
                                Text(
                                    "向电脑 Codex 发起对话",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                                    fontSize = 14.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            state.newConversationError?.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onCreate,
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isCreatingConversation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 1.7.dp,
                    )
                } else {
                    Text("发起对话")
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
    }
}

@Composable
private fun RemoteNewSelector(
    selectorId: String,
    title: String,
    label: String,
    items: List<RemoteNewSelectorItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    expandedSelector: String?,
    onExpandedSelectorChange: (String?) -> Unit,
) {
    val expanded = expandedSelector == selectorId
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "remote_new_selector_arrow",
    )
    Box {
        val pillShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(pillShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                    shape = pillShape,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(enabled = items.isNotEmpty()) {
                        onExpandedSelectorChange(if (expanded) null else selectorId)
                    }
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
                    modifier = Modifier.widthIn(max = 118.dp),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }
        ChatGlassDropdown(
            expanded = expanded,
            onDismissRequest = { onExpandedSelectorChange(null) },
            width = 224.dp,
            cornerRadius = 16.dp,
            alignEnd = false,
            subduedMaterial = true,
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            color = if (item.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        if (item.id == selectedId) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = LocalRemoteStrings.current.t("当前选项"),
                            )
                        }
                    },
                    enabled = item.enabled,
                    onClick = {
                            onExpandedSelectorChange(null)
                            onSelect(item.id)
                    },
                )
            }
        }
    }
}

private fun remoteReasoningLabel(value: String?): String = when (value?.lowercase()) {
    "none" -> "关闭推理"
    "minimal" -> "极简"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    null, "" -> "未读取"
    else -> value
}

private fun remotePermissionLabel(value: String?): String = when (
    value?.trim()?.removePrefix(":")?.lowercase()
) {
    "read-only", "readonly", "read_only" -> "请求批准"
    "workspace", "workspace-write", "workspace_write" -> "帮我批准"
    "danger-full-access", "danger_full_access", "full-access", "full_access" -> "完全访问权限"
    null, "" -> "未读取"
    else -> value.removePrefix(":")
}

private fun LazyListScope.remoteConversationSection(title: String) {
    item(key = "remote-section-$title") {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 5.dp),
        )
    }
}

private fun LazyListScope.remoteConversationRows(
    conversations: List<RemoteConversationSummary>,
    uiState: RemoteConversationListUiState,
    rowInteractionsEnabled: Boolean,
    openActionThreadId: String?,
    confirmingArchiveThreadId: String?,
    onOpenActionThreadChange: (String?) -> Unit,
    onConfirmingArchiveChange: (String?) -> Unit,
    onPinChange: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onConversationClick: (RemoteConversationSummary) -> Unit,
) {
    items(
        items = conversations,
        key = RemoteConversationSummary::threadId,
    ) { conversation ->
        RemoteConversationListRow(
            conversation = conversation,
            hasUnreadCompletion = conversation.threadId in uiState.unreadCompletionThreadIds,
            enabled = rowInteractionsEnabled && conversation.threadId !in uiState.mutatingThreadIds,
            actionsOpen = openActionThreadId == conversation.threadId,
            confirmingArchive = confirmingArchiveThreadId == conversation.threadId,
            onActionsOpenChange = { open ->
                onOpenActionThreadChange(conversation.threadId.takeIf { open })
            },
            onClick = {
                if (openActionThreadId != null && openActionThreadId != conversation.threadId) {
                    onConfirmingArchiveChange(null)
                    onOpenActionThreadChange(null)
                } else {
                    onConversationClick(conversation)
                }
            },
            onTogglePinned = {
                onConfirmingArchiveChange(null)
                onOpenActionThreadChange(null)
                onPinChange(conversation.threadId, !conversation.isPinned)
            },
            onArchiveRequested = {
                onConfirmingArchiveChange(conversation.threadId)
                onOpenActionThreadChange(conversation.threadId)
            },
            onArchiveCancelled = {
                onConfirmingArchiveChange(null)
                onOpenActionThreadChange(null)
            },
            onArchiveConfirmed = {
                onConfirmingArchiveChange(null)
                onOpenActionThreadChange(null)
                onArchive(conversation.threadId)
            },
        )
    }
}

@Composable
private fun RemoteConversationListRow(
    conversation: RemoteConversationSummary,
    hasUnreadCompletion: Boolean,
    enabled: Boolean,
    actionsOpen: Boolean,
    confirmingArchive: Boolean,
    onActionsOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onArchiveRequested: () -> Unit,
    onArchiveCancelled: () -> Unit,
    onArchiveConfirmed: () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { RemoteConversationActionWidth.toPx() }
    val offsetX = remember(conversation.threadId) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    fun settleActions(open: Boolean) {
        onActionsOpenChange(open)
        coroutineScope.launch {
            offsetX.animateTo(
                targetValue = if (open) -actionWidthPx else 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 560f),
            )
        }
    }

    LaunchedEffect(actionsOpen, actionWidthPx) {
        val target = if (actionsOpen) -actionWidthPx else 0f
        if (offsetX.targetValue != target) {
            offsetX.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 1f, stiffness = 560f),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(0.dp)),
    ) {
        RemoteConversationActions(
            conversation = conversation,
            confirmingArchive = confirmingArchive,
            enabled = enabled,
            onTogglePinned = onTogglePinned,
            onArchiveRequested = onArchiveRequested,
            onArchiveCancelled = onArchiveCancelled,
            onArchiveConfirmed = onArchiveConfirmed,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(RemoteConversationActionWidth)
                .fillMaxHeight(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(enabled, actionWidthPx, confirmingArchive) {
                    if (!enabled || confirmingArchive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { coroutineScope.launch { offsetX.stop() } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f))
                            }
                        },
                        onDragEnd = {
                            val shouldOpen = offsetX.value <= -actionWidthPx * 0.42f
                            settleActions(shouldOpen)
                        },
                        onDragCancel = { settleActions(actionsOpen) },
                    )
                }
                .clickable(enabled = enabled) {
                    if (actionsOpen) settleActions(false) else onClick()
                }
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.67f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = conversation.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        when (conversation.executionStatus) {
                            RemoteExecutionStatus.RUNNING -> {
                                Spacer(Modifier.width(7.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 1.5.dp,
                                )
                            }
                            RemoteExecutionStatus.COMPLETED -> if (hasUnreadCompletion) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            else -> Unit
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemoteConversationTime(
                            updatedAt = conversation.updatedAt,
                            english = LocalRemoteStrings.current.isEnglish,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = conversation.preview.ifBlank { "还没有消息" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.67f),
                )
            }
        }
    }
}

@Composable
private fun RemoteConversationActions(
    conversation: RemoteConversationSummary,
    confirmingArchive: Boolean,
    enabled: Boolean,
    onTogglePinned: () -> Unit,
    onArchiveRequested: () -> Unit,
    onArchiveCancelled: () -> Unit,
    onArchiveConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        if (confirmingArchive) {
            RemoteConversationAction(
                label = "确定",
                enabled = enabled,
                destructive = true,
                onClick = onArchiveConfirmed,
                modifier = Modifier.weight(1f),
            )
            RemoteConversationAction(
                label = "取消",
                enabled = enabled,
                onClick = onArchiveCancelled,
                modifier = Modifier.weight(1f),
            )
        } else {
            RemoteConversationAction(
                label = if (conversation.isPinned) "取消置顶" else "置顶",
                enabled = enabled,
                onClick = onTogglePinned,
                modifier = Modifier.weight(1f),
            )
            RemoteConversationAction(
                label = "归档",
                enabled = enabled,
                destructive = true,
                onClick = onArchiveRequested,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RemoteConversationAction(
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (destructive) MaterialTheme.colorScheme.error else Color.Black
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background.copy(alpha = if (enabled) 1f else 0.42f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

internal fun formatRemoteConversationTime(
    updatedAt: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    english: Boolean = false,
): String {
    if (updatedAt <= 0) return ""
    val timestampMillis = if (updatedAt < 100_000_000_000L) updatedAt * 1_000 else updatedAt
    val dateTime = Instant.ofEpochMilli(timestampMillis).atZone(zoneId)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when (val date = dateTime.toLocalDate()) {
        today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> if (english) "Yesterday" else "昨天"
        else -> if (date.year == today.year) {
            dateTime.format(DateTimeFormatter.ofPattern(if (english) "M/d" else "M月d日"))
        } else {
            dateTime.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        }
    }
}

@Composable
private fun BoxScope.RemoteListEmptyState(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        modifier = Modifier.align(Alignment.Center),
    )
}

@Composable
private fun BoxScope.RemoteListError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
internal fun RemoteFloatingSurface(
    shape: Shape,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    largeSurface: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .masonGlassShadow(cornerRadius)
            .clip(shape),
    ) {
        ChatGlassMaterial(
            shape = shape,
            cornerRadius = cornerRadius,
            role = if (largeSurface) ChatSurfaceRole.Large else ChatSurfaceRole.Compact,
            blur = if (largeSurface) ChatBackdropBlur.Soft else ChatBackdropBlur.Strong,
            refraction = true,
            blurredAlpha = 0.80f,
            fallbackAlpha = 1f,
        )
        content()
    }
}

private fun Modifier.remoteListEdgeFadeMask(
    topProgress: Float,
    bottomProgress: Float,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    if (size.height <= 0f) return@drawWithContent
    val topStop = (topFadeHeight.toPx() / size.height).coerceIn(0f, 0.45f)
    val bottomStop = (1f - bottomFadeHeight.toPx() / size.height).coerceIn(0.55f, 1f)
    val bottomRange = 1f - bottomStop
    fun topAlpha(step: Float) = 1f - topProgress * (1f - step)
    fun bottomAlpha(step: Float) = 1f - bottomProgress * step
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = topAlpha(0f)),
            topStop * 0.25f to Color.White.copy(alpha = topAlpha(0.15625f)),
            topStop * 0.50f to Color.White.copy(alpha = topAlpha(0.50f)),
            topStop * 0.75f to Color.White.copy(alpha = topAlpha(0.84375f)),
            topStop to Color.White,
            bottomStop to Color.White,
            bottomStop + bottomRange * 0.25f to Color.White.copy(alpha = bottomAlpha(0.15625f)),
            bottomStop + bottomRange * 0.50f to Color.White.copy(alpha = bottomAlpha(0.50f)),
            bottomStop + bottomRange * 0.75f to Color.White.copy(alpha = bottomAlpha(0.84375f)),
            1f to Color.White.copy(alpha = bottomAlpha(1f)),
        ),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun BoxScope.RemoteListEdgeFades(
    state: HazeState?,
    backgroundColor: Color,
    topProgress: Float,
    bottomProgress: Float,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
) {
    val glassMaterialEnabled = LocalInterfaceEffects.current.glassMaterialEnabled
    val fadeSurface = backgroundColor.copy(alpha = if (glassMaterialEnabled) 0.10f else 0.995f)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(topFadeHeight)
            .graphicsLayer { alpha = topProgress }
            .blurLayerOuterEdgeFeather(
                edge = ProgressiveBlurEdge.Bottom,
                featherHeight = 10.dp,
            )
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Top,
                backgroundColor = backgroundColor,
                smoothBoundary = true,
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to fadeSurface,
                        0.32f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.82f),
                        0.70f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.28f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bottomFadeHeight)
            .graphicsLayer { alpha = bottomProgress }
            .blurLayerOuterEdgeFeather(
                edge = ProgressiveBlurEdge.Top,
                featherHeight = 10.dp,
            )
            .progressiveEdgeBlur(
                state = state,
                edge = ProgressiveBlurEdge.Bottom,
                backgroundColor = backgroundColor,
                smoothBoundary = true,
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.30f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.28f),
                        0.68f to fadeSurface.copy(alpha = fadeSurface.alpha * 0.82f),
                        1f to fadeSurface,
                    ),
                ),
            ),
    )
}
