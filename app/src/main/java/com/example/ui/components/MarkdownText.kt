package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MarkdownText(
    text: String,
    onRunCode: (code: String, language: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val blocks = splitMarkdownBlocks(text)
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Code -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    CodeBlockWithRun(
                        code = block.code,
                        language = block.language,
                        onRunCode = onRunCode
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 14.sp
                    }
                    Text(
                        text = block.text,
                        color = EmeraldLight,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedStringFromMarkdown(block.text),
                        color = SlateTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun splitMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()
    var inCodeBlock = false
    var currentLang = ""
    val codeBuilder = StringBuilder()
    val textBuilder = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // End code block
                result.add(MarkdownBlock.Code(currentLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
            } else {
                // Flush text
                if (textBuilder.isNotBlank()) {
                    result.add(MarkdownBlock.Paragraph(textBuilder.toString().trim()))
                    textBuilder.clear()
                }
                currentLang = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (inCodeBlock) {
            codeBuilder.appendLine(line)
        } else {
            val trimmed = line.trim()
            if (trimmed.startsWith("### ")) {
                if (textBuilder.isNotBlank()) {
                    result.add(MarkdownBlock.Paragraph(textBuilder.toString().trim()))
                    textBuilder.clear()
                }
                result.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ")))
            } else if (trimmed.startsWith("## ")) {
                if (textBuilder.isNotBlank()) {
                    result.add(MarkdownBlock.Paragraph(textBuilder.toString().trim()))
                    textBuilder.clear()
                }
                result.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ")))
            } else if (trimmed.startsWith("# ")) {
                if (textBuilder.isNotBlank()) {
                    result.add(MarkdownBlock.Paragraph(textBuilder.toString().trim()))
                    textBuilder.clear()
                }
                result.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ")))
            } else {
                textBuilder.appendLine(line)
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        result.add(MarkdownBlock.Code(currentLang, codeBuilder.toString().trimEnd()))
    }
    if (textBuilder.isNotBlank()) {
        result.add(MarkdownBlock.Paragraph(textBuilder.toString().trim()))
    }

    return result
}

private fun buildAnnotatedStringFromMarkdown(raw: String) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        if (i + 1 < raw.length && raw[i] == '*' && raw[i + 1] == '*') {
            // Bold
            val end = raw.indexOf("**", i + 2)
            if (end != -1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SlateTextPrimary))
                append(raw.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        } else if (raw[i] == '`') {
            // Inline code
            val end = raw.indexOf('`', i + 1)
            if (end != -1) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CyanGlow,
                        background = CodeBackground,
                        fontSize = 12.sp
                    )
                )
                append(" ${raw.substring(i + 1, end)} ")
                pop()
                i = end + 1
                continue
            }
        }
        append(raw[i])
        i++
    }
}
