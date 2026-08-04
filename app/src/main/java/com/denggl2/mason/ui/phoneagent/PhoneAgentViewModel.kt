package com.denggl2.mason.ui.phoneagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.phoneagent.PhoneAgentController
import com.denggl2.mason.phoneagent.PhoneAgentLogStore
import com.denggl2.mason.phoneagent.PhoneAgentRuntimeState
import com.denggl2.mason.phoneagent.isPhoneAgentAccessibilityEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhoneAgentPermissionState(
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
) {
    val allGranted: Boolean
        get() = accessibilityEnabled && overlayEnabled
}

@HiltViewModel
class PhoneAgentViewModel @Inject constructor(
    private val configStore: ApiConfigDataStore,
    private val controller: PhoneAgentController,
    private val logStore: PhoneAgentLogStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val config = configStore.config.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ApiConfig(),
    )
    val runtime: kotlinx.coroutines.flow.StateFlow<PhoneAgentRuntimeState> = controller.runtimeState
    val logs = logStore.entries

    private val _permissions = MutableStateFlow(PhoneAgentPermissionState())
    val permissions = _permissions.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch { logStore.refresh() }
        viewModelScope.launch {
            combine(config, permissions) { currentConfig, currentPermissions ->
                currentConfig.takeIf {
                    shouldDisableScreenAssistant(it.phoneToolsEnabled, currentPermissions)
                }
            }.collectLatest { invalidConfig ->
                invalidConfig?.let {
                    configStore.updateConfig(it.copy(phoneToolsEnabled = false))
                }
            }
        }
    }

    fun refreshPermissions() {
        _permissions.value = PhoneAgentPermissionState(
            accessibilityEnabled = isPhoneAgentAccessibilityEnabled(context),
            overlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
        )
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !canEnableScreenAssistant(permissions.value)) return
        viewModelScope.launch {
            configStore.updateConfig(config.value.copy(phoneToolsEnabled = enabled))
        }
    }

    fun resumeExecution() {
        controller.resume()
    }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openOverlaySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun canEnableScreenAssistant(permissions: PhoneAgentPermissionState): Boolean =
    permissions.allGranted

internal fun shouldDisableScreenAssistant(
    enabled: Boolean,
    permissions: PhoneAgentPermissionState,
): Boolean = enabled && !canEnableScreenAssistant(permissions)
