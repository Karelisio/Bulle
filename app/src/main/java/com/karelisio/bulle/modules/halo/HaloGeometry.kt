package com.karelisio.bulle.modules.halo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Rectangle en pixels (logique pure, sans android.graphics). */
data class HaloRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
}

/**
 * L'écran vu par le halo, en pixels : taille, rayon réel des quatre coins arrondis
 * (RoundedCorner API ; haut-gauche, haut-droit, bas-droit, bas-gauche) et caméra du haut.
 */
data class HaloScreen(
    val width: Float,
    val height: Float,
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
    val cutout: HaloRect? = null,
    /** 1 dp en pixels. */
    val density: Float = 1f,
)

/** Un morceau du contour : droite ou arc de cercle (angles en degrés, y vers le bas). */
sealed interface HaloSeg {
    val length: Float
    fun pointAt(d: Float): Pair<Float, Float>

    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : HaloSeg {
        override val length: Float get() = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        override fun pointAt(d: Float): Pair<Float, Float> {
            val t = if (length <= 0f) 0f else (d / length).coerceIn(0f, 1f)
            return (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t)
        }
    }

    data class Arc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweepDeg: Float) : HaloSeg {
        override val length: Float get() = (abs(sweepDeg) / 360f * 2f * PI.toFloat() * r)
        override fun pointAt(d: Float): Pair<Float, Float> {
            val t = if (length <= 0f) 0f else (d / length).coerceIn(0f, 1f)
            val a = Math.toRadians((startDeg + sweepDeg * t).toDouble())
            return (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
        }
    }
}

/**
 * Contour fermé que parcourt le halo, de longueur [length], qui commence (distance 0) sous la
 * caméra — ou au milieu du bord haut s'il n'y en a pas — et tourne dans le sens horaire.
 */
