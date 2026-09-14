package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigLogicTest {
    @Test
    fun connectionUsesSelectedModelInsteadOfFirstModel() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "Remote",
            apiUrl = "https://example.invalid/v1",
            modelIds = listOf("kimi-k3-free", "deepseek-v4-flash-free"),
        )

        assertEquals(
            "deepseek-v4-flash-free",
            connection.modelForReference(ModelReference(connection.id, "deepseek-v4-flash-free")),
        )
        assertEquals(
            "kimi-k3-free",
            connection.modelForReference(ModelReference(connection.id, "missing")),
        )
    }

    @Test
    fun emptyConfigDoesNotInventAProviderOrModel() {
        val config = ApiConfig()

        assertEquals("", config.providerId)
        assertEquals("", config.model)
        assertTrue(config.resolvedConnections().isEmpty())
        assertNull(config.configuredChatModelRef())
    }

    @Test
    fun remoteModelWithoutRequiredKeyIsNotConfigured() {
        val config = ApiConfig(
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com",
            model = "deepseek-v4-flash",
        )

        assertFalse(config.configuredConnections().isNotEmpty())
        assertNull(config.configuredChatModelRef())
    }

    @Test
    fun savedRemoteModelWithKeyCanBecomeTheChatModel() {
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
        )
        val config = ApiConfig(
            providerId = connection.providerId,
            apiUrl = connection.apiUrl,
            apiKey = connection.apiKey,
            model = connection.modelIds.single(),
            connections = listOf(connection),
            chatModelRef = ModelReference(connection.id, connection.modelIds.single()),
        )

        assertEquals(
            ModelReference("deepseek", "deepseek-v4-flash"),
            config.configuredChatModelRef(),
        )
    }

    @Test
    fun configuredChatCandidatesContainOnlySavedChatCapableModelsWithCurrentFirst() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "Custom",
            apiUrl = "https://example.invalid/v1",
            apiKey = "key",
            modelIds = listOf("secondary", "image-only", "current"),
            modelCapabilities = mapOf(
                "secondary" to ApiModelCapabilities(supportsChat = true),
                "image-only" to ApiModelCapabilities(supportsImageGeneration = true),
                "current" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
                "not-configured" to ApiModelCapabilities(supportsChat = true),
            ),
        )
        val config = ApiConfig(
            connections = listOf(connection),
            chatModelRef = ModelReference(connection.id, "current"),
        )

        assertEquals(
            listOf(
                ModelReference("custom", "current"),
                ModelReference("custom", "secondary"),
            ),
            config.configuredChatModelRefs(),
        )
    }

    @Test
    fun kimiUsesTheOfficialMoonshotEndpointAndDefaultModel() {
        val kimi = requireNotNull(AiProviderCatalog.getProvider("kimi"))

        assertEquals(AiProviderKind.Official, kimi.kind)
        assertEquals("https://api.moonshot.cn/v1", kimi.apiUrl)
        assertEquals("kimi-k2.5", kimi.defaultModel)
        assertTrue(kimi.modelOptions.single().supportsVision)
        assertEquals("kimi", AiProviderCatalog.inferProviderId(kimi.apiUrl))
    }

    @Test
    fun zhipuUsesTheOfficialCompatibleEndpoint() {
        val zhipu = requireNotNull(AiProviderCatalog.getProvider("zhipu"))

        assertEquals(AiProviderKind.Official, zhipu.kind)
        assertEquals("https://open.bigmodel.cn/api/paas/v4", zhipu.apiUrl)
        assertEquals("zhipu", AiProviderCatalog.inferProviderId(zhipu.apiUrl))
        assertTrue(zhipu.modelOptions.isNotEmpty())
    }

    @Test
    fun selectingModelUsesItsVerifiedCapabilitiesAndSignature() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("text-only", "tool-model"),
            modelCapabilities = mapOf(
                "text-only" to ApiModelCapabilities(),
                "tool-model" to ApiModelCapabilities(
                    supportsTools = true,
                    supportsVision = true,
                ),
            ),
            verifiedModelSignatures = mapOf(
                "text-only" to "text-signature",
                "tool-model" to "tool-signature",
            ),
        )
        val config = ApiConfig(connections = listOf(connection))

        val selected = config.selectChatModel(ModelReference("custom", "tool-model"))

        assertEquals("tool-model", selected.model)
        assertTrue(selected.toolsEnabled)
        assertEquals("tool-signature", selected.verifiedSignature)
        assertTrue(connection.capabilitiesFor("tool-model").supportsVision)
        assertFalse(connection.capabilitiesFor("text-only").supportsTools)
    }

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
    fun savingImageModelOnTheChatConnectionKeepsTheChatVerification() {
        val activeConfig = ApiConfig(
            providerId = "custom",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            model = "chat-model",
            chatModelRef = ModelReference("custom", "chat-model"),
        )
        val chatSignature = AiProviderCatalog.verificationSignature(activeConfig)
        val imageSignature = AiProviderCatalog.verificationSignature(
            activeConfig.copy(model = "gpt-image-2"),
        )
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("chat-model", "gpt-image-2"),
            verifiedSignature = imageSignature,
            verifiedModelSignatures = mapOf(
                "chat-model" to chatSignature,
                "gpt-image-2" to imageSignature,
            ),
        )
        val config = activeConfig.copy(
            verifiedSignature = chatSignature,
            connections = listOf(connection.copy(verifiedSignature = chatSignature)),
        )

        val updated = config.saveConnection(connection)

        assertEquals(chatSignature, updated.verifiedSignature)
        assertTrue(AiProviderCatalog.isVerified(updated))
        assertTrue(
            AiProviderCatalog.isVerified(
                config.copy(
                    verifiedSignature = imageSignature,
                    connections = listOf(connection),
                ),
            ),
        )
    }

    @Test
    fun firstVerifiedImageModelIsSelectedOnlyWhenImageModelIsMissing() {
        val connection = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = "https://relay.example/v1",
            apiKey = "key",
            modelIds = listOf("chat-model", "gpt-image-2"),
            modelCapabilities = mapOf(
                "chat-model" to ApiModelCapabilities(supportsChat = true),
                "gpt-image-2" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
        )
        val legacySaved = ApiConfig(connections = listOf(connection))
        assertEquals(
            ModelReference("custom", "gpt-image-2"),
            legacySaved.configuredImageModelRef(),
        )

        val saved = legacySaved.selectInitialImageModel(connection)

        assertEquals(ModelReference("custom", "gpt-image-2"), saved.imageModelRef)

        val existing = saved.copy(
            imageModel = "existing-image",
            imageModelRef = ModelReference("custom", "existing-image"),
            connections = listOf(
                connection.copy(
                    modelIds = connection.modelIds + "existing-image",
                    modelCapabilities = connection.modelCapabilities +
                        ("existing-image" to ApiModelCapabilities(supportsImageGeneration = true)),
                ),
            ),
        )
        assertEquals(
            ModelReference("custom", "existing-image"),
            existing.selectInitialImageModel(connection).configuredImageModelRef(),
        )
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

    @Test
    fun customEndpointsGetIndependentConnectionIds() {
        val first = connectionIdForEndpoint("custom", "https://relay-a.example/v1")
        val second = connectionIdForEndpoint("custom", "https://relay-b.example/v1")

        assertTrue(first.startsWith("custom:"))
        assertTrue(second.startsWith("custom:"))
        assertFalse(first == second)
        assertEquals(first, connectionIdForEndpoint("custom", "https://relay-a.example/v1/"))
    }

    @Test
    fun legacyMergedCustomConnectionSplitsModelsByVerificationEndpoint() {
        val endpointA = "https://apiarc.ai/v1"
        val endpointB = "https://oneai-gw-api.shiyue.com/v1"
        val merged = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "远端模型",
            apiUrl = endpointB,
            apiKey = "shared-key",
            modelIds = listOf("kimi-k3-free", "gpt-5.6-luna"),
            modelCapabilities = mapOf(
                "kimi-k3-free" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
                "gpt-5.6-luna" to ApiModelCapabilities(supportsChat = true),
            ),
            verifiedModelSignatures = mapOf(
                "kimi-k3-free" to "custom|$endpointA|kimi-k3-free|1",
                "gpt-5.6-luna" to "custom|$endpointB|gpt-5.6-luna|2",
            ),
        )

        val split = splitEndpointScopedConnections(listOf(merged))

        assertEquals(2, split.size)
        val kimi = split.single { "kimi-k3-free" in it.modelIds }
        val selected = ApiConfig(connections = split).selectChatModel(
            ModelReference(kimi.id, "kimi-k3-free"),
        )
        assertEquals(endpointA, selected.apiUrl)
        assertEquals("shared-key", selected.apiKey)
        assertTrue(selected.toolsEnabled)
    }

    @Test
    fun activeCustomEndpointDoesNotRecombineOtherConnections() {
        val first = ApiConnection(
            id = connectionIdForEndpoint("custom", "https://relay-a.example/v1"),
            providerId = "custom",
            name = "A",
            apiUrl = "https://relay-a.example/v1",
            apiKey = "a-key",
            modelIds = listOf("a-model"),
        )
        val second = ApiConnection(
            id = connectionIdForEndpoint("custom", "https://relay-b.example/v1"),
            providerId = "custom",
            name = "B",
            apiUrl = "https://relay-b.example/v1",
            apiKey = "b-key",
            modelIds = listOf("b-model"),
        )
        val config = ApiConfig(connections = listOf(first, second))
            .selectChatModel(ModelReference(second.id, "b-model"))

        val normalized = config.withActiveConnection()

        assertEquals(2, normalized.connections.size)
        assertEquals("https://relay-b.example/v1", normalized.apiUrl)
        val normalizedFirst = normalized.connections.single { "a-model" in it.modelIds }
        val normalizedSecond = normalized.connections.single { "b-model" in it.modelIds }
        assertEquals("a-key", normalizedFirst.apiKey)
        assertEquals("b-key", normalizedSecond.apiKey)
        assertEquals(
            connectionIdForModel("custom", first.apiUrl, "a-model"),
            normalizedFirst.id,
        )
        assertEquals(
            connectionIdForModel("custom", second.apiUrl, "b-model"),
            normalizedSecond.id,
        )
    }

    @Test
    fun sameEndpointModelsUseIndependentModelScopedConnectionIds() {
        val endpoint = "https://relay.example/v1"
        val first = ApiConnection(
            id = connectionIdForModel("custom", endpoint, "model-a"),
            providerId = "custom",
            name = "A",
            apiUrl = endpoint,
            apiKey = "a-key",
            modelIds = listOf("model-a"),
        )
        val second = ApiConnection(
            id = connectionIdForModel("custom", endpoint, "model-b"),
            providerId = "custom",
            name = "B",
            apiUrl = endpoint,
            apiKey = "b-key",
            modelIds = listOf("model-b"),
        )

        val normalized = splitModelScopedConnections(listOf(first, second))

        assertEquals(2, normalized.size)
        assertEquals("a-key", normalized.single { it.modelIds == listOf("model-a") }.apiKey)
        assertEquals("b-key", normalized.single { it.modelIds == listOf("model-b") }.apiKey)
        assertFalse(
            connectionIdForModel("custom", endpoint, "model-a") ==
                connectionIdForModel("custom", endpoint, "model-b"),
        )
    }

    @Test
    fun legacyMultiModelConnectionIsSplitIntoSingleModelRecords() {
        val legacy = ApiConnection(
            id = "custom",
            providerId = "custom",
            name = "Legacy",
            apiUrl = "https://relay.example/v1",
            apiKey = "legacy-key",
            modelIds = listOf("model-a", "model-b"),
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(supportsChat = true),
                "model-b" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
            ),
        )

        val split = splitModelScopedConnections(listOf(legacy))

        assertEquals(2, split.size)
        assertTrue(split.all { it.modelIds.size == 1 })
        assertEquals("legacy-key", split.single { it.modelIds == listOf("model-a") }.apiKey)
        assertTrue(split.single { it.modelIds == listOf("model-b") }.toolsSupported)
    }
}
