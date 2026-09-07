package com.denggl2.masonremote.data

import android.content.Context
import com.denggl2.masonremote.ui.settings.RemoteFontSizePreference
import com.denggl2.masonremote.ui.settings.RemoteInterfaceStyle
import com.denggl2.masonremote.ui.settings.RemoteLanguagePreference
import com.denggl2.masonremote.ui.settings.RemoteMessageSendMode
import com.denggl2.masonremote.ui.settings.RemoteThemeMode
import com.denggl2.masonremote.ui.settings.TaskNotificationMode

private const val PREFS_NAME = "mason_remote_preferences"
private const val KEY_NOTIFICATION_MODE = "task_notification_mode"
private const val KEY_MESSAGE_SEND_MODE = "message_send_mode"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_INTERFACE_STYLE = "interface_style"
private const val KEY_FONT_SIZE = "font_size"
private const val KEY_LANGUAGE = "language"
private const val KEY_GLASS_REFRACTION = "glass_refraction"
private const val KEY_GLASS_TRANSPARENCY = "glass_transparency"
private const val KEY_GLASS_FROST = "glass_frost"
private const val KEY_COMPLETION_NOTIFICATION_BASELINE_PREFIX = "completion_notification_baseline_"
private const val KEY_COMPLETION_NOTIFICATION_BASELINE_SEEDED_PREFIX = "completion_notification_baseline_seeded_"
private const val KEY_COMPLETION_NOTIFICATION_SEEN_PREFIX = "completion_notification_seen_"

class RemotePreferences(private val context: Context) {
    private val preferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var taskNotificationMode: TaskNotificationMode
        get() = preferences
            .getString(KEY_NOTIFICATION_MODE, TaskNotificationMode.REGULAR.name)
            ?.let { value -> TaskNotificationMode.entries.firstOrNull { it.name == value } }
            ?: TaskNotificationMode.REGULAR
        set(value) {
            preferences.edit().putString(KEY_NOTIFICATION_MODE, value.name).apply()
        }

    var messageSendMode: RemoteMessageSendMode
        get() = preferences
            .getString(KEY_MESSAGE_SEND_MODE, RemoteMessageSendMode.QUEUE.name)
            ?.let { value -> RemoteMessageSendMode.entries.firstOrNull { it.name == value } }
            ?: RemoteMessageSendMode.QUEUE
        set(value) {
            preferences.edit().putString(KEY_MESSAGE_SEND_MODE, value.name).apply()
        }

    var themeMode: RemoteThemeMode
        get() = preferences.getString(KEY_THEME_MODE, RemoteThemeMode.SYSTEM.name)
            ?.let { value -> RemoteThemeMode.entries.firstOrNull { it.name == value } }
            ?: RemoteThemeMode.SYSTEM
        set(value) { preferences.edit().putString(KEY_THEME_MODE, value.name).apply() }

    var interfaceStyle: RemoteInterfaceStyle
        get() = preferences.getString(KEY_INTERFACE_STYLE, RemoteInterfaceStyle.NATIVE.name)
            ?.let { value -> RemoteInterfaceStyle.entries.firstOrNull { it.name == value } }
            ?: RemoteInterfaceStyle.NATIVE
        set(value) { preferences.edit().putString(KEY_INTERFACE_STYLE, value.name).apply() }

    var fontSize: RemoteFontSizePreference
        get() = preferences.getString(KEY_FONT_SIZE, RemoteFontSizePreference.MEDIUM.name)
            ?.let { value -> RemoteFontSizePreference.entries.firstOrNull { it.name == value } }
            ?: RemoteFontSizePreference.MEDIUM
        set(value) { preferences.edit().putString(KEY_FONT_SIZE, value.name).apply() }

    var language: RemoteLanguagePreference
        get() = preferences.getString(KEY_LANGUAGE, RemoteLanguagePreference.SYSTEM.name)
            ?.let { value -> RemoteLanguagePreference.entries.firstOrNull { it.name == value } }
            ?: RemoteLanguagePreference.SYSTEM
        set(value) { preferences.edit().putString(KEY_LANGUAGE, value.name).apply() }

    var glassRefractionEnabled: Boolean
        get() = preferences.getBoolean(KEY_GLASS_REFRACTION, true)
        set(value) { preferences.edit().putBoolean(KEY_GLASS_REFRACTION, value).apply() }

    var glassTransparency: Float
        get() = preferences.getFloat(KEY_GLASS_TRANSPARENCY, 0.90f)
        set(value) { preferences.edit().putFloat(KEY_GLASS_TRANSPARENCY, value).apply() }

    var glassFrost: Float
        get() = preferences.getFloat(KEY_GLASS_FROST, 0.10f)
        set(value) { preferences.edit().putFloat(KEY_GLASS_FROST, value).apply() }

    fun completionNotificationBaselineAt(scopeId: String, now: Long = System.currentTimeMillis()): Long {
        val key = KEY_COMPLETION_NOTIFICATION_BASELINE_PREFIX + scopeId
        if (!preferences.contains(key)) {
            preferences.edit().putLong(key, now).apply()
        }
        return preferences.getLong(key, now)
    }

    fun isCompletionNotificationBaselineSeeded(scopeId: String): Boolean =
        preferences.getBoolean(KEY_COMPLETION_NOTIFICATION_BASELINE_SEEDED_PREFIX + scopeId, false)

    fun markCompletionNotificationBaselineSeeded(scopeId: String) {
        preferences.edit()
            .putBoolean(KEY_COMPLETION_NOTIFICATION_BASELINE_SEEDED_PREFIX + scopeId, true)
            .apply()
    }

    fun lastSeenCompletionId(scopeId: String, threadId: String): String? =
        preferences.getString(completionNotificationSeenKey(scopeId, threadId), null)

    fun markCompletionSeen(scopeId: String, threadId: String, completionId: String) {
        preferences.edit()
            .putString(completionNotificationSeenKey(scopeId, threadId), completionId)
            .apply()
    }

    private fun completionNotificationSeenKey(scopeId: String, threadId: String): String =
        KEY_COMPLETION_NOTIFICATION_SEEN_PREFIX + scopeId + "_" + threadId
}
