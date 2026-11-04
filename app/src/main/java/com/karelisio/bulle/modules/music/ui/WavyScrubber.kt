package com.karelisio.bulle.modules.music.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Barre de progression ondulée et déplaçable : la partie écoulée ondule (vague qui avance pendant
 * la lecture), le reste est plat. Le pouce vertical se saisit pour chercher dans le morceau.
 *
 * [fraction] est lu dans la lambda de dessin : la progression image par image n'entraîne aucune recomposition.
 */
@Composable
internal fun WavyScrubber(
    fraction: () -> Float,
    playing: Boolean,
    accent: Color,
    seekable: Boolean,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Partie restante (trait plat) et pouce : blancs par défaut, sur le fond noir de l'île. */
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    thumbColor: Color = Color.White,
) {
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current
    val color by animateColorAsState(accent, tween(600), label = "scrubColor")
    var drag by remember { mutableStateOf<Float?>(null) }
    val phase = remember { mutableFloatStateOf(0f) }
    val amplitude = remember { Animatable(if (playing) 1f else 0f) }
    val thumb = remember { Animatable(1f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)

    // L'onde s'aplatit en pause et pendant le glissement (le geste prime sur la décoration).
    LaunchedEffect(playing, drag != null, reduceMotion) {
        val target = when {
            reduceMotion || !playing -> 0f
            drag != null -> 0.4f
            else -> 1f
        }
        amplitude.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(drag != null) {
        thumb.animateTo(if (drag != null) 1.4f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(playing, reduceMotion) {
        if (!playing || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                phase.floatValue = (phase.floatValue + (now - last) / 1e9f * 2.4f * PI.toFloat()) % (2 * PI.toFloat() * 1000f)
                last = now
            }
        }
    }

    val gestures = if (!seekable) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { pos ->
                val f = (pos.x / size.width).coerceIn(0f, 1f)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentOnSeek(f)
            }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { pos ->
                    drag = (pos.x / size.width).coerceIn(0f, 1f)
                    currentOnScrub(drag)
                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    drag = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                    currentOnScrub(drag)
                },
                onDragEnd = {
                    drag?.let { currentOnSeek(it) }
                    drag = null
                    currentOnScrub(null)
                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                },
                onDragCancel = {
                    drag = null
                    currentOnScrub(null)
                },
            )
        }

    val path = remember { Path() }
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .then(gestures)
            .drawBehind {
                val stroke = 4.dp.toPx()
                val inset = 3.dp.toPx()
                val start = inset
                val end = size.width - inset
                val cy = size.height / 2f
                val f = (drag ?: fraction()).coerceIn(0f, 1f)
                val x = start + (end - start) * f
                val gap = 5.dp.toPx()
                val amp = 3.2.dp.toPx() * amplitude.value
                val wavelength = 26.dp.toPx()

                // Partie restante : trait plat, discret.
                val restStart = min(x + gap, end)
                if (end - restStart > 0.5f) {
                    drawLine(
                        trackColor,
                        Offset(restStart, cy),
                        Offset(end, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                // Partie écoulée : vague qui s'amortit juste avant le pouce pour s'y raccorder en douceur.
                val playedEnd = max(start, x - gap)
                if (playedEnd - start > 0.5f) {
                    path.reset()
                    val step = 2f
                    var px = start
                    val damp = 8.dp.toPx()
                    while (true) {
                        val k = ((playedEnd - px) / damp).coerceIn(0f, 1f) * ((px - start) / damp).coerceIn(0f, 1f)
                        val y = cy + amp * k * sin((px / wavelength) * 2f * PI.toFloat() - phase.floatValue)
                        if (px == start) path.moveTo(px, y) else path.lineTo(px, y)
                        if (px >= playedEnd) break
                        px = min(px + step, playedEnd)
                    }
                    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
                // Pouce : barre verticale arrondie.
                val tw = 4.dp.toPx()
                val th = 18.dp.toPx() * thumb.value
                drawRoundRect(
                    thumbColor,
                    topLeft = Offset(x - tw / 2f, cy - th / 2f),
                    size = Size(tw, th),
                    cornerRadius = CornerRadius(tw / 2f),
                )
            },
    )
}
