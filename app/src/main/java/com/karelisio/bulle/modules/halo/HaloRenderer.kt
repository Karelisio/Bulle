package com.karelisio.bulle.modules.halo

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.karelisio.bulle.island.ui.Burst
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Réglages d'apparence figés pour une animation (pixels pour l'épaisseur). */
data class HaloLook(
    val thicknessPx: Float,
    val intensity: Float,
    val durationMs: Int,
    val passes: Int,
    val speed: Float,
) {
    companion object {
        fun of(s: HaloSettings, density: Float) = HaloLook(
            thicknessPx = s.thicknessDp.coerceIn(HaloSettings.THICKNESS) * density,
            intensity = s.intensity.coerceIn(HaloSettings.INTENSITY),
            durationMs = s.durationMs.coerceIn(HaloSettings.DURATION),
            passes = s.passes.coerceIn(HaloSettings.PASSES),
            speed = s.speed.coerceIn(HaloSettings.SPEED),
        )
    }

    /** Durée totale (même calcul que [HaloTiming.totalMs]). */
    fun totalMs(kind: HaloKind): Long = when (kind) {
        HaloKind.CHARGE -> 2600L
        HaloKind.ECHO -> 3 * HaloTiming.HEART_BEAT_MS + 300L
        else -> (passes * durationMs / speed).toLong() + HaloTiming.FADE_MS
    }
}

/**
 * Halo qui fait le tour de l'écran décrit par [screen] (taille du Canvas), joué une fois puis
 * [onFinished] ; [loop] le rejoue sans fin (aperçu des réglages). Les animations ne provoquent
 * que des redessins : l'horloge est lue dans la phase de dessin.
 */
@Composable
fun HaloCanvas(
    request: HaloRequest,
    screen: HaloScreen,
    look: HaloLook,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val reduce = rememberReduceMotion()
    val total = look.totalMs(request.kind)
    val clock = remember(request.id) { Animatable(0f) }
    val finished = rememberUpdatedState(onFinished)
    LaunchedEffect(request.id, total, loop, reduce) {
        if (reduce) {
            // Sans animations : bord statique (milieu de l'animation, pleine opacité), jamais de boucle
            // — une animation de durée nulle relancée en boucle tournerait à chaque image.
            clock.snapTo(total / 2f)
            if (loop) return@LaunchedEffect
            delay(total)
        } else {
            while (true) {
                clock.snapTo(0f)
                clock.animateTo(total.toFloat(), tween(total.toInt().coerceAtLeast(1), easing = LinearEasing))
                if (!loop) break
                // Petite pause entre deux passages de l'aperçu.
                delay(LOOP_PAUSE_MS)
            }
        }
        finished.value()
    }
    val inset = look.thicknessPx * 0.5f + screen.density
    val outline = remember(screen, inset) { HaloOutline.of(screen, inset) }
    val painter = remember(look.thicknessPx) { HaloPainter(look.thicknessPx) }
    val burst = remember(request.id) {
        if (request.style == HaloStyle.SIGNATURE && request.kind != HaloKind.CHARGE && request.kind != HaloKind.ECHO) {
            Burst(request.id.toInt(), Color(request.color))
        } else {
            null
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawHalo(outline, request, look, clock.value.toLong(), total, reduce, painter, burst, screen.density)
    }
}

/** Pause entre deux passages en boucle (aperçu, écran éteint). */
private const val LOOP_PAUSE_MS = 500L

/** Pinceaux réutilisés d'une image à l'autre (le flou coûte cher à recréer). */
class HaloPainter(thickness: Float) {
    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeWidth = thickness * 3.2f
        maskFilter = BlurMaskFilter(max(thickness * 2.4f, 1f), BlurMaskFilter.Blur.NORMAL)
    }
}

