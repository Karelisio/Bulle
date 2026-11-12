package com.karelisio.bulle.modules.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.weather.WeatherKind
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Sun = Color(0xFFFFD25E)
private val Moon = Color(0xFFE6E1FF)
private val CloudLight = Color(0xFFE3E9F2)
private val CloudDark = Color(0xFFA3AEBF)
private val Drop = Color(0xFF7CC4FF)
private val Snow = Color(0xFFFFFFFF)
private val Bolt = Color(0xFFFFE066)

/**
 * Icône météo dessinée en Compose, animée doucement (rayons qui tournent, gouttes qui tombent,
 * nuage qui dérive). La phase n'est lue qu'au dessin ; figée si « réduire les animations ».
 */
@Composable
fun WeatherGlyph(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, description: String? = null) {
    val reduceMotion = rememberReduceMotion()
    val phase: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.35f) }
    } else {
        rememberInfiniteTransition(label = "weather").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodOf(kind), easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    }
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val p = phase.value
        val u = this.size.minDimension
        when (kind) {
            WeatherKind.CLEAR -> if (isDay) sun(Offset(u / 2, u / 2), u * 0.22f, p) else moon(Offset(u / 2, u / 2), u * 0.3f)
            WeatherKind.PARTLY_CLOUDY -> {
                if (isDay) sun(Offset(u * 0.36f, u * 0.36f), u * 0.16f, p) else moon(Offset(u * 0.36f, u * 0.34f), u * 0.2f)
                cloud(u, Offset(u * 0.56f + drift(p, u), u * 0.62f), 0.8f, CloudLight)
            }
            WeatherKind.CLOUDY -> {
                cloud(u, Offset(u * 0.38f - drift(p, u), u * 0.42f), 0.6f, CloudDark)
                cloud(u, Offset(u * 0.54f + drift(p, u), u * 0.6f), 0.85f, CloudLight)
            }
            WeatherKind.FOG -> fog(u, p)
            WeatherKind.DRIZZLE -> {
                cloud(u, Offset(u / 2, u * 0.4f), 0.85f, CloudLight)
                drops(u, p, count = 2, length = 0.08f)
            }
            WeatherKind.RAIN -> {
                cloud(u, Offset(u / 2, u * 0.4f), 0.85f, CloudLight)
                drops(u, p, count = 3, length = 0.14f)
            }
            WeatherKind.SNOW -> {
                cloud(u, Offset(u / 2, u * 0.4f), 0.85f, CloudLight)
                flakes(u, p)
            }
            WeatherKind.STORM -> {
                cloud(u, Offset(u / 2, u * 0.38f), 0.85f, CloudDark)
                bolt(u, p)
            }
        }
    }
}

private fun periodOf(kind: WeatherKind): Int = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY -> 24_000
    WeatherKind.CLOUDY, WeatherKind.FOG -> 9_000
    WeatherKind.SNOW -> 3_200
    else -> 1_600
}

/** Dérive horizontale lente et réversible (sinus). */
private fun drift(p: Float, u: Float) = sin(p * 2 * PI).toFloat() * u * 0.03f

private fun DrawScope.sun(c: Offset, r: Float, p: Float) {
    drawCircle(Sun, r, c)
    rotate(p * 360f, c) {
        for (i in 0 until 8) {
            val a = i * PI / 4
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(Sun, c + dir * (r * 1.45f), c + dir * (r * 1.95f), strokeWidth = r * 0.32f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.55f, -r * 0.35f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), Moon)
}

/** Nuage centré en [c] ; [scale] relatif à la taille de l'icône. */
private fun DrawScope.cloud(u: Float, c: Offset, scale: Float, color: Color) {
    val s = u * scale
    drawCircle(color, s * 0.2f, c + Offset(-s * 0.16f, 0f))
    drawCircle(color, s * 0.26f, c + Offset(s * 0.08f, -s * 0.08f))
    drawRoundRect(
        color,
        topLeft = c + Offset(-s * 0.36f, -s * 0.02f),
        size = Size(s * 0.72f, s * 0.22f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.11f),
    )
}

private fun DrawScope.drops(u: Float, p: Float, count: Int, length: Float) {
    for (i in 0 until count) {
        val t = (p + i.toFloat() / count) % 1f
        val x = u * (0.32f + 0.18f * i) - t * u * 0.04f
        val y = u * (0.62f + 0.24f * t)
        val alpha = 1f - t
        drawLine(Drop.copy(alpha = alpha), Offset(x, y), Offset(x - u * 0.03f, y + u * length), strokeWidth = u * 0.07f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.flakes(u: Float, p: Float) {
    for (i in 0 until 3) {
        val t = (p + i / 3f) % 1f
        val x = u * (0.32f + 0.18f * i) + sin((t + i) * 2 * PI).toFloat() * u * 0.03f
        drawCircle(Snow.copy(alpha = 1f - t * 0.8f), u * 0.045f, Offset(x, u * (0.64f + 0.24f * t)))
    }
}

private fun DrawScope.bolt(u: Float, p: Float) {
    // Éclair qui « clignote » brièvement une fois par cycle.
    val alpha = if (p in 0.1f..0.2f) 0.45f else 1f
    val path = Path().apply {
        moveTo(u * 0.54f, u * 0.5f)
        lineTo(u * 0.4f, u * 0.72f)
        lineTo(u * 0.52f, u * 0.72f)
        lineTo(u * 0.44f, u * 0.94f)
        lineTo(u * 0.64f, u * 0.66f)
        lineTo(u * 0.52f, u * 0.66f)
        close()
    }
    drawPath(path, Bolt.copy(alpha = alpha))
}

private fun DrawScope.fog(u: Float, p: Float) {
    for (i in 0 until 3) {
        val shift = sin((p + i / 3f) * 2 * PI).toFloat() * u * 0.05f
        val y = u * (0.32f + 0.18f * i)
        drawLine(CloudLight.copy(alpha = 0.9f - i * 0.15f), Offset(u * 0.18f + shift, y), Offset(u * 0.82f + shift, y), strokeWidth = u * 0.08f, cap = StrokeCap.Round)
    }
}
