package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.modules.alerts.ui.ClockTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Horloge de l'alarme : réveil calé sur la minute (ou la seconde) pile. */
class ClockTicksTest {

    @Test
    fun wakesOnTheMinute() {
        assertEquals(60_000L, ClockTicks.msUntilNextTick(120_000L, withSeconds = false))
        assertEquals(1L, ClockTicks.msUntilNextTick(179_999L, withSeconds = false))
        assertEquals(30_000L, ClockTicks.msUntilNextTick(90_000L, withSeconds = false))
    }

    @Test
    fun wakesOnTheSecond() {
        assertEquals(750L, ClockTicks.msUntilNextTick(10_250L, withSeconds = true))
    }

    @Test
    fun detectsSecondsInPattern() {
        assertTrue(ClockTicks.patternHasSeconds("HH:mm:ss"))
        assertFalse(ClockTicks.patternHasSeconds("HH:mm"))
        assertFalse(ClockTicks.patternHasSeconds("h:mm 'secs'"))
    }
}
