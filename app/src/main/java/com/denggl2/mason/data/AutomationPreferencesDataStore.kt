package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.automationPreferencesStore by preferencesDataStore(name = "automation_preferences")

data class AutomationPreferences(
    val backgroundExecutionEnabled: Boolean = false,
)

@Singleton
class AutomationPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val KEY_BACKGROUND_EXECUTION_ENABLED = booleanPreferencesKey("background_execution_enabled")
    }

    val preferences: Flow<AutomationPreferences> = context.automationPreferencesStore.data.map { prefs ->
        AutomationPreferences(
            backgroundExecutionEnabled = prefs[KEY_BACKGROUND_EXECUTION_ENABLED] ?: false,
        )
    }

    suspend fun updateBackgroundExecutionEnabled(enabled: Boolean) {
        context.automationPreferencesStore.edit { prefs ->
            prefs[KEY_BACKGROUND_EXECUTION_ENABLED] = enabled
        }
    }
}
