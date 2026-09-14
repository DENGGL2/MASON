package com.denggl2.mason.sync.remote

import android.content.Context
import com.denggl2.mason.protocol.MasonProtocolJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class PairedConnector(
    val connectorDeviceId: String,
    val endpoint: String,
    val tlsCertificateSha256: String,
    val pairedAt: Long,
    val displayName: String = "电脑",
)

@Singleton
class PairedConnectorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _connector = MutableStateFlow(readStoredConnector())
    val connector: StateFlow<PairedConnector?> = _connector.asStateFlow()

    fun save(connector: PairedConnector) {
        check(
            preferences.edit()
                .putString(CONNECTOR_KEY, MasonProtocolJson.encode(connector))
                .commit(),
        ) { "Cannot persist paired Connector" }
        _connector.value = connector
    }

    fun load(): PairedConnector? = _connector.value

    fun clear() {
        check(preferences.edit().remove(CONNECTOR_KEY).commit()) {
            "Cannot clear paired Connector"
        }
        _connector.value = null
    }

    private fun readStoredConnector(): PairedConnector? = preferences.getString(CONNECTOR_KEY, null)?.let { encoded ->
        runCatching { MasonProtocolJson.decode<PairedConnector>(encoded) }.getOrNull()
    }

    private companion object {
        const val PREFERENCES_NAME = "mason_paired_connector"
        const val CONNECTOR_KEY = "active_connector"
    }
}
