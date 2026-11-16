package com.karelisio.bulle.modules.halo

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import com.karelisio.bulle.island.ui.Burst
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Réglages d'apparence figés pour une animation (pixels pour l'épaisseur). */
data class HaloLook(
    val thicknessPx: Float,
    val intensity: Float,
    val durationMs: Int,
    val passes: Int,
    val speed: Float,
    val trail: Float = 0.22f,
    val glow: Float = 0.6f,
    val direction: HaloDirection = HaloDirection.BOTH,
    val start: HaloStart = HaloStart.CAMERA,
    val camera: HaloCamera = HaloCamera.EMERGE,
    val cameraGapPx: Float = 0f,
    /** Seconde couleur (queue de la traînée, dégradé) ; null = une seule couleur. */
    val secondColor: Int? = null,
    val burst: Boolean = true,
    val burstSize: Float = 1f,
) {
    companion object {
        fun of(s: HaloSettings, density: Float) = HaloLook(
            thicknessPx = s.thicknessDp.coerceIn(HaloSettings.THICKNESS) * density,
            intensity = s.intensity.coerceIn(HaloSettings.INTENSITY),
            durationMs = s.durationMs.coerceIn(HaloSettings.DURATION),
            passes = s.passes.coerceIn(HaloSettings.PASSES),
            speed = s.speed.coerceIn(HaloSettings.SPEED),
            trail = s.trail.coerceIn(HaloSettings.TRAIL),
            glow = s.glow.coerceIn(0f, 1f),
            direction = s.direction,
            start = s.start,
            camera = s.camera,
            cameraGapPx = s.cameraGapDp.coerceIn(HaloSettings.CAMERA_GAP) * density,
            secondColor = s.secondColor?.toInt(),
            burst = s.burst,
            burstSize = s.burstSize.coerceIn(HaloSettings.BURST_SIZE),
        )
    }

    /** Durée totale : passages × durée / vitesse, plus le fondu final. */
    fun totalMs(kind: HaloKind): Long = when (kind) {
        HaloKind.CHARGE -> 2600L
        HaloKind.ECHO -> 3 * HaloTiming.HEART_BEAT_MS + 300L
        else -> (passes * durationMs / speed).toLong() + HaloTiming.FADE_MS
    }
}

/**
 * Halo qui fait le tour de l'écran décrit par [screen] (taille du Canvas), joué une fois puis
 * [onFinished] ; [loop] le rejoue avec une courte pause (aperçu). L'horloge est lue dans la phase
 * de dessin : aucune recomposition pendant l'animation, aucun flou logiciel (60/120 im/s).
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
            // Sans animations : bord statique (pleine opacité), jamais de boucle — une animation de
            // durée nulle relancée en boucle tournerait à chaque image.
            clock.snapTo(total / 2f)
            if (loop) return@LaunchedEffect
            delay(total)
        } else {
            while (true) {
                clock.snapTo(0f)
                clock.animateTo(total.toFloat(), tween(total.toInt().coerceAtLeast(1), easing = LinearEasing))
                if (!loop) break
                delay(LOOP_PAUSE_MS)
            }
        }
        finished.value()
    }
    val inset = look.thicknessPx * 0.5f + screen.density
    val gap = look.cameraGapPx + screen.density
    val outline = remember(screen, inset, look.camera, gap) { HaloOutline.of(screen, inset, gap, look.camera) }
    val burst = remember(request.id) {
        if (request.style == HaloStyle.SIGNATURE && look.burst && request.kind != HaloKind.CHARGE && request.kind != HaloKind.ECHO) {
            Burst(request.id.toInt(), Color(request.color))
        } else {
            null
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawHalo(outline, request, look, clock.value.toLong(), total, reduce, burst, screen.density)
    }
}

/** Pause entre deux passages en boucle (aperçu, écran éteint). */
private const val LOOP_PAUSE_MS = 500L

