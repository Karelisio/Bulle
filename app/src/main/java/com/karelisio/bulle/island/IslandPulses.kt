package com.karelisio.bulle.island

import android.os.SystemClock
import com.karelisio.bulle.core.island.IslandContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battements de l'île : publiés quand un nouveau contenu arrive et que l'île gonfle pour
 * l'afficher. Le Halo s'y cale pour démarrer au même instant (et laisse l'île seule vibrer).
 */
object IslandPulses {
    data class Pulse(
        val key: String,
        val moduleId: String,
        /** Clé de la notification d'origine, si le contenu en vient. */
        val notificationKey: String?,
        /** Instant du gonflement (elapsedRealtime). */
        val at: Long,
    )

    private val _latest = MutableStateFlow<Pulse?>(null)
    val latest: StateFlow<Pulse?> = _latest.asStateFlow()

    fun emit(content: IslandContent, now: Long = SystemClock.elapsedRealtime()) {
        _latest.value = Pulse(content.key, content.moduleId, content.notificationKey, now)
    }
}
