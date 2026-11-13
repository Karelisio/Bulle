package com.karelisio.bulle.modules.alerts

import android.os.SystemClock
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.modules.alerts.ui.StyledAlert

/**
 * Détecte l'APPARITION des alertes pour jouer leur retour (vibration / son) une seule fois :
 * une clé déjà présente à l'émission précédente est une simple mise à jour, rien n'est rejoué.
 * Une alerte « ancienne » (redémarrage du module, alerte persistante retrouvée) n'est pas rejouée.
 */
class AlertArrivals(private val freshMs: Long = FRESH_MS) {
    private var previous: Set<String> = emptySet()

    /** Styles des alertes nouvellement apparues dans [contents] qui demandent un retour. */
    fun onContents(contents: List<IslandContent>, now: Long = SystemClock.elapsedRealtime()): List<AlertStyle> {
        val arrivals = contents.filter { it.key !in previous && now - it.postedAt in 0..freshMs }
        previous = contents.mapTo(HashSet()) { it.key }
        return arrivals.mapNotNull { (it as? StyledAlert)?.style?.takeIf(AlertStyle::hasFeedback) }
    }

    companion object {
        /** Au-delà, l'alerte n'est plus « en train d'apparaître ». */
        const val FRESH_MS = 5_000L
    }
}
