package com.denggl2.masonremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.localizedText as Text

internal sealed interface RemoteMarkdownBlock {
    data class Text(val value: String, val style: RemoteMarkdownTextStyle) : RemoteMarkdownBlock
    data class Code(val language: String, val value: String) : RemoteMarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RemoteMarkdownBlock
}

internal enum class RemoteMarkdownTextStyle { BODY, HEADING, QUOTE, BULLET, TASK, ORDERED, DIFF }

internal fun parseRemoteMarkdown(source: String): List<RemoteMarkdownBlock> {
    if (source.isBlank()) return emptyList()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<RemoteMarkdownBlock>()
    val paragraph = StringBuilder()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += RemoteMarkdownBlock.Text(paragraph.toString(), RemoteMarkdownTextStyle.BODY)
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val fence = line.trim().removePrefix("```")
        if (line.trim().startsWith("```")) {
            flushParagraph()
            val language = fence.trim().lowercase()
            val code = StringBuilder()
            index += 1
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[index])
                index += 1
            }
            blocks += RemoteMarkdownBlock.Code(language, code.toString())
            index += 1
            continue
        }
        if (line.isBlank()) {
            flushParagraph()
            index += 1
            continue
        }
        if (index + 1 < lines.size && isTableSeparator(lines[index + 1]) && hasTablePipes(line)) {
            flushParagraph()
            val headers = splitTableLine(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && hasTablePipes(lines[index]) && lines[index].isNotBlank()) {
                rows += splitTableLine(lines[index])
                index += 1
            }
            blocks += RemoteMarkdownBlock.Table(headers, rows)
            continue
        }
        val trimmed = line.trimStart()
        when {
            trimmed.matches(Regex("^#{1,6}\\s+.+$")) -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text(
                    trimmed.replaceFirst(Regex("^#{1,6}\\s+"), ""),
                    RemoteMarkdownTextStyle.HEADING,
                )
            }
            trimmed.startsWith("> ") || trimmed == ">" -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text(trimmed.removePrefix("> ").removePrefix(">"), RemoteMarkdownTextStyle.QUOTE)
            }
            Regex("^[-*+] \\[([ xX])\\] ").containsMatchIn(trimmed) -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text(
                    trimmed.replaceFirst(Regex("^[-*+] \\[([ xX])\\] "), if (trimmed.contains("[x]", true)) "☑ " else "☐ "),
                    RemoteMarkdownTextStyle.TASK,
                )
            }
            trimmed.matches(Regex("^[-*+]\\s+.+$")) -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text("• " + trimmed.substring(2), RemoteMarkdownTextStyle.BULLET)
            }
            trimmed.matches(Regex("^\\d+[.]\\s+.+$")) -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text(trimmed, RemoteMarkdownTextStyle.ORDERED)
            }
            trimmed.startsWith("+") || trimmed.startsWith("-") -> {
                flushParagraph()
                blocks += RemoteMarkdownBlock.Text(trimmed, RemoteMarkdownTextStyle.DIFF)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        index += 1
    }
    flushParagraph()
    return blocks
}

@Composable
internal fun RemoteMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    // Conversation messages, activity text, command output, and table cells
    // come from the computer or the user. Keep them verbatim by default;
    // callers rendering an app-authored demo/status snippet can opt in.
    translateAppText: Boolean = false,
) {
    val visibleSource = if (translateAppText) LocalRemoteStrings.current.content(source) else source
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        parseRemoteMarkdown(visibleSource).forEachIndexed { index, block ->
            when (block) {
                is RemoteMarkdownBlock.Text -> RemoteMarkdownTextBlock(block, Modifier.fillMaxWidth(), compact)
                is RemoteMarkdownBlock.Code -> RemoteMarkdownCodeBlock(block, Modifier.fillMaxWidth(), compact)
                is RemoteMarkdownBlock.Table -> RemoteMarkdownTable(block, Modifier.fillMaxWidth(), compact)
            }
            if (index == 0 && visibleSource.isBlank()) Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun RemoteMarkdownTextBlock(
    block: RemoteMarkdownBlock.Text,
    modifier: Modifier,
    compact: Boolean,
) {
    val style = when (block.style) {
        RemoteMarkdownTextStyle.BODY -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 19.sp else 23.sp,
        )
        RemoteMarkdownTextStyle.HEADING -> MaterialTheme.typography.titleLarge.copy(
            fontSize = if (compact) 15.sp else 19.sp,
            lineHeight = if (compact) 21.sp else 27.sp,
        )
        RemoteMarkdownTextStyle.QUOTE -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 19.sp else 23.sp,
        )
        else -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 19.sp else 23.sp,
        )
    }
    val textModifier = when (block.style) {
        RemoteMarkdownTextStyle.QUOTE -> {
            val quoteShape = RoundedCornerShape(4.dp)
            val quoteBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            val quoteStroke = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
            modifier
                .clip(quoteShape)
                .background(quoteBackground)
                .drawBehind {
                    val strokeWidth = 3.dp.toPx()
                    drawRect(
                        color = quoteStroke,
                        size = Size(strokeWidth, size.height),
                    )
                }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        }
        RemoteMarkdownTextStyle.DIFF -> modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (block.value.startsWith("+")) Color(0xFF2E7D32).copy(alpha = 0.13f)
                else Color(0xFFC62828).copy(alpha = 0.13f),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
        else -> modifier
    }
    RemoteMarkdownInlineText(block.value, textModifier, style, block.style == RemoteMarkdownTextStyle.HEADING)
}

