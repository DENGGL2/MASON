package com.denggl2.mason.ui.remote

import android.content.Context
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteComposerOptionsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(connectorDeviceId: String): RemoteComposerOptions? = preferences
        .getString(optionsKey(connectorDeviceId), null)
        ?.let { encoded -> runCatching { MasonProtocolJson.decode<RemoteComposerOptions>(encoded) }.getOrNull() }
        ?.takeIf(RemoteComposerOptions::hasSelectableOptions)

    @Synchronized
    fun write(connectorDeviceId: String, options: RemoteComposerOptions) {
        mergeAndWrite(connectorDeviceId, options)
    }

    @Synchronized
    fun mergeAndWrite(
        connectorDeviceId: String,
        options: RemoteComposerOptions,
    ): RemoteComposerOptions {
        val merged = mergeRemoteComposerOptions(options, readRaw(connectorDeviceId))
        if (merged.hasCatalogOptions()) {
            preferences.edit()
                .putString(optionsKey(connectorDeviceId), MasonProtocolJson.encode(merged))
                .apply()
        }
        return merged
    }

    private fun readRaw(connectorDeviceId: String): RemoteComposerOptions? = preferences
        .getString(optionsKey(connectorDeviceId), null)
        ?.let { encoded -> runCatching { MasonProtocolJson.decode<RemoteComposerOptions>(encoded) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "remote_composer_options"
    }
}

internal fun RemoteComposerOptions.hasSelectableOptions(): Boolean =
    models.isNotEmpty() && permissionProfiles.any { it.allowed }

internal fun RemoteComposerOptions.hasCompleteNewConversationOptions(): Boolean =
    projects.isNotEmpty() && hasSelectableOptions()

internal fun mergeRemoteComposerOptions(
    fresh: RemoteComposerOptions,
    cached: RemoteComposerOptions?,
): RemoteComposerOptions = fresh.copy(
    projects = fresh.projects.ifEmpty { cached?.projects.orEmpty() },
    models = fresh.models.ifEmpty { cached?.models.orEmpty() },
    skills = fresh.skills.ifEmpty { cached?.skills.orEmpty() },
    permissionProfiles = fresh.permissionProfiles.ifEmpty { cached?.permissionProfiles.orEmpty() },
)

internal fun resolveRemoteModel(
    options: RemoteComposerOptions,
    authoritativeModelId: String?,
    selectedModelId: String?,
    preserveUserSelection: Boolean,
): RemoteModelOption? = selectedModelId
    ?.takeIf { preserveUserSelection }
    ?.let { selected -> options.models.firstOrNull { it.id == selected } }
    ?: options.models.firstOrNull { it.id == authoritativeModelId }
    ?: options.models.firstOrNull { it.isDefault }
    ?: options.models.firstOrNull()

internal fun resolveRemoteReasoningEffort(
    model: RemoteModelOption?,
    authoritativeEffort: String?,
    selectedEffort: String?,
    preserveUserSelection: Boolean,
): String? = selectedEffort
    ?.takeIf { preserveUserSelection }
    ?.takeIf { effort -> model?.supportedReasoningEfforts?.any { it.id == effort } == true }
    ?: authoritativeEffort
        ?.takeIf { effort -> model?.supportedReasoningEfforts?.any { it.id == effort } == true }
    ?: model?.defaultReasoningEffort
        ?.takeIf { effort -> model.supportedReasoningEfforts.any { it.id == effort } }
    ?: model?.supportedReasoningEfforts?.firstOrNull()?.id

internal fun resolveRemotePermissionProfile(
    options: RemoteComposerOptions,
    authoritativePermissionId: String?,
    selectedPermissionId: String?,
    preserveUserSelection: Boolean,
): String? = selectedPermissionId
    ?.takeIf { preserveUserSelection }
    ?.takeIf { id -> options.permissionProfiles.any { it.id == id && it.allowed } }
    ?: authoritativePermissionId
        ?.takeIf { id -> options.permissionProfiles.any { it.id == id && it.allowed } }
    ?: options.permissionProfiles.firstOrNull { it.allowed }?.id

internal fun resolveRemoteProjectPath(
    options: RemoteComposerOptions,
    authoritativeProjectPath: String?,
    selectedProjectPath: String?,
    preserveUserSelection: Boolean,
): String? = selectedProjectPath
    ?.takeIf { preserveUserSelection }
    ?.takeIf { selected -> options.projects.any { it.path == selected } }
    ?: options.projects.firstOrNull { it.path == authoritativeProjectPath }?.path
    ?: options.projects.firstOrNull()?.path

internal fun shouldSelectRemoteProject(
    options: RemoteComposerOptions,
    currentProjectPath: String?,
    requestedProjectPath: String,
): Boolean = requestedProjectPath != currentProjectPath &&
    options.projects.any { it.path == requestedProjectPath }

internal fun shouldSelectRemoteModel(
    currentModelId: String?,
    requestedModelId: String,
): Boolean = requestedModelId != currentModelId

private fun RemoteComposerOptions.hasCatalogOptions(): Boolean =
    projects.isNotEmpty() || models.isNotEmpty() || skills.isNotEmpty() || permissionProfiles.isNotEmpty()

internal fun optionsKey(connectorDeviceId: String): String =
    "${connectorDeviceId.length}:$connectorDeviceId"
