package com.karelisio.bulle.modules.echo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/** Rose d'Écho. */
val EchoPink = Color(0xFFFF6FA5)
private val EchoPinkLight = Color(0xFFFFB3CF)

/** Chemin de cœur inscrit dans un carré de côté [s], centré en (0,0). */
private fun heartPath(s: Float): Path = Path().apply {
    val h = s / 2
    moveTo(0f, h * 0.9f)
    cubicTo(-h * 1.15f, h * 0.05f, -h * 0.95f, -h * 0.95f, 0f, -h * 0.45f)
    cubicTo(h * 0.95f, -h * 0.95f, h * 1.15f, h * 0.05f, 0f, h * 0.9f)
    close()
}

private fun DrawScope.heart(center: Offset, s: Float, brush: Brush, alpha: Float = 1f) {
    translate(center.x, center.y) { drawPath(heartPath(s), brush, alpha = alpha) }
}

/**
 * Cœur pulsé d'Écho : double battement doux (« boum-boum »), halo rose qui s'étend
 * et, si [particles], petits cœurs qui montent en ondulant. Figé si les animations sont coupées.
 */
@Composable
fun EchoHeart(modifier: Modifier = Modifier, size: Dp = 22.dp, particles: Boolean = false) {
    val reduce = rememberReduceMotion()
    val beat: State<Float>
    val halo: State<Float>
    val rise: State<Float>
    if (reduce) {
        beat = remember { mutableFloatStateOf(1f) }
        halo = remember { mutableFloatStateOf(0.4f) }
        rise = remember { mutableFloatStateOf(0.5f) }
    } else {
        val t = rememberInfiniteTransition(label = "echo")
        beat = t.animateFloat(
            1f, 1f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 1150
                    1.16f at 130; 0.98f at 260; 1.1f at 390; 1f at 600
                },
            ),
            label = "beat",
        )
        halo = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "halo")
        rise = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2300, easing = LinearEasing)), label = "rise")
    }
    val fill = remember { Brush.verticalGradient(listOf(EchoPinkLight, EchoPink)) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val heartSize = if (particles) s * 0.46f else s * 0.78f
        val c = Offset(this.size.width / 2, if (particles) this.size.height * 0.58f else this.size.height / 2)
        // Halo qui s'étend et s'efface.
        val h = halo.value
        drawCircle(EchoPink.copy(alpha = 0.35f * (1f - h)), heartSize * (0.45f + 0.55f * h), c)
        drawCircle(
            EchoPink.copy(alpha = 0.5f * (1f - h)), heartSize * (0.5f + 0.7f * h), c,
            style = Stroke(heartSize * 0.05f),
        )
        // Petits cœurs qui montent, déphasés.
        if (particles) {
            for (i in 0 until 4) {
                val p = (rise.value + i / 4f) % 1f
                val x = c.x + (i - 1.5f) * s * 0.14f + sin((p * 2 * PI + i).toFloat()) * s * 0.05f
                val y = c.y - heartSize * 0.3f - p * s * 0.5f
                val a = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
                heart(Offset(x, y), s * (0.1f + 0.04f * (i % 2)), fill, alpha = a.coerceIn(0f, 1f) * 0.85f)
            }
        }
        // Le cœur, qui bat.
        scale(beat.value, pivot = c) { heart(c, heartSize, fill) }
    }
}
