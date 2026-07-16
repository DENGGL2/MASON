package com.denggl2.mason

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.data.toComposeColor
import com.denggl2.mason.navigation.MasonNavGraph
import com.denggl2.mason.integration.McpOAuthCoordinator
import com.denggl2.mason.tool.BatteryOptimizationTool
import com.denggl2.mason.tool.ScreenshotTool
import com.denggl2.mason.ui.theme.MasonTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
        lifecycleScope.launch { mcpOAuthCoordinator.handleCallback(intent?.data) }
        setContent {
            val uiPreferences by uiPreferencesDataStore.preferences.collectAsState(initial = UiPreferences())
            val scope = rememberCoroutineScope()
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
                accentColor = uiPreferences.accentColor.toComposeColor(),
            ) {
                MasonNavGraph(
                    uiPreferences = uiPreferences,
                    onThemeModeChange = { mode ->
                        scope.launch { uiPreferencesDataStore.updateThemeMode(mode) }
                    },
                    onAccentColorChange = { color ->
                        scope.launch { uiPreferencesDataStore.updateAccentColor(color) }
                    },
                    onNotificationIslandEnabledChange = { enabled ->
                        scope.launch { uiPreferencesDataStore.updateNotificationIslandEnabled(enabled) }
                    },
                    onNotificationDeliveryModeChange = { mode ->
                        scope.launch { uiPreferencesDataStore.updateNotificationDeliveryMode(mode) }
                    },
                    onNotifyOnTaskCompleteChange = { enabled ->
                        scope.launch { uiPreferencesDataStore.updateNotifyOnTaskComplete(enabled) }
                    },
                    onNotifyOnPaymentSuccessChange = { enabled ->
                        scope.launch { uiPreferencesDataStore.updateNotifyOnPaymentSuccess(enabled) }
                    },
                    onIslandVendorModeChange = { mode ->
                        scope.launch { uiPreferencesDataStore.updateIslandVendorMode(mode) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { mcpOAuthCoordinator.handleCallback(intent.data) }
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
