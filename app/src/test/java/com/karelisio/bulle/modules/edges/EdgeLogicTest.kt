package com.karelisio.bulle.modules.edges

import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeLogicTest {

    private val d = 3f
    private fun place(s: EdgeSettings, top: Int = 120, bottom: Int = 0) = EdgeGeometry.place(s, 1080, 2400, d, top, bottom)

    @Test
    fun defaultZonesAreShortAndInTheUpperThird() {
        val zones = place(EdgeSettings(enabled = true))
        assertEquals(setOf(EdgeSlot.LEFT, EdgeSlot.RIGHT), zones.map { it.slot }.toSet())
        zones.forEach { z ->
            assertTrue(z.rect.bottom <= 2400 / 3 + 10)
            assertEquals(36, z.rect.width)
        }
        assertEquals(0, zones.first { it.slot == EdgeSlot.LEFT }.rect.x)
        assertEquals(1080 - 36, zones.first { it.slot == EdgeSlot.RIGHT }.rect.x)
    }

    @Test
    fun zonesNeverOverlapTheVoletBand() {
        val top = EdgeGeometry.topReserved(statusBarPx = 90, voletTriggerPx = 180, density = d)
        assertEquals(180 + 24, top)
        val s = EdgeSettings(left = EdgeZone(start = 0f, length = 0.2f))
        val left = place(s, top).first { it.slot == EdgeSlot.LEFT }
        assertEquals(top, left.rect.y)
        assertTrue(left.adjusted)
        assertFalse(EdgeGeometry.overlapsVolet(left.rect, 180))
    }

    @Test
    fun lowerZoneStaysBelowTheUpperOneAndAboveNavigation() {
        val s = EdgeSettings(
            bars = EdgeBars.DOUBLE,
            left = EdgeZone(start = 0.1f, length = 0.5f),
            leftLower = EdgeZone(enabled = true, start = 0.3f, length = 0.6f),
        )
        val zones = place(s, bottom = 100)
        val upper = zones.first { it.slot == EdgeSlot.LEFT }.rect
        val lower = zones.first { it.slot == EdgeSlot.LEFT_LOWER }.rect
        assertFalse(upper.overlapsVertically(lower))
        assertTrue(lower.bottom <= 2400 - 100 - 24)
    }

    @Test
    fun singleBarCanTakeTheFullLengthAndIgnoresLowerZones() {
        val s = EdgeSettings(left = EdgeZone(start = 0f, length = 1f), leftLower = EdgeZone(enabled = true))
        val zones = place(s, top = 120, bottom = 100)
        assertTrue(zones.none { it.slot == EdgeSlot.LEFT_LOWER })
        val left = zones.first { it.slot == EdgeSlot.LEFT }.rect
        assertEquals(120, left.y)
        assertEquals(2400 - 100 - 24, left.bottom)
    }

    @Test
    fun thicknessIsClampedTo8To24Dp() {
        val zones = place(EdgeSettings(left = EdgeZone(thicknessDp = 80f), right = EdgeZone(thicknessDp = 1f)))
        assertEquals(72, zones.first { it.slot == EdgeSlot.LEFT }.rect.width)
        assertEquals(24, zones.first { it.slot == EdgeSlot.RIGHT }.rect.width)
    }

    @Test
    fun swipeInIsOffByDefault() {
        val s = EdgeSettings()
        assertFalse(s.swipeInAllowed)
        assertEquals(ActionType.NONE, s.left.swipeIn.type)
        val z = s.left.with(EdgeGesture.SWIPE_IN, GestureAction(ActionType.HOME))
        assertEquals(ActionType.HOME, z.actionFor(EdgeGesture.SWIPE_IN).type)
    }

    @Test
    fun sliderMath() {
        assertEquals(0f, EdgeSliderMath.brightnessToFraction(0), 0.001f)
        assertEquals(1f, EdgeSliderMath.brightnessToFraction(255), 0.001f)
        assertEquals(255, EdgeSliderMath.fractionToBrightness(1f))
        assertEquals(1, EdgeSliderMath.fractionToBrightness(0f))
        val f = EdgeSliderMath.brightnessToFraction(100)
        assertEquals(100.0, EdgeSliderMath.fractionToBrightness(f).toDouble(), 1.0)
        assertEquals(8, EdgeSliderMath.fractionToVolume(0.5f, 15))
        assertTrue(EdgeSliderMath.crossedNotch(0.04f, 0.06f))
        assertFalse(EdgeSliderMath.crossedNotch(0.06f, 0.07f))
    }

    @Test
    fun sanitizeKeepsZonesValid() {
        val s = EdgeSettings(left = EdgeZone(start = 2f, length = Float.NaN, thicknessDp = 100f)).sanitized()
        assertEquals(1f - EdgeZone.MIN_LENGTH, s.left.start)
        assertEquals(EdgeZone().length, s.left.length)
        assertEquals(24f, s.left.thicknessDp)
    }
}
