package com.karelisio.bulle.modules.volet.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.volet.VoletCardDensity
import com.karelisio.bulle.modules.volet.VoletSettings

/**
 * Dimensions des cartes du volet selon la densité et l'échelle du texte (calcul pur, testé).
 * Densité « Normale », échelle 1 et 2 lignes = rendu historique.
 */
@Immutable
internal data class NotifDims(
    val density: VoletCardDensity,
    /** Marge interne des cartes. */
    val pad: Dp,
    /** Espacement entre cartes. */
    val gap: Dp,
    /** Pastille de l'appli / avatar en tête de carte. */
    val badge: Dp,
    /** Grande icône (vignette à droite). */
    val largeIcon: Dp,
    /** Espace entre la pastille et le texte. */
    val iconGap: Dp,
    val corner: Dp,
    /** Marge au-dessus des en-têtes de section. */
    val sectionTop: Dp,
    /** Taille du texte (1 = normale). */
    val textScale: Float,
    /** Lignes de texte d'une notification repliée. */
    val collapsedLines: Int,
) {
    val compact: Boolean get() = density == VoletCardDensity.COMPACT

    companion object {
        const val SCALE_MIN = 0.8f
        const val SCALE_MAX = 1.2f
        const val LINES_MIN = 1
        const val LINES_MAX = 4

        fun of(settings: VoletSettings): NotifDims =
            of(settings.effectiveCardDensity, settings.cornerRadiusDp, settings.cardTextScale, settings.cardCollapsedLines)

        fun of(density: VoletCardDensity, cornerDp: Float, textScale: Float = 1f, collapsedLines: Int = 2): NotifDims {
            val scale = textScale.takeIf { !it.isNaN() }?.coerceIn(SCALE_MIN, SCALE_MAX) ?: 1f
            val lines = collapsedLines.coerceIn(LINES_MIN, LINES_MAX)
            val corner = cornerDp.coerceAtLeast(0f)
            // Les icônes suivent la taille du texte ; les marges restent celles de la densité.
            return when (density) {
                VoletCardDensity.COMPACT -> NotifDims(
                    density, pad = 10.dp, gap = 4.dp, badge = (28 * scale).dp, largeIcon = (32 * scale).dp,
                    iconGap = 8.dp, corner = (corner * 0.75f).dp, sectionTop = 2.dp, textScale = scale, collapsedLines = lines,
                )
                VoletCardDensity.NORMAL -> NotifDims(
                    density, pad = 16.dp, gap = 10.dp, badge = (38 * scale).dp, largeIcon = (44 * scale).dp,
                    iconGap = 12.dp, corner = corner.dp, sectionTop = 6.dp, textScale = scale, collapsedLines = lines,
                )
                VoletCardDensity.AIRY -> NotifDims(
                    density, pad = 20.dp, gap = 14.dp, badge = (44 * scale).dp, largeIcon = (52 * scale).dp,
                    iconGap = 14.dp, corner = corner.dp, sectionTop = 10.dp, textScale = scale, collapsedLines = lines,
                )
            }
        }
    }
}
