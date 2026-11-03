package com.karelisio.bulle.modules.live

import java.util.Locale
import kotlin.math.abs

/** Formatage des durées affichées dans l'île (fonctions pures). */
object LiveFormat {

    /**
     * Durée au format « m:ss » sous une heure, « h:mm:ss » au-delà.
     * Les valeurs négatives (minuteur dépassé) sont préfixées d'un signe moins.
     */
    fun clock(millis: Long): String {
        val negative = millis < 0
        val totalSeconds = abs(millis) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val body = if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
        return if (negative && totalSeconds > 0) "−$body" else body
    }

    /** Pourcentage entier 0..100. */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100f).toInt()
}
