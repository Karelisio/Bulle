package com.karelisio.bulle.modules.volet.ui

import kotlin.math.max

/** Mise en page du volet selon la forme de la fenêtre. */
enum class VoletOrientation { PORTRAIT, LANDSCAPE }

/**
 * Répartition des tuiles : [rowSlots] emplacements dans la rangée repliée, puis grille de
 * [columns] colonnes et [rowsPerPage] rangées par page.
 */
data class QsGridSpec(val rowSlots: Int, val columns: Int, val rowsPerPage: Int) {
    val perPage: Int get() = columns * rowsPerPage
}

/** Choix de mise en page (logique pure, testée). */
object VoletLayout {
    /** Portrait : comme avant. */
    const val PORTRAIT_COLUMNS = 2
    const val PORTRAIT_ROWS = 4

    /** Paysage : petites tuiles rondes et grandes tuiles plus étroites, deux rangées par page. */
    const val LANDSCAPE_ROW_TILE_DP = 56f
    const val LANDSCAPE_GRID_TILE_MIN_DP = 150f
    const val LANDSCAPE_ROWS = 2
    const val LANDSCAPE_MAX_ROW_SLOTS = 8
    const val LANDSCAPE_MAX_COLUMNS = 4

    /** Part de la largeur donnée à la colonne de gauche (en-tête, tuiles, luminosité) en paysage. */
    const val LANDSCAPE_LEFT_FRACTION = 0.46f

    /** Paysage dès que la fenêtre est plus large que haute. */
    fun orientation(widthDp: Float, heightDp: Float): VoletOrientation =
        if (widthDp > heightDp) VoletOrientation.LANDSCAPE else VoletOrientation.PORTRAIT

    /**
     * Grille des tuiles. Portrait : réglage « tuiles visibles » (4 – 6), 2 colonnes, 4 rangées
     * (comportement d'origine). Paysage : autant de tuiles de 56 dp que la largeur [widthDp] de la
     * colonne en permet (au moins le réglage, au plus 8), 2 à 4 colonnes, 2 rangées par page
     * (davantage si la rangée repliée ne tiendrait pas sur la première page).
     */
    fun gridSpec(
        orientation: VoletOrientation,
        widthDp: Float,
        gapDp: Float,
        collapsedSetting: Int,
        tileCount: Int,
    ): QsGridSpec {
        val setting = collapsedSetting.coerceIn(4, 6)
        return when (orientation) {
            VoletOrientation.PORTRAIT ->
                QsGridSpec(setting.coerceAtMost(tileCount), PORTRAIT_COLUMNS, PORTRAIT_ROWS)
            VoletOrientation.LANDSCAPE -> {
                val fit = ((widthDp + gapDp) / (LANDSCAPE_ROW_TILE_DP + gapDp)).toInt()
                val slots = max(fit, setting).coerceAtMost(LANDSCAPE_MAX_ROW_SLOTS).coerceAtMost(tileCount)
                val cols = ((widthDp + gapDp) / (LANDSCAPE_GRID_TILE_MIN_DP + gapDp)).toInt()
                    .coerceIn(PORTRAIT_COLUMNS, LANDSCAPE_MAX_COLUMNS)
                // La première page porte toute la rangée repliée : assez de rangées pour la contenir.
                val rows = max(LANDSCAPE_ROWS, (slots + cols - 1) / cols)
                QsGridSpec(slots, cols, rows)
            }
        }
    }
}
