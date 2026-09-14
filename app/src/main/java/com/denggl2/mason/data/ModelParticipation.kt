package com.denggl2.mason.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ModelContribution(
    val modelId: String,
    val engineId: String,
    val parts: List<String>,
)

@Serializable
data class ModelParticipation(
    val contributions: List<ModelContribution> = emptyList(),
)

private const val MODEL_PARTICIPATION_MARKER_PREFIX = "<!-- mason-model-participation"
private const val MODEL_PARTICIPATION_MARKER_SUFFIX = "-->"
private val modelParticipationJson = Json { ignoreUnknownKeys = true }

fun annotateModelParticipation(
    content: String,
    contributions: List<ModelContribution>,
): String {
    val visibleContent = stripModelParticipationMarkers(content)
    val marker = modelParticipationJson.encodeToString(ModelParticipation(contributions))
    return visibleContent.trimEnd() +
        "\n\n$MODEL_PARTICIPATION_MARKER_PREFIX $marker $MODEL_PARTICIPATION_MARKER_SUFFIX"
}

fun extractModelParticipation(content: String): ModelParticipation? = modelParticipationMarkerRegex()
    .findAll(content)
    .mapNotNull { match ->
        runCatching {
            modelParticipationJson.decodeFromString<ModelParticipation>(match.groupValues[1].trim())
        }.getOrNull()
    }
    .lastOrNull()

fun stripModelParticipationMarkers(content: String): String =
    content.replace(modelParticipationMarkerRegex(), "").trimEnd()

fun mergeModelContribution(
    current: List<ModelContribution>,
    modelId: String,
    engineId: String,
    part: String,
): List<ModelContribution> {
    if (modelId.isBlank() || part.isBlank()) return current
    val existing = current.firstOrNull { it.modelId == modelId && it.engineId == engineId }
    if (existing == null) {
        return current + ModelContribution(modelId, engineId, listOf(part))
    }
    if (part in existing.parts) return current
    return current.map { item ->
        if (item == existing) item.copy(parts = item.parts + part) else item
    }
}

private fun modelParticipationMarkerRegex(): Regex = Regex(
    Regex.escape(MODEL_PARTICIPATION_MARKER_PREFIX) +
        """\s+([\s\S]*?)\s+""" +
        Regex.escape(MODEL_PARTICIPATION_MARKER_SUFFIX),
)
