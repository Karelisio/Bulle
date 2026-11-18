package com.karelisio.bulle.modules.standby.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.karelisio.bulle.modules.standby.SceneLogic
import com.karelisio.bulle.modules.standby.SceneLogic.frac
import com.karelisio.bulle.modules.standby.SceneLogic.rand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Ce dont une scène a besoin pour dessiner une image. */
internal class SceneFrame(
    /** Temps de la scène (s), déjà multiplié par la vitesse. */
    val t: Float,
    val colors: List<Color>,
    val density: Float,
    /** Pulsation d'une notification (0 – 1) et sa couleur. */
    val pulse: Float,
    val pulseColor: Color,
    /** Musique en lecture (vagues). */
    val playing: Boolean = false,
) {
    fun color(i: Int): Color = colors[((i % colors.size) + colors.size) % colors.size]
}

/**
 * Effet « liquide » (métaballes sans shader, Android 12+) : flou puis seuil sur l'alpha — deux
 * formes proches fusionnent par un pont, puis se séparent en gouttes nettes.
 */
internal fun gooeyEffect(blurPx: Float) = RenderEffect.createChainEffect(
    RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 22f, -9f * 255f,
                ),
            ),
        ),
    ),
    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL),
).asComposeRenderEffect()

/** Flou doux (aurore). */
internal fun softBlur(px: Float) = RenderEffect.createBlurEffect(px, px, Shader.TileMode.DECAL).asComposeRenderEffect()

/** Lampe à lave : gouttes qui montent, descendent, fusionnent ; une flaque en bas et en haut. */
internal fun DrawScope.drawLava(f: SceneFrame) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    val n = SceneLogic.count(7, f.density).coerceAtMost(16)
    val grow = 1f + 0.18f * f.pulse
    // Flaques (la lave s'y fond et s'en détache).
    drawOval(f.color(0), Offset(-w * 0.1f, h * 0.9f), Size(w * 1.2f, h * 0.3f))
    drawOval(f.color(1), Offset(-w * 0.1f, -h * 0.2f), Size(w * 1.2f, h * 0.26f))
    repeat(n) { i ->
        val r = m * (0.07f + 0.08f * rand(i, 1)) * grow
        val vy = 0.04f + 0.05f * rand(i, 2)
        val vx = 0.03f + 0.04f * rand(i, 3)
        val cx = w * (0.5f + 0.4f * sin(f.t * vx + rand(i, 4) * 6.28f))
        // Montée et descente lentes, d'une flaque à l'autre.
        val cy = h * (0.5f + 0.47f * sin(f.t * vy + rand(i, 5) * 6.28f))
        drawCircle(f.color(i), r, Offset(cx, cy))
    }
    // Notification : une goutte de sa couleur tombe depuis l'île.
    if (f.pulse > 0.01f) {
        drawCircle(f.pulseColor, m * 0.12f * f.pulse, Offset(w / 2, h * (0.08f + 0.25f * (1f - f.pulse))))
    }
}

/** Bulles de savon irisées qui montent, oscillent et éclatent. */
internal fun DrawScope.drawBubbles(f: SceneFrame) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    val dp = density
    val n = SceneLogic.count(14, f.density).coerceAtMost(40)
    repeat(n) { i ->
        val r = m * (0.03f + 0.06f * rand(i, 2))
        val (prog, pop) = SceneLogic.bubbleLife(i, f.t)
        val travel = (h + 2 * r) * (0.55f + 0.45f * rand(i, 6))
        val x = w * (0.06f + 0.88f * rand(i, 1)) + sin(f.t * 0.6f + i) * w * 0.02f
        val y = h + r - prog * travel
        val c = Offset(x, y)
        val tint = f.color(i)
        if (pop == null) {
            drawCircle(Brush.radialGradient(listOf(Color.Transparent, tint.copy(alpha = 0.16f)), c, r), r, c)
            val iris = listOf(tint, Color(0xFFFFFFFF), f.color(i + 1), f.color(i + 2), tint)
            drawCircle(
                Brush.sweepGradient(iris.map { it.copy(alpha = 0.75f) }, c),
                r, c, style = Stroke(1.6f * dp),
            )
            // Reflet en haut à gauche.
            drawArc(
                Color.White.copy(alpha = 0.55f), 195f, 55f, false,
                Offset(x - r * 0.72f, y - r * 0.72f), Size(r * 1.44f, r * 1.44f),
                style = Stroke(2f * dp, cap = StrokeCap.Round),
            )
        } else {
            // Éclat : un anneau qui s'efface et des gouttelettes projetées.
            val a = 1f - pop
            drawCircle(tint.copy(alpha = 0.5f * a), r * (1f + 0.4f * pop), c, style = Stroke(1.2f * dp))
            repeat(8) { k ->
                val ang = k * (PI.toFloat() / 4f) + rand(i, 7)
                val d = r * (1f + 1.3f * pop)
                drawCircle(f.color(i + k).copy(alpha = a), 2.2f * dp * a + 0.5f, Offset(x + cos(ang) * d, y + sin(ang) * d))
            }
        }
    }
}

