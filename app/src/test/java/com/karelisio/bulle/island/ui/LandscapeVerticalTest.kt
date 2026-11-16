package com.karelisio.bulle.island.ui

import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.island.CutoutLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paysage, caméra latérale : île verticale collée à la caméra, carte centrée sur elle sans sortir de l'écran. */
class LandscapeVerticalTest {

    // Honor Magic7 Pro en paysage : 2800 × 1280, pilule de caméra verticale sur le bord gauche.
    private val w = 2800
    private val h = 1280
    private val d = 3f

    @Test
    fun sideGeometryHugsTheCameraVertically() {
        val g = CutoutLocator.sideGeometry(24, 475, 134, 805, w, h, d, cameraLeft = true)
        assertTrue(g.vertical)
        assertTrue(g.cameraLeft)
        assertEquals(OpenDirection.RIGHT, g.openDirection)
        // Épaisseur = largeur du trou + 2 × 4 dp ; longueur = hauteur du trou + 2 × 4 dp.
        assertEquals(110 + 24, g.width)
        assertEquals(330 + 24, g.height)
        // Centrée exactement sur la caméra.
        assertEquals(79, g.centerX)
        assertEquals(640, g.centerY)
        assertEquals(h, g.screenHeight)
        assertTrue(g.height > g.width)
    }

    @Test
    fun rightCameraOpensToTheLeft() {
        val g = CutoutLocator.sideGeometry(2666, 475, 2776, 805, w, h, d, cameraLeft = false)
        assertFalse(g.cameraLeft)
        assertEquals(OpenDirection.LEFT, g.openDirection)
        assertEquals(2721, g.centerX)
        assertEquals(90f, IslandLayout.compactRotation(false), 0f)
        assertEquals(-90f, IslandLayout.compactRotation(true), 0f)
    }

    @Test
    fun cameraNearTheEdgeStaysOnScreen() {
        val g = CutoutLocator.sideGeometry(0, 0, 90, 120, w, h, d)
        assertTrue(g.top >= 0)
        assertTrue(g.centerX - g.width / 2 >= 0)
    }

    @Test
    fun expandedCardIsCenteredOnTheCamera() {
        // Île au repos : haut 463, longueur 354 → centre 640. Carte de 600 px : centrée → haut 340.
        val dy = IslandLayout.verticalOffsetY(463f, 354f, 600f, h.toFloat(), 30f)
        assertEquals(340f, 463f + dy, 0.01f)
        assertEquals(640f, 463f + dy + 300f, 0.01f)
    }

    @Test
    fun expandedCardNeverLeavesTheScreen() {
        // Caméra basse : la carte remonte pour finir à la marge du bas.
        val low = IslandLayout.verticalOffsetY(1000f, 200f, 800f, h.toFloat(), 30f)
        assertEquals(h - 30f - 800f, 1000f + low, 0.01f)
        // Caméra haute : la carte descend jusqu'à la marge du haut.
        val high = IslandLayout.verticalOffsetY(0f, 200f, 800f, h.toFloat(), 30f)
        assertEquals(30f, high, 0.01f)
        // Plus haute que l'écran : calée en haut (elle défile alors à l'intérieur).
        val huge = IslandLayout.verticalOffsetY(463f, 354f, 5000f, h.toFloat(), 30f)
        assertEquals(30f, 463f + huge, 0.01f)
        assertEquals(h - 60f, IslandLayout.verticalMaxHeight(h.toFloat(), 30f), 0.01f)
    }

    @Test
    fun envelopeCoversCurrentAndTargetSpans() {
        // Animation de la compacte (offset 0, 354) vers la carte (-123, 600).
        val (top, bottom) = IslandLayout.verticalSpan(0f, 354f, -123f, 600f, overshoot = 0.1f, settled = false)
        assertTrue(top <= -123f)
        assertTrue(bottom >= 477f)
        val (sTop, sBottom) = IslandLayout.verticalSpan(0f, 354f, -123f, 600f, overshoot = 0.1f, settled = true, shadow = 30f)
        assertEquals(-153f, sTop, 0.01f)
        assertEquals(507f, sBottom, 0.01f)
    }

    @Test
    fun cardOpensTowardTheInsideFromTheCamera() {
        // Caméra à gauche (centre 79, épaisseur 134) : la carte de 1400 px part de la caméra vers la droite.
        val dx = IslandLayout.openingOffsetX(OpenDirection.RIGHT, 134f, 1400f, 79f, w.toFloat(), 0f, clampToScreen = true)
        val left = 79f + dx - 700f
        assertEquals(12f, left, 0.01f)
        // Caméra à droite : vers la gauche.
        val dxR = IslandLayout.openingOffsetX(OpenDirection.LEFT, 134f, 1400f, 2721f, w.toFloat(), 0f, clampToScreen = true)
        assertEquals(2788f, 2721f + dxR + 700f, 0.01f)
    }
}
