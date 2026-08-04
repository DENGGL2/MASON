package com.denggl2.mason.ui.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.MASON_PROTOCOL_VERSION
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.remote.PairedConnector
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedPairingHttpsClient
import com.denggl2.mason.sync.remote.RemotePairingException
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DevicePairingUiState {
    data object Scanning : DevicePairingUiState
    data class Confirming(val bootstrap: PairingBootstrap) : DevicePairingUiState
    data class Pairing(val bootstrap: PairingBootstrap) : DevicePairingUiState
    data class Paired(val connector: PairedConnector) : DevicePairingUiState
    data class Error(val message: String) : DevicePairingUiState
}

@HiltViewModel
class DevicePairingViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val connectorStore: PairedConnectorStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DevicePairingUiState>(DevicePairingUiState.Scanning)
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    fun acceptScan(rawValue: String) {
        if (_uiState.value !is DevicePairingUiState.Scanning) return
        _uiState.value = runCatching {
            DevicePairingUiState.Confirming(decodePairingBootstrap(rawValue))
        }.getOrElse {
            DevicePairingUiState.Error("这不是有效的 MASON 设备配对二维码")
        }
    }

    fun scanAgain() {
        _uiState.value = DevicePairingUiState.Scanning
    }

    fun confirmPairing() {
        val bootstrap = (_uiState.value as? DevicePairingUiState.Confirming)?.bootstrap ?: return
        _uiState.value = DevicePairingUiState.Pairing(bootstrap)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val deviceId = syncManager.getLocalDeviceId()
                    val client = PinnedPairingHttpsClient(bootstrap, AndroidDeviceIdentityStore())
                    try {
                        client.pair(
                            deviceId = deviceId,
                            displayName = Build.MODEL.ifBlank { "Android device" },
                            capabilities = setOf(
                                DeviceCapability.ANDROID_TOOLS,
                                DeviceCapability.FILE_RECEIVE,
                            ),
                            requestedPermissions = setOf(
                                DevicePermission.VIEW_SHARED_CONVERSATIONS,
                                DevicePermission.SEND_MESSAGES,
                                DevicePermission.CONTROL_EXECUTION,
                                DevicePermission.RESOLVE_APPROVALS,
                                DevicePermission.REQUEST_FILES,
                            ),
                        )
                    } catch (error: RemotePairingException) {
                        if (error.errorCode != "DEVICE_ALREADY_PAIRED") throw error
                    }
                    val grant = client.authenticate(deviceId)
                    client.getSession(grant.sessionToken)
                    PairedConnector(
                        connectorDeviceId = bootstrap.offer.connectorDeviceId,
                        endpoint = bootstrap.endpoint,
                        tlsCertificateSha256 = bootstrap.tlsCertificateSha256.lowercase(),
                        pairedAt = System.currentTimeMillis(),
                        displayName = bootstrap.connectorDisplayName.trim().ifBlank { "电脑" },
                    ).also(connectorStore::save)
                }
            }.onSuccess { connector ->
                _uiState.value = DevicePairingUiState.Paired(connector)
            }.onFailure { error ->
                _uiState.value = DevicePairingUiState.Error(
                    error.message?.takeIf(String::isNotBlank) ?: "无法连接到电脑，请确认两台设备在同一私有网络",
                )
            }
        }
    }
}

internal fun decodePairingBootstrap(
    rawValue: String,
    now: Long = System.currentTimeMillis(),
): PairingBootstrap {
    val bootstrap = MasonProtocolJson.decode<PairingBootstrap>(rawValue.trim())
    require(bootstrap.protocolVersion == MASON_PROTOCOL_VERSION)
    require(bootstrap.offer.protocolVersion == MASON_PROTOCOL_VERSION)
    require(bootstrap.offer.expiresAt > now)
    require(bootstrap.offer.pairingId.isNotBlank())
    require(bootstrap.offer.connectorDeviceId.isNotBlank())
    require(bootstrap.offer.oneTimeToken.isNotBlank())
    val endpoint = URI(bootstrap.endpoint)
    require(endpoint.scheme.equals("https", ignoreCase = true))
    require(!endpoint.host.isNullOrBlank())
    val fingerprint = bootstrap.tlsCertificateSha256.trim()
    require(fingerprint.length == 64 && fingerprint.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' })
    return bootstrap.copy(
        endpoint = bootstrap.endpoint.trimEnd('/'),
        tlsCertificateSha256 = fingerprint.lowercase(),
    )
}
