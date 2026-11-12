package com.karelisio.bulle.modules.calendar

import com.karelisio.bulle.modules.calendar.CalendarPlanner.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPlannerTest {
    private val now = 1_800_000_000_000L
    private fun ev(id: Long, beginMin: Long, durMin: Long = 60, allDay: Boolean = false, location: String? = null) =
        CalendarEvent(id, id * 10, "E$id", location, now + beginMin * MINUTE, now + (beginMin + durMin) * MINUTE, allDay)

    @Test
    fun nextEventSkipsAllDayPastAndBeyondHorizon() {
        val events = listOf(ev(1, -30), ev(2, 90, allDay = true), ev(3, 25 * 60), ev(4, 120), ev(5, 45))
        assertEquals(5L, CalendarPlanner.nextEvent(events, now)?.instanceId)
        assertNull(CalendarPlanner.nextEvent(listOf(ev(3, 25 * 60), ev(2, 10, allDay = true)), now))
    }

    @Test
    fun nextEventTieBreaksOnShortestThenId() {
        val events = listOf(ev(7, 30, durMin = 60), ev(6, 30, durMin = 15), ev(8, 30, durMin = 15))
        assertEquals(6L, CalendarPlanner.nextEvent(events, now)?.instanceId)
    }

    @Test
    fun reminderMomentAndWindow() {
        val e = ev(1, 12)
        assertEquals(e.begin - 10 * MINUTE, CalendarPlanner.reminderAt(e, 10))
        // 12 min avant : pas encore de rappel (délai 10), prochain changement = ouverture du rappel.
        val before = CalendarPlanner.plan(listOf(e), now, CalendarSettings())
        assertNull(before.reminder)
        assertEquals(e.begin - 10 * MINUTE, before.nextChangeAt)
        // Délai 15 : le rappel est déjà ouvert.
        assertEquals(e, CalendarPlanner.plan(listOf(e), now, CalendarSettings(reminderMinutes = 15)).reminder)
        // Délai inconnu : retombe sur 10.
        assertEquals(10, CalendarSettings(reminderMinutes = 7).effectiveReminderMinutes)
    }

    @Test
    fun ongoingShownOnlyWithoutReminderAndWhenEnabled() {
        val current = ev(1, -20, durMin = 50)
        val soon = ev(2, 5)
        val plan = CalendarPlanner.plan(listOf(current, soon), now, CalendarSettings())
        assertEquals(soon, plan.reminder)
        assertNull(plan.ongoing)
        val alone = CalendarPlanner.plan(listOf(current), now, CalendarSettings())
        assertEquals(current, alone.ongoing)
        assertEquals(current.end, alone.nextChangeAt)
        assertNull(CalendarPlanner.plan(listOf(current), now, CalendarSettings(showOngoing = false)).ongoing)
    }

    @Test
    fun minutesUntilRoundsUp() {
        assertEquals(1L, CalendarPlanner.minutesUntil(now + 1_000, now))
        assertEquals(10L, CalendarPlanner.minutesUntil(now + 10 * MINUTE, now))
        assertEquals(0L, CalendarPlanner.minutesUntil(now - 5_000, now))
    }
}
