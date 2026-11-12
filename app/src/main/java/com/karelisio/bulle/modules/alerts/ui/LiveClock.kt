package com.karelisio.bulle.modules.alerts.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.util.Date

/** Horloge pure : délai jusqu'au prochain changement d'affichage (minute pile, ou seconde pile). */
object ClockTicks {
    fun msUntilNextTick(nowMs: Long, withSeconds: Boolean): Long {
        val period = if (withSeconds) 1_000L else 60_000L
        return period - Math.floorMod(nowMs, period)
    }

    /** Le motif d'heure affiche-t-il les secondes ? (lettre « s » hors texte littéral entre apostrophes) */
    fun patternHasSeconds(pattern: String): Boolean {
        var quoted = false
        for (c in pattern) {
            if (c == '\'') quoted = !quoted else if (!quoted && c == 's') return true
        }
        return false
    }
}

/**
 * Heure courante au format du système, qui avance d'elle-même : réveil calé sur la minute pile
 * (ou la seconde si le format en affiche), sans recréer le contenu qui l'affiche.
 */
@Composable
fun rememberClockText(): String {
    val ctx = LocalContext.current
    val format = DateFormat.getTimeFormat(ctx)
    val withSeconds = (format as? java.text.SimpleDateFormat)?.toPattern()?.let(ClockTicks::patternHasSeconds) ?: false
    val text by produceState(format.format(Date()), format, withSeconds) {
        while (true) {
            value = format.format(Date())
            delay(ClockTicks.msUntilNextTick(System.currentTimeMillis(), withSeconds) + 5)
        }
    }
    return text
}
