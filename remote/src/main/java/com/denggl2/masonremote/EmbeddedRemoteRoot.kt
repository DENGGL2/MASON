package com.denggl2.masonremote

import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.denggl2.masonremote.data.PairingStore
import com.denggl2.masonremote.diagnostics.DiagnosticLog
import com.denggl2.masonremote.notification.RemoteNotificationManager
import com.denggl2.masonremote.transport.TransportMode
import com.denggl2.masonremote.transport.parsePairingOffer
import com.denggl2.masonremote.ui.DisconnectedScreen
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.MasonRemoteTheme
import com.denggl2.masonremote.ui.PairingLandingScreen
import com.denggl2.masonremote.ui.resolveRemoteStrings
import com.denggl2.masonremote.ui.pairing.PairingSheet
import com.denggl2.masonremote.ui.remote.PairingDisconnectDialog
import com.denggl2.masonremote.ui.remote.RemoteConversationDetailScreen
import com.denggl2.masonremote.ui.remote.RemoteConversationListScreen
import com.denggl2.masonremote.ui.remote.RemoteConversationListViewModel
import com.denggl2.masonremote.ui.settings.RemoteFontSizePreference
import com.denggl2.masonremote.ui.settings.RemoteInterfaceStyle
import com.denggl2.masonremote.ui.settings.RemoteLanguagePreference
import com.denggl2.masonremote.ui.settings.RemoteMessageSendMode
import com.denggl2.masonremote.ui.settings.RemoteThemeMode
import com.denggl2.masonremote.ui.settings.TaskNotificationMode

/** Settings supplied by MASON so the embedded Remote surface has one control plane. */
data class RemoteHostSettings(
    val themeMode: RemoteThemeMode = RemoteThemeMode.SYSTEM,
    val interfaceStyle: RemoteInterfaceStyle = RemoteInterfaceStyle.NATIVE,
    val fontSize: RemoteFontSizePreference = RemoteFontSizePreference.MEDIUM,
    val language: RemoteLanguagePreference = RemoteLanguagePreference.SYSTEM,
    val glassRefractionEnabled: Boolean = true,
    val glassTransparency: Float = 0.90f,
    val glassFrost: Float = 0.10f,
    val notificationMode: TaskNotificationMode = TaskNotificationMode.REGULAR,
    val messageSendMode: RemoteMessageSendMode = RemoteMessageSendMode.QUEUE,
)

private sealed interface RemotePage {
    data object Pairing : RemotePage
    data object Disconnected : RemotePage
    data object Conversations : RemotePage
    data class Detail(val threadId: String) : RemotePage
}

/**
 * Embeddable Remote experience. It intentionally has no settings page or
 * launcher activity; MASON owns navigation and settings while Remote owns the
 * pairing/transport/conversation experience.
 */
