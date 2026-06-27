package com.aether.lv.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renderer markdown ringan untuk changelog GitHub Releases.
 *
 * Block:
 *   ## Heading 2        → teks tebal, warna primary
 *   ### Heading 3       → teks tebal medium
 *   - item / * item     → bullet list
 *   1. item             → numbered list
 *   > quote             → blockquote dengan garis │
 *   ---                 → separator (spasi)
 *
 * Inline (bisa nested):
 *   **teks**            → tebal
 *   *teks* / _teks_     → miring
 *   __teks__            → garis bawah
 *   `kode`              → monospace
 *   ~~teks~~            → strikethrough
 */

// ─── Block types ──────────────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading2(val text: String)                   : MdBlock()
    data class Heading3(val text: String)                   : MdBlock()
    data class Bullet(val text: String)                     : MdBlock()
    data class Numbered(val n: Int, val text: String)       : MdBlock()
    data class Quote(val text: String)                      : MdBlock()
    data class Paragraph(val text: String)                  : MdBlock()
    object Divider                                          : MdBlock()
    object Blank                                            : MdBlock()
}

// ─── Block parser ─────────────────────────────────────────────────────────────

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    for (line in markdown.lines()) {
        val t = line.trim()
        when {
            t.isBlank()                                     -> blocks.add(MdBlock.Blank)
            t == "---" || t == "***" || t == "___"          -> blocks.add(MdBlock.Divider)
            t.startsWith("## ")                             -> blocks.add(MdBlock.Heading2(t.removePrefix("## ").trim()))
            t.startsWith("### ")                            -> blocks.add(MdBlock.Heading3(t.removePrefix("### ").trim()))
            t.startsWith("- ")                              -> blocks.add(MdBlock.Bullet(t.removePrefix("- ").trim()))
            t.startsWith("* ")                              -> blocks.add(MdBlock.Bullet(t.removePrefix("* ").trim()))
            t.matches(Regex("^\\d+\\.\\s.*")) -> {
                val dot = t.indexOf('.')
                val n   = t.substring(0, dot).toIntOrNull() ?: 1
                blocks.add(MdBlock.Numbered(n, t.substring(dot + 1).trim()))
            }
            t.startsWith("> ")                              -> blocks.add(MdBlock.Quote(t.removePrefix("> ").trim()))
            t.isNotEmpty()                                  -> blocks.add(MdBlock.Paragraph(t))
        }
    }
    return blocks
}

// ─── Inline parser ────────────────────────────────────────────────────────────

// Extension helper — cek apakah string mulai dengan prefix di posisi idx
private fun String.matchAt(idx: Int, prefix: String): Boolean {
    if (idx + prefix.length > length) return false
    return regionMatches(idx, prefix, 0, prefix.length)
}

/**
 * Parse inline markdown menjadi AnnotatedString.
  * Prioritas: ~~  >  **  >  __  >  *_  >  `
 */
internal fun parseInline(
    text           : String,
    codeBackground : Color = Color.Unspecified,
    codeForeground : Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // ── Inline code `…` ─────────────────────────────────────
            text.matchAt(i, "`") -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        color      = if (codeForeground != Color.Unspecified) codeForeground else Color.Unspecified,
                        background = if (codeBackground != Color.Unspecified) codeBackground else Color.Unspecified,
                    )) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            // ── Strikethrough ~~…~~ ──────────────────────────────────
            text.matchAt(i, "~~") -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(parseInline(text.substring(i + 2, end), codeBackground, codeForeground))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // ── Bold **…** ───────────────────────────────────────────
            text.matchAt(i, "**") -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(parseInline(text.substring(i + 2, end), codeBackground, codeForeground))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // ── Underline __…__ ─────────────────────────────────────
            text.matchAt(i, "__") -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(parseInline(text.substring(i + 2, end), codeBackground, codeForeground))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // ── Italic *…* ───────────────────────────────────────────
            text.matchAt(i, "*") -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(parseInline(text.substring(i + 1, end), codeBackground, codeForeground))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            // ── Italic _…_ (hanya jika bukan __ yang sudah ditangani) ─
            text.matchAt(i, "_") && !text.matchAt(i, "__") -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1 && !text.matchAt(end, "__")) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(parseInline(text.substring(i + 1, end), codeBackground, codeForeground))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

// ─── Composable ───────────────────────────────────────────────────────────────

@Composable
fun MarkdownText(
    markdown : String,
    modifier : Modifier = Modifier,
) {
    val colorScheme    = MaterialTheme.colorScheme
    val typography     = MaterialTheme.typography
    val codeBackground = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
    val codeForeground = colorScheme.tertiary

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        var prevWasDivider = false

        for (block in parseBlocks(markdown)) {
            when (block) {
                MdBlock.Blank -> { prevWasDivider = false }

                MdBlock.Divider -> { prevWasDivider = true }

                is MdBlock.Heading2 -> {
                    Text(
                        text       = parseInline(block.text, codeBackground, codeForeground),
                        style      = typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = colorScheme.primary,
                        modifier   = Modifier.padding(top = if (prevWasDivider) 4.dp else 10.dp, bottom = 2.dp),
                    )
                    prevWasDivider = false
                }

                is MdBlock.Heading3 -> {
                    Text(
                        text       = parseInline(block.text, codeBackground, codeForeground),
                        style      = typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = colorScheme.onSurfaceVariant,
                        modifier   = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    prevWasDivider = false
                }

                is MdBlock.Bullet -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.padding(vertical = 1.5.dp),
                    ) {
                        Text(
                            "•",
                            style      = typography.bodySmall,
                            color      = colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier   = Modifier.padding(top = 0.5.dp),
                        )
                        Text(
                            text  = parseInline(block.text, codeBackground, codeForeground),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    prevWasDivider = false
                }

                is MdBlock.Numbered -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.padding(vertical = 1.5.dp),
                    ) {
                        Text(
                            "${block.n}.",
                            style      = typography.bodySmall,
                            color      = colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier   = Modifier.padding(top = 0.5.dp),
                        )
                        Text(
                            text  = parseInline(block.text, codeBackground, codeForeground),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    prevWasDivider = false
                }

                is MdBlock.Quote -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier              = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            "│",
                            style      = typography.bodySmall,
                            color      = colorScheme.primary.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text      = parseInline(block.text, codeBackground, codeForeground),
                            style     = typography.bodySmall,
                            color     = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    prevWasDivider = false
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text     = parseInline(block.text, codeBackground, codeForeground),
                        style    = typography.bodySmall,
                        color    = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                    prevWasDivider = false
                }
            }
        }
    }
}
