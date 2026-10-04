package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    val defaultTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val primaryColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    val lines = text.split("\n")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                trimmed == "---" || trimmed == "***" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                // Headings
                trimmed.startsWith("### ") -> {
                    val headerText = trimmed.removePrefix("### ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, defaultTextColor, primaryColor),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                trimmed.startsWith("## ") -> {
                    val headerText = trimmed.removePrefix("## ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, defaultTextColor, primaryColor),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                trimmed.startsWith("# ") -> {
                    val headerText = trimmed.removePrefix("# ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, defaultTextColor, primaryColor),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Bullet items (* item, - item, • item)
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                    val content = when {
                        trimmed.startsWith("* ") -> trimmed.removePrefix("* ")
                        trimmed.startsWith("- ") -> trimmed.removePrefix("- ")
                        else -> trimmed.removePrefix("• ")
                    }.trim()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                        Text(
                            text = parseInlineMarkdown(content, defaultTextColor, primaryColor),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = defaultTextColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Numbered lists (1. item, 2. item, etc.)
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val numberMatch = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
                    val num = numberMatch?.groupValues?.getOrNull(1) ?: "•"
                    val content = numberMatch?.groupValues?.getOrNull(2) ?: trimmed

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "$num.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = primaryColor,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(content, defaultTextColor, primaryColor),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = defaultTextColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Regular text paragraph
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed, defaultTextColor, primaryColor),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = defaultTextColor
                    )
                }
            }
            i++
        }
    }
}

/**
 * Parses inline markdown:
 * - `**bold text**`
 * - `*italic text*`
 * - `` `inline code` ``
 */
fun parseInlineMarkdown(
    rawText: String,
    defaultColor: Color,
    highlightColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = rawText.length

        while (cursor < length) {
            // Check for bold: **text**
            if (rawText.startsWith("**", cursor)) {
                val end = rawText.indexOf("**", cursor + 2)
                if (end != -1) {
                    val boldContent = rawText.substring(cursor + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(boldContent)
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Check for inline code: `code`
            if (rawText.startsWith("`", cursor)) {
                val end = rawText.indexOf("`", cursor + 1)
                if (end != -1) {
                    val codeContent = rawText.substring(cursor + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = highlightColor
                        )
                    )
                    append(codeContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Check for italic: *text* (single asterisk not followed by another asterisk)
            if (rawText.startsWith("*", cursor) && !rawText.startsWith("**", cursor)) {
                val end = rawText.indexOf("*", cursor + 1)
                if (end != -1 && (end == rawText.length - 1 || rawText[end + 1] != '*')) {
                    val italicContent = rawText.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(italicContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            append(rawText[cursor])
            cursor++
        }
    }
}