class HaloOutline(
    val segments: List<HaloSeg>,
    /**
     * Demi-largeur de la trouée à la caméra (distance de part et d'autre du départ) : le trait s'y
     * interrompt et en jaillit de chaque côté. 0 = contour continu.
     */
    val hole: Float = 0f,
) {
    val length: Float = segments.sumOf { it.length.toDouble() }.toFloat()

    /** Point à la distance [d] (modulo la longueur). */
    fun pointAt(d: Float): Pair<Float, Float> {
        if (segments.isEmpty() || length <= 0f) return 0f to 0f
        var rest = ((d % length) + length) % length
        for (s in segments) {
            if (rest <= s.length) return s.pointAt(rest)
            rest -= s.length
        }
        return segments.last().pointAt(segments.last().length)
    }

    /**
     * Morceaux du contour entre les distances [from] et [to] (to ≥ from, tour complet au plus),
     * découpés aux limites des segments. Une plage qui déborde le point de départ est repliée.
     */
    fun slice(from: Float, to: Float): List<HaloSeg> {
        if (length <= 0f || to <= from) return emptyList()
        val span = min(to - from, length)
        val start = ((from % length) + length) % length
        val end = start + span
        return if (end <= length) sliceIn(start, end) else sliceIn(start, length) + sliceIn(0f, end - length)
    }

    private fun sliceIn(a0: Float, b0: Float): List<HaloSeg> {
        // Rien n'est dessiné dans la trouée de la caméra.
        val a = if (hole > 0f) max(a0, hole) else a0
        val b = if (hole > 0f) min(b0, length - hole) else b0
        if (b <= a) return emptyList()
        val out = ArrayList<HaloSeg>()
        var pos = 0f
        for (s in segments) {
            val s0 = pos
            val s1 = pos + s.length
            pos = s1
            if (s1 <= a || s0 >= b || s.length <= 0f) continue
            val u0 = max(a, s0) - s0
            val u1 = min(b, s1) - s0
            out += part(s, u0, u1)
        }
        return out
    }

    private fun part(s: HaloSeg, u0: Float, u1: Float): HaloSeg = when (s) {
        is HaloSeg.Line -> {
            val (ax, ay) = s.pointAt(u0)
            val (bx, by) = s.pointAt(u1)
            HaloSeg.Line(ax, ay, bx, by)
        }
        is HaloSeg.Arc -> {
            val k = s.sweepDeg / s.length
            HaloSeg.Arc(s.cx, s.cy, s.r, s.startDeg + k * u0, k * (u1 - u0))
        }
    }

    companion object {
        /**
         * Contour de l'écran, rentré de [inset] (la moitié de l'épaisseur du trait pour qu'il colle
         * au bord), qui suit les coins arrondis réels et contourne la caméra à [gap] de distance.
         */
        fun of(
            screen: HaloScreen,
            inset: Float,
            gap: Float = 4f * screen.density,
            camera: HaloCamera = HaloCamera.AROUND,
        ): HaloOutline {
            val w = screen.width
            val h = screen.height
            if (w <= 2 * inset || h <= 2 * inset) return HaloOutline(emptyList())
            val left = inset
            val top = inset
            val right = w - inset
            val bottom = h - inset
            val maxR = min(right - left, bottom - top) / 2
            fun r(v: Float) = (v - inset).coerceIn(0f, maxR)
            val tl = r(screen.topLeft)
            val tr = r(screen.topRight)
            val br = r(screen.bottomRight)
            val bl = r(screen.bottomLeft)
            val segs = ArrayList<HaloSeg>()
            val detour = if (camera == HaloCamera.AROUND) screen.cutout?.let { detour(it, top, gap, left + tl, right - tr) } else null
            val startX = detour?.cx ?: ((left + tl + right - tr) / 2)
            // Départ : sous la caméra (milieu du contournement) ou milieu du bord haut.
            if (detour != null) {
                segs += detour.rightHalf
            }
            val topRightStartX = detour?.exitX ?: startX
            segs += HaloSeg.Line(topRightStartX, top, right - tr, top)
            if (tr > 0f) segs += HaloSeg.Arc(right - tr, top + tr, tr, -90f, 90f)
            segs += HaloSeg.Line(right, top + tr, right, bottom - br)
            if (br > 0f) segs += HaloSeg.Arc(right - br, bottom - br, br, 0f, 90f)
            segs += HaloSeg.Line(right - br, bottom, left + bl, bottom)
            if (bl > 0f) segs += HaloSeg.Arc(left + bl, bottom - bl, bl, 90f, 90f)
            segs += HaloSeg.Line(left, bottom - bl, left, top + tl)
            if (tl > 0f) segs += HaloSeg.Arc(left + tl, top + tl, tl, 180f, 90f)
            segs += HaloSeg.Line(left + tl, top, detour?.entryX ?: startX, top)
            if (detour != null) segs += detour.leftHalf
            // Caméra « jaillissante » : départ au centre de la caméra, trouée de sa largeur (+ écart).
            val hole = if (camera == HaloCamera.EMERGE) screen.cutout?.let { it.width / 2 + gap } ?: 0f else 0f
            val cut = screen.cutout
            val emergeStart = if (camera == HaloCamera.EMERGE && cut != null) cut.centerX else null
            val final = if (emergeStart != null && detour == null) {
                // Décale le départ sous la caméra (qui n'est pas forcément au centre de l'écran).
                val topLine = segs.first() as HaloSeg.Line
                val lastLine = segs.last() as HaloSeg.Line
                listOf(HaloSeg.Line(emergeStart, top, topLine.x1, topLine.y1)) + segs.drop(1).dropLast(1) +
                    HaloSeg.Line(lastLine.x0, lastLine.y0, emergeStart, top)
            } else {
                segs
            }
            return HaloOutline(final.filter { it.length > 0.01f }, hole)
        }

        /** Contournement de la caméra du haut : descend à gauche, passe dessous, remonte à droite. */
        private class Detour(val cx: Float, val entryX: Float, val exitX: Float, val leftHalf: List<HaloSeg>, val rightHalf: List<HaloSeg>)

        private fun detour(c: HaloRect, y0: Float, gap: Float, minX: Float, maxX: Float): Detour? {
            val l = c.left - gap
            val rr = c.right + gap
            val b = c.bottom + gap
            if (b <= y0 + 1f || rr - l <= 0f) return null
            // Petit arrondi d'entrée / sortie (raccord avec le bord haut), au plus près de la caméra.
            val k = min(max(gap, 1f), (b - y0) / 3f).coerceAtLeast(0.5f)
            if (l - k <= minX || rr + k >= maxX) return null
            // Arrondi du bas : jamais à l'intérieur de la caméra (coin du rectangle à √2·(rb − écart) ≥ rb).
            val rb = min(min((rr - l) / 2f, b - (y0 + k)), gap * 3.4f).coerceAtLeast(0f)
            val cx = c.centerX
            // Moitié gauche : du bord haut jusque sous la caméra.
            val leftHalf = listOf(
                HaloSeg.Arc(l - k, y0 + k, k, -90f, 90f),
                HaloSeg.Line(l, y0 + k, l, b - rb),
                HaloSeg.Arc(l + rb, b - rb, rb, 180f, -90f),
                HaloSeg.Line(l + rb, b, cx, b),
            )
            // Moitié droite : de sous la caméra jusqu'au bord haut.
            val rightHalf = listOf(
                HaloSeg.Line(cx, b, rr - rb, b),
                HaloSeg.Arc(rr - rb, b - rb, rb, 90f, -90f),
                HaloSeg.Line(rr, b - rb, rr, y0 + k),
                HaloSeg.Arc(rr + k, y0 + k, k, 180f, 90f),
            )
            return Detour(cx, l - k, rr + k, leftHalf, rightHalf)
        }
    }
}
