package com.karelisio.bulle.modules.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phases Pomodoro et temps restant : fonctions pures, horloge injectée. */
class FocusTimerTest {

    private val settings = FocusSettings(workMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, cycles = 4)
    private val t0 = 1_700_000_000_000L
    private val min = 60_000L

    @Test
    fun simpleTimerCountsDownFromTheClock() {
        val s = FocusTimer.start(FocusPreset.Minutes(10), settings, t0)
        assertEquals(FocusPhase.TIMER, s.phase)
        assertEquals(t0 + 10 * min, s.endAtWall)
        assertEquals(10 * min, FocusTimer.remaining(s, t0))
        assertEquals(4 * min, FocusTimer.remaining(s, t0 + 6 * min))
        assertEquals(0.6f, FocusTimer.progress(s, t0 + 6 * min), 0.0001f)
        assertFalse(FocusTimer.isFinished(s, t0 + 10 * min - 1))
        assertTrue(FocusTimer.isFinished(s, t0 + 10 * min))
        // Jamais négatif, jamais plus que la durée (horloge reculée).
        assertEquals(0, FocusTimer.remaining(s, t0 + 20 * min))
        assertEquals(10 * min, FocusTimer.remaining(s, t0 - 5 * min))
        assertNull(FocusTimer.next(s, settings, t0 + 10 * min, autoStart = true))
    }

    @Test
    fun pauseFreezesAndResumeShiftsTheEnd() {
        val s = FocusTimer.start(FocusPreset.Minutes(5), settings, t0)
        val paused = FocusTimer.pause(s, t0 + 2 * min)
        assertFalse(paused.running)
        assertEquals(3 * min, FocusTimer.remaining(paused, t0 + 50 * min))
        val resumed = FocusTimer.resume(paused, t0 + 50 * min)
        assertTrue(resumed.running)
        assertEquals(t0 + 53 * min, resumed.endAtWall)
        // Idempotents.
        assertEquals(resumed, FocusTimer.resume(resumed, t0 + 51 * min))
        assertEquals(paused, FocusTimer.pause(paused, t0 + 60 * min))
    }

    @Test
    fun pomodoroSequenceWorkBreaksThenLongBreak() {
        var s: FocusState? = FocusTimer.start(FocusPreset.Pomodoro, settings, t0)
        val seen = mutableListOf<Pair<FocusPhase, Int>>()
        var now = t0
        while (s != null) {
            seen += s.phase to s.cycle
            now = s.endAtWall!!
            s = FocusTimer.next(s, settings, now, autoStart = true)
        }
        assertEquals(
            listOf(
                FocusPhase.WORK to 1, FocusPhase.SHORT_BREAK to 1,
                FocusPhase.WORK to 2, FocusPhase.SHORT_BREAK to 2,
                FocusPhase.WORK to 3, FocusPhase.SHORT_BREAK to 3,
                FocusPhase.WORK to 4, FocusPhase.LONG_BREAK to 4,
            ),
            seen,
        )
        // 4 × 25 + 3 × 5 + 15 minutes.
        assertEquals(t0 + 130 * min, now)
    }

    @Test
    fun withoutAutoStartNextPhaseWaitsPaused() {
        val work = FocusTimer.start(FocusPreset.Pomodoro, settings, t0)
        val next = FocusTimer.next(work, settings, t0 + 25 * min, autoStart = false)!!
        assertEquals(FocusPhase.SHORT_BREAK, next.phase)
        assertFalse(next.running)
        assertEquals(5 * min, FocusTimer.remaining(next, t0 + 99 * min))
        assertEquals(0f, FocusTimer.progress(next, t0 + 99 * min), 0f)
    }

    @Test
    fun settingsAreClamped() {
        val s = FocusTimer.start(FocusPreset.Pomodoro, FocusSettings(workMinutes = 0, cycles = 99), t0)
        assertEquals(1 * min, s.durationMs)
        assertEquals(12, s.cycles)
    }

    @Test
    fun formatsCountdown() {
        assertEquals("0:00", FocusTimer.format(0))
        assertEquals("0:01", FocusTimer.format(1))
        assertEquals("4:05", FocusTimer.format(245_000))
        assertEquals("25:00", FocusTimer.format(25 * min))
        assertEquals("1:02:03", FocusTimer.format(3_723_000))
    }
}
