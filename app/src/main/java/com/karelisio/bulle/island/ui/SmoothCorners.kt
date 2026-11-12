package com.karelisio.bulle.island.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Angles lissés de l'île : on passe d'un arc de cercle (lissage 0) à un coin de
 * superellipse |x|ⁿ + |y|ⁿ = rⁿ (lissage 1, « squircle »), dont la courbure varie
 * progressivement au lieu de sauter d'un coup à la jonction avec le bord droit.
 *
 * Les mêmes paramètres pilotent le shader AGSL (norme p) et le repli Bézier,
 * pour que les deux rendus coïncident.
 */
object CornerSmoothing {

    /** En dessous de ce lissage, on garde exactement les arcs de cercle actuels. */
    const val Epsilon = 0.001f

    /** Exposant de la superellipse : 2 = cercle, 4,5 = squircle franc. */
    fun exponent(smoothing: Float): Float = 2f + 2.5f * smoothing.coerceIn(0f, 1f)

    /**
     * Étendue du coin le long de chaque bord. Un coin lissé commence un peu plus tôt
     * (jusqu'à +60 %) pour garder une rondeur perçue comparable, sans dépasser [maxExtent].
     */
    fun extent(radius: Float, smoothing: Float, maxExtent: Float): Float =
        (radius * (1f + 0.6f * smoothing.coerceIn(0f, 1f))).coerceIn(0f, max(0f, maxExtent))

    /**
     * Retrait, depuis l'angle du rectangle, du point à 45° de la superellipse
     * d'exposant [n] et d'étendue [r] : r · (1 − 2^(−1/n)).
     */
    fun diagonalInset(r: Float, n: Float): Float = r * (1f - 2f.pow(-1f / n))

    /**
     * Longueur des poignées de la Bézier cubique (fraction de l'étendue) qui fait passer
     * la courbe par le point à 45° de la superellipse. Pour n = 2 on retrouve 0,5523,
     * l'approximation classique du quart de cercle.
     */
    fun handleFraction(n: Float): Float = (8f * 2f.pow(-1f / n) - 4f) / 3f
}

/**
 * Ajoute un rectangle à coins lissés : rayons [topRadius] en haut, [bottomRadius] en bas.
 * Lissage nul : rectangle arrondi classique (identique à l'ancien rendu).
 */
fun Path.addSmoothRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    topRadius: Float,
    bottomRadius: Float,
    smoothing: Float,
) {
    val maxE = max(0f, min(right - left, bottom - top) / 2f)
    if (smoothing <= CornerSmoothing.Epsilon) {
        val rt = topRadius.coerceIn(0f, maxE)
        val rb = bottomRadius.coerceIn(0f, maxE)
        addRoundRect(
            RoundRect(
                left, top, right, bottom,
                topLeftCornerRadius = CornerRadius(rt), topRightCornerRadius = CornerRadius(rt),
                bottomRightCornerRadius = CornerRadius(rb), bottomLeftCornerRadius = CornerRadius(rb),
            ),
        )
        return
    }
    val f = CornerSmoothing.handleFraction(CornerSmoothing.exponent(smoothing))
    val et = CornerSmoothing.extent(topRadius, smoothing, maxE)
    val eb = CornerSmoothing.extent(bottomRadius, smoothing, maxE)
    // Poignées : chaque extrémité de coin tire vers l'angle de f × étendue.
    val ht = et * f
    val hb = eb * f
    moveTo(left + et, top)
    lineTo(right - et, top)
    cubicTo(right - et + ht, top, right, top + et - ht, right, top + et)
    lineTo(right, bottom - eb)
    cubicTo(right, bottom - eb + hb, right - eb + hb, bottom, right - eb, bottom)
    lineTo(left + eb, bottom)
    cubicTo(left + eb - hb, bottom, left, bottom - eb + hb, left, bottom - eb)
    lineTo(left, top + et)
    cubicTo(left, top + et - ht, left + et - ht, top, left + et, top)
    close()
}

/** Forme Compose à coins lissés (repli sans shader, découpe du contenu). Rayons en pixels. */
data class SmoothCornerShape(val topRadius: Float, val bottomRadius: Float, val smoothing: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (smoothing <= CornerSmoothing.Epsilon) {
            return RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                .createOutline(size, layoutDirection, density)
        }
        return Outline.Generic(
            Path().apply { addSmoothRoundRect(0f, 0f, size.width, size.height, topRadius, bottomRadius, smoothing) },
        )
    }
}

/** Forme de l'île : arcs de cercle sans lissage (comportement historique), sinon coins lissés. */
fun islandShape(topRadius: Float, bottomRadius: Float, smoothing: Float): Shape =
    if (smoothing <= CornerSmoothing.Epsilon) {
        RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomEnd = bottomRadius, bottomStart = bottomRadius)
    } else {
        SmoothCornerShape(topRadius, bottomRadius, smoothing)
    }
