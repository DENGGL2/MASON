package com.denggl2.mason.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal object OfficeDocumentTextExtractor {
    fun supports(fileName: String, mimeType: String): Boolean =
        fileName.extension() in supportedExtensions || mimeType.lowercase() in supportedMimeTypes

    fun extract(fileName: String, mimeType: String, bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "文档内容为空" }
        val extension = fileName.extension().ifBlank { extensionForMimeType(mimeType) }
        val text = when (extension) {
            "docx" -> extractWord(bytes)
            "xlsx" -> extractWorkbook(bytes)
            "pptx" -> extractPresentation(bytes)
            else -> error("暂不支持解析此文档格式")
        }
        return normalize(text).take(MAX_EXTRACTED_CHARACTERS).also {
            require(it.isNotBlank()) { "文档中没有可提取的文本" }
        }
    }

    private fun extractWord(bytes: ByteArray): String {
        val entries = readEntries(bytes) { name ->
            name == "word/document.xml" ||
                name.matches(Regex("word/(header|footer)\\d+\\.xml"))
        }
        require("word/document.xml" in entries) { "Word 文档结构不完整或已加密" }
        return entries.entries
            .sortedBy { if (it.key == "word/document.xml") "0" else it.key }
            .joinToString("\n") { (_, xml) -> parseTextParagraphs(xml) }
    }

    private fun extractPresentation(bytes: ByteArray): String {
        val entries = readEntries(bytes) { name ->
            name.matches(Regex("ppt/slides/slide\\d+\\.xml"))
        }
        require(entries.isNotEmpty()) { "演示文稿结构不完整或已加密" }
        return entries.entries
            .sortedBy { slideNumber(it.key) }
            .mapIndexed { index, (_, xml) ->
                "[幻灯片 ${index + 1}]\n${parseTextParagraphs(xml)}"
            }
            .joinToString("\n\n")
    }

    private fun extractWorkbook(bytes: ByteArray): String {
        val entries = readEntries(bytes) { name ->
            name == "xl/sharedStrings.xml" ||
                name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))
        }
        val sheets = entries.filterKeys { it.startsWith("xl/worksheets/sheet") }
        require(sheets.isNotEmpty()) { "Excel 文档结构不完整或已加密" }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        return sheets.entries
            .sortedBy { sheetNumber(it.key) }
            .mapIndexed { index, (_, xml) ->
                "[工作表 ${index + 1}]\n${parseWorksheet(xml, sharedStrings)}"
            }
            .joinToString("\n\n")
    }

    private fun readEntries(
        bytes: ByteArray,
        shouldRead: (String) -> Boolean,
    ): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                ensureParsingActive()
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (!entry.isDirectory && shouldRead(name)) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryBytes = 0
                    while (true) {
                        ensureParsingActive()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        require(entryBytes <= MAX_XML_ENTRY_BYTES) { "文档内部单个 XML 文件过大" }
                        require(totalBytes <= MAX_TOTAL_XML_BYTES) { "文档解压后的内容过大" }
                        output.write(buffer, 0, count)
                    }
                    result[name] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun parseTextParagraphs(xml: ByteArray): String {
        val output = StringBuilder()
        parseXml(xml, object : DefaultHandler() {
            private var captureText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "t" -> captureText = true
                    "tab" -> output.append('\t')
                    "br" -> output.append('\n')
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                ensureParsingActive()
                if (captureText) output.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "t" -> captureText = false
                    "p" -> output.append('\n')
                }
            }
        })
        return output.toString()
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val values = mutableListOf<String>()
        parseXml(xml, object : DefaultHandler() {
            private var current: StringBuilder? = null
            private var captureText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "si" -> current = StringBuilder()
                    "t" -> captureText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                ensureParsingActive()
                if (captureText) current?.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "t" -> captureText = false
                    "si" -> values += current?.toString().orEmpty().also { current = null }
                }
            }
        })
        return values
    }

    private fun parseWorksheet(xml: ByteArray, sharedStrings: List<String>): String {
        val output = StringBuilder()
        parseXml(xml, object : DefaultHandler() {
            private var cellType: String? = null
            private var rawValue = StringBuilder()
            private var captureValue = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "c" -> {
                        cellType = attributes?.getValue("t")
                        rawValue = StringBuilder()
                    }
                    "v", "t" -> captureValue = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                ensureParsingActive()
                if (captureValue) rawValue.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                ensureParsingActive()
                when (elementName(localName, qName)) {
                    "v", "t" -> captureValue = false
                    "c" -> {
                        val raw = rawValue.toString()
                        val value = if (cellType == "s") {
                            raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                        } else {
                            raw
                        }
                        if (output.isNotEmpty() && output.last() !in setOf('\n', '\t')) output.append('\t')
                        output.append(value)
                        cellType = null
                    }
                    "row" -> output.append('\n')
                }
            }
        })
        return output.toString()
    }

    private fun parseXml(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    private fun normalize(value: String): String = value
        .replace('\u0000', ' ')
        .lineSequence()
        .map { it.trimEnd() }
        .fold(mutableListOf<String>()) { lines, line ->
            if (line.isNotBlank() || lines.lastOrNull()?.isNotBlank() == true) lines += line
            lines
        }
        .joinToString("\n")
        .trim()

    private fun elementName(localName: String?, qName: String?): String =
        localName?.takeIf(String::isNotBlank) ?: qName.orEmpty().substringAfter(':')

    private fun ensureParsingActive() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("文档解析已取消")
    }

    private fun String.extension(): String = substringAfterLast('.', "").lowercase()

    private fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        else -> ""
    }

    private fun slideNumber(path: String): Int =
        Regex("slide(\\d+)\\.xml").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun sheetNumber(path: String): Int =
        Regex("sheet(\\d+)\\.xml").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private val supportedExtensions = setOf("docx", "xlsx", "pptx")
    private val supportedMimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
    private const val MAX_XML_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_XML_BYTES = 20 * 1024 * 1024
    private const val MAX_EXTRACTED_CHARACTERS = 200_000
}
