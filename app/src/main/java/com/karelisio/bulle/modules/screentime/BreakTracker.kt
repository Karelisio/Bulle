package com.karelisio.bulle.modules.screentime

/**
 * Suivi de l'utilisation continue de l'écran pour le rappel de pause (fonction pure, horloge
 * fournie par l'appelant : elapsedRealtime en production, valeurs fixes dans les tests).
 *
 * « Actif » = écran allumé ET déverrouillé. Seul le temps actif compte ; une pause écran éteint
 * d'au moins [PAUSE_RESET_MS] remet la séance à zéro. Après un rappel, le suivant arrive au bout
 * d'une nouvelle période de même durée si l'utilisation continue.
 */
data class BreakState(
    /** Début de la période active en cours, ou null si l'écran est éteint / verrouillé. */
    val activeSince: Long? = null,
    /** Temps actif cumulé de la séance avant [activeSince]. */
    val accumulatedMs: Long = 0,
    /** Écran éteint depuis (pour la remise à zéro), ou null. */
    val offSince: Long? = null,
    /** Rappels déjà montrés pendant la séance. */
    val reminders: Int = 0,
) {
    val isActive: Boolean get() = activeSince != null

    /** Écran allumé et déverrouillé. */
    fun active(now: Long, pauseResetMs: Long = PAUSE_RESET_MS): BreakState {
        if (activeSince != null) return this
        val off = offSince
        return if (off != null && now - off >= pauseResetMs) BreakState(activeSince = now) else copy(activeSince = now, offSince = null)
    }

    /** Écran éteint. */
    fun screenOff(now: Long): BreakState {
        val since = activeSince ?: return if (offSince == null) copy(offSince = now) else this
        return copy(activeSince = null, accumulatedMs = accumulatedMs + (now - since).coerceAtLeast(0), offSince = now)
    }

    /** Temps actif de la séance à [now]. */
    fun usedMs(now: Long): Long = accumulatedMs + (activeSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Délai avant le prochain rappel (0 = maintenant), ou null si l'écran n'est pas actif. */
    fun dueInMs(now: Long, thresholdMs: Long): Long? {
        if (activeSince == null || thresholdMs <= 0) return null
        return (thresholdMs * (reminders + 1) - usedMs(now)).coerceAtLeast(0)
    }

    fun reminded(): BreakState = copy(reminders = reminders + 1)

    companion object {
        /** Pause écran éteint qui remet la séance à zéro. */
        const val PAUSE_RESET_MS = 5 * 60_000L
    }
}
