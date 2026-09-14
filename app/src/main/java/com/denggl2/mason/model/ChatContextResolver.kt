package com.denggl2.mason.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.denggl2.mason.llm.ModelAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
            references.flatMap { reference ->
                currentCoroutineContext().ensureActive()
                try {
                    withTimeout(ATTACHMENT_PARSE_TIMEOUT_MS) {
                        if (reference.image) listOf(resolveImage(reference)) else resolveFile(reference)
                    }
                } catch (_: TimeoutCancellationException) {
                    listOf(failedAttachment(reference, "解析超过 15 秒，已停止"))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    listOf(failedAttachment(reference, error.message ?: "格式损坏或无法读取"))
                }
            }
        }

    private suspend fun resolveImage(reference: ChatAttachmentReference): ModelAttachment {
        val uri = Uri.parse(reference.uri)
        val bytes = readLimited(uri, MAX_IMAGE_BYTES)
        val normalized = normalizeImage(bytes)
        return ModelAttachment(
            name = reference.name,
            uri = "data:image/jpeg;base64,${Base64.encodeToString(normalized, Base64.NO_WRAP)}",
            mimeType = "image/jpeg",
        )
    }

    private fun normalizeImage(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "附件不是可读取的图片" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE)
        }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
            "图片解码失败"
        }
        val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val width = maxOf(1, (decoded.width * scale).toInt())
        val height = maxOf(1, (decoded.height * scale).toInt())
        val scaled = if (width == decoded.width && height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        }
        val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (scaled !== flattened) scaled.recycle()
        return ByteArrayOutputStream().use { output ->
            require(flattened.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, output)) {
                "图片压缩失败"
            }
            flattened.recycle()
            output.toByteArray()
        }
    }

    private suspend fun resolveFile(reference: ChatAttachmentReference): List<ModelAttachment> {
        val uri = Uri.parse(reference.uri)
        val mime = context.contentResolver.getType(uri) ?: "text/plain"
        if (mime == "application/pdf" || reference.name.endsWith(".pdf", ignoreCase = true)) {
            return resolvePdf(reference, uri)
        }
        if (OfficeDocumentTextExtractor.supports(reference.name, mime)) {
            val bytes = readLimited(uri, MAX_OFFICE_BYTES)
            val text = runInterruptible {
                OfficeDocumentTextExtractor.extract(reference.name, mime, bytes)
            }
            return listOf(ModelAttachment(reference.name, reference.uri, mime, inlineText = text))
        }
        if (reference.name.substringAfterLast('.', "").lowercase() in setOf("doc", "xls", "ppt")) {
            return listOf(failedAttachment(reference, "暂不支持旧版 Office 格式，请另存为 docx、xlsx 或 pptx"))
        }
        val bytes = readLimited(uri, MAX_TEXT_BYTES)
        val text = if (mime.startsWith("text/") || reference.name.hasTextExtension()) {
            bytes.toString(Charsets.UTF_8)
        } else {
            "文件 ${displayName(uri, reference.name)}，类型 $mime，大小 ${bytes.size} 字节。当前仅自动提取文本文件内容。"
        }
        return listOf(ModelAttachment(reference.name, reference.uri, mime, inlineText = text))
    }

    private suspend fun resolvePdf(reference: ChatAttachmentReference, uri: Uri): List<ModelAttachment> {
        val bytes = readLimited(uri, MAX_PDF_BYTES.toInt())
        val extracted = try {
            runInterruptible {
                PdfDocumentTextExtractor.extract(context, bytes)
            }
        } catch (error: PasswordProtectedPdfException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (extracted?.text?.hasUsefulPdfText() == true) {
            return listOf(
                ModelAttachment(
                    name = reference.name,
                    uri = reference.uri,
                    mimeType = "application/pdf",
                    inlineText = extracted.text,
                ),
            )
        }
        return renderPdfPages(reference, uri)
    }

    private suspend fun renderPdfPages(reference: ChatAttachmentReference, uri: Uri): List<ModelAttachment> {
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
            "无法读取 PDF"
        }
        try {
            descriptor.use { fileDescriptor ->
                require(fileDescriptor.statSize < 0L || fileDescriptor.statSize <= MAX_PDF_BYTES) {
                    "PDF 过大，最多支持 ${MAX_PDF_BYTES / 1024 / 1024} MB"
                }
                PdfRenderer(fileDescriptor).use { renderer ->
                    require(renderer.pageCount > 0) { "PDF 没有可读取页面" }
                    val pages = mutableListOf<ModelAttachment>()
                    for (pageIndex in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                        currentCoroutineContext().ensureActive()
                        pages += renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(1f, MAX_PDF_EDGE.toFloat() / maxOf(page.width, page.height))
                            val width = maxOf(1, (page.width * scale).toInt())
                            val height = maxOf(1, (page.height * scale).toInt())
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val output = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, PDF_JPEG_QUALITY, output)
                            bitmap.recycle()
                            ModelAttachment(
                                name = "${reference.name.substringBeforeLast('.')}-page-${pageIndex + 1}.jpg",
                                uri = "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}",
                                mimeType = "image/jpeg",
                            )
                        }
                    }
                    return pages
                }
            }
        } catch (error: SecurityException) {
            throw IllegalArgumentException("PDF 已加密，无法解析", error)
        }
    }

    private suspend fun readLimited(uri: Uri, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取附件" }
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "附件过大，最多支持 ${maxBytes / 1024 / 1024} MB" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun failedAttachment(reference: ChatAttachmentReference, reason: String): ModelAttachment =
        ModelAttachment(
            name = reference.name,
            uri = reference.uri,
            mimeType = "text/plain",
            inlineText = "附件 ${reference.name} 解析失败：$reason",
        )

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
        const val MAX_IMAGE_EDGE = 2048
        const val IMAGE_JPEG_QUALITY = 88
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_OFFICE_BYTES = 20 * 1024 * 1024
        const val MAX_PDF_BYTES = 25L * 1024 * 1024
        const val MAX_PDF_PAGES = 4
        const val MAX_PDF_EDGE = 1600
        const val PDF_JPEG_QUALITY = 85
        const val ATTACHMENT_PARSE_TIMEOUT_MS = 15_000L
    }
}

internal fun calculateImageSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > maxEdge) sampleSize *= 2
    return sampleSize
}
