package com.karelisio.bulle.island.ui

import com.karelisio.bulle.core.settings.OpenDirection

/** Calculs de placement de l'île, sans Compose (testables en JUnit). */
object IslandLayout {

    /** Hauteur minimale (dp, à l'échelle du texte 1) pour afficher la seconde ligne compacte. */
    const val TwoLineMinHeightDp = 30f

    /**
     * Décalage horizontal du centre de l'île par rapport à la caméra pour le sens d'ouverture.
     *  - CENTER : l'île grandit des deux côtés (décalage nul) ;
     *  - LEFT : le bord droit de l'île au repos reste fixe, elle s'étend vers la gauche ;
     *  - RIGHT : le bord gauche reste fixe, elle s'étend vers la droite.
     * Si [clampToScreen], l'île est ensuite gardée dans l'écran, à [margin] des bords.
     */
    fun openingOffsetX(
        direction: OpenDirection,
        baseWidth: Float,
        targetWidth: Float,
        cameraX: Float,
        screenWidth: Float,
        margin: Float,
        clampToScreen: Boolean,
    ): Float {
        val raw = when (direction) {
            OpenDirection.CENTER -> 0f
            OpenDirection.LEFT -> (baseWidth - targetWidth) / 2f
            OpenDirection.RIGHT -> (targetWidth - baseWidth) / 2f
        }
        if (!clampToScreen) return raw
        val lo = targetWidth / 2f + margin
        val hi = screenWidth - targetWidth / 2f - margin
        // Île plus large que l'écran : on la centre sur l'écran.
        if (lo > hi) return screenWidth / 2f - cameraX
        return (cameraX + raw).coerceIn(lo, hi) - cameraX
    }

    /** La seconde ligne compacte tient-elle dans une île de [heightPx] pixels ? */
    fun twoLineFits(heightPx: Int, density: Float, textScale: Float): Boolean =
        density > 0f && heightPx / density >= TwoLineMinHeightDp * textScale.coerceIn(0.85f, 1.25f)
}
