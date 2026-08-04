package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPreferencesStore by preferencesDataStore(name = "ui_preferences")

@Singleton
class UiPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_INTERFACE_STYLE = stringPreferencesKey("interface_style")
        val KEY_LIQUID_GLASS_TRANSPARENCY = floatPreferencesKey("liquid_glass_transparency")
        val KEY_ACCENT_COLOR = longPreferencesKey("accent_color")
        // Legacy keys are retained for a one-way migration from older builds.
        val KEY_NOTIFICATION_ISLAND_ENABLED = booleanPreferencesKey("notification_island_enabled")
        val KEY_NOTIFICATION_DELIVERY_MODE = stringPreferencesKey("notification_delivery_mode")
        val KEY_REGULAR_NOTIFICATIONS_ENABLED = booleanPreferencesKey("regular_notifications_enabled")
        val KEY_ISLAND_NOTIFICATIONS_ENABLED = booleanPreferencesKey("island_notifications_enabled")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
    }

    val preferences: Flow<UiPreferences> = context.uiPreferencesStore.data.map { prefs ->
        UiPreferences(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            interfaceStyle = prefs[KEY_INTERFACE_STYLE]
                ?.let { value -> InterfaceStyle.entries.firstOrNull { it.name == value } }
                ?: InterfaceStyle.ACRYLIC,
            liquidGlassTransparency = prefs[KEY_LIQUID_GLASS_TRANSPARENCY]
                ?.coerceIn(0f, 1f)
                ?: 0.72f,
            accentColor = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR,
            regularNotificationsEnabled = prefs[KEY_REGULAR_NOTIFICATIONS_ENABLED]
                ?: legacyNotificationsEnabled(
                    storedMode = prefs[KEY_NOTIFICATION_DELIVERY_MODE],
                    legacyEnabled = prefs[KEY_NOTIFICATION_ISLAND_ENABLED],
                ),
            islandNotificationsEnabled = prefs[KEY_ISLAND_NOTIFICATIONS_ENABLED] ?: false,
            fontSize = prefs[KEY_FONT_SIZE]
                ?.let { value -> FontSizePreference.entries.firstOrNull { it.name == value } }
                ?: FontSizePreference.MEDIUM,
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun updateInterfaceStyle(style: InterfaceStyle) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_INTERFACE_STYLE] = style.name
        }
    }

    suspend fun updateLiquidGlassTransparency(transparency: Float) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_LIQUID_GLASS_TRANSPARENCY] = transparency.coerceIn(0f, 1f)
        }
    }

    suspend fun updateAccentColor(color: Long) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_ACCENT_COLOR] = color
        }
    }

    suspend fun updateRegularNotificationsEnabled(enabled: Boolean) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_REGULAR_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateIslandNotificationsEnabled(enabled: Boolean) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_ISLAND_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateFontSize(fontSize: FontSizePreference) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = fontSize.name
        }
    }
}

internal fun legacyNotificationsEnabled(
    storedMode: String?,
    legacyEnabled: Boolean?,
): Boolean = when {
    legacyEnabled == false -> false
    storedMode == "DISABLED" -> false
    storedMode != null -> true
    else -> legacyEnabled == true
}