/** Dessine l'image du halo à l'instant [elapsed] (ms) d'une animation de [total] ms. */
fun DrawScope.drawHalo(
    outline: HaloOutline,
    req: HaloRequest,
    look: HaloLook,
    elapsed: Long,
    total: Long,
    reduce: Boolean,
    burst: Burst?,
    density: Float,
) {
    val l = outline.length
    if (l <= 0f) return
    val env = HaloTiming.envelope(elapsed, total) * look.intensity
    if (env <= 0.001f) return
    val color = Color(req.color)
    val tail = look.secondColor?.let { Color(it) } ?: color
    val w = look.thicknessPx
    val origin = if (look.start == HaloStart.BOTTOM) l / 2 else 0f

    // Sans animations : bord statique qui apparaît puis s'efface (charge : jusqu'au niveau).
    if (reduce) {
        if (req.kind == HaloKind.CHARGE) {
            val reach = l / 2 * req.level.coerceIn(0f, 1f)
            line(outline.slice(l / 2 - reach, l / 2 + reach), color, w, env, look.glow)
        } else {
            line(outline.segments, color, w, env * 0.85f, look.glow)
        }
        return
    }

    when (req.kind) {
        HaloKind.ECHO -> {
            // Rose pulsé, au rythme du cœur d'Écho.
            val beat = HaloTiming.heartbeat(elapsed)
            line(outline.segments, color, w * (0.8f + 0.5f * beat), env * beat, look.glow)
            return
        }
        HaloKind.CHARGE -> {
            // Vert qui monte des deux côtés depuis le bas, jusqu'au pourcentage.
            val grow = easeOut((elapsed / 1200f).coerceIn(0f, 1f))
            val reach = l / 2 * req.level.coerceIn(0.02f, 1f) * grow
            line(outline.slice(l / 2 - reach, l / 2 + reach), color, w, env, look.glow)
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
    if (elapsed >= look.passes * passMs) return
    val trail = l * look.trail
    // Départ : de part et d'autre de la trouée de la caméra (le trait en jaillit).
    val start = origin + if (look.start == HaloStart.CAMERA) outline.hole else 0f
    when (req.style) {
        HaloStyle.SIGNATURE -> {
            val travel = 0.8f
            val half = l / 2 - (start - origin)
            if (p < travel) {
                val d = half * easeInOutSine(p / travel)
                travelers(outline, look.direction, start, origin, d, trail, color, tail, w, env, look.glow, both = half)
            } else {
                // Les traits se rejoignent : éclair doux qui s'étale et éclat de bulles irisées.
                val q = (p - travel) / (1f - travel)
                val meet = origin + l / 2
                val spread = l * 0.08f * easeOut(q)
                line(outline.slice(meet - spread, meet + spread), lerp(color, Color.White, 0.3f), w * (1.4f - 0.6f * q), env * (1f - q), look.glow)
                if (burst != null) {
                    val (mx, my) = outline.pointAt(meet)
                    val k = look.burstSize
                    // Vers l'intérieur de l'écran : retourné si la rencontre est en bas.
                    val flip = if (my > size.height / 2) -1f else 1f
                    scale(k, k * flip, pivot = Offset(mx, my)) {
                        burst.draw(this, q, mx, my, 110f * density, 6f * density, density)
                    }
                }
            }
        }
        HaloStyle.STROKE -> travelers(outline, look.direction, start, origin, l * easeInOutSine(p), trail, color, tail, w, env, look.glow, both = l / 2)
        HaloStyle.PULSE -> line(outline.segments, lerp(color, tail, p), w, env * sin(PI * p).toFloat().pow(1.5f), look.glow)
        HaloStyle.WAVE -> {
            // Trait continu : tranches fines à bouts droits, seule la lumière varie (pas de points).
            val n = 180
            line(outline.segments, color, w, env * 0.18f, look.glow * 0.6f)
            for (i in 0 until n) {
                val f = i / n.toFloat()
                val a = max(0f, cos(2 * PI * (f * 2 - p)).toFloat()).pow(2.5f)
                if (a < 0.02f) continue
                core(outline.slice(f * l, (f + 1f / n) * l + 1f), lerp(tail, color, a), w, env * a, StrokeCap.Butt)
            }
        }
        HaloStyle.GRADIENT -> {
            glow(outline.segments, color, w, env * 0.6f, look.glow)
            val stops = (0..12).map { i ->
                val f = i / 12f
                val a = (0.5f + 0.5f * cos(2 * PI * (f - p))).toFloat()
                f to lerp(tail.copy(alpha = 0.08f), lerp(color, Color.White, 0.2f), a).copy(alpha = env * (0.12f + 0.88f * a))
            }.toTypedArray()
            drawPath(pathOf(outline.segments), Brush.sweepGradient(*stops, center = center), style = Stroke(w, cap = StrokeCap.Round))
        }
        HaloStyle.SPARKLES -> {
            core(outline.segments, color, w * 0.5f, env * 0.18f)
            val r = Random(req.id.toInt())
            repeat(48) {
                val d = r.nextFloat() * l
                val phase = r.nextFloat()
                val a = max(0f, sin(2 * PI * (p * 2 + phase)).toFloat()).pow(4)
                if (a > 0.02f) {
                    val (x, y) = outline.pointAt(d)
                    val c = if (it % 2 == 0) color else tail
                    drawCircle(c.copy(alpha = env * a * 0.45f * (0.4f + look.glow)), w * (1.6f + 2.2f * a), Offset(x, y))
                    drawCircle(Color.White.copy(alpha = env * a), w * (0.5f + 0.7f * a), Offset(x, y))
                }
            }
        }
    }
}

private fun easeInOutSine(t: Float): Float = (-(cos(PI * t.coerceIn(0f, 1f)) - 1) / 2).toFloat()
private fun easeOut(t: Float): Float = 1 - (1 - t).pow(3)

/**
 * Trait(s) en mouvement selon le sens : deux traits opposés depuis [start] (BOTH), ou un seul
 * (horaire / antihoraire). [d] = distance parcourue depuis le départ.
 */
private fun DrawScope.travelers(
    o: HaloOutline,
    direction: HaloDirection,
    start: Float,
    origin: Float,
    d: Float,
    trail: Float,
    color: Color,
    tail: Color,
    w: Float,
    a: Float,
    glow: Float,
    both: Float,
) {
    val mirror = origin - (start - origin)
    when (direction) {
        HaloDirection.BOTH -> {
            val dd = d.coerceAtMost(both)
            comet(o, start + dd, trail, 1f, color, tail, w, a, glow)
            comet(o, mirror - dd, trail, -1f, color, tail, w, a, glow)
        }
        HaloDirection.CLOCKWISE -> comet(o, start + d * 2, trail, 1f, color, tail, w, a, glow)
        HaloDirection.COUNTER -> comet(o, mirror - d * 2, trail, -1f, color, tail, w, a, glow)
    }
}

/**
 * Comète : tête lumineuse en [head], traînée de longueur [trail] derrière elle ([dir] = sens de
 * marche). Tranches à bouts droits (traînée continue), couleur de [tail] vers [color], épaisseur
 * croissante, tête ronde et claire.
 */
private fun DrawScope.comet(o: HaloOutline, head: Float, trail: Float, dir: Float, color: Color, tail: Color, w: Float, a: Float, glow: Float) {
    val back = head - dir * trail
    val from = minOf(back, head)
    val to = maxOf(back, head)
    glow(o.slice(from, to), color, w, a * 0.8f, glow)
    val n = 16
    for (i in 0 until n) {
        val f0 = i / n.toFloat()
        val f1 = (i + 1) / n.toFloat()
        // f = 0 au bout de la traînée, 1 à la tête.
        val d0 = back + dir * trail * f0
        val d1 = back + dir * trail * f1 + dir * 1f
        val c = lerp(lerp(tail, color, f1), Color.White, f1 * f1 * 0.35f)
        core(o.slice(minOf(d0, d1), maxOf(d0, d1)), c, w * (0.35f + 0.65f * f1), a * f1 * f1, StrokeCap.Butt)
    }
    val (x, y) = o.pointAt(head)
    if (o.hole <= 0f || ((head % o.length) + o.length) % o.length in o.hole..(o.length - o.hole)) {
        drawCircle(lerp(color, Color.White, 0.55f).copy(alpha = a), w * 0.75f, Offset(x, y))
    }
}

/** Trait lumineux complet : lueur + cœur net. */
private fun DrawScope.line(segs: List<HaloSeg>, color: Color, w: Float, a: Float, glow: Float) {
    glow(segs, color, w, a, glow)
    core(segs, lerp(color, Color.White, 0.15f), w, a)
}

private fun DrawScope.core(segs: List<HaloSeg>, color: Color, w: Float, a: Float, cap: StrokeCap = StrokeCap.Round) {
    if (segs.isEmpty() || a <= 0.001f) return
    drawPath(pathOf(segs), color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f)), style = Stroke(w, cap = cap))
}