/** Dessine l'image du halo à l'instant [elapsed] (ms) d'une animation de [total] ms. */
fun DrawScope.drawHalo(
    outline: HaloOutline,
    req: HaloRequest,
    look: HaloLook,
    elapsed: Long,
    total: Long,
    reduce: Boolean,
    painter: HaloPainter,
    burst: Burst?,
    density: Float,
) {
    val l = outline.length
    if (l <= 0f) return
    val env = HaloTiming.envelope(elapsed, total) * look.intensity
    if (env <= 0.001f) return
    val color = Color(req.color)
    val w = look.thicknessPx

    // Sans animations : bord statique qui apparaît puis s'efface (charge : jusqu'au niveau).
    if (reduce) {
        if (req.kind == HaloKind.CHARGE) {
            val reach = l / 2 * req.level.coerceIn(0f, 1f)
            stroke(outline.slice(l / 2 - reach, l / 2 + reach), color, w, env, painter)
        } else {
            stroke(outline.segments, color, w, env * 0.85f, painter)
        }
        return
    }

    when (req.kind) {
        HaloKind.ECHO -> {
            // Rose pulsé, au rythme du cœur d'Écho.
            val beat = HaloTiming.heartbeat(elapsed)
            stroke(outline.segments, color, w * (0.8f + 0.5f * beat), env * beat, painter)
            return
        }
        HaloKind.CHARGE -> {
            // Vert qui monte des deux côtés depuis le bas, jusqu'au pourcentage.
            val grow = easeOut((elapsed / 1200f).coerceIn(0f, 1f))
            val reach = l / 2 * req.level.coerceIn(0.02f, 1f) * grow
            stroke(outline.slice(l / 2 - reach, l / 2 + reach), color, w, env, painter)
            val tip = lerp(color, Color.White, 0.6f)
            for (d in floatArrayOf(l / 2 - reach, l / 2 + reach)) {
                val (x, y) = outline.pointAt(d)
                drawCircle(tip.copy(alpha = env), w * 0.9f, Offset(x, y))
            }
            return
        }
        else -> Unit
    }

    val passMs = (look.durationMs / look.speed).coerceAtLeast(1f)
    val p = ((elapsed % passMs.toLong()) / passMs).coerceIn(0f, 1f)
    val done = elapsed >= look.passes * passMs
    when (req.style) {
        HaloStyle.SIGNATURE -> {
            if (done) return
            val travel = 0.78f
            if (p < travel) {
                // Deux traits partent de la caméra et font le tour, chacun d'un côté.
                val head = l / 2 * easeInOut(p / travel)
                val tail = l * 0.16f
                comet(outline, head, tail, color, w, env, painter)
                cometBackward(outline, head, tail, color, w, env, painter)
            } else {
                // Ils se rejoignent en bas : éclat de bulles irisées et bref éclair du bord.
                val q = (p - travel) / (1f - travel)
                stroke(outline.segments, color, w * 0.8f, env * 0.55f * (1f - q), painter)
                val (mx, my) = outline.pointAt(l / 2)
                drawCircle(lerp(color, Color.White, 0.5f).copy(alpha = env * (1f - q)), w * (1.2f + 3f * q), Offset(mx, my))
                burst?.let { b ->
                    scale(1f, -1f, pivot = Offset(mx, my)) {
                        b.draw(this, q, mx, my, 120f * density, 6f * density, density)
                    }
                }
            }
        }
        HaloStyle.STROKE -> if (!done) comet(outline, l * easeInOut(p), l * 0.28f, color, w, env, painter)
        HaloStyle.PULSE -> if (!done) stroke(outline.segments, color, w, env * sin(PI * p).toFloat().pow(1.5f), painter)
        HaloStyle.WAVE -> if (!done) {
            val n = 72
            glowOnly(outline.segments, color, env * 0.25f, painter)
            for (i in 0 until n) {
                val f = i / n.toFloat()
                val a = 0.18f + 0.82f * max(0f, cos(2 * PI * (f * 2 - p)).toFloat()).pow(3)
                core(outline.slice(f * l, (f + 1f / n) * l + 0.5f), color, w * (0.6f + 0.6f * a), env * a)
            }
        }
        HaloStyle.GRADIENT -> if (!done) {
            glowOnly(outline.segments, color, env * 0.35f, painter)
            val stops = (0..8).map { i ->
                val f = i / 8f
                val a = (0.5f + 0.5f * cos(2 * PI * (f - p))).toFloat()
                f to lerp(color.copy(alpha = 0.05f), lerp(color, Color.White, 0.25f), a).copy(alpha = env * (0.15f + 0.85f * a))
            }.toTypedArray()
            drawPath(pathOf(outline.segments), Brush.sweepGradient(*stops, center = center), style = Stroke(w, cap = StrokeCap.Round))
        }
        HaloStyle.SPARKLES -> if (!done) {
            core(outline.segments, color, w * 0.5f, env * 0.18f)
            val r = Random(req.id.toInt())
            repeat(48) {
                val d = r.nextFloat() * l
                val phase = r.nextFloat()
                val a = max(0f, sin(2 * PI * (p * 2 + phase)).toFloat()).pow(4)
                if (a > 0.02f) {
                    val (x, y) = outline.pointAt(d)
                    drawCircle(color.copy(alpha = env * a * 0.6f), w * (1.6f + 2.2f * a), Offset(x, y))
                    drawCircle(Color.White.copy(alpha = env * a), w * (0.5f + 0.7f * a), Offset(x, y))
                }
            }
        }
    }
}

