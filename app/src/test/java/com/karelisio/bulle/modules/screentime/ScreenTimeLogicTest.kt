package com.karelisio.bulle.modules.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeLogicTest {
    private val min = 60_000L

    @Test
    fun aggregatesForegroundTimePerApp() {
        val events = listOf(
            UsageEvt(10 * min, UsageKind.SCREEN_ON),
            UsageEvt(10 * min, UsageKind.KEYGUARD_HIDDEN),
            UsageEvt(11 * min, UsageKind.RESUMED, "a"),
            UsageEvt(21 * min, UsageKind.PAUSED, "a"),
            UsageEvt(21 * min, UsageKind.RESUMED, "b"),
            UsageEvt(26 * min, UsageKind.SCREEN_OFF),
            UsageEvt(40 * min, UsageKind.SCREEN_ON),
            UsageEvt(40 * min, UsageKind.KEYGUARD_HIDDEN),
            UsageEvt(41 * min, UsageKind.RESUMED, "a"),
        )
        val usage = UsageAggregator.aggregate(events, start = 0, end = 50 * min)
        assertEquals(19 * min, usage.perApp["a"]) // 10 + 9 (ouverte jusqu'à la fin)
        assertEquals(5 * min, usage.perApp["b"]) // fermée par l'extinction de l'écran
        assertEquals(24 * min, usage.totalMs)
        assertEquals(2, usage.unlocks)
        assertEquals(listOf("a", "b"), usage.top(5).map { it.first })
    }

    @Test
    fun appOpenAtMidnightCountsFromStart() {
        val events = listOf(UsageEvt(5 * min, UsageKind.PAUSED, "a"))
        assertEquals(5 * min, UsageAggregator.aggregate(events, 0, 10 * min).perApp["a"])
        // Écran éteint avant : l'appli n'était pas réellement au premier plan.
        val off = listOf(UsageEvt(1 * min, UsageKind.SCREEN_OFF), UsageEvt(5 * min, UsageKind.PAUSED, "a"))
        assertNull(UsageAggregator.aggregate(off, 0, 10 * min).perApp["a"])
    }

    @Test
    fun multiWindowDoesNotDoubleCountTotalAndExcludesLauncher() {
        val events = listOf(
            UsageEvt(0, UsageKind.RESUMED, "a"),
            UsageEvt(2 * min, UsageKind.RESUMED, "b"),
            UsageEvt(4 * min, UsageKind.PAUSED, "a"),
            UsageEvt(6 * min, UsageKind.PAUSED, "b"),
            UsageEvt(6 * min, UsageKind.RESUMED, "launcher"),
            UsageEvt(8 * min, UsageKind.PAUSED, "launcher"),
        )
        val usage = UsageAggregator.aggregate(events, 0, 10 * min, excluded = setOf("launcher"))
        assertEquals(6 * min, usage.totalMs)
        assertEquals(4 * min, usage.perApp["a"])
        assertNull(usage.perApp["launcher"])
    }

    @Test
    fun unlocksFallBackToScreenOnWithoutKeyguardEvents() {
        val events = listOf(UsageEvt(1, UsageKind.SCREEN_ON), UsageEvt(2, UsageKind.SCREEN_OFF), UsageEvt(3, UsageKind.SCREEN_ON))
        assertEquals(2, UsageAggregator.aggregate(events, 0, 10).unlocks)
        assertEquals(DayUsage(), UsageAggregator.aggregate(events, 10, 10))
    }

    @Test
    fun breakReminderAfterContinuousUse() {
        val threshold = 30 * min
        var s = BreakState().active(0)
        assertEquals(threshold, s.dueInMs(0, threshold))
        s = s.screenOff(20 * min) // pause courte : la séance continue
        assertNull(s.dueInMs(21 * min, threshold))
        s = s.active(22 * min)
        assertEquals(20 * min, s.usedMs(22 * min))
        assertEquals(10 * min, s.dueInMs(22 * min, threshold))
        assertEquals(0L, s.dueInMs(32 * min, threshold))
        s = s.reminded()
        assertEquals(30 * min, s.dueInMs(32 * min, threshold)) // rappel suivant après une nouvelle période
    }

    @Test
    fun longScreenOffResetsTheSession() {
        val threshold = 20 * min
        var s = BreakState().active(0).screenOff(15 * min)
        s = s.active(15 * min + BreakState.PAUSE_RESET_MS)
        assertEquals(0L, s.usedMs(15 * min + BreakState.PAUSE_RESET_MS))
        assertEquals(threshold, s.dueInMs(15 * min + BreakState.PAUSE_RESET_MS, threshold))
        assertEquals(0, s.reminders)
        // Activations répétées sans extinction : sans effet.
        assertEquals(s, s.active(30 * min))
    }

    @Test
    fun limitsReachedOncePerDay() {
        val settings = ScreenTimeSettings(appLimits = mapOf("a" to 30, "b" to 60, "c" to 0))
        val usage = DayUsage(perApp = mapOf("a" to 31 * min, "b" to 10 * min, "c" to 500 * min))
        assertEquals(listOf("a" to 30), reachedLimits(settings, usage, epochDay = 100))
        assertTrue(reachedLimits(settings.copy(limitAlerted = setOf("a@100")), usage, 100).isEmpty())
        assertEquals(1, reachedLimits(settings.copy(limitAlerted = setOf("a@99")), usage, 100).size)
    }

    @Test
    fun hoursMinutesSplit() {
        assertEquals(2 to 14, hoursMinutes(134 * min + 59_000))
        assertEquals(0 to 0, hoursMinutes(-5))
    }
}
