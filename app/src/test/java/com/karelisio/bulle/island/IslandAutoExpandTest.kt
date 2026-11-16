package com.karelisio.bulle.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paysage : seuls les appels ouvrent la carte d'eux-mêmes ; portrait inchangé. */
class IslandAutoExpandTest {
    @Test
    fun onlyCallsAutoExpandInLandscape() {
        assertTrue(IslandAutoExpand.allowed(vertical = true, moduleId = "call"))
        assertFalse(IslandAutoExpand.allowed(vertical = true, moduleId = "alerts"))
        assertFalse(IslandAutoExpand.allowed(vertical = true, moduleId = "push"))
        assertTrue(IslandAutoExpand.allowed(vertical = false, moduleId = "alerts"))
        // Réglage « Ouvrir les appels en paysage » coupé : rien ne s'ouvre en paysage.
        assertFalse(IslandAutoExpand.allowed(vertical = true, moduleId = "call", callsInLandscape = false))
        assertTrue(IslandAutoExpand.allowed(vertical = false, moduleId = "call", callsInLandscape = false))
    }

    @Test
    fun journalLinesAreSortedByCategory() {
        assertEquals(IslandLog.Category.AUDIO, IslandLog.category("Sortie audio : MediaRouter2 → true"))
        assertEquals(IslandLog.Category.AUDIO, IslandLog.category("Volume : panneau du système demandé"))
        assertEquals(IslandLog.Category.SHIZUKU, IslandLog.category("Shizuku : « bulle-tether wifi on » → 0 ok"))
        assertEquals(IslandLog.Category.SHIZUKU, IslandLog.category("Tuiles d'applis : 95 services trouvés"))
        assertEquals(IslandLog.Category.VOLET, IslandLog.category("Effacement demandé : 0|pkg|1|null|1"))
        assertEquals(IslandLog.Category.VOLET, IslandLog.category("Masquée dans Bulle (menu) : k"))
        assertEquals(IslandLog.Category.ISLAND, IslandLog.category("Île affichée"))
        val entries = listOf(IslandLog.Entry(0, 0, "Île affichée"), IslandLog.Entry(0, 0, "Sortie audio : bouton"))
        assertEquals(2, IslandLog.filter(entries, null).size)
        assertEquals(listOf("Sortie audio : bouton"), IslandLog.filter(entries, IslandLog.Category.AUDIO).map { it.message })
    }

    @Test
    fun landscapeTouchMarginWidensTheZone() {
        val base = CutoutLocator.sideGeometry(24, 475, 134, 805, 2800, 1280, 3f)
        val wide = CutoutLocator.sideGeometry(24, 475, 134, 805, 2800, 1280, 3f, marginDp = 12f)
        // 12 dp de chaque côté (3 px/dp) : +72 px en épaisseur et en longueur, toujours centrée sur la caméra.
        assertEquals(base.width + 72, wide.width)
        assertEquals(base.height + 72, wide.height)
        assertEquals(base.centerY, wide.centerY)
        // Plafonnée.
        val max = CutoutLocator.sideGeometry(24, 475, 134, 805, 2800, 1280, 3f, marginDp = 500f)
        assertEquals(CutoutLocator.sideGeometry(24, 475, 134, 805, 2800, 1280, 3f, marginDp = CutoutLocator.MAX_LANDSCAPE_MARGIN_DP).height, max.height)
        // Trou du portrait (haut, centré) vu en paysage caméra à gauche.
        val r = CutoutLocator.portraitTopToLandscapeLeft(475, 24, 805, 134, 1280)
        assertEquals(listOf(24, 475, 134, 805), r.toList())
    }
}
