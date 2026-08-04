package com.denggl2.mason.model

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

internal data class PdfTextExtraction(
    val text: String,
    val pageCount: Int,
    val parsedPageCount: Int,
)

internal class PasswordProtectedPdfException(cause: Throwable) :
    IllegalArgumentException("PDF 已加密，无法解析", cause)

internal object PdfDocumentTextExtractor {
    fun extract(context: Context, bytes: ByteArray): PdfTextExtraction {
        require(bytes.isNotEmpty()) { "PDF 内容为空" }
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = try {
            PDDocument.load(bytes)
        } catch (error: InvalidPasswordException) {
            throw PasswordProtectedPdfException(error)
        }
        document.use {
            if (it.isEncrypted) {
                throw PasswordProtectedPdfException(IllegalStateException("PDF 使用了加密保护"))
            }
            require(it.numberOfPages > 0) { "PDF 没有可读取页面" }
            val parsedPageCount = minOf(it.numberOfPages, MAX_PDF_TEXT_PAGES)
            val output = StringBuilder()
            for (pageNumber in 1..parsedPageCount) {
                ensureParsingActive()
                val pageText = PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = pageNumber
                    endPage = pageNumber
                }.getText(it).normalizePdfText()
                if (pageText.isNotBlank()) {
                    if (output.isNotEmpty()) output.append("\n\n")
                    output.append("[PDF 第 ").append(pageNumber).append(" 页]\n")
                    output.append(pageText)
                }
                if (output.length >= MAX_EXTRACTED_CHARACTERS) break
            }
            return PdfTextExtraction(
                text = output.toString().take(MAX_EXTRACTED_CHARACTERS),
                pageCount = it.numberOfPages,
                parsedPageCount = parsedPageCount,
            )
        }
    }

    private fun ensureParsingActive() {
        if (Thread.currentThread().isInterrupted) {
            throw java.util.concurrent.CancellationException("PDF 解析已取消")
        }
    }

    private const val MAX_PDF_TEXT_PAGES = 50
    private const val MAX_EXTRACTED_CHARACTERS = 200_000
}

internal fun String.hasUsefulPdfText(): Boolean = count(Char::isLetterOrDigit) >= 8

private fun String.normalizePdfText(): String =
    replace('\u0000', ' ')
        .lineSequence()
        .map(String::trimEnd)
        .fold(mutableListOf<String>()) { lines, line ->
            if (line.isNotBlank() || lines.lastOrNull()?.isNotBlank() == true) lines += line
            lines
        }
        .joinToString("\n")
        .trim()
