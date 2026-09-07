package com.denggl2.mason

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.LanguagePreference
import com.denggl2.mason.data.MessageSendMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.data.toComposeColor
import com.denggl2.mason.navigation.MasonNavGraph
import com.denggl2.mason.integration.McpOAuthCoordinator
import com.denggl2.mason.localization.applyPlatformLanguage
import com.denggl2.mason.localization.toRemoteLanguagePreference
import com.denggl2.mason.tool.BatteryOptimizationTool
import com.denggl2.mason.tool.NotificationTool
import com.denggl2.mason.tool.ScreenshotTool
import com.denggl2.mason.ui.theme.MasonTheme
import com.denggl2.masonremote.notification.RemoteNotificationManager
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.ProvideRemoteLocale
import com.denggl2.masonremote.ui.resolveRemoteStrings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var notificationConversationId = mutableStateOf<Long?>(null)
    private var notificationTaskCommand = mutableStateOf<String?>(null)
    private var notificationArtifactPath = mutableStateOf<String?>(null)
    private var notificationRemoteThreadId = mutableStateOf<String?>(null)

    @Inject
    lateinit var batteryOptimizationTool: BatteryOptimizationTool

    @Inject
    lateinit var screenshotTool: ScreenshotTool

    @Inject
    lateinit var uiPreferencesDataStore: UiPreferencesDataStore

    @Inject
    lateinit var mcpOAuthCoordinator: McpOAuthCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationConversationId.value = intent.notificationConversationId()
        notificationTaskCommand.value = intent.notificationTaskCommand()
        notificationArtifactPath.value = intent.notificationArtifactPath()
        notificationRemoteThreadId.value = intent.remoteNotificationThreadId()
        lifecycleScope.launch { mcpOAuthCoordinator.handleCallback(intent?.data) }
        setContent {
            val loadedUiPreferences: UiPreferences? by
                uiPreferencesDataStore.preferences.collectAsState(initial = null)
            val uiPreferences = loadedUiPreferences ?: UiPreferences()
            val scope = rememberCoroutineScope()
            var glassTransparencyPreview by remember {
                mutableFloatStateOf(uiPreferences.glassTransparency)
            }
            var glassFrostPreview by remember {
                mutableFloatStateOf(uiPreferences.glassFrost)
            }
            LaunchedEffect(uiPreferences.glassTransparency) {
                glassTransparencyPreview = uiPreferences.glassTransparency
            }
            LaunchedEffect(uiPreferences.glassFrost) {
                glassFrostPreview = uiPreferences.glassFrost
            }
            val baseDensity = LocalDensity.current
            val remoteStrings = resolveRemoteStrings(uiPreferences.language.toRemoteLanguagePreference())
            LaunchedEffect(loadedUiPreferences?.language) {
                loadedUiPreferences?.let { applyPlatformLanguage(it.language) }
            }
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (uiPreferences.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
            }

            MasonTheme(
                themeMode = uiPreferences.themeMode,
                interfaceStyle = uiPreferences.interfaceStyle,
                glassRefractionEnabled = uiPreferences.glassRefractionEnabled,
                glassTransparency = glassTransparencyPreview,
                glassFrost = glassFrostPreview,
            ) {
                CompositionLocalProvider(
                    LocalRemoteStrings provides remoteStrings,
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * uiPreferences.fontSize.scale,
                    ),
                ) {
                    ProvideRemoteLocale {
                    val windowBackground = MaterialTheme.colorScheme.background.toArgb()
                    SideEffect {
                        window.decorView.setBackgroundColor(windowBackground)
                    }
                    MasonNavGraph(
                    uiPreferences = uiPreferences.copy(
                        glassTransparency = glassTransparencyPreview,
                        glassFrost = glassFrostPreview,
                    ),
                    openConversationId = notificationConversationId.value,
                    notificationTaskCommand = notificationTaskCommand.value,
                    notificationArtifactPath = notificationArtifactPath.value,
                    notificationRemoteThreadId = notificationRemoteThreadId.value,
                    onRemoteNotificationConsumed = { notificationRemoteThreadId.value = null },
                    onThemeModeChange = { mode ->
                        scope.launch { uiPreferencesDataStore.updateThemeMode(mode) }
                    },
                    onInterfaceStyleChange = { style ->
                        scope.launch { uiPreferencesDataStore.updateInterfaceStyle(style) }
                    },
                    onGlassRefractionChange = { enabled ->
                        scope.launch {
                            uiPreferencesDataStore.updateGlassRefractionEnabled(enabled)
                        }
                    },
                    onGlassTransparencyPreview = { transparency ->
                        glassTransparencyPreview = transparency
                    },
                    onGlassTransparencyCommit = { transparency ->
                        scope.launch {
                            uiPreferencesDataStore.updateGlassTransparency(transparency)
                        }
                    },
                    onGlassFrostPreview = { frost ->
                        glassFrostPreview = frost
                    },
                    onGlassFrostCommit = { frost ->
                        scope.launch {
                            uiPreferencesDataStore.updateGlassFrost(frost)
                        }
                    },
                    onAccentColorChange = { color ->
                        scope.launch { uiPreferencesDataStore.updateAccentColor(color) }
                    },
                    onRegularNotificationsChange = { enabled ->
                        scope.launch { uiPreferencesDataStore.updateRegularNotificationsEnabled(enabled) }
                    },
                    onIslandNotificationsChange = { enabled ->
                        scope.launch { uiPreferencesDataStore.updateIslandNotificationsEnabled(enabled) }
                    },
                    onFontSizeChange = { fontSize ->
                        scope.launch { uiPreferencesDataStore.updateFontSize(fontSize) }
                    },
                    onLanguageChange = { language ->
                        scope.launch { uiPreferencesDataStore.updateLanguage(language) }
                    },
                     onMessageSendModeChange = { mode ->
                        scope.launch { uiPreferencesDataStore.updateMessageSendMode(mode) }
                    },
                    )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationConversationId.value = intent.notificationConversationId()
        notificationTaskCommand.value = intent.notificationTaskCommand()
        notificationArtifactPath.value = intent.notificationArtifactPath()
        notificationRemoteThreadId.value = intent.remoteNotificationThreadId()
        lifecycleScope.launch { mcpOAuthCoordinator.handleCallback(intent.data) }
    }

    override fun onResume() {
        super.onResume()
        AppForegroundState.onActivityResumed()
    }

    override fun onPause() {
        AppForegroundState.onActivityPaused()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ScreenshotTool.REQUEST_CODE_MEDIA_PROJECTION -> {
                ScreenshotTool.onAuthResult(resultCode, data)
            }
        }
    }
}

private fun Intent?.notificationConversationId(): Long? =
    takeIf { it?.action == NotificationTool.ACTION_OPEN_TASK }
        ?.getLongExtra(NotificationTool.EXTRA_CONVERSATION_ID, -1L)
        ?.takeIf { it > 0L }

private fun Intent?.remoteNotificationThreadId(): String? =
    takeIf { it?.action == RemoteNotificationManager.ACTION_OPEN_TASK }
        ?.getStringExtra(RemoteNotificationManager.EXTRA_THREAD_ID)

private fun Intent?.notificationTaskCommand(): String? =
    takeIf { it?.action == NotificationTool.ACTION_OPEN_TASK }
        ?.getStringExtra(NotificationTool.EXTRA_TASK_COMMAND)
        ?.takeIf {
            it == NotificationTool.TASK_COMMAND_RESUME || it == NotificationTool.TASK_COMMAND_CANCEL
                || it == NotificationTool.TASK_COMMAND_OPEN_ARTIFACT
        }

private fun Intent?.notificationArtifactPath(): String? =
    takeIf { it?.action == NotificationTool.ACTION_OPEN_TASK }
        ?.getStringExtra(NotificationTool.EXTRA_ARTIFACT_PATH)
        ?.takeIf { it.isNotBlank() }
