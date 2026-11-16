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

    /**
     * Île verticale (paysage, caméra latérale) : décalage du haut de la surface par rapport au haut
     * de l'île au repos ([baseTop], longueur [baseLength]) pour une surface de hauteur [targetLength],
     * centrée sur la caméra puis gardée dans l'écran à [margin] des bords (haut prioritaire si trop
     * grande : elle défile alors à l'intérieur).
     */
    fun verticalOffsetY(baseTop: Float, baseLength: Float, targetLength: Float, screenHeight: Float, margin: Float): Float {
        val centered = baseTop + (baseLength - targetLength) / 2f
        if (screenHeight <= 0f) return centered - baseTop
        val lo = margin
        val hi = screenHeight - margin - targetLength
        val top = if (hi < lo) lo else centered.coerceIn(lo, hi)
        return top - baseTop
    }

    /** Hauteur maximale de la carte dépliée verticale (paysage) : l'écran moins les marges. */
    fun verticalMaxHeight(screenHeight: Float, margin: Float): Float = (screenHeight - 2 * margin).coerceAtLeast(1f)

    /**
     * Étendue verticale (haut, bas) de l'enveloppe d'une île verticale, relative au haut de l'île au
     * repos : état courant et cible réunis, plus le dépassement du ressort ; posée, la cible seule.
     */
    fun verticalSpan(
        currentOffset: Float,
        currentLength: Float,
        targetOffset: Float,
        targetLength: Float,
        overshoot: Float,
        settled: Boolean,
        shadow: Float = 0f,
    ): Pair<Float, Float> {
        if (settled) return (targetOffset - shadow) to (targetOffset + targetLength + shadow)
        val slack = kotlin.math.abs(targetLength - currentLength) * overshoot / 2f +
            kotlin.math.abs(targetOffset - currentOffset) * overshoot
        val top = kotlin.math.min(currentOffset, targetOffset) - slack - shadow
        val bottom = kotlin.math.max(currentOffset + currentLength, targetOffset + targetLength) + slack + shadow
        return top to bottom
    }

    /** Rectangle de la jauge de paysage (pixels de la fenêtre d'affichage). */
    data class GaugeRect(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * Jauge de paysage : 6 dp d'épaisseur, 160 dp de long, centrée sur la caméra et posée 8 dp
     * à côté d'elle, vers l'intérieur de l'écran ; gardée dans l'écran.
     */
    fun landscapeGauge(
        cameraCenterX: Float,
        cameraHalfThickness: Float,
        cameraCenterY: Float,
        cameraLeft: Boolean,
        density: Float,
        screenHeight: Float,
    ): GaugeRect {
        val w = 6f * density
        val h = (160f * density).coerceAtMost((screenHeight - 16f * density).coerceAtLeast(w))
        val gap = 8f * density
        val left = if (cameraLeft) cameraCenterX + cameraHalfThickness + gap else cameraCenterX - cameraHalfThickness - gap - w
        val top = (cameraCenterY - h / 2f).coerceIn(8f * density, (screenHeight - 8f * density - h).coerceAtLeast(8f * density))
        return GaugeRect(left, top, w, h)
    }

    /** Rotation du contenu compact de l'île verticale : comme l'île du portrait, collée au bord de la caméra. */
    fun compactRotation(cameraLeft: Boolean): Float = if (cameraLeft) -90f else 90f

    /** La seconde ligne compacte tient-elle dans une île de [heightPx] pixels ? */
    fun twoLineFits(heightPx: Int, density: Float, textScale: Float): Boolean =
        density > 0f && heightPx / density >= TwoLineMinHeightDp * textScale.coerceIn(0.85f, 1.25f)
}