/**
 * Lueur sans flou logiciel (le flou de masque se recalcule sur le processeur à chaque image et
 * faisait saccader) : deux traits plus larges et translucides, réglés par [strength].
 */
private fun DrawScope.glow(segs: List<HaloSeg>, color: Color, w: Float, a: Float, strength: Float) {
    if (segs.isEmpty() || a <= 0.001f || strength <= 0.01f) return
    val path = pathOf(segs)
    drawPath(path, color.copy(alpha = (a * 0.14f * strength).coerceIn(0f, 1f)), style = Stroke(w * (2f + 2.5f * strength), cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = (a * 0.28f * strength).coerceIn(0f, 1f)), style = Stroke(w * (1.4f + 0.8f * strength), cap = StrokeCap.Round))
}

/** Chemin Compose d'une suite de morceaux (nouveau départ quand deux morceaux ne se touchent pas). */
fun pathOf(segs: List<HaloSeg>): Path = Path().apply {
    var lastX = Float.NaN
    var lastY = Float.NaN
    for (s in segs) {
        val (sx, sy) = s.pointAt(0f)
        if (lastX.isNaN() || kotlin.math.abs(sx - lastX) > 0.75f || kotlin.math.abs(sy - lastY) > 0.75f) moveTo(sx, sy)
        when (s) {
            is HaloSeg.Line -> lineTo(s.x1, s.y1)
            is HaloSeg.Arc -> arcTo(Rect(s.cx - s.r, s.cy - s.r, s.cx + s.r, s.cy + s.r), s.startDeg, s.sweepDeg, false)
        }
        val (ex, ey) = s.pointAt(s.length)
        lastX = ex
        lastY = ey
    }
}
