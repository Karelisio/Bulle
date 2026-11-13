package com.karelisio.bulle.modules.external

import java.util.Locale

/** Calculs purs des modèles `countdown` et `chronometer` (heures murales, en ms). */
object ExternalTime {
    private const val SECOND = 1000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Temps restant avant [targetTime] (jamais négatif). */
    fun remaining(targetTime: Long, now: Long): Long = (targetTime - now).coerceAtLeast(0)

    /** Temps écoulé depuis [startTime] (jamais négatif). */
    fun elapsed(startTime: Long, now: Long): Long = (now - startTime).coerceAtLeast(0)

    /**
     * Avancement 0..1 d'un compte à rebours dont on connaît le début ([startTime]),
     * ou null s'il est inconnu.
     */
    fun countdownFraction(startTime: Long?, targetTime: Long, now: Long): Float? {
        val start = startTime ?: return null
        val span = targetTime - start
        if (span <= 0) return null
        return ((now - start).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Durée lisible : « 4:09 », « 1:05:09 », puis au-delà d'un jour « 3 j 04 h » ([dayUnit] = « j »,
     * [hourUnit] = « h », traduits par l'appelant).
     */
    fun format(ms: Long, dayUnit: String = "j", hourUnit: String = "h"): String {
        val v = ms.coerceAtLeast(0)
        val days = v / DAY
        val hours = (v % DAY) / HOUR
        val minutes = (v % HOUR) / MINUTE
        val seconds = (v % MINUTE) / SECOND
        return when {
            days > 0 -> String.format(Locale.ROOT, "%d %s %02d %s", days, dayUnit, hours, hourUnit)
            hours > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Intervalle de rafraîchissement adapté : chaque seconde sous un jour, chaque minute au-delà. */
    fun tickMs(ms: Long): Long = if (ms >= DAY) MINUTE else SECOND
}