@Composable
fun MasonRemoteRoot(
    settings: RemoteHostSettings = RemoteHostSettings(),
    onBack: () -> Unit,
    notificationActivityClass: Class<*>? = null,
    notificationThreadId: String? = null,
    debugPairingPayload: String? = null,
    debugThreadId: String? = null,
    debugList: Boolean = false,
    onNotificationThreadConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember(appContext) { PairingStore(appContext) }
    val activeConnector by store.connector.collectAsState()
    val isPaired = activeConnector != null
    var showPairingSheet by remember { mutableStateOf(debugPairingPayload != null) }
    var showPairingChooser by remember { mutableStateOf(!isPaired && debugPairingPayload == null) }
    var selectedPairingMode by remember { mutableStateOf<TransportMode?>(TransportMode.CLOUDFLARE_TUNNEL) }
    var selectedRemoteConversationId by remember { mutableStateOf(debugThreadId) }
    val remoteConversationDrafts = remember { mutableStateMapOf<String, String>() }
    var showDisconnected by remember { mutableStateOf(false) }
    var disconnectedAfterRetry by remember { mutableStateOf(false) }
    var disconnectConfirmationStage by remember { mutableIntStateOf(0) }
    val remoteStrings = resolveRemoteStrings(settings.language)
    val notificationManager = remember(appContext, remoteStrings.language, notificationActivityClass) {
        RemoteNotificationManager(
            context = appContext,
            english = remoteStrings.isEnglish,
            activityClass = notificationActivityClass,
        )
    }
    val remoteViewModel = remember(activeConnector, appContext, debugList) {
        RemoteConversationListViewModel(
            pairedConnector = activeConnector,
            appContext = appContext,
            debugDemoMode = BuildConfig.DEBUG && debugList,
        )
    }
    val remoteUiState by remoteViewModel.uiState.collectAsState()

    fun requestPairing(mode: TransportMode) {
        selectedPairingMode = mode
        showPairingChooser = false
        showPairingSheet = true
    }

    fun openPairingChooser() {
        selectedPairingMode = TransportMode.CLOUDFLARE_TUNNEL
        showPairingSheet = false
        showPairingChooser = true
        showDisconnected = false
        selectedRemoteConversationId = null
    }

    fun requestDisconnect() {
        if (isPaired && remoteUiState.connector != null && !remoteUiState.isDisconnecting) {
            remoteViewModel.clearDisconnectError()
            disconnectConfirmationStage = 1
        }
    }
    val latestNotificationMode by rememberUpdatedState(settings.notificationMode)

    LaunchedEffect(remoteViewModel) {
        remoteViewModel.notificationEvents.collect { event ->
            notificationManager.notifyTaskEvent(
                event,
                latestNotificationMode,
            )
        }
    }

    DisposableEffect(remoteViewModel) {
        // Keep the desktop status long-poll alive across detail/settings pages
        // and while the activity is paused, so notification delivery is not
        // tied to the conversation list being visible.
        remoteViewModel.onResume()
        remoteViewModel.startExecutionEventObservation()
        onDispose { remoteViewModel.stopExecutionEventObservation() }
    }

    LaunchedEffect(remoteViewModel) {
        remoteViewModel.pairingDisconnected.collect {
            disconnectConfirmationStage = 0
            store.clearActive()
            showDisconnected = true
            showPairingChooser = false
            selectedRemoteConversationId = null
        }
    }

    LaunchedEffect(notificationThreadId) {
        notificationThreadId?.let {
            selectedRemoteConversationId = it
            onNotificationThreadConsumed()
        }
    }

    LaunchedEffect(debugThreadId) {
        if (BuildConfig.DEBUG) {
            debugThreadId?.let { selectedRemoteConversationId = it }
        }
    }

    LaunchedEffect(debugPairingPayload) {
        debugPairingPayload
            ?.let(::parsePairingOffer)
            ?.bootstrap
            ?.transportMode
            ?.takeIf {
                it == TransportMode.LOCAL_TLS ||
                    it == TransportMode.CLOUDFLARE_TUNNEL ||
                    it == TransportMode.WEBRTC_DIRECT
            }
            ?.let {
                selectedPairingMode = it
                showPairingChooser = false
            }
        if (debugPairingPayload != null && !isPaired) {
            showPairingSheet = true
        }
    }

    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (settings.themeMode) {
        RemoteThemeMode.SYSTEM -> systemDark
        RemoteThemeMode.LIGHT -> false
        RemoteThemeMode.DARK -> true
    }

    MasonRemoteTheme(
        darkTheme = useDarkTheme,
        interfaceStyle = settings.interfaceStyle,
        glassRefractionEnabled = settings.glassRefractionEnabled,
        glassTransparency = settings.glassTransparency,
        glassFrost = settings.glassFrost,
    ) {
        CompositionLocalProvider(
            LocalRemoteStrings provides remoteStrings,
        ) {
        val activity = context as? ComponentActivity
        val windowBackground = MaterialTheme.colorScheme.background
        SideEffect {
            // Keep the platform window fallback aligned with Compose during
            // page and theme transitions so a light frame cannot leak into
            // dark mode.
            activity?.window?.setBackgroundDrawable(ColorDrawable(windowBackground.toArgb()))
            activity?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
        val page = when {
            selectedRemoteConversationId != null && isPaired -> RemotePage.Detail(checkNotNull(selectedRemoteConversationId))
            BuildConfig.DEBUG && debugList -> RemotePage.Conversations
            showDisconnected -> RemotePage.Disconnected
            showPairingChooser || !isPaired -> RemotePage.Pairing
            isPaired -> RemotePage.Conversations
            else -> RemotePage.Pairing
        }
        BackHandler {
            if (selectedRemoteConversationId != null) {
                selectedRemoteConversationId = null
            } else {
                onBack()
            }
        }
        LaunchedEffect(page, remoteUiState.isRefreshing, remoteUiState.errorMessage) {
            val retryFinished =
                page == RemotePage.Conversations &&
                    !remoteUiState.isRefreshing &&
                    remoteUiState.errorMessage == null
            val retryFailed = page == RemotePage.Disconnected && remoteUiState.errorMessage != null
            if (disconnectedAfterRetry && (retryFinished || retryFailed)) {
                disconnectedAfterRetry = false
            }
        }

        AnimatedContent(
            targetState = page,
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                // Keep the outgoing and incoming pages on the active theme while
                // AnimatedContent is moving them across the window.
                .background(MaterialTheme.colorScheme.background),
            transitionSpec = {
                val movingForward = isForwardRemotePageTransition(
                    initial = initialState,
                    target = targetState,
                    disconnectedAfterRetry = disconnectedAfterRetry,
                )
                if (movingForward) {
                    (
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(360, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(220)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(360, easing = FastOutSlowInEasing),
                        )
                    ) togetherWith (
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                        ) + fadeOut(tween(180)) + scaleOut(
                            targetScale = 0.985f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                        )
                    )
                } else {
                    (
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(200)) + scaleIn(
                            initialScale = 0.99f,
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                        )
                    ) togetherWith (
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) + fadeOut(tween(160)) + scaleOut(
                            targetScale = 0.99f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        )
                    )
                }
            },
            label = "remote_page_transition",
        ) { targetPage ->
            when (targetPage) {
                is RemotePage.Detail -> RemoteConversationDetailScreen(
                    threadId = targetPage.threadId,
                    pairedConnector = activeConnector,
                    defaultMessageSendMode = settings.messageSendMode,
                    draft = remoteConversationDrafts[targetPage.threadId].orEmpty(),
                    onDraftChange = { draft ->
                        if (draft.isEmpty()) {
                            remoteConversationDrafts.remove(targetPage.threadId)
                        } else {
                            remoteConversationDrafts[targetPage.threadId] = draft
                        }
                    },
                    onBack = { selectedRemoteConversationId = null },
                )
                RemotePage.Conversations -> RemoteConversationListScreen(
                    onBack = onBack,
                    onRequestDisconnect = ::requestDisconnect,
                    onConversationSelected = { threadId ->
                        DiagnosticLog.record("CONVERSATION_SELECTED threadId=$threadId")
                        selectedRemoteConversationId = threadId
                    },
                    viewModel = remoteViewModel,
                )
                RemotePage.Pairing -> PairingLandingScreen(
                    selectedMode = selectedPairingMode,
                    onModeSelected = { selectedPairingMode = it },
                    onBack = onBack,
                    onStart = {
                        showDisconnected = false
                        selectedPairingMode?.let(::requestPairing)
                    },
                )
                RemotePage.Disconnected -> DisconnectedScreen(
                    onRetry = {
                        disconnectedAfterRetry = true
                        showDisconnected = false
                        remoteViewModel.retry()
                    },
                    onRepair = {
                        disconnectedAfterRetry = false
                        openPairingChooser()
                    },
                    errorCode = remoteUiState.errorMessage
                        ?.let { message ->
                            remoteStrings.text(
                                "错误码${Integer.toHexString(message.hashCode()).uppercase().padStart(8, '0')}",
                                "Error ${Integer.toHexString(message.hashCode()).uppercase().padStart(8, '0')}",
                            )
                        }
                        ?: remoteStrings.text("错误码XXXXXXXXXXXXXXXXXXX", "Error XXXXXXXXXXXXXXXXXXX"),
                )
            }
        }

        if (showPairingSheet) {
            PairingSheet(
                onDismiss = {
                    showPairingSheet = false
                    showPairingChooser = true
                },
                debugRawPayload = debugPairingPayload,
                requestedTransportMode = selectedPairingMode,
                onPaired = { connector ->
                    store.markPaired(connector)
                    store.activate(connector.transportMode)
                    showDisconnected = false
                    showPairingChooser = false
                    disconnectedAfterRetry = false
                    showPairingSheet = false
                },
            )
        }

        PairingDisconnectDialog(
            stage = disconnectConfirmationStage,
            state = remoteUiState,
            onDismiss = {
                remoteViewModel.clearDisconnectError()
                disconnectConfirmationStage = 0
            },
            onFirstConfirm = { disconnectConfirmationStage = 2 },
            onFinalConfirm = remoteViewModel::disconnectPairing,
            onBackToFirst = {
                remoteViewModel.clearDisconnectError()
                disconnectConfirmationStage = 1
            },
        )

        }
    }
}

private fun isForwardRemotePageTransition(
    initial: RemotePage,
    target: RemotePage,
    disconnectedAfterRetry: Boolean = false,
): Boolean = when {
    initial is RemotePage.Disconnected && target is RemotePage.Conversations -> true
    target is RemotePage.Disconnected && disconnectedAfterRetry -> false
    target is RemotePage.Disconnected -> true
    initial is RemotePage.Conversations && target is RemotePage.Detail -> true
    initial is RemotePage.Pairing && target is RemotePage.Conversations -> true
    initial is RemotePage.Detail && target is RemotePage.Conversations -> false
    initial is RemotePage.Conversations && target is RemotePage.Pairing -> false
    else -> true
}
