package com.karelisio.bulle.modules.calendar

/** Une occurrence d'événement lue dans CalendarContract.Instances. */
data class CalendarEvent(
    /** Identifiant de l'occurrence (Instances._ID). */
    val instanceId: Long,
    /** Identifiant de l'événement (Events._ID), pour l'ouvrir dans l'agenda. */
    val eventId: Long,
    val title: String,
    val location: String?,
    val begin: Long,
    val end: Long,
    val allDay: Boolean = false,
    /** Couleur ARGB de l'événement ou de son agenda (0 = aucune). */
    val color: Int = 0,
)

/** Ce que l'île doit montrer pour l'agenda à un instant donné. */
data class CalendarPlan(
    /** Événement dont le rappel est en cours (fenêtre [début − délai, début[). */
    val reminder: CalendarEvent?,
    /** Événement en cours (si l'option est active et qu'aucun rappel n'est prioritaire). */
    val ongoing: CalendarEvent?,
    /** Prochain instant (epoch ms) où le plan change ; null = rien de prévu dans l'horizon. */
    val nextChangeAt: Long?,
)

/** Logique pure de l'agenda : choix du prochain événement, moment du rappel. */
object CalendarPlanner {
    const val MINUTE = 60_000L
    const val HORIZON_MS = 24 * 60 * MINUTE

    /** Événements utilisables : pas « journée entière », titre non vide, durée cohérente. */
    private fun usable(events: List<CalendarEvent>) = events.filter { !it.allDay && it.end >= it.begin }

    /** Prochain événement qui commence après [now] dans les 24 h (le plus tôt, puis le plus court). */
    fun nextEvent(events: List<CalendarEvent>, now: Long, horizonMs: Long = HORIZON_MS): CalendarEvent? =
        usable(events)
            .filter { it.begin > now && it.begin <= now + horizonMs }
            .minWithOrNull(compareBy<CalendarEvent> { it.begin }.thenBy { it.end }.thenBy { it.instanceId })

    /** Événement en cours à [now] (celui qui finit le plus tôt s'il y en a plusieurs). */
    fun currentEvent(events: List<CalendarEvent>, now: Long): CalendarEvent? =
        usable(events)
            .filter { it.begin <= now && now < it.end }
            .minWithOrNull(compareBy<CalendarEvent> { it.end }.thenBy { it.instanceId })

    /** Instant du rappel d'un événement. */
    fun reminderAt(event: CalendarEvent, minutesBefore: Int): Long = event.begin - minutesBefore * MINUTE

    /** Plan complet à [now]. */
    fun plan(events: List<CalendarEvent>, now: Long, settings: CalendarSettings): CalendarPlan {
        val minutes = settings.effectiveReminderMinutes
        val next = nextEvent(events, now)
        val reminder = next?.takeIf { now >= reminderAt(it, minutes) }
        val ongoing = if (settings.showOngoing && reminder == null) currentEvent(events, now) else null

        // Prochains instants où quelque chose change : un rappel s'ouvre, un événement commence ou finit.
        val candidates = buildList {
            usable(events).forEach { e ->
                add(reminderAt(e, minutes))
                add(e.begin)
                add(e.end)
            }
        }
        val nextChangeAt = candidates.filter { it > now }.minOrNull()?.takeIf { it <= now + HORIZON_MS }
        return CalendarPlan(reminder, ongoing, nextChangeAt)
    }

    /** Minutes restantes, arrondies au supérieur (« dans 1 min » jusqu'au début). */
    fun minutesUntil(target: Long, now: Long): Long = ((target - now).coerceAtLeast(0) + MINUTE - 1) / MINUTE
}
