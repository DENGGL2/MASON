package com.denggl2.mason.ui.settings

import com.denggl2.mason.data.AiProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
