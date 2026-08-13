package com.denggl2.mason.ui.remote

import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteModelOption
import com.denggl2.mason.protocol.RemotePermissionProfileOption
import com.denggl2.mason.protocol.RemoteProjectOption
import com.denggl2.mason.protocol.RemoteReasoningEffortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteComposerOptionsStoreTest {
    @Test
    fun selectableCacheNeedsModelsAndAnAllowedPermission() {
        val model = RemoteModelOption(
            id = "model-1",
            model = "model-1",
            displayName = "Model 1",
            description = "",
            defaultReasoningEffort = "medium",
        )

        assertFalse(RemoteComposerOptions().hasSelectableOptions())
        assertFalse(RemoteComposerOptions(models = listOf(model)).hasSelectableOptions())
        assertFalse(
            RemoteComposerOptions(
                models = listOf(model),
                permissionProfiles = listOf(RemotePermissionProfileOption("blocked", allowed = false)),
            ).hasSelectableOptions(),
        )
        assertTrue(
            RemoteComposerOptions(
                models = listOf(model),
                permissionProfiles = listOf(RemotePermissionProfileOption("allowed", allowed = true)),
            ).hasSelectableOptions(),
        )
    }

    @Test
    fun cacheKeysRemainDistinctForAmbiguousConnectorIds() {
        assertTrue(optionsKey("ab") != optionsKey("a:b"))
    }

    @Test
    fun partialResponseKeepsCachedCatalogButNotCachedCurrentValues() {
        val cached = sampleOptions(
            currentModelId = "cached-model",
            currentPermissionId = "cached-permission",
            cwd = "D:/cached",
        )

        val merged = mergeRemoteComposerOptions(RemoteComposerOptions(), cached)

        assertEquals(cached.models, merged.models)
        assertEquals(cached.projects, merged.projects)
        assertEquals(cached.permissionProfiles, merged.permissionProfiles)
        assertNull(merged.currentModelId)
        assertNull(merged.currentPermissionProfileId)
        assertNull(merged.cwd)
    }

    @Test
    fun newConversationCacheIsCompleteOnlyWithProjectModelAndAllowedPermission() {
        val complete = sampleOptions()

        assertTrue(complete.hasCompleteNewConversationOptions())
        assertFalse(complete.copy(projects = emptyList()).hasCompleteNewConversationOptions())
        assertFalse(complete.copy(models = emptyList()).hasCompleteNewConversationOptions())
        assertFalse(
            complete.copy(
                permissionProfiles = listOf(RemotePermissionProfileOption("blocked", allowed = false)),
            ).hasCompleteNewConversationOptions(),
        )
    }

    @Test
    fun authoritativeDefaultsWinUnlessUserSelectedDuringThisSession() {
        val options = sampleOptions().copy(
            models = listOf(model("remote-model"), model("user-model")),
            permissionProfiles = listOf(
                RemotePermissionProfileOption("remote-permission", allowed = true),
                RemotePermissionProfileOption("user-permission", allowed = true),
            ),
        )

        assertEquals(
            "remote-model",
            resolveRemoteModel(options, "remote-model", "user-model", false)?.id,
        )
        assertEquals(
            "user-model",
            resolveRemoteModel(options, "remote-model", "user-model", true)?.id,
        )
        assertEquals(
            "remote-permission",
            resolveRemotePermissionProfile(
                options,
                "remote-permission",
                "user-permission",
                false,
            ),
        )
        assertEquals(
            "user-permission",
            resolveRemotePermissionProfile(
                options,
                "remote-permission",
                "user-permission",
                true,
            ),
        )
    }

    @Test
    fun userReasoningSelectionSurvivesRefreshOnlyWhenStillSupported() {
        val model = model("model")

        assertEquals(
            "high",
            resolveRemoteReasoningEffort(model, "low", "high", true),
        )
        assertEquals(
            "low",
            resolveRemoteReasoningEffort(model, "low", "missing", true),
        )
        assertEquals(
            "low",
            resolveRemoteReasoningEffort(model, "low", "high", false),
        )
    }

    @Test
    fun tappingCurrentProjectIsNoOp() {
        val options = sampleOptions()

        assertFalse(shouldSelectRemoteProject(options, "D:/Mason", "D:/Mason"))
        assertTrue(shouldSelectRemoteProject(options, "D:/Other", "D:/Mason"))
        assertFalse(shouldSelectRemoteProject(options, "D:/Mason", "D:/Missing"))
    }

    @Test
    fun tappingCurrentModelIsNoOp() {
        assertFalse(shouldSelectRemoteModel("gpt-5.6-sol", "gpt-5.6-sol"))
        assertTrue(shouldSelectRemoteModel("gpt-5.6-terra", "gpt-5.6-sol"))
        assertTrue(shouldSelectRemoteModel(null, "gpt-5.6-sol"))
    }

    private fun sampleOptions(
        currentModelId: String? = "model",
        currentPermissionId: String? = "permission",
        cwd: String? = "D:/Mason",
    ) = RemoteComposerOptions(
        projects = listOf(RemoteProjectOption("D:/Mason", "Mason")),
        models = listOf(model("model")),
        permissionProfiles = listOf(RemotePermissionProfileOption("permission", allowed = true)),
        currentModelId = currentModelId,
        currentPermissionProfileId = currentPermissionId,
        cwd = cwd,
    )

    private fun model(id: String) = RemoteModelOption(
        id = id,
        model = id,
        displayName = id,
        description = "",
        isDefault = id == "model",
        defaultReasoningEffort = "low",
        supportedReasoningEfforts = listOf(
            RemoteReasoningEffortOption("low", ""),
            RemoteReasoningEffortOption("high", ""),
        ),
    )
}
