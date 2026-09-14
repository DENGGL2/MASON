package com.denggl2.mason.ui.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConversationDraftStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(connectorDeviceId: String): String = preferences
        .getString(remoteConversationDraftKey(connectorDeviceId), "")
        .orEmpty()

    fun write(connectorDeviceId: String, value: String) {
        val key = remoteConversationDraftKey(connectorDeviceId)
        preferences.edit().apply {
            if (value.isEmpty()) remove(key) else putString(key, value)
        }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "remote_conversation_drafts"
    }
}

internal fun remoteConversationDraftKey(connectorDeviceId: String): String =
    "${connectorDeviceId.length}:$connectorDeviceId:new"
