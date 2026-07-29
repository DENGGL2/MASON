package com.denggl2.mason.ui.chat

internal enum class MessageBlockKind { Heading, Paragraph, Bullet, Numbered, Code, Quote, Divider, Table }

internal data class MessageTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

internal data class MessageTextBlock(
    val kind: MessageBlockKind,
    val text: String = "",
    val meta: String? = null,
    val table: MessageTable? = null,
)

internal fun parseMessageBlocks(content: String): List<MessageTextBlock> {
    val blocks = mutableListOf<MessageTextBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    var codeLanguage: String? = null
    val code = StringBuilder()
    val lines = content.lines()
    var index = 0

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) blocks.add(MessageTextBlock(MessageBlockKind.Paragraph, text))
        paragraph.clear()
    }

    while (index < lines.size) {
        val rawLine = lines[index]
        val line = rawLine.trim()
        if (line.startsWith("```")) {
            if (inCode) {
                blocks.add(MessageTextBlock(MessageBlockKind.Code, code.toString().trimEnd(), codeLanguage))
                code.clear()
                codeLanguage = null
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = line.removePrefix("```").trim().ifBlank { null }
                inCode = true
            }
            index += 1
            continue
        }

        if (inCode) {
            code.appendLine(rawLine)
            index += 1
            continue
        }

        val headerCells = parseMarkdownTableRow(rawLine)
        val separatorLine = lines.getOrNull(index + 1)
        if (headerCells != null && separatorLine != null && isMarkdownTableSeparator(separatorLine, headerCells.size)) {
            flushParagraph()
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size) {
                val row = parseMarkdownTableRow(lines[index]) ?: break
                if (row.isEmpty()) break
                rows += normalizeTableRow(row, headerCells.size)
                index += 1
            }
            blocks += MessageTextBlock(
                kind = MessageBlockKind.Table,
                table = MessageTable(headerCells, rows),
            )
            continue
        }

        when {
            line.isBlank() -> flushParagraph()
            line == "---" || line == "***" -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Divider))
            }
            line.startsWith("#") -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Heading, line.trimStart('#').trim()))
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Quote, line.removePrefix(">").trim()))
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                flushParagraph()
                blocks.add(MessageTextBlock(MessageBlockKind.Bullet, line.drop(2).trim()))
            }
            line.matches(Regex("""\d+[.)]\s+.*""")) -> {
                flushParagraph()
                val label = line.substringBefore(' ').trim()
                blocks.add(
                    MessageTextBlock(
                        MessageBlockKind.Numbered,
                        line.substringAfter(' ').trim(),
                        label,
                    ),
                )
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(rawLine)
            }
        }
        index += 1
    }

    if (inCode) {
        blocks.add(MessageTextBlock(MessageBlockKind.Code, code.toString().trimEnd(), codeLanguage))
    } else {
        flushParagraph()
    }

    return blocks.ifEmpty {
        listOf(MessageTextBlock(MessageBlockKind.Paragraph, content))
    }
}

internal fun messageTableToTsv(table: MessageTable): String =
    (listOf(table.headers) + table.rows).joinToString("\n") { row -> row.joinToString("\t") }

internal fun messageTableToCsv(table: MessageTable): String =
    (listOf(table.headers) + table.rows).joinToString("\r\n") { row ->
        row.joinToString(",") { cell -> escapeCsvCell(cell) }
    }

private fun escapeCsvCell(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (value.any { it == ',' || it == '\"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

private fun isMarkdownTableSeparator(line: String, expectedColumns: Int): Boolean {
    val cells = parseMarkdownTableRow(line) ?: return false
    return cells.size == expectedColumns && cells.all { it.matches(Regex("^:?-{3,}:?$")) }
}

private fun normalizeTableRow(row: List<String>, columnCount: Int): List<String> =
    List(columnCount) { column -> row.getOrNull(column).orEmpty() }

private fun parseMarkdownTableRow(line: String): List<String>? {
    val trimmed = line.trim()
    if ('|' !in trimmed) return null
    val body = trimmed.removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var escaped = false
    body.forEach { char ->
        when {
            escaped && char == '|' -> {
                cell.append('|')
                escaped = false
            }
            escaped -> {
                cell.append('\\')
                cell.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(char)
        }
    }
    if (escaped) cell.append('\\')
    cells += cell.toString().trim()
    return cells
}
