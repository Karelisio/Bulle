package com.karelisio.bulle.modules.edges

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rectangle d'une zone en pixels écran. */
data class EdgeRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val bottom: Int get() = y + height
    fun overlapsVertically(other: EdgeRect): Boolean = y < other.bottom && other.y < bottom
}

/** Une zone placée : emplacement, rectangle et s'il a fallu la décaler (Volet, barre de navigation). */
data class PlacedZone(val slot: EdgeSlot, val rect: EdgeRect, val adjusted: Boolean)

/**
 * Placement des zones (logique pure, testée).
 *
 * - jamais dans la bande du haut : barre d'état et zones de déclenchement du Volet (+ marge) ;
 * - jamais dans la barre de navigation du bas (geste Accueil) ;
 * - sur un même bord, une zone basse ne chevauche jamais la zone haute (elle passe dessous).
 */
object EdgeGeometry {

    /** Marge gardée sous la bande du Volet et au-dessus de la barre de navigation (dp). */
    const val SAFETY_DP = 8f

    /**
     * Hauteur réservée en haut : barre d'état, ou zones du Volet si elles sont plus hautes
     * ([voletTriggerPx] : 0 si le Volet est coupé), plus la marge.
     */
    fun topReserved(statusBarPx: Int, voletTriggerPx: Int, density: Float): Int =
        max(statusBarPx, voletTriggerPx) + (SAFETY_DP * density).roundToInt()

    fun place(
        settings: EdgeSettings,
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        topReservedPx: Int,
        bottomReservedPx: Int,
    ): List<PlacedZone> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        val minY = topReservedPx.coerceIn(0, screenHeight)
        val maxY = (screenHeight - bottomReservedPx - (SAFETY_DP * density).roundToInt()).coerceIn(minY, screenHeight)
        val out = ArrayList<PlacedZone>()
        for (side in EdgeSide.entries) {
            val upper = if (side == EdgeSide.LEFT) EdgeSlot.LEFT else EdgeSlot.RIGHT
            val lower = if (side == EdgeSide.LEFT) EdgeSlot.LEFT_LOWER else EdgeSlot.RIGHT_LOWER
            var upperRect: EdgeRect? = null
            for (slot in listOf(upper, lower)) {
                val z = settings.zone(slot)
                if (!z.enabled) continue
                val thick = (z.thicknessDp.coerceIn(EdgeZone.THICKNESS) * density).roundToInt().coerceAtLeast(1)
                val wantTop = (z.start.coerceIn(0f, 1f) * screenHeight).roundToInt()
                val wantLen = (z.length.coerceIn(EdgeZone.MIN_LENGTH, EdgeZone.MAX_LENGTH) * screenHeight).roundToInt()
                var top = max(wantTop, minY)
                // Zone basse : toujours sous la zone haute du même bord.
                upperRect?.let { u -> if (slot.lower && top < u.bottom) top = u.bottom }
                val bottom = min(top + wantLen, maxY)
                if (bottom - top < (24 * density).roundToInt()) continue
                val x = if (side == EdgeSide.LEFT) 0 else screenWidth - thick
                val rect = EdgeRect(x, top, thick, bottom - top)
                out += PlacedZone(slot, rect, adjusted = top != wantTop || bottom - top != wantLen)
                if (!slot.lower) upperRect = rect
            }
        }
        return out
    }

    /** Vrai si un rectangle empiète sur la bande des zones de déclenchement du Volet. */
    fun overlapsVolet(rect: EdgeRect, voletBandPx: Int): Boolean = voletBandPx > 0 && rect.y < voletBandPx
}
