package com.denggl2.mason.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ArtifactMetadata(
    val name: String,
    val path: String,
    val mimeType: String,
    val bytes: Long,
    val createdAt: Long,
)

data class ArtifactSaveResult(
    val content: String,
    val artifacts: List<ArtifactMetadata>,
)

@Singleton
class ArtifactStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveArtifactsAndAnnotate(
        content: String,
        createdAt: Long = System.currentTimeMillis(),
        existingArtifacts: List<ArtifactMetadata> = emptyList(),
    ): ArtifactSaveResult = withContext(Dispatchers.IO) {
        val newArtifacts = extractGeneratedFiles(content, createdAt)
        val artifacts = (existingArtifacts + newArtifacts).distinctBy { it.path }
        if (artifacts.isEmpty()) {
            ArtifactSaveResult(content = content, artifacts = emptyList())
        } else {
            ArtifactSaveResult(
                content = content.trimEnd() + "\n\n" + artifacts.joinToString("\n") { it.toMarker() },
                artifacts = artifacts,
            )
        }
    }

    fun metadataForExistingFile(path: String?): ArtifactMetadata? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return ArtifactMetadata(
            name = file.name,
            path = file.absolutePath,
            mimeType = file.mimeType(),
            bytes = file.length(),
            createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun extractGeneratedFiles(content: String, createdAt: Long): List<ArtifactMetadata> {
        val blockRegex = Regex("```([^`\\n]*)\\n([\\s\\S]*?)```")
        return blockRegex.findAll(content)
            .mapNotNull { match ->
                val header = match.groupValues.getOrNull(1).orEmpty().trim()
                val body = match.groupValues.getOrNull(2).orEmpty().trimEnd()
                val fileName = fileNameFromHeader(header)
                    ?: fileNameBeforeBlock(content, match.range.first)
                    ?: return@mapNotNull null
                if (body.isBlank()) return@mapNotNull null
                saveArtifact(fileName, body, createdAt)
            }
            .toList()
    }

    private fun saveArtifact(fileName: String, content: String, createdAt: Long): ArtifactMetadata? {
        val relativePath = sanitizeRelativePath(fileName).ifBlank { return null }
        val root = File(context.filesDir, "artifacts")
        val target = uniqueFile(File(root, relativePath))
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) return null
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        return ArtifactMetadata(
            name = target.name,
            path = target.absolutePath,
            mimeType = target.mimeType(),
            bytes = target.length(),
            createdAt = createdAt,
        )
    }

    private fun fileNameFromHeader(header: String): String? {
        if (header.isBlank()) return null
        val explicit = Regex("""(?:file|filename|path)=["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
        if (!explicit.isNullOrBlank()) return explicit

        val tokens = header.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.firstOrNull { token ->
            val clean = token.trim('"', '\'', '`')
            clean.contains('.') && !clean.startsWith(".") && clean.length <= 160
        }?.trim('"', '\'', '`')
    }

    private fun fileNameBeforeBlock(content: String, blockStart: Int): String? {
        val prefix = content.take(blockStart).lines().takeLast(4).asReversed()
        val regex = Regex(
            """(?i)(?:file|filename|path|文件|檔案)\s*[:：]\s*`?([A-Za-z0-9._/\- ]+\.[A-Za-z0-9]{1,12})`?""",
        )
        return prefix.firstNotNullOfOrNull { line ->
            regex.find(line)?.groupValues?.getOrNull(1)?.trim()
        }
    }

    private fun sanitizeRelativePath(value: String): String {
        val fallbackName = "artifact-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.md"
        val normalized = value.replace('\\', '/').trim().trimStart('/')
        val parts = normalized.split('/')
            .map { segment ->
                segment.trim()
                    .replace(Regex("""[<>:"|?*\u0000-\u001F]"""), "_")
                    .take(80)
            }
            .filter { it.isNotBlank() && it != "." && it != ".." }
        return parts.joinToString("/").ifBlank { fallbackName }
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val base = file.nameWithoutExtension.ifBlank { "artifact" }
        val ext = file.extension
        var index = 2
        while (true) {
            val candidateName = if (ext.isBlank()) "$base-$index" else "$base-$index.$ext"
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private fun ArtifactMetadata.toMarker(): String =
        "$MARKER_PREFIX ${json.encodeToString(this)} $MARKER_SUFFIX"
}

const val ARTIFACT_MARKER_PREFIX = "<!-- mason-artifact"
const val ARTIFACT_MARKER_SUFFIX = "-->"

private val MARKER_PREFIX = ARTIFACT_MARKER_PREFIX
private val MARKER_SUFFIX = ARTIFACT_MARKER_SUFFIX
private val artifactMarkerJson = Json { ignoreUnknownKeys = true }

fun extractArtifactMetadataMarkers(content: String): List<ArtifactMetadata> =
    artifactMarkerRegex().findAll(content)
        .mapNotNull { match ->
            runCatching {
                artifactMarkerJson.decodeFromString(
                    ArtifactMetadata.serializer(),
                    match.groupValues[1].trim(),
                )
            }.getOrNull()
        }
        .distinctBy { it.path }
        .toList()

fun stripArtifactMarkers(content: String): String =
    content.replace(artifactMarkerRegex(), "").trimEnd()

private fun artifactMarkerRegex(): Regex =
    Regex(
        Regex.escape(ARTIFACT_MARKER_PREFIX) + """\s+([\s\S]*?)\s+""" + Regex.escape(ARTIFACT_MARKER_SUFFIX),
    )

fun File.mimeType(): String {
    return when (extension.lowercase(Locale.getDefault())) {
        "txt", "log" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "yaml", "yml" -> "application/x-yaml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "kt" -> "text/x-kotlin"
        "java" -> "text/x-java"
        "py" -> "text/x-python"
        "js" -> "text/javascript"
        "ts" -> "text/typescript"
        "css" -> "text/css"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        else -> "*/*"
    }
}
