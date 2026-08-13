package com.denggl2.mason.data

import android.content.Context
import android.util.Base64
import android.util.Base64InputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.InetAddress
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
    private val httpClient = OkHttpClient()
    private val streamWriteMutex = Mutex()

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

    suspend fun saveTextArtifact(
        fileName: String,
        content: String,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata = withContext(Dispatchers.IO) {
        require(content.isNotBlank()) { "产出内容不能为空" }
        requireNotNull(saveArtifact(fileName, content.trimEnd(), createdAt)) {
            "产出文件名不正确"
        }
    }

    suspend fun saveBinaryArtifact(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata {
        require(bytes.isNotEmpty()) { "产出内容不能为空" }
        return ByteArrayInputStream(bytes).use { input ->
            saveStreamArtifact(
                fileName = fileName,
                input = input,
                mimeType = mimeType,
                maxBytes = bytes.size.toLong(),
                createdAt = createdAt,
            )
        }
    }

    suspend fun saveRemoteConversationArtifact(
        cacheKey: String,
        fileName: String,
        input: InputStream,
        mimeType: String,
        expectedBytes: Long,
        maxBytes: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata = withContext(Dispatchers.IO) {
        require(cacheKey.isNotBlank()) { "远端附件缓存标识不能为空" }
        require(expectedBytes in 1..maxBytes) { "远端附件大小不正确" }
        streamWriteMutex.withLock {
            val root = File(context.filesDir, "remote-previews")
            val safeCacheKey = cacheKey
                .map { character -> if (character.isLetterOrDigit() || character in "-_.") character else '_' }
                .joinToString("")
                .take(160)
                .ifBlank { "remote-preview" }
            val safeName = sanitizeRelativePath(fileName).substringAfterLast('/').ifBlank { "attachment.bin" }
            val cacheDirectory = File(root, safeCacheKey)
            val target = File(cacheDirectory, safeName)
            val canonicalRoot = root.canonicalFile
            val canonicalTarget = target.canonicalFile
            require(canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
                "远端附件缓存路径不安全"
            }
            replaceRemoteConversationCacheFile(
                target = target,
                input = input,
                expectedBytes = expectedBytes,
                maxBytes = maxBytes,
            )
            ArtifactMetadata(
                name = target.name,
                path = target.absolutePath,
                mimeType = mimeType,
                bytes = target.length(),
                createdAt = createdAt,
            )
        }
    }

    suspend fun saveRemoteImageArtifact(
        url: String,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrNull() ?: error("图片地址格式不正确")
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "图片地址不受支持" }
        require(!uri.host.isPrivateArtifactHost()) { "拒绝从本机或内网地址下载图片" }
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            require(response.isSuccessful) { "图片下载失败：HTTP ${response.code}" }
            val body = requireNotNull(response.body) { "图片下载结果为空" }
            val contentLength = body.contentLength()
            require(contentLength < 0 || contentLength <= MAX_REMOTE_IMAGE_BYTES) { "生成图片超过 25 MB" }
            body.byteStream().use { input ->
                saveImageStreamArtifact(
                    fileName = "generated-image-$createdAt",
                    input = input,
                    declaredMimeType = response.header("Content-Type"),
                    maxBytes = MAX_REMOTE_IMAGE_BYTES.toLong(),
                    createdAt = createdAt,
                )
            }
        }
    }

    suspend fun saveGeneratedImageBase64Artifact(
        encoded: String,
        declaredMimeType: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata {
        require(encoded.isNotBlank()) { "生图模型返回内容为空" }
        return Base64InputStream(AsciiCharSequenceInputStream(encoded), Base64.DEFAULT).use { decoded ->
            saveImageStreamArtifact(
                fileName = "generated-image-$createdAt",
                input = decoded,
                declaredMimeType = declaredMimeType,
                maxBytes = MAX_REMOTE_IMAGE_BYTES.toLong(),
                createdAt = createdAt,
            )
        }
    }

    suspend fun saveGeneratedImageArtifact(
        bytes: ByteArray,
        createdAt: Long = System.currentTimeMillis(),
    ): ArtifactMetadata {
        val mimeType = detectImageMimeType(bytes, null) ?: error("生图模型返回的内容不是支持的图片")
        return saveBinaryArtifact(
            fileName = "generated-image-$createdAt.${mimeType.imageExtension()}",
            bytes = bytes,
            mimeType = mimeType,
            createdAt = createdAt,
        )
    }

    private suspend fun saveImageStreamArtifact(
        fileName: String,
        input: InputStream,
        declaredMimeType: String?,
        maxBytes: Long,
        createdAt: Long,
    ): ArtifactMetadata {
        val staged = saveStreamArtifact(
            fileName = "$fileName.part",
            input = input,
            mimeType = "application/octet-stream",
            maxBytes = maxBytes,
            createdAt = createdAt,
        )
        val stagedFile = File(staged.path)
        try {
            val mimeType = detectImageMimeType(stagedFile.readHeader(), declaredMimeType)
                ?: error("生图结果不是支持的图片")
            return moveStagedArtifact(
                stagedFile = stagedFile,
                finalName = "$fileName.${mimeType.imageExtension()}",
                mimeType = mimeType,
                createdAt = createdAt,
            )
        } catch (error: Throwable) {
            if (stagedFile.exists()) stagedFile.delete()
            throw error
        }
    }

    private suspend fun saveStreamArtifact(
        fileName: String,
        input: InputStream,
        mimeType: String,
        maxBytes: Long,
        createdAt: Long,
    ): ArtifactMetadata = withContext(Dispatchers.IO) {
        streamWriteMutex.withLock {
            val relativePath = sanitizeRelativePath(fileName).ifBlank { "artifact.bin" }
            val root = File(context.filesDir, "artifacts")
            val target = uniqueFile(File(root, relativePath))
            val canonicalRoot = root.canonicalFile
            val canonicalTarget = target.canonicalFile
            require(canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) { "产出路径不安全" }
            target.parentFile?.mkdirs()
            val temporary = File.createTempFile(".mason-artifact-", ".part", target.parentFile)
            try {
                FileOutputStream(temporary).buffered().use { output ->
                    copyArtifactStream(input, output, maxBytes)
                }
                require(temporary.length() > 0L) { "产出内容不能为空" }
                require(temporary.renameTo(target)) { "产出文件登记失败" }
                ArtifactMetadata(
                    name = target.name,
                    path = target.absolutePath,
                    mimeType = mimeType,
                    bytes = target.length(),
                    createdAt = createdAt,
                )
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private suspend fun moveStagedArtifact(
        stagedFile: File,
        finalName: String,
        mimeType: String,
        createdAt: Long,
    ): ArtifactMetadata = withContext(Dispatchers.IO) {
        streamWriteMutex.withLock {
            val root = File(context.filesDir, "artifacts")
            val target = uniqueFile(File(root, sanitizeRelativePath(finalName)))
            require(stagedFile.renameTo(target)) { "产出文件登记失败" }
            ArtifactMetadata(
                name = target.name,
                path = target.absolutePath,
                mimeType = mimeType,
                bytes = target.length(),
                createdAt = createdAt,
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

private const val MAX_REMOTE_IMAGE_BYTES = 25 * 1024 * 1024

internal suspend fun copyArtifactStream(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
): Long {
    require(maxBytes > 0L) { "产出大小限制必须大于 0" }
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "产出文件超过 ${maxBytes / 1024 / 1024} MB" }
        output.write(buffer, 0, count)
    }
    return total
}

internal suspend fun replaceRemoteConversationCacheFile(
    target: File,
    input: InputStream,
    expectedBytes: Long,
    maxBytes: Long,
) {
    val cacheDirectory = requireNotNull(target.parentFile) { "远端附件缓存目录缺失" }
    require(cacheDirectory.exists() || cacheDirectory.mkdirs()) { "远端附件缓存目录不可用" }
    val temporary = File.createTempFile(".mason-remote-preview-", ".part", cacheDirectory)
    try {
        FileOutputStream(temporary).buffered().use { output ->
            copyArtifactStream(input, output, maxBytes)
        }
        require(temporary.length() == expectedBytes) { "远端附件下载不完整" }
        if (target.exists()) require(target.delete()) { "远端附件缓存更新失败" }
        require(temporary.renameTo(target)) { "远端附件缓存登记失败" }
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}

internal class AsciiCharSequenceInputStream(
    private val value: CharSequence,
) : InputStream() {
    private var index = 0

    override fun read(): Int = if (index >= value.length) {
        -1
    } else {
        value[index++].code.takeIf { it <= 0x7F } ?: error("Base64 内容包含非 ASCII 字符")
    }
}

private fun File.readHeader(maxBytes: Int = 16): ByteArray = inputStream().use { input ->
    val buffer = ByteArray(maxBytes)
    val count = input.read(buffer)
    if (count <= 0) ByteArray(0) else buffer.copyOf(count)
}

internal fun String.isPrivateArtifactHost(): Boolean {
    val host = lowercase().trim('[', ']')
    if (host == "localhost" || host == "::1" || host.startsWith("127.")) return true
    if (host.startsWith("10.") || host.startsWith("192.168.")) return true
    val parts = host.split('.')
    if (parts.size == 4 && parts[0] == "172" && parts[1].toIntOrNull() in 16..31) return true
    return runCatching {
        InetAddress.getAllByName(host).any { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
        }
    }.getOrDefault(true)
}

internal fun detectImageMimeType(bytes: ByteArray, contentType: String?): String? {
    val declared = contentType?.substringBefore(';')?.trim()?.lowercase()
    val detected = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        ) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 6 && bytes.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "GIF" -> "image/gif"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
    return detected?.takeIf { declared == null || declared == "application/octet-stream" || declared == it }
}

private fun String.imageExtension(): String = when (lowercase()) {
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

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
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "heif" -> "image/heif"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        "ico" -> "image/x-icon"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        else -> "*/*"
    }
}
