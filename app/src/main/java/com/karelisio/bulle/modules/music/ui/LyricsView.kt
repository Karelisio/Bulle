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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.abs

/** Fondu en haut et en bas d'une zone de texte défilante. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.2f to Color.Black,
                0.78f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Mesures des lignes, partagées entre la mise en page et l'animation de défilement. */
private class LyricsScroll {
    val offset = Animatable(0f)
    /** Défilement souhaité, recalculé à chaque mesure (ligne courante placée au tiers de la hauteur). */
    val target = mutableFloatStateOf(Float.NaN)
}

/**
 * Paroles synchronisées : la ligne courante est mise en avant (pleine opacité, taille normale),
 * les autres s'estompent et se floutent légèrement avec la distance ; le défilement suit au ressort.
 */
@Composable
internal fun SyncedLyrics(lyrics: Lyrics, currentIndex: Int, height: Dp, modifier: Modifier = Modifier) {
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
    Layout(
        content = {
            lyrics.lines.forEachIndexed { i, line ->
                LyricLineText(
                    text = line.text.ifBlank { "♪" },
                    distance = if (currentIndex < 0) i + 1 else abs(i - currentIndex),
                    reduceMotion = reduceMotion,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .fadingEdges(),
    ) { measurables, constraints ->
        val lineConstraints = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(lineConstraints) }
        val spacing = 6.dp.roundToPx()
        val tops = IntArray(placeables.size)
        var y = 0
        placeables.forEachIndexed { i, p -> tops[i] = y; y += p.height + spacing }
        val viewport = constraints.maxHeight
        if (placeables.isNotEmpty()) {
            val idx = currentIndex.coerceIn(0, placeables.lastIndex)
            val anchor = viewport * 0.34f
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
private fun LyricLineText(text: String, distance: Int, reduceMotion: Boolean) {
    val spec = if (reduceMotion) snap() else spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val active by animateFloatAsState(if (distance == 0) 1f else 0f, spec, label = "active")
    val blur by animateFloatAsState(if (reduceMotion) 0f else ((distance - 1).coerceIn(0, 2) * 0.9f), spec, label = "blur")
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 0.34f + 0.66f * active
                val s = 0.92f + 0.08f * active
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
internal fun PlainLyrics(lyrics: Lyrics, height: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .fadingEdges()
            .verticalScroll(rememberScrollState())
            .padding(vertical = height * 0.18f),
    ) {
        lyrics.lines.forEach { line ->
            Text(
                line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = if (line.text.isBlank()) 0f else 0.82f),
            )
        }
    }
}
