package com.denggl2.mason.data

import com.denggl2.mason.ui.chat.modelParticipationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelParticipationTest {
    @Test
    fun marker_roundTripsWithoutChangingVisibleAnswer() {
        val contributions = listOf(
            ModelContribution(
                modelId = "gpt-test",
                engineId = "openai-compatible",
                parts = listOf("理解问题并生成回答"),
            ),
        )

        val annotated = annotateModelParticipation("正常回答", contributions)

        assertEquals("正常回答", stripModelParticipationMarkers(annotated))
        assertEquals(contributions, extractModelParticipation(annotated)?.contributions)
    }

    @Test
    fun markerReplacement_isIdempotentAndMalformedMarkerIsIgnored() {
        val first = annotateModelParticipation(
            "回答",
            listOf(ModelContribution("first", "engine", listOf("初答"))),
        )
        val replacement = listOf(ModelContribution("second", "engine", listOf("复核")))
        val second = annotateModelParticipation(first, replacement)

        assertEquals(replacement, extractModelParticipation(second)?.contributions)
        assertEquals(1, "mason-model-participation".toRegex().findAll(second).count())
        assertNull(extractModelParticipation("回答\n<!-- mason-model-participation broken -->"))
    }

    @Test
    fun contributions_mergePartsPerModelAndProduceReadableSummary() {
        val initial = mergeModelContribution(emptyList(), "model-a", "engine", "生成回答")
        val merged = mergeModelContribution(initial, "model-a", "engine", "整理工具结果")
        val duplicate = mergeModelContribution(merged, "model-a", "engine", "生成回答")

        assertEquals(1, duplicate.size)
        assertEquals(listOf("生成回答", "整理工具结果"), duplicate.single().parts)
        assertTrue(modelParticipationSummary(duplicate).contains("model-a"))
        assertTrue(modelParticipationSummary(duplicate).contains("整理工具结果"))
        assertFalse(modelParticipationSummary(emptyList()).contains("model-a"))
    }
}
