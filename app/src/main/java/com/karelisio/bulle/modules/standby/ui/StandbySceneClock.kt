package com.karelisio.bulle.modules.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import com.karelisio.bulle.modules.standby.SceneLogic
import com.karelisio.bulle.modules.standby.SceneLogic.rand
import com.karelisio.bulle.modules.standby.StandbyScene
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Déplacement en cours de l'horloge (hors état Compose : lu au dessin de chaque image). */
private class ClockMove(var from: Pair<Float, Float>?, var to: Pair<Float, Float>, var startedAt: Long)

private fun clockText(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(Locale.getDefault(), "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** Points des chiffres (x, y alternés, en px depuis le coin du texte) : particules de la Constellation. */
private fun sampleGlyphs(layout: TextLayoutResult): FloatArray {
    val w = layout.size.width
    val h = layout.size.height
    if (w <= 0 || h <= 0) return FloatArray(0)
    return runCatching {
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
            drawText(layout, Color.White)
        }
        val px = bmp.toPixelMap()
        val step = SceneLogic.glyphStep(w, h)
        val out = ArrayList<Float>()
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                if (px[x, y].alpha > 0.5f) { out += x.toFloat(); out += y.toFloat() }
                x += step
            }
            y += step
        }
        out.toFloatArray()
    }.getOrDefault(FloatArray(0))
}

/**
 * Horloge par-dessus une scène, habillée selon la scène, qui change de coin toutes les 5 minutes
 * avec une transition propre à chacune : la lave fond et goutte, la bulle éclate puis se regonfle,
 * l'aurore s'estompe en s'étirant, la vague l'engloutit, la constellation se désagrège en
 * particules qui volent jusqu'au coin suivant.
 */
@Composable
internal fun SceneClock(scene: StandbyScene, colors: List<Color>, t: () -> Float, uptime: () -> Long, reduce: Boolean) {
    val now = rememberNow(seconds = false)
    val anchor = SceneLogic.clockAnchor(now)
    val move = remember { ClockMove(null, anchor, 0L) }
    if (anchor != move.to) {
        move.from = move.to
        move.to = anchor
        move.startedAt = uptime()
    }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val time = clockText(now)
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now))
    val px = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val m = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val styles = remember(scene, m, colors) { clockStyles(scene, m, colors, px) }
        val timeLayout = remember(time, styles) { measurer.measure(time, styles.first) }
        val glyphs = remember(timeLayout, scene) { if (scene == StandbyScene.STARS) sampleGlyphs(timeLayout) else FloatArray(0) }
        Canvas(Modifier.fillMaxSize()) {
            val dateLayout = measurer.measure(date, styles.second)
            val p = if (move.from == null || reduce) 1f else SceneLogic.clockTransition(uptime() - move.startedAt)
            val c = ClockDraw(scene, colors, t(), timeLayout, dateLayout, glyphs, density)
            val margin = 24f * density
            fun origin(a: Pair<Float, Float>) = SceneLogic.clockOrigin(a, c.blockW, c.blockH, size.width, size.height, margin)
                .let { Offset(it.first, it.second) }
            val to = origin(move.to)
            val from = move.from?.let(::origin)
            if (from == null || p >= 1f) c.draw(this, to, 1f) else c.transition(this, from, to, p)
        }
    }
}

