package com.karelisio.bulle.modules.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tableau des contenus (limites, expiration, DISMISS), temps des modèles et phases de respiration. */
class ExternalLogicTest {

    private fun spec(pkg: String, id: String, at: Long, timeout: Long? = null) =
        ExternalSpec(packageName = pkg, id = id, receivedAt = at, timeoutMs = timeout)

    @Test
    fun boardReplacesSameKey() {
        val b = ExternalBoard()
            .put(spec("a", "1", 0).copy(title = "v1"), 0)
            .put(spec("a", "1", 10).copy(title = "v2"), 10)
        assertEquals(1, b.items.size)
        assertEquals("v2", b.items.single().title)
    }

    @Test
    fun boardLimitsPerApp() {
        var b = ExternalBoard(maxPerApp = 3)
        for (i in 1..5) b = b.put(spec("a", "$i", i.toLong()), i.toLong())
        b = b.put(spec("b", "1", 6), 6)
        assertEquals(listOf("3", "4", "5"), b.forApp("a").map { it.id })
        assertEquals(1, b.forApp("b").size)
        // Mise à jour d'un contenu existant : rien n'est évincé.
        b = b.put(spec("a", "3", 7), 7)
        assertEquals(setOf("3", "4", "5"), b.forApp("a").map { it.id }.toSet())
    }

    @Test
    fun boardLimitsTotal() {
        var b = ExternalBoard(maxPerApp = 3, maxTotal = 4)
        for (i in 1..6) b = b.put(spec("p$i", "x", i.toLong()), i.toLong())
        assertEquals(listOf("p3", "p4", "p5", "p6"), b.items.map { it.packageName })
    }

    @Test
    fun boardDismissExpiryAndRemoveApp() {
        var b = ExternalBoard()
            .put(spec("a", "1", 0, timeout = 1_000), 0)
            .put(spec("a", "2", 0), 0)
            .put(spec("b", "1", 0), 0)
        assertEquals(3, b.items.size)
        assertEquals(2, b.prune(1_000).items.size)
        assertEquals(3, b.prune(999).items.size)
        b = b.dismiss("a/2")
        assertEquals(listOf("a/1", "b/1"), b.items.map { it.key })
        b = b.removeApp("a")
        assertEquals(listOf("b/1"), b.items.map { it.key })
        // Un contenu expiré ne compte plus dans la limite.
        var c = ExternalBoard(maxPerApp = 1).put(spec("a", "1", 0, timeout = 1_000), 0)
        c = c.put(spec("a", "2", 2_000), 2_000)
        assertEquals(listOf("a/2"), c.items.map { it.key })
    }

    @Test
    fun countdownAndChronometer() {
        assertEquals(60_000L, ExternalTime.remaining(160_000, 100_000))
        assertEquals(0L, ExternalTime.remaining(100_000, 160_000))
        assertEquals(60_000L, ExternalTime.elapsed(100_000, 160_000))
        assertEquals(0L, ExternalTime.elapsed(160_000, 100_000))
        assertEquals(0.5f, ExternalTime.countdownFraction(0, 100, 50)!!, 1e-4f)
        assertEquals(1f, ExternalTime.countdownFraction(0, 100, 500)!!, 1e-4f)
        assertNull(ExternalTime.countdownFraction(null, 100, 50))
        assertNull(ExternalTime.countdownFraction(100, 100, 50))

        assertEquals("0:00", ExternalTime.format(0))
        assertEquals("4:09", ExternalTime.format(249_000))
        assertEquals("1:05:09", ExternalTime.format(3_909_000))
        val twelveDays = (12 * 24 + 5) * 3_600_000L + 59_000L
        assertEquals("12 j 05 h", ExternalTime.format(twelveDays))
        assertEquals("12 d 05 h", ExternalTime.format(twelveDays, "d", "h"))
        assertEquals(1_000L, ExternalTime.tickMs(3_909_000))
        assertEquals(60_000L, ExternalTime.tickMs(twelveDays))
    }

    @Test
    fun breathing478Phases() {
        val p = BreathPattern.RELAX_478
        assertEquals(19_000L, p.cycleMs)
        assertEquals(76_000L, Breathing.totalMs(p, p.defaultCycles))

        val start = Breathing.stateAt(p, 0)
        assertEquals(BreathPhase.INHALE, start.phase)
        assertEquals(4, start.secondsLeft)
        assertEquals(1, start.cycle)
        assertEquals(Breathing.MIN_SCALE, Breathing.scaleOf(start), 1e-4f)

        val hold = Breathing.stateAt(p, 4_000)
        assertEquals(BreathPhase.HOLD, hold.phase)
        assertEquals(7, hold.secondsLeft)
        assertEquals(1f, Breathing.scaleOf(hold), 1e-4f)

        val exhale = Breathing.stateAt(p, 11_500)
        assertEquals(BreathPhase.EXHALE, exhale.phase)
        assertEquals(8, exhale.secondsLeft)
        assertTrue(Breathing.scaleOf(exhale) < 1f)

        val second = Breathing.stateAt(p, 19_000)
        assertEquals(BreathPhase.INHALE, second.phase)
        assertEquals(2, second.cycle)
        assertEquals(0.25f, second.totalProgress, 1e-4f)

        val end = Breathing.stateAt(p, 76_000)
        assertTrue(end.finished)
        assertEquals(1f, end.totalProgress, 1e-4f)
        assertFalse(Breathing.stateAt(p, 75_999).finished)
    }

    @Test
    fun breathingBoxPhases() {
        val p = BreathPattern.BOX
        assertEquals(96_000L, Breathing.totalMs(p, p.defaultCycles))
        assertEquals(BreathPhase.HOLD_OUT, Breathing.stateAt(p, 12_000).phase)
        assertEquals(Breathing.MIN_SCALE, Breathing.scaleOf(Breathing.stateAt(p, 13_000)), 1e-4f)
        assertEquals(BreathPhase.INHALE, Breathing.stateAt(p, 16_000).phase)
        // Nombre de cycles borné.
        assertEquals(p.cycleMs * 20, Breathing.totalMs(p, 500))
        assertEquals(p.cycleMs, Breathing.totalMs(p, 0))
        assertEquals(BreathPattern.BOX, BreathPattern.parse("BOX"))
        assertEquals(BreathPattern.RELAX_478, BreathPattern.parse("478"))
        // Inspiration à mi-chemin : échelle intermédiaire.
        val mid = Breathing.scaleOf(Breathing.stateAt(p, 2_000))
        assertEquals((Breathing.MIN_SCALE + 1f) / 2, mid, 1e-3f)
    }
}
