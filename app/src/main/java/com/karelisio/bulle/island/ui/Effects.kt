package com.karelisio.bulle.island.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Une petite bulle de l'éclat. */
private class Spark(
    val startX: Float, // fraction de la largeur de l'île (-0.5..0.5)
    val startY: Float, // fraction de la hauteur (0..1)
    val angle: Float,
    val distance: Float,
    val size: Float,
    val delay: Float,
    val hueShift: Float,
)

/**
 * Éclat : de petites bulles irisées s'échappent autour de l'île, flottent puis éclatent
 * (anneau qui s'ouvre), aux couleurs de l'alerte.
 */
class Burst(seed: Int, private val accent: Color) {
    private val sparks: List<Spark> = Random(seed).let { r ->
        List(16) {
            val side = r.nextFloat()
            Spark(
                startX = (side - 0.5f),
                startY = 0.4f + r.nextFloat() * 0.6f,
                // Vers le bas et les côtés (l'île touche le haut de l'écran), avec une remontée finale.
                angle = (PI / 2 + (side - 0.5f) * PI * 1.1f).toFloat(),
                distance = 0.6f + r.nextFloat() * 0.8f,
                size = 0.6f + r.nextFloat() * 0.8f,
                delay = r.nextFloat() * 0.25f,
                hueShift = r.nextFloat(),
            )
        }
    }

    private val palette = listOf(accent, lerp(accent, Color(0xFF8FE3FF), 0.5f), lerp(accent, Color(0xFFFFA8E8), 0.5f), Color.White)

    /**
     * @param t progression 0..1, [cx]/[top] : centre et haut de l'île, [w]/[h] : sa taille,
     * [unit] : 1 dp en pixels.
     */
    fun draw(scope: DrawScope, t: Float, cx: Float, top: Float, w: Float, h: Float, unit: Float) = with(scope) {
        for (s in sparks) {
            val p = ((t - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val ease = 1f - (1f - p) * (1f - p) * (1f - p)
            val reach = s.distance * 26f * unit * ease
            // Trajet : s'écarte puis remonte légèrement (flottaison).
            val x = cx + s.startX * w + cos(s.angle) * reach
            val y = top + s.startY * h + sin(s.angle) * reach - ease * ease * 10f * unit
            val r = s.size * 3.2f * unit * (0.4f + 0.6f * ease)
            val color = palette[(s.hueShift * palette.size).toInt().coerceIn(0, palette.lastIndex)]
            if (p < 0.82f) {
                val a = (p / 0.15f).coerceAtMost(1f)
                // Bulle : disque translucide + reflet.
                drawCircle(color.copy(alpha = 0.55f * a), radius = r, center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.85f * a), radius = r * 0.32f, center = Offset(x - r * 0.35f, y - r * 0.35f))
            } else {
                // Éclatement : anneau qui s'ouvre et s'efface.
                val q = (p - 0.82f) / 0.18f
                drawCircle(
                    color.copy(alpha = 0.9f * (1f - q)),
                    radius = r * (1f + q * 1.4f),
                    center = Offset(x, y),
                    style = Stroke(width = unit * (1.4f - q)),
                )
            }
        }
    }
}

/** Reflet irisé qui glisse en diagonale sur l'île (t de 0 à 1). */
fun DrawScope.drawShimmer(t: Float, accent: Color) {
    if (t <= 0f || t >= 1f) return
    val w = size.width
    val band = w * 0.35f + size.height
    val x = -band + (w + 2 * band) * t
    val fade = sin(t * PI).toFloat()
    val brush = Brush.linearGradient(
        0f to Color.Transparent,
        0.35f to accent.copy(alpha = 0.18f * fade),
        0.5f to Color.White.copy(alpha = 0.22f * fade),
        0.65f to Color(0xFF9EE7FF).copy(alpha = 0.16f * fade),
        1f to Color.Transparent,
        start = Offset(x, 0f),
        end = Offset(x + band, size.height),
    )
    drawRect(brush, blendMode = BlendMode.Plus)
}