/** Aurore : rideaux de lumière ondulants, bord inférieur brillant, rayons verticaux, étoiles. */
internal fun DrawScope.drawAurora(f: SceneFrame) {
    val w = size.width
    val h = size.height
    val dp = density
    val bands = SceneLogic.count(3, f.density).coerceAtMost(6)
    val glow = 1f + 0.6f * f.pulse
    repeat(bands) { k ->
        val col = f.color(k)
        val base = h * (0.42f + 0.12f * k / max(1, bands - 1).toFloat())
        val curtain = h * (0.32f + 0.08f * rand(k, 9))
        val amp = h * 0.08f
        val ph = rand(k, 10) * 6.28f
        val v = 0.18f + 0.12f * rand(k, 11)
        fun edge(x: Float): Float =
            base + amp * sin(x / w * 5.2f + f.t * v + ph) + amp * 0.5f * sin(x / w * 11f - f.t * v * 1.6f + ph * 2)
        val steps = 48
        val path = Path()
        for (s in 0..steps) {
            val x = w * s / steps
            val y = edge(x) - curtain
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (s in steps downTo 0) {
            val x = w * s / steps
            path.lineTo(x, edge(x))
        }
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.7f to col.copy(alpha = (0.18f * glow).coerceAtMost(1f)),
                1f to col.copy(alpha = (0.55f * glow).coerceAtMost(1f)),
                startY = base - curtain - amp, endY = base + amp,
            ),
        )
        // Rayons.
        repeat(36) { j ->
            val x = w * (j + rand(j, k)) / 36f
            val a = 0.10f * (0.5f + 0.5f * sin(f.t * (0.6f + rand(j, 12)) + j)) * glow
            val y = edge(x)
            drawLine(col.copy(alpha = a.coerceIn(0f, 1f)), Offset(x, y), Offset(x, y - curtain * (0.5f + 0.5f * rand(j, 13))), 3f * dp)
        }
    }
}

/** Étoiles fixes qui scintillent (fond de l'aurore, hors du calque flouté). */
internal fun DrawScope.drawTwinkles(f: SceneFrame, count: Int) {
    repeat(count) { i ->
        val a = 0.25f + 0.35f * (0.5f + 0.5f * sin(f.t * (0.5f + rand(i, 21)) + i))
        drawCircle(Color.White.copy(alpha = a), (0.6f + rand(i, 22)) * density, Offset(size.width * rand(i, 23), size.height * 0.55f * rand(i, 24)))
    }
}

/** Vagues superposées : amples et rythmées en musique, lentes sinon. */
internal fun DrawScope.drawWaves(f: SceneFrame) {
    val w = size.width
    val h = size.height
    val layers = SceneLogic.count(4, f.density).coerceAtMost(8)
    val beat = if (f.playing) 0.5f + 0.5f * sin(f.t * 2f * PI.toFloat() * 1.8f) else 0f
    val amp = h * (if (f.playing) 0.07f + 0.035f * beat else 0.035f) * (1f + 0.5f * f.pulse)
    repeat(layers) { k ->
        val col = f.color(k)
        val base = h * (0.5f + 0.4f * (k + 1) / (layers + 1).toFloat())
        val v = (if (f.playing) 0.9f else 0.35f) * (0.7f + 0.6f * rand(k, 31))
        val ph = rand(k, 32) * 6.28f
        val path = Path()
        val steps = 64
        for (s in 0..steps) {
            val x = w * s / steps
            val y = base + amp * sin(x / w * (3f + k) * 2f + f.t * v + ph) + amp * 0.4f * sin(x / w * 13f - f.t * v * 1.7f + ph)
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val crest = Path().apply { addPath(path) }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.28f + 0.1f * k / layers), Color.Black.copy(alpha = 0.6f)), base - amp, h))
        drawPath(crest, col.copy(alpha = 0.7f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
    }
}

