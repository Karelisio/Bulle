package com.karelisio.bulle.core.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Bloc de markdown simplifié (titres, puces, paragraphes) : suffisant pour un changelog. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val depth: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

object Markdown {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)\\d+[.)]\\s+(.*)$")
    private val rule = Regex("^[-*_]{3,}$")

    /** Découpe le texte en blocs (pure). */
    fun parse(text: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
        for (raw in text.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            val h = heading.matchEntire(line.trimStart())
            val b = bullet.matchEntire(line) ?: ordered.matchEntire(line)
            when {
                h != null -> {
                    flush()
                    out += MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim().trimEnd('#').trim())
                }
                b != null -> {
                    flush()
                    out += MdBlock.Bullet(b.groupValues[2].trim(), b.groupValues[1].length / 2)
                }
                line.isBlank() || line.trim().matches(rule) -> flush()
                else -> {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(line.trim())
                }
            }
        }
        flush()
        return out
    }

    /** Mise en forme en ligne : **gras**, *italique*, `code`, [lien](url) → texte du lien. */
    fun inline(text: String, codeBackground: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> {
                    val end = text.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
                text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                    val end = text.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
                (text[i] == '*' || text[i] == '_') && text.indexOf(text[i], i + 1) > i + 1 &&
                    (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                    val end = text.indexOf(text[i], i + 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                text[i] == '[' -> {
                    val close = text.indexOf("](", i)
                    val paren = if (close > 0) text.indexOf(')', close) else -1
                    if (close > 0 && paren > 0) {
                        append(text.substring(i + 1, close))
                        i = paren + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
}

/** Rendu Material 3 d'un markdown de changelog. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { Markdown.parse(text) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    Markdown.inline(block.text),
                    style = if (block.level <= 2) type.titleMedium else type.titleSmall,
                    color = colors.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is MdBlock.Bullet -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Column(Modifier.padding(top = 8.dp, end = 10.dp)) {
                        Box(
                            Modifier.size(6.dp).background(colors.tertiary, CircleShape),
                        )
                    }
                    Text(
                        Markdown.inline(block.text, colors.surfaceContainerHighest),
                        style = type.bodyMedium,
                        color = colors.onSurface,
                    )
                }
                is MdBlock.Paragraph -> Text(
                    Markdown.inline(block.text, colors.surfaceContainerHighest),
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
