package com.denggl2.mason.ui.chat

import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.ModelReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelAvailabilityTest {
    @Test
    fun emptyConfigCannotStartTaskComponents() {
        assertFalse(canStartModelTask(ApiConfig(), selectedLocalModelInstalled = false))
    }

    @Test
    fun unconfiguredDefaultLookingModelCannotStartTaskComponents() {
        assertFalse(
            canStartModelTask(
                ApiConfig(
                    providerId = "deepseek",
                    apiUrl = "https://api.deepseek.com",
                    model = "deepseek-v4-flash",
                ),
                selectedLocalModelInstalled = false,
            ),
        )
    }

    @Test
    fun staleLocalModelSelectionCannotStartTaskComponents() {
        val config = ApiConfig(
            localModel = "gemma-4-e2b-it-litert",
            localModelDirectEnabled = true,
        )

        assertFalse(canStartModelTask(config, selectedLocalModelInstalled = false))
        assertTrue(canStartModelTask(config, selectedLocalModelInstalled = true))
    }

    @Test
    fun installedLocalOnlyModelCanStartWithoutAnExtraRoutingSwitch() {
        val config = ApiConfig(localModel = "minicpm5-1b-q4-k-m-gguf")

        assertTrue(canStartModelTask(config, selectedLocalModelInstalled = true))
    }

    @Test
    fun configuredRemoteModelCanStartTaskComponents() {
        val connection = ApiConnection(
            id = "deepseek",
            providerId = "deepseek",
            name = "DeepSeek",
            apiUrl = "https://api.deepseek.com",
            apiKey = "ds-key",
            modelIds = listOf("deepseek-v4-flash"),
            modelCapabilities = mapOf(
                "deepseek-v4-flash" to ApiModelCapabilities(supportsChat = true),
            ),
            verifiedModelSignatures = mapOf("deepseek-v4-flash" to "verified"),
        )
        val config = ApiConfig(
            providerId = connection.providerId,
            apiUrl = connection.apiUrl,
            apiKey = connection.apiKey,
            model = connection.modelIds.single(),
            connections = listOf(connection),
            chatModelRef = ModelReference(connection.id, connection.modelIds.single()),
        )

        assertTrue(canStartModelTask(config, selectedLocalModelInstalled = false))
    }

    @Test
    fun untestedSelectedRemoteModelIsTreatedAsUnconfigured() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("gpt-image-2"),
        )
        val config = ApiConfig(
            connections = listOf(connection),
            chatModelRef = ModelReference(connection.id, "gpt-image-2"),
        )

        assertFalse(canStartModelTask(config, selectedLocalModelInstalled = false))
    }
}
