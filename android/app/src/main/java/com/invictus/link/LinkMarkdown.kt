package com.invictus.link

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight markdown for agent replies: headers, bullet/numbered lists,
 * fenced code blocks, inline `code` and **bold**. No external dependencies.
 */

private sealed class MarkdownBlock {
    data class Code(val language: String, val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Numbered(val marker: String, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = input.replace("\r\n", "\n").split("\n")
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks.add(MarkdownBlock.Paragraph(text))
        paragraph.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i += 1
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i += 1
                }
                blocks.add(MarkdownBlock.Code(language, code.toString().trimEnd()))
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
                blocks.add(MarkdownBlock.Header(level, trimmed.dropWhile { it == '#' }.trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Bullet(trimmed.drop(2).trim()))
            }
            Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> {
                flushParagraph()
                val marker = trimmed.takeWhile { it.isDigit() }
                val text = trimmed.dropWhile { it.isDigit() }.drop(1).trim()
                blocks.add(MarkdownBlock.Numbered("$marker.", text))
            }
            trimmed.isBlank() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trim())
            }
        }
        i += 1
    }
    flushParagraph()
    return blocks
}

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = InvictusBrand.White)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i += 1
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = InvictusBrand.Accent,
                            background = Color(0x1AFFFFFF),
                            fontSize = 13.sp,
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i += 1
                }
            }
            else -> {
                append(text[i]); i += 1
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InvictusBrand.NavyDeep)
                            .padding(12.dp),
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                block.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = InvictusBrand.Muted,
                            )
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                block.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = InvictusBrand.White,
                            )
                        }
                    }
                }
                is MarkdownBlock.Header -> Text(
                    renderInline(block.text),
                    modifier = Modifier.fillMaxWidth(),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = InvictusBrand.White,
                )
                is MarkdownBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.Accent,
                    )
                    Text(
                        renderInline(block.text),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.White,
                    )
                }
                is MarkdownBlock.Numbered -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${block.marker}  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.Accent,
                    )
                    Text(
                        renderInline(block.text),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.White,
                    )
                }
                is MarkdownBlock.Paragraph -> Text(
                    renderInline(block.text),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InvictusBrand.White,
                )
            }
        }
    }
}