@Composable
private fun RemoteMarkdownInlineText(
    value: String,
    modifier: Modifier,
    style: androidx.compose.ui.text.TextStyle,
    heading: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(value, heading) { buildRemoteMarkdownText(value, heading) }
    var layoutResult by remember(value, heading) { mutableStateOf<TextLayoutResult?>(null) }
    val clickable = remember(annotated) { annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty() }
    Text(
        text = annotated,
        modifier = modifier.then(
            if (clickable) {
                Modifier.pointerInput(annotated) {
                    detectTapGestures { position ->
                        layoutResult?.getOffsetForPosition(position)?.let { offset ->
                            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                runCatching { uriHandler.openUri(it.item) }
                            }
                        }
                    }
                }
            } else Modifier
        ),
        color = MaterialTheme.colorScheme.onSurface,
        style = style,
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun RemoteMarkdownCodeBlock(
    block: RemoteMarkdownBlock.Code,
    modifier: Modifier,
    compact: Boolean,
) {
    val scrollState = rememberScrollState()
    val diff = block.language == "diff"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (block.language.isNotBlank()) {
            Text(
                text = block.language,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(5.dp))
        }
        Text(
            text = block.value,
            color = if (diff) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 16.sp else 19.sp,
            modifier = Modifier.horizontalScroll(scrollState),
        )
    }
}

@Composable
private fun RemoteMarkdownTable(
    block: RemoteMarkdownBlock.Table,
    modifier: Modifier,
    compact: Boolean,
) {
    val scrollState = rememberScrollState()
    val shape = RoundedCornerShape(7.dp)
    Column(
        modifier = modifier
            .horizontalScroll(scrollState)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape),
    ) {
        RemoteMarkdownTableRow(block.headers, header = true, compact = compact)
        block.rows.forEach { row -> RemoteMarkdownTableRow(row, header = false, compact = compact) }
    }
}

@Composable
private fun RemoteMarkdownTableRow(cells: List<String>, header: Boolean, compact: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (header) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .height(IntrinsicSize.Min)
            .heightIn(min = if (compact) 32.dp else 38.dp),
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(if (compact) 140.dp else 150.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 6.dp else 8.dp),
            ) {
                RemoteMarkdownInlineText(
                    value = cell,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    heading = false,
                )
            }
        }
    }
}

private fun buildRemoteMarkdownText(value: String, heading: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val tokenPattern = Regex("\\[[^]]+\\]\\(https?://[^)]+\\)|https?://[^\\s<>]+|`[^`]+`|\\*\\*[^*]+\\*\\*|__[^_]+__")
    var cursor = 0
    tokenPattern.findAll(value).forEach { match ->
        if (match.range.first > cursor) builder.append(value.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("[") -> {
                val split = token.indexOf("](")
                val label = token.substring(1, split)
                val url = token.substring(split + 2, token.length - 1)
                val start = builder.length
                builder.withStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)) {
                    append(label)
                }
                builder.addStringAnnotation("URL", url, start, builder.length)
            }
            token.startsWith("http") -> {
                val url = token.trimEnd('.', ',', '。', '，', '！', '！')
                val start = builder.length
                builder.withStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                builder.addStringAnnotation("URL", url, start, builder.length)
            }
            token.startsWith("`") -> builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0xFF8A8A8A).copy(alpha = 0.18f),
                ),
            ) { append(token.substring(1, token.length - 1)) }
            token.startsWith("**") || token.startsWith("__") -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.substring(2, token.length - 2))
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < value.length) builder.append(value.substring(cursor))
    if (builder.length == 0 && value.isNotEmpty()) builder.append(value)
    return builder.toAnnotatedString()
}

private fun hasTablePipes(line: String): Boolean = line.count { it == '|' } >= 1

private fun isTableSeparator(line: String): Boolean = splitTableLine(line).isNotEmpty() &&
    splitTableLine(line).all { it.trim().matches(Regex(":?-{3,}:?")) }

private fun splitTableLine(line: String): List<String> = line.trim()
    .removePrefix("|")
    .removeSuffix("|")
    .split('|')
    .map(String::trim)
