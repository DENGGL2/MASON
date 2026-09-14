package com.denggl2.masonremote.data

import android.content.Context
import com.denggl2.masonremote.transport.PairedConnector
import com.denggl2.masonremote.transport.PairingRoutePayload
import com.denggl2.masonremote.transport.RemoteProtocolJson
import com.denggl2.masonremote.transport.TransportMode
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "mason_remote_pairing"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_DEVICE_NAME = "device_name"
private const val KEY_CONNECTOR_DEVICE_ID = "connector_device_id"
private const val KEY_ENDPOINT = "endpoint"
private const val KEY_TLS_CERTIFICATE_SHA256 = "tls_certificate_sha256"
private const val KEY_PAIRED_AT = "paired_at"
private const val KEY_TRANSPORT_MODE = "transport_mode"
private const val KEY_ROUTE_BOOTSTRAP = "route_bootstrap"
private const val KEY_OFFER_ID = "offer_id"
private const val KEY_CONNECTORS = "connectors"
private const val KEY_ACTIVE_TRANSPORT_MODE = "active_transport_mode"

/** Persists the authorized device identity and route, never the one-time QR token. */
class PairingStore(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _connector = MutableStateFlow(load())

    /** Emits the active connector whenever another Remote surface changes pairing. */
    val connector: StateFlow<PairedConnector?> = _connector.asStateFlow()

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _connector.value = load()
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val isPaired: Boolean
        get() = _connector.value != null

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                preferences.edit().putString(KEY_DEVICE_ID, id).apply()
            }

    val deviceName: String
        get() = preferences.getString(KEY_DEVICE_NAME, "电脑") ?: "电脑"

    /**
     * Host-facing read access for screens outside the Remote library.
     * Pairing records remain owned by this store so embedded MASON and the
     * standalone Remote surface cannot drift to different formats.
     */
    fun currentConnector(): PairedConnector? = _connector.value

    /** Clear the active route while preserving any alternate transport route. */
    fun clearCurrentConnector() = clearActive()

    internal fun availableTransportModes(): Set<TransportMode> = loadAll()
        .map(PairedConnector::transportMode)
        .toSet()

    internal fun activate(mode: TransportMode): Boolean {
        if (load(mode) == null) return false
        preferences.edit().putString(KEY_ACTIVE_TRANSPORT_MODE, mode.name).apply()
        _connector.value = load()
        return true
    }

    internal fun markPaired(connector: PairedConnector) {
        val nextConnectors = loadAll()
            .filterNot {
                it.connectorDeviceId == connector.connectorDeviceId &&
                    it.transportMode == connector.transportMode
            }
            .plus(connector)
        preferences.edit()
            .putString(KEY_DEVICE_ID, connector.deviceId)
            .putString(KEY_DEVICE_NAME, connector.displayName.ifBlank { "电脑" })
            .putString(KEY_CONNECTOR_DEVICE_ID, connector.connectorDeviceId)
            .putString(KEY_ENDPOINT, connector.endpoint)
            .putString(KEY_TLS_CERTIFICATE_SHA256, connector.tlsCertificateSha256)
            .putLong(KEY_PAIRED_AT, connector.pairedAt)
            .putString(KEY_TRANSPORT_MODE, connector.transportMode.name)
            .putString(KEY_ROUTE_BOOTSTRAP, RemoteProtocolJson.encode(connector.routeBootstrap))
            .putString(KEY_OFFER_ID, connector.offerId)
            .putString(KEY_ACTIVE_TRANSPORT_MODE, connector.transportMode.name)
            .putString(KEY_CONNECTORS, RemoteProtocolJson.encode(nextConnectors))
            .apply()
        _connector.value = load()
    }

    internal fun load(): PairedConnector? {
        val activeMode = preferences.getString(KEY_ACTIVE_TRANSPORT_MODE, null)
            ?.let { value -> runCatching { TransportMode.valueOf(value) }.getOrNull() }
        return activeMode?.let(::load) ?: loadAll().maxByOrNull(PairedConnector::pairedAt)
    }

    internal fun load(mode: TransportMode): PairedConnector? = loadAll()
        .firstOrNull { it.transportMode == mode }

    private fun loadAll(): List<PairedConnector> {
        val stored = preferences.getString(KEY_CONNECTORS, null)
            ?.let { value -> runCatching { RemoteProtocolJson.decode<List<PairedConnector>>(value) }.getOrNull() }
        if (!stored.isNullOrEmpty()) return stored

        return loadLegacy()?.let(::listOf).orEmpty()
    }

    private fun loadLegacy(): PairedConnector? {
        val connectorDeviceId = preferences.getString(KEY_CONNECTOR_DEVICE_ID, null) ?: return null
        val endpoint = preferences.getString(KEY_ENDPOINT, null) ?: return null
        val certificate = preferences.getString(KEY_TLS_CERTIFICATE_SHA256, null) ?: return null
        val transportMode = preferences.getString(KEY_TRANSPORT_MODE, null)
            ?.let { value -> runCatching { TransportMode.valueOf(value) }.getOrNull() }
            ?: TransportMode.LOCAL_TLS
        val routeBootstrap = preferences.getString(KEY_ROUTE_BOOTSTRAP, null)
            ?.let { value -> runCatching { RemoteProtocolJson.decode<PairingRoutePayload>(value) }.getOrNull() }
            ?: PairingRoutePayload(endpoint = endpoint)
        val offerId = preferences.getString(KEY_OFFER_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: connectorDeviceId
        return PairedConnector(
            connectorDeviceId = connectorDeviceId,
            endpoint = endpoint,
            tlsCertificateSha256 = certificate,
            pairedAt = preferences.getLong(KEY_PAIRED_AT, 0L),
            displayName = deviceName,
            deviceId = deviceId,
            transportMode = transportMode,
            routeBootstrap = routeBootstrap,
            offerId = offerId,
        )
    }

    internal fun clearActive() {
        val active = load() ?: return clear()
        val remaining = loadAll().filterNot {
            it.connectorDeviceId == active.connectorDeviceId && it.transportMode == active.transportMode
        }
        if (remaining.isEmpty()) {
            clear()
            return
        }
        val next = remaining.maxByOrNull(PairedConnector::pairedAt) ?: return clear()
        preferences.edit()
            .putString(KEY_DEVICE_ID, next.deviceId)
            .putString(KEY_DEVICE_NAME, next.displayName.ifBlank { "电脑" })
            .putString(KEY_ACTIVE_TRANSPORT_MODE, next.transportMode.name)
            .putString(KEY_CONNECTORS, RemoteProtocolJson.encode(remaining))
            .apply()
        _connector.value = load()
    }

    fun clear() {
        preferences.edit().clear().apply()
        _connector.value = null
    }
}
