package com.karelisio.bulle.modules.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun haversine() {
        // Paris (Notre-Dame) → Lyon (Bellecour) ≈ 392 km.
        val d = Geo.distanceMeters(48.8530, 2.3499, 45.7578, 4.8320)
        assertEquals(392_000.0, d, 3_000.0)
        assertEquals(0.0, Geo.distanceMeters(10.0, 10.0, 10.0, 10.0), 1e-6)
        // 0,001° de latitude ≈ 111 m.
        assertEquals(111.2, Geo.distanceMeters(0.0, 0.0, 0.001, 0.0), 0.5)
    }

    @Test
    fun hysteresis() {
        val r = 200.0
        val margin = Geo.exitMargin(r)
        assertEquals(50.0, margin, 1e-9)
        assertTrue(Geo.inside(wasInside = false, distance = 199.0, radius = r))
        assertFalse(Geo.inside(wasInside = false, distance = 220.0, radius = r))
        // Déjà dedans : on reste dedans jusqu'au rayon + marge.
        assertTrue(Geo.inside(wasInside = true, distance = 240.0, radius = r))
        assertFalse(Geo.inside(wasInside = true, distance = 251.0, radius = r))
        // Grand cercle : marge proportionnelle (25 %).
        assertEquals(250.0, Geo.exitMargin(1000.0), 1e-9)
        // Position trop imprécise : aucun changement.
        assertTrue(Geo.inside(wasInside = true, distance = 5000.0, radius = r, accuracy = 2000f))
        assertFalse(Geo.inside(wasInside = false, distance = 10.0, radius = r, accuracy = 2000f))
    }

    @Test
    fun updatePlaces() {
        val home = RuleTrigger.Place(48.8530, 2.3499, 200, id = "home")
        val work = RuleTrigger.Place(48.8738, 2.2950, 300, id = "work")
        val atHome = GeoFix(48.8531, 2.3500, 20f)
        val s1 = Geo.update(emptySet(), listOf(home, work), atHome)
        assertEquals(setOf("home"), s1)
        // ~230 m au nord : au-delà du rayon mais dans la marge → toujours dedans.
        val edge = GeoFix(48.8530 + 230.0 / 111_200, 2.3499, 20f)
        assertEquals(setOf("home"), Geo.update(s1, listOf(home, work), edge))
        assertEquals(emptySet<String>(), Geo.update(emptySet(), listOf(home, work), edge))
    }
}
