package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_settings")

@Singleton
class ApiConfigDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        private val KEY_API_URL = stringPreferencesKey("api_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_TOOLS_ENABLED = booleanPreferencesKey("tools_enabled")

        val DEFAULT_PROVIDER = AiProviderCatalog.defaultProvider
    }

    val config: Flow<ApiConfig> = context.dataStore.data.map { prefs ->
        val storedUrl = prefs[KEY_API_URL]
        val providerId = prefs[KEY_PROVIDER_ID]
            ?: storedUrl?.let(AiProviderCatalog::inferProviderId)
            ?: AiProviderCatalog.DEFAULT_PROVIDER_ID
        val provider = AiProviderCatalog.getProvider(providerId)
            ?: AiProviderCatalog.defaultProvider
        val storedModel = prefs[KEY_MODEL]

        ApiConfig(
            providerId = provider.id,
            apiUrl = storedUrl ?: provider.apiUrl,
            apiKey = prefs[KEY_API_KEY] ?: "",
            model = when (storedModel) {
                "openrouter/free" -> provider.defaultModel
                null -> provider.defaultModel
                else -> storedModel
            },
            toolsEnabled = prefs[KEY_TOOLS_ENABLED] ?: provider.toolsEnabledByDefault,
        )
    }

    suspend fun updateConfig(config: ApiConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = config.providerId
            prefs[KEY_API_URL] = config.apiUrl
            prefs[KEY_API_KEY] = config.apiKey
            prefs[KEY_MODEL] = config.model
            prefs[KEY_TOOLS_ENABLED] = config.toolsEnabled
        }
    }
}
