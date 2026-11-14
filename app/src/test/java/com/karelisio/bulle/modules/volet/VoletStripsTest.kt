package com.karelisio.bulle.modules.volet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zones de déclenchement : aucun trou entre les zones et l'île, toute la largeur couverte (logique pure). */
class VoletStripsTest {

    /** Vrai si le point (x, y) est couvert par une zone ou par l'île. */
    private fun covered(zones: List<VoletStrips.Zone>, env: IslandEnvelope?, x: Int, y: Int): Boolean =
        zones.any { z -> x >= z.rect.x && x < z.rect.x + z.rect.width && y >= z.rect.y && y < z.rect.y + z.rect.height } ||
            (env != null && env.touchable && x >= env.x && x < env.right && y >= env.y && y < env.bottom)

    /** Toute la bande [0, largeur) × [0, hauteur) est couverte, sans chevauchement entre zones. */
    private fun assertFullCoverage(zones: List<VoletStrips.Zone>, env: IslandEnvelope?, width: Int, height: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            assertTrue("trou en ($x, $y)", covered(zones, env, x, y))
            val n = zones.count { z -> x >= z.rect.x && x < z.rect.x + z.rect.width && y >= z.rect.y && y < z.rect.y + z.rect.height }
            assertTrue("zones superposées en ($x, $y)", n <= 1)
        }
    }

    /** Aucune zone ne recouvre l'île (fenêtre tactile prioritaire). */
    private fun assertIslandFree(zones: List<VoletStrips.Zone>, env: IslandEnvelope) {
        zones.forEach { z ->
            val r = z.rect
            val overlapX = minOf(r.x + r.width, env.right) - maxOf(r.x, env.x)
            val overlapY = minOf(r.y + r.height, env.bottom) - maxOf(r.y, env.y)
            assertFalse("zone $r sur l'île $env", overlapX > 0 && overlapY > 0)
        }
    }

    @Test
    fun portraitZonesTouchTheIsland() {
        // Écran 1280 px, barre d'état 120 px, île 100 × 100 px à 10 px du haut, centrée.
        val env = IslandEnvelope(590, 10, 100, 100)
        val zones = VoletStrips.zones(1280, 120, 0, 0, 640, env)
        assertFullCoverage(zones, env, 1280, 120)
        assertIslandFree(zones, env)
        // Colonnes jusqu'aux bords de l'écran et aux bords exacts de l'île.
        val left = zones.first { it.side == 0 && it.rect.x == 0 }.rect
        val right = zones.first { it.side == 1 && it.rect.x + it.rect.width == 1280 }.rect
        assertEquals(590, left.width)
        assertEquals(690, right.x)
        // Bandes au-dessus (10 px) et au-dessous (10 px) de l'île, partagées au centre.
        assertTrue(zones.any { it.rect.y == 0 && it.rect.height == 10 && it.side == 0 && it.rect.x == 590 })
        assertTrue(zones.any { it.rect.y == 110 && it.rect.height == 10 && it.side == 1 && it.rect.x == 640 })
    }

    @Test
    fun atLeastStatusBarHeight() {
        // Hauteur réglée plus petite que la barre d'état : la barre d'état l'emporte.
        val zones = VoletStrips.zones(1000, 90, 40, 0, 500, null)
        assertTrue(zones.all { it.rect.height == 90 })
        // Plus grande : la hauteur réglée l'emporte.
        assertTrue(VoletStrips.zones(1000, 90, 150, 0, 500, null).all { it.rect.height == 150 })
    }

    @Test
    fun hiddenIslandLeavesNoHole() {
        // Île masquée ou vide invisible (non touchable) : la bande entière, partagée au centre.
        for (env in listOf(null, IslandEnvelope(450, 0, 100, 60, touchable = false))) {
            val zones = VoletStrips.zones(1000, 80, 0, 0, 500, env)
            assertEquals(2, zones.size)
            assertEquals(StripRect(0, 0, 500, 80), zones[0].rect)
            assertEquals(StripRect(500, 0, 500, 80), zones[1].rect)
            assertFullCoverage(zones, null, 1000, 80)
        }
        // Île plus basse que la bande : rien à épargner.
        assertEquals(2, VoletStrips.zones(1000, 80, 0, 0, 500, IslandEnvelope(450, 90, 100, 60)).size)
    }

    @Test
    fun landscapeIslandTopCenter() {
        // Paysage : 2800 × 1280, île en haut au centre, collée au bord haut, plus haute que la barre d'état.
        val env = IslandEnvelope(1300, 0, 200, 110)
        val zones = VoletStrips.zones(2800, 84, 0, 0, 1400, env)
        assertFullCoverage(zones, env, 2800, 84)
        assertIslandFree(zones, env)
        // Ni au-dessus ni au-dessous : seulement les deux colonnes.
        assertEquals(2, zones.size)
    }

    @Test
    fun edgeInsetAndOffCenterIsland() {
        // Marge réglée : respectée ; île décalée (sens d'ouverture) : les zones la suivent.
        val env = IslandEnvelope(700, 0, 150, 70)
        val zones = VoletStrips.zones(1200, 70, 0, 30, 775, env)
        assertTrue(zones.all { it.rect.x >= 30 && it.rect.x + it.rect.width <= 1170 })
        assertIslandFree(zones, env)
        val left = zones.first { it.side == 0 }.rect
        assertEquals(700, left.x + left.width)
        assertEquals(850, zones.first { it.side == 1 }.rect.x)
    }

    @Test
    fun estimateMatchesIslandWindow() {
        // Île au repos : enveloppe de la fenêtre tactile (largeur + 2, hauteur + 2), centrée.
        assertEquals(IslandEnvelope(589, 10, 102, 102), VoletStrips.estimate(640, 10, 100, 100, visible = true, contentMarginPx = 0))
        assertEquals(IslandEnvelope(539, 10, 202, 102), VoletStrips.estimate(640, 10, 100, 100, visible = true, contentMarginPx = 50))
        assertNull(VoletStrips.estimate(640, 10, 100, 100, visible = false, contentMarginPx = 0))
    }
}
