package com.karelisio.bulle.modules.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.abs

/** Fondu en haut et en bas d'une zone de texte défilante ([edge] = part de la hauteur estompée). */
private fun Modifier.fadingEdges(edge: Float = 0.2f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                edge to Color.Black,
                (1f - edge * 1.1f) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Espace entre deux lignes de paroles. */
private val LineSpacing = 8.dp

/** Taille de base de la ligne courante (multipliée par la taille choisie dans les réglages). */
private const val BASE_FONT_SP = 22f
private const val BASE_LINE_HEIGHT_SP = 29f

/** Les lignes voisines sont dessinées à cette échelle de la ligne courante. */
private const val INACTIVE_SCALE = 0.8f

/** Style d'une ligne de paroles synchronisées à l'échelle [scale] : grand, très gras, interligne confortable. */
@Composable
internal fun lyricsLineStyle(scale: Float): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontSize = (BASE_FONT_SP * scale).sp,
    lineHeight = (BASE_LINE_HEIGHT_SP * scale).sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.1).sp,
)

/** Hauteur de la fenêtre de paroles pour [lines] lignes visibles à l'échelle [scale]. */
@Composable
internal fun lyricsWindowHeight(lines: Int, scale: Float): Dp = with(LocalDensity.current) {
    ((BASE_LINE_HEIGHT_SP * scale).sp.toDp() + LineSpacing) * lines
}

/** Opacité d'une ligne selon sa distance à la ligne courante (contraste renforcé pour les voisines). */
internal fun lineAlpha(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.62f
    2 -> 0.46f
    else -> 0.34f
}

/** Mesures des lignes, partagées entre la mise en page et l'animation de défilement. */
private class LyricsScroll {
    val offset = Animatable(0f)
    /** Défilement souhaité, recalculé à chaque mesure (ligne courante placée un peu au-dessus du milieu). */
    val target = mutableFloatStateOf(Float.NaN)
}

/**
 * Paroles synchronisées : la ligne courante est mise en avant (pleine opacité, pleine taille),
 * les autres restent lisibles mais plus petites et s'estompent avec la distance (léger flou au-delà
 * de deux lignes) ; le défilement suit au ressort.
 */
@Composable
internal fun SyncedLyrics(
    lyrics: Lyrics,
    currentIndex: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    textScale: Float = 1f,
) {
    val reduceMotion = rememberReduceMotion()
    val scroll = remember(lyrics) { LyricsScroll() }
    LaunchedEffect(scroll, reduceMotion) {
        var first = true
        snapshotFlow { scroll.target.floatValue }.collect { t ->
            if (t.isNaN()) return@collect
            if (first || reduceMotion) {
                scroll.offset.snapTo(t)
                first = false
            } else {
                scroll.offset.animateTo(t, spring(dampingRatio = 0.86f, stiffness = 140f))
            }
        }
    }
    val style = lyricsLineStyle(textScale)
    // Fondu des bords proportionné à la hauteur : une grande fenêtre garde ses lignes nettes.
    val edge = with(LocalDensity.current) { (22.dp.toPx() / height.toPx().coerceAtLeast(1f)).coerceIn(0.08f, 0.2f) }
    Layout(
        content = {
            lyrics.lines.forEachIndexed { i, line ->
                LyricLineText(
                    text = line.text.ifBlank { "♪" },
                    distance = if (currentIndex < 0) i + 1 else abs(i - currentIndex),
                    style = style,
                    reduceMotion = reduceMotion,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .fadingEdges(edge),
    ) { measurables, constraints ->
        val lineConstraints = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(lineConstraints) }
        val spacing = LineSpacing.roundToPx()
        val tops = IntArray(placeables.size)
        var y = 0
        placeables.forEachIndexed { i, p -> tops[i] = y; y += p.height + spacing }
        val viewport = constraints.maxHeight
        if (placeables.isNotEmpty()) {
            val idx = currentIndex.coerceIn(0, placeables.lastIndex)
            // Ligne courante un peu au-dessus du milieu : on voit surtout ce qui arrive.
            val anchor = viewport * 0.38f
            scroll.target.floatValue = tops[idx] + placeables[idx].height / 2f - anchor
        }
        layout(constraints.maxWidth, viewport) {
            val off = scroll.offset.value
            placeables.forEachIndexed { i, p ->
                val top = tops[i] - off
                // Ne place que ce qui peut être visible.
                if (top + p.height >= -viewport && top <= viewport * 2) p.place(0, top.toInt())
            }
        }
    }
}

@Composable
private fun LyricLineText(text: String, distance: Int, style: TextStyle, reduceMotion: Boolean) {
    val spec = if (reduceMotion) snap() else spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val active by animateFloatAsState(if (distance == 0) 1f else 0f, spec, label = "active")
    val alpha by animateFloatAsState(lineAlpha(distance), spec, label = "alpha")
    // Flou très léger, seulement loin de la ligne courante : les voisines restent nettes.
    val blur by animateFloatAsState(if (reduceMotion) 0f else ((distance - 2).coerceIn(0, 2) * 0.7f), spec, label = "blur")
    Text(
        text,
        style = style,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                val s = INACTIVE_SCALE + (1f - INACTIVE_SCALE) * active
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
                val r = blur.dp.toPx()
                renderEffect = if (r > 0.3f) BlurEffect(r, r, TileMode.Decal) else null
            },
    )
}

/** Paroles non synchronisées : texte qui défile librement au doigt. */
@Composable
internal fun PlainLyrics(lyrics: Lyrics, height: Dp, modifier: Modifier = Modifier, textScale: Float = 1f) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = (18f * textScale).sp,
        lineHeight = (26f * textScale).sp,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .fadingEdges(0.12f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = height * 0.12f),
    ) {
        lyrics.lines.forEach { line ->
            Text(
                line.text,
                style = style,
                color = Color.White.copy(alpha = if (line.text.isBlank()) 0f else 0.9f),
            )
        }
    }
}
