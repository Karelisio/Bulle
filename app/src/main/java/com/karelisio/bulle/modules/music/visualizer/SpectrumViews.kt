package com.karelisio.bulle.modules.music.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlin.math.exp

/**
 * Inscrit la vue appelante auprès du visualiseur audio réel tant qu'elle est composée (donc affichée)
 * et que [enabled] et [active] sont vrais. Renvoie l'état des bandes réelles (null = pas de son réel :
 * utiliser l'animation simulée), ou null si la vue ne demande rien.
 */
@Composable
internal fun rememberRealSpectrum(enabled: Boolean, playing: Boolean, active: Boolean = true): State<FloatArray?>? {
    val context = LocalContext.current
    val token = remember { Any() }
    val want = enabled && active
    DisposableEffect(token, want, playing) {
        if (want) AudioVisualizer.update(context, token, enabled = true, playing = playing)
        onDispose { AudioVisualizer.remove(token) }
    }
    return if (want) AudioVisualizer.bands else null
}

/** Valeurs affichées : rejoignent la cible de façon exponentielle (captures à 25 Hz, affichage à chaque image). */
internal fun approach(current: Float, target: Float, dtSeconds: Float, rate: Float = 16f): Float =
    current + (target - current) * (1f - exp(-rate * dtSeconds.coerceIn(0f, 0.1f)))

/**
 * Onde symétrique qui suit le vrai son ([bands]), aux couleurs de la pochette ([accent]).
 * Les valeurs sont interpolées à chaque image et lues dans la phase de dessin uniquement.
 */
@Composable
internal fun AudioWave(bands: State<FloatArray?>, accent: Color, modifier: Modifier = Modifier) {
    val shown = remember { FloatArray(SpectrumMath.BANDS) }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(bands) {
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f
                last = now
                val target = bands.value
                for (i in shown.indices) shown[i] = approach(shown[i], target?.getOrNull(i) ?: 0f, dt)
                tick.longValue = now
            }
        }
    }
    val light = lerp(accent, Color.White, 0.45f)
    Canvas(modifier) {
        tick.longValue // invalide le dessin à chaque image
        val n = shown.size
        if (n < 2) return@Canvas
        val mid = size.height / 2f
        val amp = size.height / 2f
        val minHalf = size.height * 0.04f
        val step = size.width / (n - 1)
        fun half(i: Int) = (shown[i] * amp).coerceAtLeast(minHalf)
        val path = Path()
        // Courbe du haut (lissée par les milieux), puis retour par la courbe miroir du bas.
        path.moveTo(0f, mid - half(0))
        for (i in 1 until n) {
            val x0 = (i - 1) * step
            val x1 = i * step
            path.quadraticTo(x0, mid - half(i - 1), (x0 + x1) / 2f, mid - (half(i - 1) + half(i)) / 2f)
        }
        path.lineTo(size.width, mid - half(n - 1))
        path.lineTo(size.width, mid + half(n - 1))
        for (i in n - 1 downTo 1) {
            val x1 = i * step
            val x0 = (i - 1) * step
            path.quadraticTo(x1, mid + half(i), (x0 + x1) / 2f, mid + (half(i - 1) + half(i)) / 2f)
        }
        path.lineTo(0f, mid + half(0))
        path.close()
        drawPath(
            path,
            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.9f), light, accent.copy(alpha = 0.9f))),
        )
        // Fil central discret : l'onde reste lisible même dans les passages calmes.
        drawLine(light.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = minHalf)
    }
}
