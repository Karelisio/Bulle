package com.karelisio.bulle.modules.capture

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Durée écoulée qui avance à chaque seconde pile du chrono (simple attente, pas une animation).
 * @param elapsed durée écoulée à un instant `elapsedRealtime` donné.
 */
@Composable
fun rememberChrono(running: Boolean, elapsed: (Long) -> Long): Long {
    val value by produceState(elapsed(SystemClock.elapsedRealtime()), running, elapsed) {
        value = elapsed(SystemClock.elapsedRealtime())
        if (!running) return@produceState
        while (true) {
            val e = elapsed(SystemClock.elapsedRealtime())
            value = e
            delay(1000 - e % 1000 + 5)
        }
    }
    return value
}

/** Niveau sonore lissé au ressort ; la valeur n'est lue qu'au dessin. */
@Composable
private fun rememberLevel(level: StateFlow<Float>): Animatable<Float, *> {
    val reduceMotion = rememberReduceMotion()
    val anim = remember { Animatable(level.value) }
    LaunchedEffect(level, reduceMotion) {
        level.collectLatest { v ->
            if (reduceMotion) anim.snapTo(v) else anim.animateTo(v, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    return anim
}

private val BarShape = floatArrayOf(0.55f, 1f, 0.75f, 0.9f, 0.5f)

/** Petites barres de niveau (vue compacte). */
@Composable
fun LevelBars(level: StateFlow<Float>, color: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val anim = rememberLevel(level)
    Canvas(modifier) {
        val v = anim.value
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = (size.height * (0.18f + 0.82f * v * BarShape[i % BarShape.size])).coerceAtMost(size.height)
            drawRoundRect(
                color,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/**
 * Forme d'onde défilante (vue étendue) : historique des niveaux réels, une barre par mesure.
 * L'historique est un tableau circulaire ; seul un compteur lu au dessin invalide le tracé.
 */
@Composable
fun LevelWave(level: StateFlow<Float>, color: Color, modifier: Modifier = Modifier, samples: Int = 36) {
    val history = remember(samples) { FloatArray(samples) }
    val head = remember { mutableIntStateOf(0) }
    LaunchedEffect(level) {
        level.collect { v ->
            history[head.intValue % samples] = v
            head.intValue++
        }
    }
    Canvas(modifier) {
        val count = head.intValue
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (samples - 1)) / samples
        for (i in 0 until samples) {
            // La plus récente à droite.
            val idx = count - samples + i
            val v = if (idx < 0) 0f else history[idx % samples]
            val h = size.height * (0.08f + 0.92f * v)
            drawRoundRect(
                color.copy(alpha = 0.35f + 0.65f * (i + 1f) / samples),
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}
