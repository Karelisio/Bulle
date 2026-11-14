package com.karelisio.bulle.modules.volet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max

/** Enveloppe tactile de l'île (fenêtre tactile), en pixels écran. */
data class IslandEnvelope(val x: Int, val y: Int, val width: Int, val height: Int, val touchable: Boolean = true) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

/**
 * Géométrie des zones de déclenchement (logique pure, testée).
 *
 * Les zones couvrent toute la largeur de l'écran (moins la marge réglée, nulle par défaut), sur
 * au moins la hauteur de la barre d'état, et bordent exactement l'enveloppe tactile de l'île :
 * aucun trou par lequel le doigt retomberait sur le volet du système (MagicOS). Au-dessus et
 * au-dessous de l'île (si elle ne remplit pas toute la hauteur), des zones complètent la bande.
 * Côté 0 (gauche) / 1 (droite) : partage au centre de l'île.
 */
object VoletStrips {
    /**
     * Enveloppe tactile réelle de l'île, publiée par l'île à chaque placement de sa fenêtre.
     * null : inconnue (une estimation tirée de la géométrie est alors utilisée).
     */
    val islandEnvelope = MutableStateFlow<IslandEnvelope?>(null)

    /** Une zone et son côté (0 gauche, 1 droite). */
    data class Zone(val side: Int, val rect: StripRect)

    /**
     * Zones de déclenchement. [envelope] null ou non touchable (île masquée, île vide invisible) :
     * rien à épargner, la bande entière est partagée en deux au centre [centerX].
     */
    fun zones(
        screenWidth: Int,
        statusBarHeight: Int,
        triggerHeightPx: Int,
        edgeInsetPx: Int,
        centerX: Int,
        envelope: IslandEnvelope?,
    ): List<Zone> {
        if (screenWidth <= 0) return emptyList()
        val h = max(statusBarHeight, triggerHeightPx).coerceAtLeast(1)
        val inset = edgeInsetPx.coerceIn(0, screenWidth / 4)
        val minX = inset
        val maxX = screenWidth - inset
        val cx = centerX.coerceIn(minX, maxX)
        val zones = mutableListOf<Zone>()
        fun add(side: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
            val a = x0.coerceIn(minX, maxX)
            val b = x1.coerceIn(minX, maxX)
            val top = y0.coerceIn(0, h)
            val bottom = y1.coerceIn(0, h)
            if (b > a && bottom > top) zones += Zone(side, StripRect(a, top, b - a, bottom - top))
        }
        val env = envelope?.takeIf { it.touchable && it.width > 0 && it.height > 0 && it.y < h && it.bottom > 0 }
        if (env == null) {
            add(0, minX, 0, cx, h)
            add(1, cx, 0, maxX, h)
            return zones
        }
        val l = env.x.coerceIn(minX, maxX)
        val r = env.right.coerceIn(l, maxX)
        val mid = cx.coerceIn(l, r)
        // Colonnes de part et d'autre de l'île, jusqu'à ses bords exacts.
        add(0, minX, 0, l, h)
        add(1, r, 0, maxX, h)
        // Au-dessus de l'île (si elle ne touche pas le haut de l'écran).
        add(0, l, 0, mid, env.y)
        add(1, mid, 0, r, env.y)
        // Au-dessous de l'île, jusqu'au bas de la bande.
        add(0, l, env.bottom, mid, h)
        add(1, mid, env.bottom, r, h)
        return zones
    }

    /**
     * Estimation de l'enveloppe quand l'île ne l'a pas publiée : île au repos centrée sur la caméra
     * (largeur et hauteur de la géométrie + 2 px, comme la fenêtre tactile), élargie de [contentMarginPx]
     * de chaque côté quand un contenu compact l'élargit. null si l'île est masquée.
     */
    fun estimate(centerX: Int, top: Int, width: Int, height: Int, visible: Boolean, contentMarginPx: Int): IslandEnvelope? {
        if (!visible || width <= 0) return null
        val w = width + 2 + 2 * contentMarginPx.coerceAtLeast(0)
        return IslandEnvelope(centerX - w / 2, top, w, height + 2)
    }
}
