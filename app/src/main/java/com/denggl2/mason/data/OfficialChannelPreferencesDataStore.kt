package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.officialChannelStore by preferencesDataStore(name = "official_channels")

@Singleton
class OfficialChannelPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val KEY_WECHAT_OFFICIAL = booleanPreferencesKey("wechat_official_enabled")
        val KEY_ALIPAY_MCP = booleanPreferencesKey("alipay_mcp_enabled")
        val KEY_MEITUAN_MCP = booleanPreferencesKey("meituan_mcp_enabled")
    }

    val preferences: Flow<OfficialChannelPreferences> = context.officialChannelStore.data.map { prefs ->
        OfficialChannelPreferences(
            wechatOfficialEnabled = prefs[KEY_WECHAT_OFFICIAL] ?: false,
            alipayMcpEnabled = prefs[KEY_ALIPAY_MCP] ?: false,
            meituanMcpEnabled = prefs[KEY_MEITUAN_MCP] ?: false,
        )
    }

    suspend fun updateWechatOfficialEnabled(enabled: Boolean) {
        context.officialChannelStore.edit { it[KEY_WECHAT_OFFICIAL] = enabled }
    }

    suspend fun updateAlipayMcpEnabled(enabled: Boolean) {
        context.officialChannelStore.edit { it[KEY_ALIPAY_MCP] = enabled }
    }

    suspend fun updateMeituanMcpEnabled(enabled: Boolean) {
        context.officialChannelStore.edit { it[KEY_MEITUAN_MCP] = enabled }
    }
}
