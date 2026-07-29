package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiConfigLogicTest {
    @Test
    fun savingAnotherConnectionDoesNotReplaceTheChatConnection() {
        val deepSeek = ApiConnection(
            id = "deepseek",
            providerId = "deepseek",
            name = "DeepSeek",
            apiUrl = "https://api.deepseek.com",
            apiKey = "ds-key",
            modelIds = listOf("deepseek-v4-flash"),
        )
        val gemini = ApiConnection(
            id = "gemini",
            providerId = "gemini",
            name = "Gemini",
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            apiKey = "gemini-key",
            modelIds = listOf("gemini-3.5-flash"),
        )
        val config = ApiConfig(
            providerId = deepSeek.providerId,
            apiUrl = deepSeek.apiUrl,
            apiKey = deepSeek.apiKey,
            model = deepSeek.modelIds.single(),
            connections = listOf(deepSeek),
            chatModelRef = ModelReference(deepSeek.id, deepSeek.modelIds.single()),
        )

        val updated = config.saveConnection(gemini)

        assertEquals("deepseek", updated.resolvedChatModelRef().connectionId)
        assertEquals("ds-key", updated.connection("deepseek")?.apiKey)
        assertEquals("gemini-key", updated.connection("gemini")?.apiKey)
    }

    @Test
    fun visionModelKeepsItsOwnProviderAndCredentials() {
        val config = ApiConfig(
            connections = listOf(
                ApiConnection(
                    id = "deepseek",
                    providerId = "deepseek",
                    name = "DeepSeek",
                    apiUrl = "https://api.deepseek.com",
                    apiKey = "ds-key",
                    modelIds = listOf("deepseek-v4-flash"),
                ),
                ApiConnection(
                    id = "gemini",
                    providerId = "gemini",
                    name = "Gemini",
                    apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                    apiKey = "gemini-key",
                    modelIds = listOf("gemini-3.5-flash"),
                ),
            ),
            chatModelRef = ModelReference("deepseek", "deepseek-v4-flash"),
            visionModelRef = ModelReference("gemini", "gemini-3.5-flash"),
        )

        val vision = config.resolvedVisionModelRef()

        assertNotNull(vision)
        assertEquals("gemini", vision?.connectionId)
        assertEquals("gemini-key", config.connection(vision!!.connectionId)?.apiKey)
    }
}
