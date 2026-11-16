package com.karelisio.bulle.modules.standby

import com.karelisio.bulle.modules.battery.PowerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyLogicTest {

    @Test
    fun postureFromGravityWithHysteresis() {
        assertEquals(Posture.LANDSCAPE, StandbyLogic.posture(9.6f, 0.5f, 1f))
        assertEquals(Posture.PORTRAIT, StandbyLogic.posture(0.3f, 9.7f, 1f))
        assertEquals(Posture.FLAT, StandbyLogic.posture(0.2f, 0.3f, 9.8f))
        // Légèrement incliné : reste à l'horizontale.
        assertEquals(Posture.LANDSCAPE, StandbyLogic.posture(6f, 5f, 4f, Posture.LANDSCAPE))
        assertEquals(Posture.PORTRAIT, StandbyLogic.posture(2f, 9f, 2f, Posture.LANDSCAPE))
    }

    @Test
    fun chargerFilter() {
        assertTrue(StandbyLogic.chargerMatches(PowerSource.AC, StandbyCharger.ANY))
        assertFalse(StandbyLogic.chargerMatches(PowerSource.NONE, StandbyCharger.ANY))
        assertFalse(StandbyLogic.chargerMatches(PowerSource.WIRELESS, StandbyCharger.WIRED))
        assertTrue(StandbyLogic.chargerMatches(PowerSource.WIRELESS, StandbyCharger.WIRELESS))
        assertFalse(StandbyLogic.chargerMatches(PowerSource.USB, StandbyCharger.WIRELESS))
    }

    @Test
    fun conditionsNeedChargeLandscapeAndIdle() {
        assertTrue(StandbyLogic.conditions(true, PowerSource.AC, StandbyCharger.ANY, Posture.LANDSCAPE, idle = true))
        assertFalse(StandbyLogic.conditions(true, PowerSource.AC, StandbyCharger.ANY, Posture.LANDSCAPE, idle = false))
        assertFalse(StandbyLogic.conditions(true, PowerSource.AC, StandbyCharger.ANY, Posture.PORTRAIT, idle = true))
        assertFalse(StandbyLogic.conditions(false, PowerSource.AC, StandbyCharger.ANY, Posture.LANDSCAPE, idle = true))
    }

    @Test
    fun nightWindowAcrossMidnightAndDarkness() {
        assertTrue(StandbyLogic.inWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(StandbyLogic.inWindow(3 * 60, 22 * 60, 7 * 60))
        assertFalse(StandbyLogic.inWindow(12 * 60, 22 * 60, 7 * 60))
        assertTrue(StandbyLogic.inWindow(10, 0, 60))
        assertTrue(StandbyLogic.isNight(StandbyNightMode.AUTO, 12 * 60, 22 * 60, 7 * 60, lux = 1f, darkLux = 4f))
        assertFalse(StandbyLogic.isNight(StandbyNightMode.SCHEDULE, 12 * 60, 22 * 60, 7 * 60, lux = 1f, darkLux = 4f))
        assertFalse(StandbyLogic.isNight(StandbyNightMode.LIGHT, 23 * 60, 22 * 60, 7 * 60, lux = null, darkLux = 4f))
        assertFalse(StandbyLogic.isNight(StandbyNightMode.OFF, 23 * 60, 22 * 60, 7 * 60, lux = 0f, darkLux = 4f))
    }

    @Test
    fun pixelShiftMovesEveryMinuteWithinRange() {
        val a = StandbyLogic.pixelShift(0, 6f)
        val b = StandbyLogic.pixelShift(60_000, 6f)
        assertTrue(a != b)
        repeat(16) { i ->
            val (x, y) = StandbyLogic.pixelShift(i * 60_000L, 6f)
            assertTrue(x in -6f..6f && y in -6f..6f)
        }
        assertEquals(a, StandbyLogic.pixelShift(8 * 60_000L, 6f))
    }

    @Test
    fun pagesPhotosNeedAnAlbumAndCanBeReordered() {
        val s = StandbySettings(pages = listOf(StandbyPage.PHOTOS, StandbyPage.CLOCKS))
        assertEquals(listOf(StandbyPage.CLOCKS), StandbyLogic.effectivePages(s))
        assertEquals(listOf(StandbyPage.PHOTOS, StandbyPage.CLOCKS), StandbyLogic.effectivePages(s.copy(albumId = "1")))
        assertEquals(listOf(StandbyPage.CLOCKS), StandbyLogic.effectivePages(StandbySettings(pages = emptyList())))
        val moved = StandbyLogic.move(listOf(StandbyPage.CLOCKS, StandbyPage.MUSIC, StandbyPage.ECHO), StandbyPage.ECHO, -1)
        assertEquals(listOf(StandbyPage.CLOCKS, StandbyPage.ECHO, StandbyPage.MUSIC), moved)
        assertEquals(moved, StandbyLogic.move(moved, StandbyPage.CLOCKS, -1))
    }

    @Test
    fun clockInWords() {
        assertEquals("Il est dix heures et quart", ClockWords.french(10, 14))
        assertEquals("Il est midi moins le quart", ClockWords.french(11, 45))
        assertEquals("Il est minuit", ClockWords.french(23, 58))
        assertEquals("Il est une heure et demie", ClockWords.french(13, 30))
        assertEquals("Il est cinq heures moins vingt", ClockWords.french(16, 40))
        assertEquals("It's quarter past ten", ClockWords.english(10, 15))
        assertEquals("It's twenty to five", ClockWords.english(16, 40))
        assertEquals("It's noon", ClockWords.english(12, 1))
    }

    @Test
    fun kenBurnsAlternatesZoom() {
        val (s0a, _, _) = KenBurns.frame(0, 0f)
        val (s0b, _, _) = KenBurns.frame(0, 1f)
        val (s1a, _, _) = KenBurns.frame(1, 0f)
        val (s1b, _, _) = KenBurns.frame(1, 1f)
        assertTrue(s0b > s0a)
        assertTrue(s1b < s1a)
    }

    @Test
    fun sanitize() {
        val s = StandbySettings(delaySeconds = 999, pages = emptyList(), nightStart = -5, darkLux = Float.NaN).sanitized()
        assertEquals(60, s.delaySeconds)
        assertEquals(listOf(StandbyPage.CLOCKS), s.pages)
        assertEquals(0, s.nightStart)
        assertEquals(4f, s.darkLux)
    }
}
