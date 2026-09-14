package com.denggl2.mason.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun modelEndpointRemovesKnownCompletionSuffixes() {
        assertEquals(
            "https://relay.example/v1/models",
            remoteModelsEndpoint("https://relay.example/v1/chat/completions"),
        )
        assertEquals(
            "https://relay.example/v1/models",
            remoteModelsEndpoint("https://relay.example/v1/responses/"),
        )
        assertEquals(
            "https://relay.example/v1/models",
            remoteModelsEndpoint("https://relay.example/v1/models/"),
        )
    }

    @Test
    fun parserAcceptsOpenAiDataEnvelope() {
        val models = parseRemoteModelsResponse(
            json,
            """{"data":[{"id":"model-b"},{"id":"model-a","name":"Model A"}]}""",
        )

        assertEquals(listOf("model-a", "model-b"), models.map(AiModelPreset::id))
        assertEquals("Model A", models.first().name)
    }

    @Test
    fun parserAcceptsTopLevelArrayAndAlternativeEnvelopes() {
        assertEquals(
            listOf("array-model"),
            parseRemoteModelsResponse(json, """["array-model"]""").map(AiModelPreset::id),
        )
        assertEquals(
            listOf("models-model"),
            parseRemoteModelsResponse(
                json,
                """{"models":[{"model":"models-model"}]}""",
            ).map(AiModelPreset::id),
        )
        assertEquals(
            listOf("items-model"),
            parseRemoteModelsResponse(
                json,
                """{"items":[{"model_id":"items-model"}]}""",
            ).map(AiModelPreset::id),
        )
    }

    @Test
    fun malformedOrUnsupportedPayloadReturnsNoModels() {
        assertEquals(emptyList<AiModelPreset>(), parseRemoteModelsResponse(json, "not-json"))
        assertEquals(emptyList<AiModelPreset>(), parseRemoteModelsResponse(json, """{"ok":true}"""))
    }
}
