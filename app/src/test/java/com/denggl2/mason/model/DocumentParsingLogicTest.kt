package com.denggl2.mason.model

import com.denggl2.mason.llm.ModelAttachment
import com.denggl2.mason.llm.model.ChatMessage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParsingLogicTest {
    @Test
    fun extractsWordParagraphsForLocalModelContext() {
        val document = officeZip(
            "word/document.xml" to """
                <document><body><p><r><t>第一段</t></r></p><p><r><t>第二段</t></r></p></body></document>
            """.trimIndent(),
        )

        val text = OfficeDocumentTextExtractor.extract("report.docx", "", document)

        assertTrue(text.contains("第一段\n第二段"))
    }

    @Test
    fun extractsSharedAndInlineSpreadsheetCells() {
        val workbook = officeZip(
            "xl/sharedStrings.xml" to """
                <sst><si><t>项目</t></si><si><t>MASON</t></si></sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet><sheetData><row><c t="s"><v>0</v></c><c t="s"><v>1</v></c><c><v>42</v></c></row></sheetData></worksheet>
            """.trimIndent(),
        )

        val text = OfficeDocumentTextExtractor.extract("data.xlsx", "", workbook)

        assertTrue(text.contains("项目\tMASON\t42"))
    }

    @Test
    fun presentationSlidesAreExtractedInNumericOrder() {
        val presentation = officeZip(
            "ppt/slides/slide2.xml" to "<sld><p><t>第二页</t></p></sld>",
            "ppt/slides/slide1.xml" to "<sld><p><t>第一页</t></p></sld>",
        )

        val text = OfficeDocumentTextExtractor.extract("slides.pptx", "", presentation)

        assertTrue(text.indexOf("第一页") < text.indexOf("第二页"))
    }

    @Test
    fun extractedDocumentTextIsEmbeddedWithoutLeakingTheContentUri() {
        val messages = listOf(ChatMessage(role = "user", content = "总结附件"))
        val attachment = ModelAttachment(
            name = "notes.docx",
            uri = "content://private/document/1",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            inlineText = "本地解析后的正文",
        )

        val prepared = appendInlineAttachmentText(messages, listOf(attachment)).single().content.orEmpty()

        assertTrue(prepared.contains("本地解析后的正文"))
        assertFalse(prepared.contains("content://private"))
    }

    @Test
    fun scannedPdfFallbackRequiresMeaningfulExtractedText() {
        assertFalse("\n  1  \n".hasUsefulPdfText())
        assertTrue("MASON PDF 报告正文".hasUsefulPdfText())
    }

    private fun officeZip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
