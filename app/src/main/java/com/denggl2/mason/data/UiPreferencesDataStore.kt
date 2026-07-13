package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val KEY_ACCENT_COLOR = longPreferencesKey("accent_color")
        val KEY_NOTIFICATION_ISLAND_ENABLED = booleanPreferencesKey("notification_island_enabled")
        val KEY_NOTIFICATION_DELIVERY_MODE = stringPreferencesKey("notification_delivery_mode")
        val KEY_NOTIFY_ON_TASK_COMPLETE = booleanPreferencesKey("notify_on_task_complete")
        val KEY_NOTIFY_ON_PAYMENT_SUCCESS = booleanPreferencesKey("notify_on_payment_success")
        val KEY_ISLAND_VENDOR_MODE = stringPreferencesKey("island_vendor_mode")
    }

    val preferences: Flow<UiPreferences> = context.uiPreferencesStore.data.map { prefs ->
        UiPreferences(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            accentColor = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR,
            notificationIslandEnabled = prefs[KEY_NOTIFICATION_ISLAND_ENABLED] ?: false,
            notificationDeliveryMode = prefs[KEY_NOTIFICATION_DELIVERY_MODE]
                ?.let { value -> NotificationDeliveryMode.entries.firstOrNull { it.name == value } }
                ?: NotificationDeliveryMode.REGULAR,
            notifyOnTaskComplete = prefs[KEY_NOTIFY_ON_TASK_COMPLETE] ?: true,
            notifyOnPaymentSuccess = prefs[KEY_NOTIFY_ON_PAYMENT_SUCCESS] ?: true,
            islandVendorMode = prefs[KEY_ISLAND_VENDOR_MODE]
                ?.let { value -> IslandVendorMode.entries.firstOrNull { it.name == value } }
                ?: IslandVendorMode.AUTO,
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun updateAccentColor(color: Long) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_ACCENT_COLOR] = color
        }
    }

    suspend fun updateNotificationIslandEnabled(enabled: Boolean) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_ISLAND_ENABLED] = enabled
        }
    }

    suspend fun updateNotificationDeliveryMode(mode: NotificationDeliveryMode) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_DELIVERY_MODE] = mode.name
        }
    }

    suspend fun updateNotifyOnTaskComplete(enabled: Boolean) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_NOTIFY_ON_TASK_COMPLETE] = enabled
        }
    }

    suspend fun updateNotifyOnPaymentSuccess(enabled: Boolean) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_NOTIFY_ON_PAYMENT_SUCCESS] = enabled
        }
    }

    suspend fun updateIslandVendorMode(mode: IslandVendorMode) {
        context.uiPreferencesStore.edit { prefs ->
            prefs[KEY_ISLAND_VENDOR_MODE] = mode.name
        }
    }
}