private fun easeInOut(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun easeOut(t: Float): Float = 1 - (1 - t).pow(3)

/** Trait avec traînée : la tête à la distance [head], la traînée derrière (sens horaire). */
private fun DrawScope.comet(o: HaloOutline, head: Float, tail: Float, color: Color, w: Float, a: Float, painter: HaloPainter) {
    glowOnly(o.slice(head - tail, head), color, a * 0.7f, painter)
    val n = 10
    for (i in 0 until n) {
        val f0 = i / n.toFloat()
        val f1 = (i + 1) / n.toFloat()
        core(o.slice(head - tail + tail * f0, head - tail + tail * f1 + 0.5f), lerp(color, Color.White, f1 * 0.35f), w * (0.4f + 0.6f * f1), a * f1)
    }
}

/** Même trait, parti dans l'autre sens (sens antihoraire). */
private fun DrawScope.cometBackward(o: HaloOutline, head: Float, tail: Float, color: Color, w: Float, a: Float, painter: HaloPainter) {
    val h = -head
    glowOnly(o.slice(h, h + tail), color, a * 0.7f, painter)
    val n = 10
    for (i in 0 until n) {
        val f0 = i / n.toFloat()
        val f1 = (i + 1) / n.toFloat()
        core(o.slice(h + tail * (1f - f1), h + tail * (1f - f0) + 0.5f), lerp(color, Color.White, f1 * 0.35f), w * (0.4f + 0.6f * f1), a * f1)
    }
}

/** Trait lumineux complet : halo flou + cœur net. */
private fun DrawScope.stroke(segs: List<HaloSeg>, color: Color, w: Float, a: Float, painter: HaloPainter) {
    glowOnly(segs, color, a * 0.75f, painter)
    core(segs, lerp(color, Color.White, 0.15f), w, a)
}

private fun DrawScope.core(segs: List<HaloSeg>, color: Color, w: Float, a: Float) {
    if (segs.isEmpty() || a <= 0.001f) return
    drawPath(pathOf(segs), color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f)), style = Stroke(w, cap = StrokeCap.Round))
}

private fun DrawScope.glowOnly(segs: List<HaloSeg>, color: Color, a: Float, painter: HaloPainter) {
    if (segs.isEmpty() || a <= 0.001f) return
    val path = pathOf(segs)
    painter.glow.color = color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f)).toArgb()
    drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), painter.glow) }
}

/** Chemin Compose d'une suite de morceaux contigus. */
fun pathOf(segs: List<HaloSeg>): Path = Path().apply {
    var first = true
    for (s in segs) {
        when (s) {
            is HaloSeg.Line -> {
                if (first) moveTo(s.x0, s.y0)
                lineTo(s.x1, s.y1)
            }
            is HaloSeg.Arc -> {
                if (first) {
                    val (x, y) = s.pointAt(0f)
                    moveTo(x, y)
                }
                arcTo(Rect(s.cx - s.r, s.cy - s.r, s.cx + s.r, s.cy + s.r), s.startDeg, s.sweepDeg, false)
            }
        }
        first = false
    }
}
