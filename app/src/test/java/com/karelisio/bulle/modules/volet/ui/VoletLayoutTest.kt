package com.karelisio.bulle.modules.volet.ui

import com.karelisio.bulle.modules.volet.qs.QsPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choix portrait / paysage et répartition des tuiles (logique pure). */
class VoletLayoutTest {

    @Test
    fun orientationFollowsWindowShape() {
        // Honor Magic7 Pro : ~366 × 800 dp en portrait.
        assertEquals(VoletOrientation.PORTRAIT, VoletLayout.orientation(366f, 800f))
        assertEquals(VoletOrientation.LANDSCAPE, VoletLayout.orientation(800f, 366f))
        // Carré : portrait (comportement d'origine).
        assertEquals(VoletOrientation.PORTRAIT, VoletLayout.orientation(500f, 500f))
    }

    @Test
    fun portraitUnchanged() {
        // Portrait : réglage 4 – 6, 2 colonnes, 4 rangées, quelle que soit la largeur.
        for (w in listOf(0f, 366f, 900f)) {
            assertEquals(QsGridSpec(6, 2, 4), VoletLayout.gridSpec(VoletOrientation.PORTRAIT, w, 8f, 6, 13))
        }
        assertEquals(QsGridSpec(4, 2, 4), VoletLayout.gridSpec(VoletOrientation.PORTRAIT, 366f, 8f, 4, 13))
        // Bornes du réglage et peu de tuiles.
        assertEquals(6, VoletLayout.gridSpec(VoletOrientation.PORTRAIT, 366f, 8f, 12, 13).rowSlots)
        assertEquals(4, VoletLayout.gridSpec(VoletOrientation.PORTRAIT, 366f, 8f, 1, 13).rowSlots)
        assertEquals(3, VoletLayout.gridSpec(VoletOrientation.PORTRAIT, 366f, 8f, 6, 3).rowSlots)
        assertEquals(QsPaging.PER_PAGE, VoletLayout.gridSpec(VoletOrientation.PORTRAIT, 366f, 8f, 6, 13).perPage)
    }

    @Test
    fun landscapeFitsMoreSmallerTiles() {
        // Colonne de gauche ≈ 46 % de 800 dp moins les marges : ~350 dp → 5 tuiles de 56 dp, mais au moins le réglage.
        val narrow = VoletLayout.gridSpec(VoletOrientation.LANDSCAPE, 350f, 6f, 4, 13)
        assertEquals(5, narrow.rowSlots)
        assertEquals(2, narrow.columns)
        // 5 tuiles sur 2 colonnes : 3 rangées pour que la rangée repliée tienne sur la première page.
        assertEquals(3, narrow.rowsPerPage)
        assertTrue(narrow.perPage >= narrow.rowSlots)
        assertEquals(6, VoletLayout.gridSpec(VoletOrientation.LANDSCAPE, 350f, 6f, 6, 13).rowSlots)
        // Colonne large : jusqu'à 8 tuiles par rangée et 4 colonnes.
        val wide = VoletLayout.gridSpec(VoletOrientation.LANDSCAPE, 700f, 6f, 6, 13)
        assertEquals(8, wide.rowSlots)
        assertEquals(4, wide.columns)
        assertEquals(8, wide.perPage)
        assertEquals(2, wide.rowsPerPage)
        val mid = VoletLayout.gridSpec(VoletOrientation.LANDSCAPE, 480f, 6f, 6, 13)
        assertEquals(3, mid.columns)
        assertTrue(mid.perPage >= mid.rowSlots)
        // Jamais plus d'emplacements que de tuiles.
        assertEquals(2, VoletLayout.gridSpec(VoletOrientation.LANDSCAPE, 700f, 6f, 6, 2).rowSlots)
    }

    @Test
    fun pagingWithCustomPageSize() {
        // Pagination conservée en paysage : pages de colonnes × 2 rangées.
        assertEquals(1, QsPaging.pageCount(4, 4))
        assertEquals(4, QsPaging.pageCount(13, 4))
        assertEquals(listOf(0 until 6, 6 until 12, 12 until 13), QsPaging.pages(13, 6))
        assertEquals((0 until 13).toList(), QsPaging.pages(13, 6).flatten())
        // Taille de page aberrante : pas de division par zéro.
        assertTrue(QsPaging.pages(3, 0).flatten() == listOf(0, 1, 2))
        // Valeurs par défaut identiques à l'existant.
        assertEquals(QsPaging.pages(13), QsPaging.pages(13, QsPaging.PER_PAGE))
    }
}
