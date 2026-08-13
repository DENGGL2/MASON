package com.denggl2.mason.ui.remote

import android.content.Context
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RemoteConversationReadStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _seenCompletionVersions = MutableStateFlow(loadSeenVersions())
    val seenCompletionVersions: StateFlow<Map<String, String>> = _seenCompletionVersions.asStateFlow()

    fun markCompletionSeen(
        connectorDeviceId: String,
        conversation: RemoteConversationSummary,
    ) {
        val completionId = conversation.latestCompletionId ?: return
        val key = remoteConversationReadKey(connectorDeviceId, conversation.threadId)
        if (_seenCompletionVersions.value[key] == completionId) return
        _seenCompletionVersions.value = _seenCompletionVersions.value + (key to completionId)
        preferences.edit().putString(key, completionId).apply()
    }

    fun isCompletionUnread(
        connectorDeviceId: String,
        conversation: RemoteConversationSummary,
    ): Boolean {
        val key = remoteConversationReadKey(connectorDeviceId, conversation.threadId)
        return isRemoteCompletionUnread(
            status = conversation.executionStatus,
            completionId = conversation.latestCompletionId,
            seenVersion = _seenCompletionVersions.value[key],
        )
    }

    private fun loadSeenVersions(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    private companion object {
        const val PREFERENCES_NAME = "remote_conversation_read_state"
    }
}

internal fun remoteConversationReadKey(connectorDeviceId: String, threadId: String): String =
    "${connectorDeviceId.length}:$connectorDeviceId$threadId"

internal fun isRemoteCompletionUnread(
    status: RemoteExecutionStatus,
    completionId: String?,
    seenVersion: String?,
): Boolean = status == RemoteExecutionStatus.COMPLETED &&
    completionId != null &&
    completionId != seenVersion
