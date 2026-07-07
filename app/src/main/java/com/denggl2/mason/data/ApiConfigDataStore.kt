package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        private val KEY_API_URL = stringPreferencesKey("api_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")

        const val DEFAULT_URL = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL = "deepseek-chat"
    }

    val config: Flow<ApiConfig> = context.dataStore.data.map { prefs ->
        ApiConfig(
            apiUrl = prefs[KEY_API_URL] ?: DEFAULT_URL,
            apiKey = prefs[KEY_API_KEY] ?: "",
            model = prefs[KEY_MODEL] ?: DEFAULT_MODEL,
        )
    }

    suspend fun updateConfig(config: ApiConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_URL] = config.apiUrl
            prefs[KEY_API_KEY] = config.apiKey
            prefs[KEY_MODEL] = config.model
        }
    }
}
