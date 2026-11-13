package com.karelisio.bulle.modules.countdown

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** État d'un compte à rebours, du plus lointain au jour J. */
sealed interface CountdownStatus {
    /** Plus de 24 h : « J-n » (jours calendaires). */
    data class Days(val days: Int) : CountdownStatus

    /** Dernières 24 h : heures restantes (arrondi supérieur). */
    data class Hours(val hours: Int) : CountdownStatus

    /** Dernière heure : minutes restantes (arrondi supérieur, 1 à 60). */
    data class Minutes(val minutes: Int) : CountdownStatus

    /** Jour J (à partir de l'heure de l'événement, jusqu'à minuit). */
    data object Today : CountdownStatus
}

/** Prochaine occurrence d'un événement, calculée pour un instant donné. */
data class CountdownOccurrence(
    val event: CountdownEvent,
    val date: LocalDate,
    val target: ZonedDateTime,
    val status: CountdownStatus,
    /** Avancement depuis la création (ou la dernière occurrence d'un anniversaire), 0..1. */
    val progress: Float,
) {
    val reminderKey: String get() = reminderKey(event, date)

    companion object {
        fun reminderKey(event: CountdownEvent, date: LocalDate) = "${event.id}@$date"
    }
}

/**
 * Calculs de dates du compte à rebours : fonctions pures (java.time, fuseau fourni), testables.
 * Récurrence annuelle : un 29 février tombe le 28 février les années non bissextiles.
 */
object CountdownMath {
    private const val HOUR_MS = 3_600_000L
    private const val MINUTE_MS = 60_000L

    /** Heure du rappel de la veille. */
    val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    fun parseTime(text: String?): LocalTime? = text?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** Date anniversaire de [base] pour l'année [year] (29 février → 28 février hors années bissextiles). */
    fun anniversary(base: LocalDate, year: Int): LocalDate =
        if (base.month == Month.FEBRUARY && base.dayOfMonth == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            base.withYear(year)
        }

    /** Prochaine date (aujourd'hui compris) de l'événement, ou null s'il est passé. */
    fun nextDate(event: CountdownEvent, today: LocalDate): LocalDate? {
        val base = parseDate(event.date) ?: return null
        if (!event.yearly || !base.isBefore(today)) return base.takeIf { !it.isBefore(today) }
        val thisYear = anniversary(base, today.year)
        return if (thisYear.isBefore(today)) anniversary(base, today.year + 1) else thisYear
    }

    /** Instant visé : la date à l'heure choisie, ou à minuit pour un événement d'une journée. */
    fun target(event: CountdownEvent, date: LocalDate, zone: ZoneId): ZonedDateTime =
        date.atTime(parseTime(event.time) ?: LocalTime.MIDNIGHT).atZone(zone)

    fun status(event: CountdownEvent, now: ZonedDateTime): CountdownStatus? {
        val today = now.toLocalDate()
        val date = nextDate(event, today) ?: return null
        return statusFor(date, target(event, date, now.zone), now)
    }

    private fun statusFor(date: LocalDate, target: ZonedDateTime, now: ZonedDateTime): CountdownStatus {
        val today = now.toLocalDate()
        if (date == today && !now.isBefore(target)) return CountdownStatus.Today
        val remaining = Duration.between(now, target).toMillis()
        return when {
            remaining > 24 * HOUR_MS -> CountdownStatus.Days(ChronoUnit.DAYS.between(today, date).toInt().coerceAtLeast(1))
            remaining > HOUR_MS -> CountdownStatus.Hours(ceilDiv(remaining, HOUR_MS).toInt())
            else -> CountdownStatus.Minutes(ceilDiv(remaining, MINUTE_MS).toInt().coerceIn(1, 60))
        }
    }

    fun occurrence(event: CountdownEvent, now: ZonedDateTime): CountdownOccurrence? {
        val date = nextDate(event, now.toLocalDate()) ?: return null
        val target = target(event, date, now.zone)
        val status = statusFor(date, target, now)
        var start = event.createdAt
        if (event.yearly) {
            val base = parseDate(event.date)
            if (base != null && date.year > base.year) {
                val previous = target(event, anniversary(base, date.year - 1), now.zone).toInstant().toEpochMilli()
                start = maxOf(start, previous)
            }
        }
        val end = target.toInstant().toEpochMilli()
        val nowMs = now.toInstant().toEpochMilli()
        val progress = if (status == CountdownStatus.Today || end <= start) 1f else ((nowMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        return CountdownOccurrence(event, date, target, status, progress)
    }

    /** Occurrences à venir (jour J compris), de la plus proche à la plus lointaine. */
    fun upcoming(events: List<CountdownEvent>, now: ZonedDateTime): List<CountdownOccurrence> =
        events.mapNotNull { occurrence(it, now) }.sortedBy { it.target.toInstant() }

    /**
     * Prochain instant où l'affichage de [occurrence] change (minuit, passage aux heures, aux
     * minutes, minute suivante, jour J). Permet de ne recomposer qu'au besoin.
     */
    fun nextChange(occurrence: CountdownOccurrence, now: ZonedDateTime): ZonedDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val target = occurrence.target
        val candidate = when (val s = occurrence.status) {
            CountdownStatus.Today -> midnight
            is CountdownStatus.Days -> minOf(midnight, target.minusHours(24))
            is CountdownStatus.Hours -> target.minusHours((s.hours - 1).toLong())
            is CountdownStatus.Minutes -> target.minusMinutes((s.minutes - 1).toLong())
        }
        return if (candidate.isAfter(now)) candidate else now.plusMinutes(1)
    }

    /** Rappels de la veille à montrer maintenant (pas encore montrés, à partir de [REMINDER_TIME]). */
    fun dueReminders(settings: CountdownSettings, now: ZonedDateTime): List<CountdownOccurrence> {
        if (!settings.remindDayBefore) return emptyList()
        val tomorrow = now.toLocalDate().plusDays(1)
        if (now.toLocalTime().isBefore(REMINDER_TIME)) return emptyList()
        return upcoming(settings.events, now).filter { it.date == tomorrow && it.reminderKey !in settings.reminded }
    }

    /** Prochain réveil utile pour l'ensemble des événements (affichage et rappels). */
    fun nextWake(settings: CountdownSettings, now: ZonedDateTime): ZonedDateTime {
        val occurrences = upcoming(settings.events, now)
        var next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        occurrences.forEach { next = minOf(next, nextChange(it, now)) }
        if (settings.remindDayBefore && occurrences.any { it.date == now.toLocalDate().plusDays(1) }) {
            val reminder = now.toLocalDate().atTime(REMINDER_TIME).atZone(now.zone)
            if (reminder.isAfter(now)) next = minOf(next, reminder)
        }
        return next
    }

    private fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)
}