/** Styles de l'heure et de la date selon la scène. */
private fun clockStyles(scene: StandbyScene, m: Float, colors: List<Color>, d: Density): Pair<TextStyle, TextStyle> {
    val big = with(d) { (m * 0.2f).toSp() }
    val small = with(d) { (m * 0.05f).toSp() }
    val c0 = colors.first()
    val c1 = colors.getOrElse(1) { c0 }
    val tnum = "tnum"
    return when (scene) {
        StandbyScene.LAVA -> TextStyle(fontSize = big, fontWeight = FontWeight.Black, fontFeatureSettings = tnum, shadow = Shadow(c0.copy(alpha = 0.7f), blurRadius = m * 0.06f)) to
            TextStyle(fontSize = small, fontWeight = FontWeight.Bold, color = c1)
        StandbyScene.BUBBLES -> TextStyle(fontSize = big * 0.85f, fontWeight = FontWeight.Light, fontFeatureSettings = tnum, color = Color.White.copy(alpha = 0.95f)) to
            TextStyle(fontSize = small * 0.9f, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
        StandbyScene.AURORA -> TextStyle(fontSize = big, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = tnum, letterSpacing = 0.06.em, shadow = Shadow(c1.copy(alpha = 0.8f), blurRadius = m * 0.05f)) to
            TextStyle(fontSize = small * 0.85f, fontWeight = FontWeight.Light, letterSpacing = 0.3.em, color = Color.White.copy(alpha = 0.65f))
        StandbyScene.WAVES -> TextStyle(fontSize = big, fontWeight = FontWeight.Medium, fontFeatureSettings = tnum, color = Color.White, shadow = Shadow(c0.copy(alpha = 0.9f), Offset(0f, m * 0.01f), m * 0.04f)) to
            TextStyle(fontSize = small, fontWeight = FontWeight.Medium, color = c0)
        StandbyScene.STARS, StandbyScene.LIQUID_CLOCK -> TextStyle(fontSize = big, fontWeight = FontWeight.Thin, fontFeatureSettings = tnum, color = Color.White, shadow = Shadow(Color.White.copy(alpha = 0.7f), blurRadius = m * 0.03f)) to
            TextStyle(fontSize = small * 0.9f, fontWeight = FontWeight.Light, letterSpacing = 0.15.em, color = Color.White.copy(alpha = 0.6f))
    }
}

/** Dessin d'une image de l'horloge : bloc (heure + date) et transitions d'un coin à l'autre. */
private class ClockDraw(
    val scene: StandbyScene,
    val colors: List<Color>,
    val t: Float,
    val time: TextLayoutResult,
    val date: TextLayoutResult,
    val glyphs: FloatArray,
    val dp: Float,
) {
    val gap = 4f * dp
    val blockW = max(time.size.width, date.size.width).toFloat()
    val blockH = time.size.height + gap + date.size.height
    private fun col(i: Int) = colors[((i % colors.size) + colors.size) % colors.size]
    private fun timeAt(o: Offset) = Offset(o.x + (blockW - time.size.width) / 2, o.y)
    private fun center(o: Offset) = Offset(o.x + blockW / 2, o.y + blockH / 2)

    /** Brosse de l'heure (dégradés propres à la lave et à l'aurore). */
    private fun brush(o: Offset): Brush? = when (scene) {
        StandbyScene.LAVA -> Brush.verticalGradient(listOf(col(0), col(1), col(2)), o.y, o.y + time.size.height)
        StandbyScene.AURORA -> {
            // Reflet qui glisse lentement le long des chiffres.
            val shift = (t * 60f * dp) % (blockW * 2)
            Brush.linearGradient(listOf(col(0), col(1), col(2), col(0)), Offset(o.x + shift - blockW, o.y), Offset(o.x + shift, o.y + time.size.height), TileMode.Mirror)
        }
        else -> null
    }

    /** Le bloc à sa place, avec l'habillage de la scène. */
    fun draw(s: DrawScope, o: Offset, alpha: Float, timeAlpha: Float = alpha): Unit = with(s) {
        if (alpha <= 0.01f && timeAlpha <= 0.01f) return
        var dy = 0f
        var rot = 0f
        when (scene) {
            StandbyScene.BUBBLES -> { dy = sin(t * 0.8f) * 4f * dp; bubble(o.copy(y = o.y + dy), alpha, 1f) }
            StandbyScene.WAVES -> { dy = sin(t * 1.3f) * 5f * dp; rot = sin(t * 0.9f) * 2f }
            StandbyScene.STARS -> twinkles(o, alpha)
            else -> Unit
        }
        val c = center(o)
        rotate(rot, c) {
            translate(0f, dy) {
                val tp = timeAt(o)
                val b = brush(o)
                if (timeAlpha > 0.01f) {
                    if (b != null) drawText(time, b, tp, alpha = timeAlpha) else drawText(time, topLeft = tp, alpha = timeAlpha)
                }
                drawText(date, topLeft = Offset(o.x + (blockW - date.size.width) / 2, o.y + time.size.height + gap), alpha = alpha)
            }
        }
    }

    /** Bulle irisée autour du bloc ([grow] 0 – 1 : gonflement). */
    private fun DrawScope.bubble(o: Offset, alpha: Float, grow: Float) {
        val c = center(o)
        val r = hypot(blockW, blockH) / 2f * 1.05f * grow
        if (r <= 1f || alpha <= 0.01f) return
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, col(0).copy(alpha = 0.14f * alpha)), c, r), r, c)
        val iris = listOf(col(0), Color.White, col(1), col(2), col(0)).map { it.copy(alpha = 0.7f * alpha) }
        drawCircle(Brush.sweepGradient(iris, c), r, c, style = Stroke(2f * dp))
        drawArc(
            Color.White.copy(alpha = 0.5f * alpha), 200f, 50f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f),
            style = Stroke(3f * dp, cap = StrokeCap.Round),
        )
    }

    /** Petites étoiles qui scintillent autour de l'heure (Constellation). */
    private fun DrawScope.twinkles(o: Offset, alpha: Float) {
        repeat(7) { i ->
            val a = alpha * (0.3f + 0.7f * (0.5f + 0.5f * sin(t * (1f + rand(i, 61)) + i)))
            val x = o.x - 12f * dp + (blockW + 24f * dp) * rand(i, 62)
            val y = o.y - 10f * dp + (blockH + 20f * dp) * rand(i, 63)
            drawCircle(col(i).copy(alpha = a), (1f + 1.2f * rand(i, 64)) * dp, Offset(x, y))
        }
    }

    /** Transition de [from] vers [to] ; [p] 0 – 1. */
    fun transition(s: DrawScope, from: Offset, to: Offset, p: Float): Unit = with(s) {
        val out = SceneLogic.clockOut(p)
        val inn = SceneLogic.clockIn(p)
        when (scene) {
            StandbyScene.LAVA -> {
                // Fonte : l'heure s'étire vers le bas et goutte ; la nouvelle naît d'une flaque et rebondit.
                if (out < 1f) {
                    scale(1f - 0.15f * out, 1f + 0.9f * out * out, Offset(center(from).x, from.y)) {
                        translate(0f, 30f * dp * out * out) { draw(this, from, 1f - out) }
                    }
                    repeat(6) { i ->
                        val x = from.x + blockW * (0.1f + 0.8f * rand(i, 71))
                        val y = from.y + time.size.height + out * (60f + 80f * rand(i, 72)) * dp
                        drawCircle(col(i).copy(alpha = 1f - out), (3f + 5f * rand(i, 73)) * dp * (1f - out * 0.5f), Offset(x, y))
                    }
                }
                if (inn > 0f) {
                    val k = SceneLogic.easeOutBack(inn)
                    scale(1.2f - 0.2f * inn, (0.2f + 0.8f * k).coerceAtLeast(0.01f), Offset(center(to).x, to.y + blockH)) { draw(this, to, inn) }
                }
            }
            StandbyScene.BUBBLES -> {
                // La bulle éclate (anneau + gouttelettes), puis une nouvelle se gonfle au coin suivant.
                if (out < 1f) {
                    val c = center(from)
                    val r = hypot(blockW, blockH) / 2f * 1.05f
                    val pop = (out / 0.5f).coerceIn(0f, 1f)
                    draw(this, from, 0f, timeAlpha = 1f - pop)
                    drawCircle(col(0).copy(alpha = 0.6f * (1f - pop)), r * (1f + 0.3f * pop), c, style = Stroke(2f * dp * (1f - pop) + 0.5f))
                    repeat(16) { i ->
                        val ang = i * (2f * PI.toFloat() / 16f) + rand(i, 74)
                        val d = r * (1f + 0.8f * out)
                        drawCircle(col(i).copy(alpha = 1f - out), 3f * dp * (1f - out) + 0.5f, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + 20f * dp * out * out))
                    }
                }
                if (inn > 0f) {
                    val k = SceneLogic.easeOutBack(inn).coerceAtLeast(0.01f)
                    scale(k, k, center(to)) { draw(this, to, inn) }
                }
            }
            StandbyScene.AURORA -> {
                // S'estompe en s'étirant vers le haut ; la nouvelle descend en se resserrant.
                if (out < 1f) scale(1f + 0.3f * out, 1f, center(from)) { translate(0f, -18f * dp * out) { draw(this, from, 1f - out) } }
                if (inn > 0f) scale(1.3f - 0.3f * inn, 1f, center(to)) { translate(0f, 18f * dp * (1f - inn)) { draw(this, to, inn) } }
            }
            StandbyScene.WAVES -> {
                // Engloutie par la vague (ronds dans l'eau), puis remonte à la surface au coin suivant.
                if (out < 1f) {
                    rotate(8f * out, center(from)) { translate(0f, 50f * dp * out * out) { draw(this, from, 1f - out) } }
                    repeat(3) { k ->
                        val q = (out * 1.4f - k * 0.2f).coerceIn(0f, 1f)
                        if (q > 0f) {
                            val w = blockW * (0.4f + 0.9f * q)
                            drawOval(
                                col(k).copy(alpha = 0.5f * (1f - q)), Offset(center(from).x - w / 2, from.y + blockH - w * 0.08f),
                                Size(w, w * 0.16f), style = Stroke(1.5f * dp),
                            )
                        }
                    }
                }
                if (inn > 0f) {
                    val e = 1f - (1f - inn) * (1f - inn)
                    translate(0f, 50f * dp * (1f - e)) { draw(this, to, inn) }
                }
            }
            StandbyScene.STARS, StandbyScene.LIQUID_CLOCK -> particles(this, from, to, p)
        }
    }

    /** Désagrégation : les chiffres se défont en particules qui volent en arc jusqu'au coin suivant. */
    private fun particles(s: DrawScope, from: Offset, to: Offset, p: Float): Unit = with(s) {
        val fadeOut = (1f - p / 0.12f).coerceIn(0f, 1f)
        val fadeIn = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
        draw(this, from, (1f - p / 0.3f).coerceIn(0f, 1f), timeAlpha = fadeOut)
        draw(this, to, ((p - 0.7f) / 0.3f).coerceIn(0f, 1f), timeAlpha = fadeIn)
        if (glyphs.isEmpty()) return
        val a = timeAt(from)
        val b = timeAt(to)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        val n = glyphs.size / 2
        for (k in 0 until n) {
            val delay = rand(k, 81) * 0.3f
            val q = ((p - 0.05f - delay) / 0.6f).coerceIn(0f, 1f)
            if (q <= 0f && p < 0.12f) continue
            if (q >= 1f && p > 0.88f) continue
            val e = SceneLogic.easeInOut(q)
            val arc = sin(q * PI.toFloat()) * len * 0.25f * (rand(k, 82) - 0.5f)
            val x = a.x + glyphs[2 * k] + dx * e + nx * arc
            val y = a.y + glyphs[2 * k + 1] + dy * e + ny * arc
            val tw = 0.6f + 0.4f * sin(t * 6f + k)
            drawCircle(col(k).copy(alpha = tw), (1.1f + 1.2f * rand(k, 83)) * dp, Offset(x, y))
        }
    }
}

/** Utilisé par les tests : mesure et dessine une image de l'horloge d'une scène. */
internal fun DrawScope.drawSceneClockFrame(
    scene: StandbyScene,
    colors: List<Color>,
    measurer: TextMeasurer,
    t: Float,
    from: Pair<Float, Float>?,
    to: Pair<Float, Float>,
    p: Float,
) {
    val m = min(size.width, size.height)
    val (ts, ds) = clockStyles(scene, m, colors, Density(density, fontScale))
    val time = measurer.measure("12:34", ts)
    val date = measurer.measure("18 nov. 2026", ds)
    val c = ClockDraw(scene, colors, t, time, date, if (scene == StandbyScene.STARS) sampleGlyphs(time) else FloatArray(0), density)
    fun origin(a: Pair<Float, Float>) = SceneLogic.clockOrigin(a, c.blockW, c.blockH, size.width, size.height, 24f * density).let { Offset(it.first, it.second) }
    if (from == null || p >= 1f) c.draw(this, origin(to), 1f) else c.transition(this, origin(from), origin(to), p)
}