/** Constellation : points qui dérivent, reliés quand ils sont proches ; étoile filante de temps en temps. */
internal fun DrawScope.drawStars(f: SceneFrame) {
    val w = size.width
    val h = size.height
    val dp = density
    val n = SceneLogic.count(42, f.density).coerceAtMost(110)
    // Le domaine déborde de 10 % : les points réapparaissent hors champ.
    val xs = FloatArray(n) { i -> -0.1f * w + 1.2f * w * frac(rand(i, 1) + f.t * (rand(i, 2) - 0.5f) * 0.012f) }
    val ys = FloatArray(n) { i -> -0.1f * h + 1.2f * h * frac(rand(i, 3) + f.t * (rand(i, 4) - 0.5f) * 0.012f) }
    val maxD = min(w, h) * 0.24f
    val bright = 1f + 1.2f * f.pulse
    for (i in 0 until n) for (j in i + 1 until n) {
        val d = hypot(xs[i] - xs[j], ys[i] - ys[j])
        if (d < maxD) {
            val a = ((1f - d / maxD) * 0.45f * bright).coerceAtMost(1f)
            drawLine(f.color(i).copy(alpha = a), Offset(xs[i], ys[i]), Offset(xs[j], ys[j]), 0.9f * dp)
        }
    }
    for (i in 0 until n) {
        val a = 0.55f + 0.45f * sin(f.t * (0.8f + 2f * rand(i, 5)) + i)
        drawCircle(f.color(i).copy(alpha = a.coerceIn(0.1f, 1f)), (1.2f + 1.4f * rand(i, 6)) * dp, Offset(xs[i], ys[i]))
    }
    // Étoile filante (toutes les ~23 s de scène).
    val k = (f.t / 23f).toInt()
    val s = frac(f.t / 23f)
    if (s < 0.06f) {
        val p = s / 0.06f
        val x0 = w * (0.1f + 0.6f * rand(k, 41))
        val y0 = h * (0.05f + 0.3f * rand(k, 42))
        val head = Offset(x0 + w * 0.3f * p, y0 + h * 0.18f * p)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - p))), Offset(head.x - w * 0.08f, head.y - h * 0.048f), head),
            Offset(head.x - w * 0.08f, head.y - h * 0.048f), head, 1.6f * dp, StrokeCap.Round,
        )
    }
}

/**
 * Horloge liquide (calque « liquide ») : l'heure, et des gouttes en orbite qui s'y fondent. À chaque
 * minute, l'ancienne heure fond pendant que la nouvelle se forme ([morph] 0 → 1).
 */
internal fun DrawScope.drawLiquidClock(f: SceneFrame, current: TextLayoutResult, previous: TextLayoutResult?, morph: Float) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    val brush = Brush.linearGradient(f.colors.ifEmpty { listOf(Color.White) }, Offset.Zero, Offset(w, h))
    fun draw(t: TextLayoutResult, alpha: Float) {
        if (alpha <= 0.01f) return
        drawText(t, brush, Offset((w - t.size.width) / 2f, (h - t.size.height) / 2f), alpha = alpha)
    }
    if (previous != null && morph < 1f) draw(previous, 1f - morph)
    draw(current, if (previous != null) morph else 1f)
    val n = SceneLogic.count(6, f.density).coerceAtMost(14)
    repeat(n) { i ->
        val ang = f.t * (0.15f + 0.2f * rand(i, 51)) * (if (i % 2 == 0) 1 else -1) + rand(i, 52) * 6.28f
        // Orbite elliptique qui frôle les chiffres : les gouttes s'y collent puis s'en détachent.
        val rx = w * (0.28f + 0.14f * rand(i, 53))
        val ry = h * (0.22f + 0.14f * rand(i, 54))
        val r = m * (0.03f + 0.04f * rand(i, 55)) * (1f + 0.3f * f.pulse)
        drawCircle(if (f.pulse > 0.05f && i == 0) f.pulseColor else f.color(i), r, Offset(w / 2 + cos(ang) * rx, h / 2 + sin(ang) * ry))
    }
}

/** Onde d'une notification : anneau qui part de l'île et lueur en haut de l'écran. */
internal fun DrawScope.drawPulse(f: SceneFrame) {
    if (f.pulse <= 0.01f) return
    val c = Offset(size.width / 2, 0f)
    val far = hypot(size.width / 2, size.height)
    val p = 1f - f.pulse
    drawCircle(Brush.radialGradient(listOf(f.pulseColor.copy(alpha = 0.35f * f.pulse), Color.Transparent), c, far * 0.6f), far * 0.6f, c)
    drawCircle(f.pulseColor.copy(alpha = 0.6f * f.pulse), far * (0.1f + 0.9f * p), c, style = Stroke(3f * density * f.pulse + 0.5f))
}
