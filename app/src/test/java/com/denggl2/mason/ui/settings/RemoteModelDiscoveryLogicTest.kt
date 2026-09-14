package com.denggl2.mason.ui.settings

import com.denggl2.mason.data.AiModelPreset
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModelDiscoveryLogicTest {
    private val target = ApiConnection(
        id = "custom",
        providerId = "custom",
        name = "Remote",
        apiUrl = "https://relay.example/v1",
        apiKey = "key",
    )

    @Test
    fun targetMatchingIgnoresModelSelectionAndTrailingSlash() {
        val selected = target.copy(
            apiUrl = "https://relay.example/v1/",
            modelIds = listOf("model-a"),
        )

        assertTrue(sameRemoteModelDiscoveryTarget(target, selected))
        assertFalse(sameRemoteModelDiscoveryTarget(target, selected.copy(apiKey = "other")))
        assertFalse(sameRemoteModelDiscoveryTarget(null, selected))
    }

    @Test
    fun rowStatusMovesFromWaitingToTestingToCapabilities() {
        val models = listOf(
            AiModelPreset("model-a", "Model A", "Remote"),
            AiModelPreset("model-b", "Model B", "Remote"),
            AiModelPreset("model-c", "Model C", "Remote"),
        )
        val waiting = RemoteModelDiscoveryUiState(
            isTesting = true,
            models = models,
            activeModelIds = setOf("model-a", "model-b"),
        )

        assertEquals("测试中", remoteModelDiscoveryStatus("model-a", waiting))
        assertEquals("测试中", remoteModelDiscoveryStatus("model-b", waiting))
        assertEquals("等待测试", remoteModelDiscoveryStatus("model-c", waiting))

        val completed = waiting.copy(
            isTesting = false,
            activeModelIds = emptySet(),
            completedCount = 3,
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(
                    supportsChat = true,
                    supportsTools = true,
                    supportsVision = true,
                ),
            ),
            modelTestErrors = mapOf("model-b" to "HTTP 429"),
        )
        assertEquals("聊天 · 工具 · 识图", remoteModelDiscoveryStatus("model-a", completed))
        assertEquals("能力未知，可稍后重测", remoteModelDiscoveryStatus("model-b", completed))
    }

    @Test
    fun batchTestingRunsAtMostFourModelsAtOnce() = runBlocking {
        val activeCount = AtomicInteger(0)
        val maxActiveCount = AtomicInteger(0)
        val startedCount = AtomicInteger(0)
        val firstWaveReady = CompletableDeferred<Unit>()
        val releaseFirstWave = CompletableDeferred<Unit>()

        val task = async {
            (1..8).mapWithConcurrencyLimit(MODEL_TEST_PARALLELISM) { value ->
                val active = activeCount.incrementAndGet()
                maxActiveCount.updateAndGet { previous -> maxOf(previous, active) }
                if (startedCount.incrementAndGet() == MODEL_TEST_PARALLELISM) {
                    firstWaveReady.complete(Unit)
                }
                try {
                    releaseFirstWave.await()
                    value * 2
                } finally {
                    activeCount.decrementAndGet()
                }
            }
        }

        withTimeout(2_000) { firstWaveReady.await() }
        assertEquals(MODEL_TEST_PARALLELISM, startedCount.get())
        assertEquals(MODEL_TEST_PARALLELISM, maxActiveCount.get())
        releaseFirstWave.complete(Unit)
        assertEquals((1..8).map { it * 2 }, task.await())
        assertTrue(maxActiveCount.get() <= MODEL_TEST_PARALLELISM)
    }

    @Test
    fun completingDiscoveryKeepsOnlySelectedModelsAndResults() {
        val models = listOf(
            AiModelPreset("model-a", "Model A", "Remote"),
            AiModelPreset("model-b", "Model B", "Remote"),
            AiModelPreset("model-c", "Model C", "Remote"),
        )
        val state = RemoteModelDiscoveryUiState(
            modelListingAvailable = true,
            models = models,
            modelCapabilities = mapOf(
                "model-a" to ApiModelCapabilities(supportsChat = true),
                "model-b" to ApiModelCapabilities(supportsImageGeneration = true),
            ),
            modelTestErrors = mapOf("model-c" to "failed"),
            verifiedModelSignatures = mapOf("model-a" to "a", "model-b" to "b"),
        )

        val selected = buildSelectedDiscoveredConnection(
            target,
            state,
            setOf("model-b", "model-c"),
        )
        assertNotNull(selected)
        val result = requireNotNull(selected)
        assertEquals(listOf("model-b", "model-c"), result.modelIds)
        assertEquals(setOf("model-b"), result.modelCapabilities.keys)
        assertEquals(setOf("model-c"), result.modelTestErrors.keys)
        assertEquals(setOf("model-b"), result.verifiedModelSignatures.keys)
        assertTrue(result.modelCapabilities.getValue("model-b").supportsImageGeneration)
        assertNull(buildSelectedDiscoveredConnection(target, state, emptySet()))
    }
}
