package com.denggl2.mason.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.denggl2.mason.llm.ModelAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatAttachmentReference(
    val name: String,
    val uri: String,
    val image: Boolean,
)

data class ChatRequestContext(
    val userText: String,
    val attachments: List<ChatAttachmentReference>,
    val skillId: String? = null,
)

object ChatContextParser {
    private const val HEADER = "Mason 附加上下文"

    fun parse(content: String): ChatRequestContext {
        val marker = Regex("\\n\\s*---\\s*\\n\\s*${Regex.escape(HEADER)}")
            .find(content)
            ?: return ChatRequestContext(content.trim(), emptyList())
        val body = content.substring(0, marker.range.first).trim()
        val context = content.substring(marker.range.last + 1)
        val attachments = mutableListOf<ChatAttachmentReference>()
        var skillId: String? = null
        context.lines().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("-").trim()
            when {
                line.startsWith("Skill：") -> skillId = line.removePrefix("Skill：")
                    .substringBefore('|').trim().takeIf(String::isNotBlank)
                line.startsWith("图片：") -> line.toReference(image = true)?.let(attachments::add)
                line.startsWith("文件：") -> line.toReference(image = false)?.let(attachments::add)
            }
        }
        return ChatRequestContext(body, attachments, skillId)
    }

    private fun String.toReference(image: Boolean): ChatAttachmentReference? {
        val value = substringAfter('：')
        val parts = value.split('|', limit = 2).map(String::trim)
        val uri = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        return ChatAttachmentReference(
            name = parts.firstOrNull().orEmpty().ifBlank { if (image) "图片" else "文件" },
            uri = uri,
            image = image,
        )
    }
}

@Singleton
class ChatAttachmentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun resolve(references: List<ChatAttachmentReference>): List<ModelAttachment> =
        withContext(Dispatchers.IO) {
            references.map { reference ->
                if (reference.image) resolveImage(reference) else resolveFile(reference)
            }
        }

    private fun resolveImage(reference: ChatAttachmentReference): ModelAttachment {
        val uri = Uri.parse(reference.uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = readLimited(uri, MAX_IMAGE_BYTES)
        return ModelAttachment(
            name = reference.name,
            uri = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
            mimeType = mime,
        )
    }

    private fun resolveFile(reference: ChatAttachmentReference): ModelAttachment {
        val uri = Uri.parse(reference.uri)
        val mime = context.contentResolver.getType(uri) ?: "text/plain"
        val bytes = readLimited(uri, MAX_TEXT_BYTES)
        val text = if (mime.startsWith("text/") || reference.name.hasTextExtension()) {
            bytes.toString(Charsets.UTF_8)
        } else {
            "文件 ${displayName(uri, reference.name)}，类型 $mime，大小 ${bytes.size} 字节。当前仅自动提取文本文件内容。"
        }
        return ModelAttachment(reference.name, reference.uri, mime, inlineText = text)
    }

    private fun readLimited(uri: Uri, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取附件" }
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "附件过大，最多支持 ${maxBytes / 1024 / 1024} MB" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun displayName(uri: Uri, fallback: String): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull().orEmpty().ifBlank { fallback }

    private fun String.hasTextExtension(): Boolean = substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "markdown", "json", "csv", "xml", "yaml", "yml", "kt", "java", "py", "js", "ts",
    )

    private companion object {
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    }
}
