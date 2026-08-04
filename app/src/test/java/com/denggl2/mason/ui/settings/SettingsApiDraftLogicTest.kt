package com.denggl2.mason.ui.settings

import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.FontSizePreference
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.UiPreferences
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SettingsApiDraftLogicTest {
    @Test
    fun clearingCustomUrlKeepsTheDraftEmpty() {
        val custom = requireNotNull(AiProviderCatalog.getProvider(AiProviderCatalog.CUSTOM_PROVIDER_ID))

        assertEquals("", resolveProviderDraftUrl(custom, ""))
    }

    @Test
    fun officialProviderUsesItsDefaultUrlWithoutPuttingItInTheDraft() {
        val deepSeek = requireNotNull(AiProviderCatalog.getProvider("deepseek"))

        assertEquals(deepSeek.apiUrl, resolveProviderDraftUrl(deepSeek, ""))
    }

    @Test
    fun qwenRegionSelectsTheMatchingOfficialEndpoint() {
        val qwen = requireNotNull(AiProviderCatalog.getProvider("qwen"))

        assertEquals(
            "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            resolveProviderDraftUrl(qwen, "", endpointId = "intl"),
        )
    }

    @Test
    fun kimiOpensTheOfficialMoonshotApiKeyPage() {
        val kimi = requireNotNull(AiProviderCatalog.getProvider("kimi"))

        assertEquals(
            "https://platform.moonshot.cn/console/api-keys",
            officialEntryUrl(kimi),
        )
    }

    @Test
    fun completeUnsavedDraftCanBeTested() {
        assertTrue(
            isApiDraftTestable(
                apiUrl = "https://relay.example/v1",
                apiKey = "key",
                modelId = "model",
                requiresApiKey = true,
            ),
        )
        assertFalse(
            isApiDraftTestable(
                apiUrl = "https://relay.example/v1",
                apiKey = "",
                modelId = "model",
                requiresApiKey = true,
            ),
        )
        assertFalse(
            isApiDraftTestable(
                apiUrl = "https://relay.example/v1",
                apiKey = "key",
                modelId = "",
                requiresApiKey = true,
            ),
        )
    }

    @Test
    fun remoteModelSummaryOnlyListsVerifiedCapabilities() {
        assertEquals(
            "聊天、工具调用、识图",
            remoteModelCapabilitySummary(
                ApiModelCapabilities(supportsTools = true, supportsVision = true),
            ),
        )
        assertEquals("聊天", remoteModelCapabilitySummary(ApiModelCapabilities()))
    }

    @Test
    fun fontSizeDefaultsToMedium() {
        assertEquals(FontSizePreference.MEDIUM, UiPreferences().fontSize)
        assertEquals(1f, UiPreferences().fontSize.scale)
    }

    @Test
    fun remoteModelIdsAreTrimmedFilteredAndDeduplicated() {
        assertEquals(
            listOf("model-a", "model-b"),
            normalizeRemoteModelIds(
                listOf(" model-a ", "", "model-b", "model-a", "bad\nmodel"),
            ),
        )
    }

    @Test
    fun addingModelStartsBlankAndUsesHistoryOnlyAsQuickInput() {
        assertEquals(listOf(""), initialRemoteModelIdDrafts(editingModelId = null))
        assertEquals(listOf("saved-model"), applyRemoteModelQuickId(listOf(""), "saved-model"))
    }

    @Test
    fun editingModelStartsWithExactlyOneFixedIdDraft() {
        assertEquals(listOf("configured-model"), initialRemoteModelIdDrafts("configured-model"))
    }

    @Test
    fun configuredUntestedDraftRequiresExitConfirmation() {
        assertTrue(
            shouldConfirmRemoteModelSheetDismiss(
                mode = RemoteModelSheetMode.Draft,
                apiUrl = "https://relay.example/v1",
                apiKey = "key",
                requiresApiKey = true,
            ),
        )
        assertFalse(
            shouldConfirmRemoteModelSheetDismiss(
                mode = RemoteModelSheetMode.Draft,
                apiUrl = "https://relay.example/v1",
                apiKey = "",
                requiresApiKey = true,
            ),
        )
        assertFalse(
            shouldConfirmRemoteModelSheetDismiss(
                mode = RemoteModelSheetMode.Testing,
                apiUrl = "https://relay.example/v1",
                apiKey = "key",
                requiresApiKey = true,
            ),
        )
        assertFalse(
            shouldConfirmRemoteModelSheetDismiss(
                mode = RemoteModelSheetMode.Verified,
                apiUrl = "https://relay.example/v1",
                apiKey = "key",
                requiresApiKey = true,
            ),
        )
    }

    @Test
    fun dirtyRemoteModelDraftCannotHideBeforeExitConfirmation() {
        assertFalse(
            shouldAllowRemoteModelSheetTransition(
                targetValue = SheetValue.Hidden,
                shouldConfirmDismiss = true,
            ),
        )
        assertTrue(
            shouldAllowRemoteModelSheetTransition(
                targetValue = SheetValue.Hidden,
                shouldConfirmDismiss = false,
            ),
        )
        assertTrue(
            shouldAllowRemoteModelSheetTransition(
                targetValue = SheetValue.Expanded,
                shouldConfirmDismiss = true,
            ),
        )
    }

    @Test
    fun savedVerifiedModelOpensInVerifiedMode() {
        val draft = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("model-a"),
        )
        val signature = AiProviderCatalog.verificationSignature(
            ApiConfig(
                providerId = draft.providerId,
                apiUrl = draft.apiUrl,
                apiKey = draft.apiKey,
                model = "model-a",
            ),
        )
        val saved = draft.copy(
            modelIds = listOf("model-a", "model-b"),
            verifiedModelSignatures = mapOf("model-a" to signature),
        )

        assertEquals(
            RemoteModelSheetMode.Verified,
            remoteModelSheetMode(
                draft = draft,
                savedConnection = saved,
                editingModelId = "model-a",
                testState = ApiTestUiState(),
            ),
        )
    }

    @Test
    fun matchingAutoSavedTestMovesSheetToVerifiedMode() {
        val draft = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("model-a"),
        )

        assertEquals(
            RemoteModelSheetMode.Verified,
            remoteModelSheetMode(
                draft = draft,
                savedConnection = null,
                editingModelId = null,
                testState = ApiTestUiState(
                    success = true,
                    saved = true,
                    targetConnection = draft,
                    testedConnection = draft,
                ),
            ),
        )
        assertEquals(
            RemoteModelSheetMode.Draft,
            remoteModelSheetMode(
                draft = draft,
                savedConnection = null,
                editingModelId = null,
                testState = ApiTestUiState(
                    success = true,
                    saved = false,
                    targetConnection = draft,
                    testedConnection = draft,
                ),
            ),
        )
    }

    @Test
    fun remoteModelRefreshAuthenticationFailureDoesNotExposeProviderBody() {
        val message = remoteModelRefreshErrorMessage(
            IllegalStateException("服务商返回 401: Authentication Fails (governor)"),
        )

        assertEquals("无法刷新，API Key 无效或没有访问权限", message)
        assertFalse(message.contains("governor"))
    }

    @Test
    fun remoteModelTestStatusFollowsTheMatchingBackgroundTest() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1",
            apiKey = "key",
            modelIds = listOf("chat-model", "image-model"),
        )

        assertEquals(
            "测试中",
            remoteModelTestStatus(
                connection,
                "image-model",
                ApiTestUiState(isTesting = true, targetConnection = connection),
            ),
        )

        val tested = connection.copy(
            modelCapabilities = mapOf(
                "chat-model" to ApiModelCapabilities(supportsTools = true),
                "image-model" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
        )
        val finished = ApiTestUiState(
            success = true,
            targetConnection = tested,
            testedConnection = tested,
        )
        assertEquals("聊天、工具调用，待保存", remoteModelTestStatus(connection, "chat-model", finished))
        assertEquals("生图，待保存", remoteModelTestStatus(connection, "image-model", finished))

        val saved = tested.copy(
            verifiedModelSignatures = mapOf("image-model" to "image-signature"),
        )
        val savedState = finished.copy(
            targetConnection = saved,
            testedConnection = saved,
        )
        assertEquals("生图", remoteModelTestStatus(saved, "image-model", savedState))
    }

    @Test
    fun testingModelADoesNotResetUnsavedResultForModelB() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1",
            apiKey = "key",
            modelIds = listOf("model-a", "model-b"),
        )
        val testingA = ApiTestUiState(
            isTesting = true,
            targetConnection = connection.copy(modelIds = listOf("model-a")),
            observedModelCapabilities = mapOf(
                apiTestModelKey(connection.id, "model-b") to
                    ApiModelCapabilities(supportsImageGeneration = true),
            ),
        )

        assertEquals("测试中", remoteModelTestStatus(connection, "model-a", testingA))
        assertEquals("生图，待保存", remoteModelTestStatus(connection, "model-b", testingA))
    }

    @Test
    fun remoteModelDraftMatchingIgnoresOnlyUrlTrailingSlash() {
        val first = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1/",
            apiKey = "key",
            modelIds = listOf("model-a", "model-b"),
        )

        assertTrue(sameRemoteModelDraft(first, first.copy(apiUrl = "https://example.invalid/v1")))
        assertFalse(sameRemoteModelDraft(first, first.copy(modelIds = listOf("model-a"))))
    }

    @Test
    fun editingOneModelPreservesSiblingCapabilities() {
        val existing = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1",
            modelIds = listOf("model-a", "model-b"),
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(supportsChat = true),
                "model-b" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
            verifiedModelSignatures = mapOf("model-a" to "a", "model-b" to "b"),
        )
        val retestedA = existing.copy(
            modelIds = listOf("model-a"),
            modelCapabilities = mapOf("model-a" to ApiModelCapabilities(supportsChat = true, supportsTools = true)),
            verifiedModelSignatures = mapOf("model-a" to "a2"),
        )

        val merged = mergeTestedConnection(existing, retestedA, replacingModelId = "model-a")

        assertEquals(listOf("model-b", "model-a"), merged.modelIds)
        assertTrue(requireNotNull(merged.modelCapabilities["model-a"]).supportsTools)
        assertTrue(requireNotNull(merged.modelCapabilities["model-b"]).supportsImageGeneration)
        assertEquals("b", merged.verifiedModelSignatures["model-b"])
    }

    @Test
    fun deletingConfiguredModelRemovesItsStateAndSelectsSibling() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1",
            apiKey = "key",
            modelIds = listOf("model-a", "model-b"),
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(supportsChat = true, supportsVision = true),
                "model-b" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
            ),
            verifiedModelSignatures = mapOf("model-a" to "a", "model-b" to "b"),
        )
        val config = ApiConfig(
            providerId = "custom",
            apiUrl = connection.apiUrl,
            apiKey = connection.apiKey,
            model = "model-a",
            visionModel = "model-a",
            connections = listOf(connection),
            chatModelRef = ModelReference("custom", "model-a"),
            visionModelRef = ModelReference("custom", "model-a"),
        )

        val updated = removeRemoteModel(config, "custom", "model-a")
        val updatedConnection = requireNotNull(updated.connections.singleOrNull())

        assertEquals(listOf("model-b"), updatedConnection.modelIds)
        assertFalse(updatedConnection.modelCapabilities.containsKey("model-a"))
        assertFalse(updatedConnection.verifiedModelSignatures.containsKey("model-a"))
        assertEquals(ModelReference("custom", "model-b"), updated.chatModelRef)
        assertEquals("", updated.visionModel)
        assertEquals(null, updated.visionModelRef)
    }

    @Test
    fun deletingAutoSavedMultiModelDraftRemovesEveryTestedModel() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("model-a", "model-b"),
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(supportsChat = true),
                "model-b" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
            verifiedModelSignatures = mapOf("model-a" to "a", "model-b" to "b"),
        )
        val config = ApiConfig(
            connections = listOf(connection),
            chatModelRef = ModelReference("custom", "model-a"),
            imageModelRef = ModelReference("custom", "model-b"),
        )

        val updated = removeRemoteModels(config, "custom", listOf("model-a", "model-b"))

        assertTrue(updated.connections.isEmpty())
        assertEquals(null, updated.chatModelRef)
        assertEquals(null, updated.imageModelRef)
    }

    @Test
    fun completedTestNotifiesAfterLeavingItsVisibleSheet() {
        val target = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://example.invalid/v1",
            modelIds = listOf("model-a"),
        )

        assertFalse(
            shouldNotifyApiTestCompletion(
                appForeground = true,
                visibleDraft = target,
                target = target,
            ),
        )
        assertTrue(
            shouldNotifyApiTestCompletion(
                appForeground = true,
                visibleDraft = null,
                target = target,
            ),
        )
        assertTrue(
            shouldNotifyApiTestCompletion(
                appForeground = false,
                visibleDraft = target,
                target = target,
            ),
        )
    }
}
