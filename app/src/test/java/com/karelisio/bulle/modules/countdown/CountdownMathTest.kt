package com.karelisio.bulle.modules.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CountdownMathTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, paris)
    private fun event(date: String, time: String? = null, yearly: Boolean = false, id: String = "e", createdAt: Long = 0) =
        CountdownEvent(id = id, name = "Évènement $id", date = date, time = time, yearly = yearly, createdAt = createdAt)

    @Test
    fun daysUntilEvent() {
        assertEquals(CountdownStatus.Days(12), CountdownMath.status(event("2026-11-25"), at(2026, 11, 13)))
        // Demain soir, plus de 24 h restantes : J-1.
        assertEquals(CountdownStatus.Days(1), CountdownMath.status(event("2026-11-14", "23:00"), at(2026, 11, 13, 10)))
    }

    @Test
    fun lastDayShowsHoursThenMinutes() {
        // Événement d'une journée : cible à minuit.
        assertEquals(CountdownStatus.Hours(12), CountdownMath.status(event("2026-11-14"), at(2026, 11, 13, 12)))
        assertEquals(CountdownStatus.Hours(2), CountdownMath.status(event("2026-11-13", "14:00"), at(2026, 11, 13, 12, 30)))
        assertEquals(CountdownStatus.Minutes(42), CountdownMath.status(event("2026-11-13", "14:00"), at(2026, 11, 13, 13, 18)))
        assertEquals(CountdownStatus.Minutes(60), CountdownMath.status(event("2026-11-13", "14:00"), at(2026, 11, 13, 13, 0)))
    }

    @Test
    fun dayJIsTodayUntilMidnightThenGone() {
        assertEquals(CountdownStatus.Today, CountdownMath.status(event("2026-11-13"), at(2026, 11, 13, 0, 0)))
        assertEquals(CountdownStatus.Today, CountdownMath.status(event("2026-11-13", "14:00"), at(2026, 11, 13, 23, 59)))
        assertNull(CountdownMath.status(event("2026-11-13"), at(2026, 11, 14, 0, 1)))
    }

    @Test
    fun yearlyRecurrence() {
        val birthday = event("1990-03-02", yearly = true)
        assertEquals(LocalDate.of(2027, 3, 2), CountdownMath.nextDate(birthday, LocalDate.of(2026, 11, 13)))
        assertEquals(LocalDate.of(2026, 11, 13), CountdownMath.nextDate(event("1990-11-13", yearly = true), LocalDate.of(2026, 11, 13)))
        // Origine dans le futur : première occurrence = la date elle-même.
        assertEquals(LocalDate.of(2028, 5, 1), CountdownMath.nextDate(event("2028-05-01", yearly = true), LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun february29() {
        val leap = event("2000-02-29", yearly = true)
        assertEquals(LocalDate.of(2027, 2, 28), CountdownMath.nextDate(leap, LocalDate.of(2026, 11, 13)))
        assertEquals(LocalDate.of(2028, 2, 29), CountdownMath.nextDate(leap, LocalDate.of(2027, 3, 1)))
        assertEquals(CountdownStatus.Today, CountdownMath.status(leap, at(2027, 2, 28)))
    }

    @Test
    fun dstChangeIsHandled() {
        // Passage à l'heure d'été en France le 28 mars 2027 : la veille à midi, 23 h restent jusqu'à midi.
        val e = event("2027-03-28", "12:00")
        assertEquals(CountdownStatus.Hours(23), CountdownMath.status(e, at(2027, 3, 27, 12)))
        // Jours calendaires, indépendants des heures d'été / d'hiver.
        assertEquals(CountdownStatus.Days(3), CountdownMath.status(event("2027-03-30"), at(2027, 3, 27, 0, 0)))
    }

    @Test
    fun nextChangeIsTheNextBoundary() {
        val e = event("2026-11-13", "14:00")
        val now = at(2026, 11, 13, 13, 18)
        val occ = CountdownMath.occurrence(e, now)!!
        assertEquals(at(2026, 11, 13, 13, 19), CountdownMath.nextChange(occ, now))
        val far = CountdownMath.occurrence(event("2026-11-25"), at(2026, 11, 13))!!
        assertEquals(at(2026, 11, 14, 0, 0), CountdownMath.nextChange(far, at(2026, 11, 13)))
        val hours = CountdownMath.occurrence(e, at(2026, 11, 13, 9, 30))!!
        assertEquals(CountdownStatus.Hours(5), hours.status)
        assertEquals(at(2026, 11, 13, 10, 0), CountdownMath.nextChange(hours, at(2026, 11, 13, 9, 30)))
    }

    @Test
    fun progressSinceCreation() {
        val created = at(2026, 11, 3, 0, 0).toInstant().toEpochMilli()
        val occ = CountdownMath.occurrence(event("2026-11-23", createdAt = created), at(2026, 11, 13, 0, 0))!!
        assertEquals(0.5f, occ.progress, 0.01f)
    }

    @Test
    fun remindersTheDayBefore() {
        val s = CountdownSettings(events = listOf(event("2026-11-14", id = "a"), event("2026-11-20", id = "b")))
        assertTrue(CountdownMath.dueReminders(s, at(2026, 11, 13, 8)).isEmpty())
        val due = CountdownMath.dueReminders(s, at(2026, 11, 13, 9, 30))
        assertEquals(listOf("a"), due.map { it.event.id })
        val marked = s.copy(reminded = setOf(due.first().reminderKey))
        assertTrue(CountdownMath.dueReminders(marked, at(2026, 11, 13, 10)).isEmpty())
        assertTrue(CountdownMath.dueReminders(s.copy(remindDayBefore = false), at(2026, 11, 13, 10)).isEmpty())
        assertEquals(at(2026, 11, 13, 9, 0), CountdownMath.nextWake(s, at(2026, 11, 13, 8, 30)))
    }

    @Test
    fun pinningAndIdleEvent() {
        var s = CountdownSettings()
        s = CountdownPrefs.upsert(s, event("2026-12-25", id = "noel"))
        assertEquals("noel", s.pinnedId)
        s = CountdownPrefs.upsert(s, event("2026-11-20", id = "proche"))
        assertEquals("noel", s.pinnedId)
        val now = at(2026, 11, 13)
        assertEquals("noel", s.idleEvent(now)?.id)
        assertEquals("proche", s.copy(idleMode = CountdownIdleMode.NEXT).idleEvent(now)?.id)
        assertNull(s.copy(idleMode = CountdownIdleMode.OFF).idleEvent(now))
        s = CountdownPrefs.remove(s, "noel")
        assertEquals("proche", s.pinnedId)
        assertEquals(1, CountdownModule.build(s, now).size)
        assertTrue(CountdownModule.build(CountdownSettings(), now).isEmpty())
    }
}
