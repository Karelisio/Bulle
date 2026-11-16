package com.karelisio.bulle.modules.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class HaloGeometryTest {

    @Test
    fun squareScreenPerimeter() {
        val o = HaloOutline.of(HaloScreen(100f, 200f), inset = 0f)
        assertEquals(600f, o.length, 0.5f)
        // Départ au milieu du bord haut, moitié du tour au milieu du bas.
        val (x0, y0) = o.pointAt(0f)
        assertEquals(50f, x0, 0.5f)
        assertEquals(0f, y0, 0.5f)
        val (xm, ym) = o.pointAt(o.length / 2)
        assertEquals(50f, xm, 0.5f)
        assertEquals(200f, ym, 0.5f)
    }

    @Test
    fun roundedCornersFollowTheRealRadius() {
        val r = 20f
        val o = HaloOutline.of(HaloScreen(100f, 200f, r, r, r, r), inset = 0f)
        // Chaque coin : 2r de droites remplacés par un quart de cercle.
        val expected = 600f - 8 * r + 4 * (PI.toFloat() / 2 * r)
        assertEquals(expected, o.length, 0.5f)
    }

    @Test
    fun outlineGoesAroundTheCameraPill() {
        val cut = HaloRect(40f, 4f, 60f, 16f)
        val plain = HaloOutline.of(HaloScreen(100f, 200f, density = 1f), inset = 2f)
        val o = HaloOutline.of(HaloScreen(100f, 200f, cutout = cut, density = 1f), inset = 2f, gap = 4f)
        assertTrue("le contournement allonge le tour", o.length > plain.length)
        // Le départ (caméra) est sous la pilule.
        val (x, y) = o.pointAt(0f)
        assertEquals(50f, x, 0.5f)
        assertEquals(20f, y, 0.5f)
        // Aucun point du contour n'entre dans la pilule.
        var d = 0f
        while (d < o.length) {
            val (px, py) = o.pointAt(d)
            assertTrue("($px,$py) dans la caméra", !(px > cut.left && px < cut.right && py > cut.top && py < cut.bottom))
            d += 0.5f
        }
    }

    @Test
    fun sliceWrapsAroundTheStart() {
        val o = HaloOutline.of(HaloScreen(100f, 200f), inset = 0f)
        val segs = o.slice(-10f, 10f)
        assertEquals(20f, segs.sumOf { it.length.toDouble() }.toFloat(), 0.01f)
        assertEquals(o.length, o.slice(0f, o.length * 3).sumOf { it.length.toDouble() }.toFloat(), 0.5f)
        assertTrue(o.slice(5f, 5f).isEmpty())
    }

    @Test
    fun tinyScreenGivesAnEmptyOutline() {
        assertTrue(HaloOutline.of(HaloScreen(4f, 4f), inset = 3f).segments.isEmpty())
    }
}
