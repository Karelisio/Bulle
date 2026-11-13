package com.karelisio.bulle.modules.network

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

/** Débits lissés (octets/s), historique des 60 dernières secondes et connexion active. */
data class NetworkSnapshot(
    val rx: Double = 0.0,
    val tx: Double = 0.0,
    val history: List<RatePoint> = emptyList(),
    val connection: ConnectionInfo = ConnectionInfo(),
) {
    val total: Double get() = rx + tx
}

/**
 * Échantillonneur partagé du débit : relève TrafficStats chaque seconde UNIQUEMENT tant qu'au
 * moins un abonné écoute (vue étendue ouverte, écran de réglages visible, ou compact actif
 * écran allumé). Sans abonné, la boucle s'arrête : aucune scrutation.
 */
object NetworkMonitor {

    private const val PERIOD_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var shared: SharedFlow<NetworkSnapshot>? = null

    private val _viewers = MutableStateFlow(0)

    /** Nombre de vues étendues ouvertes : le compact reste affiché tant qu'on le regarde. */
    val viewers: StateFlow<Int> = _viewers.asStateFlow()

    fun addViewer() = _viewers.update { it + 1 }
    fun removeViewer() = _viewers.update { (it - 1).coerceAtLeast(0) }

    fun snapshots(context: Context): Flow<NetworkSnapshot> = shared ?: synchronized(this) {
        shared ?: sample(context.applicationContext)
            // Un court délai évite de relancer la boucle quand un abonné en remplace un autre.
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_500), replay = 1)
            .also { shared = it }
    }

    private fun sample(context: Context): Flow<NetworkSnapshot> = combine(rates(), NetworkProbe.connections(context)) { r, c ->
        r.copy(connection = c)
    }

    private fun rates(): Flow<NetworkSnapshot> = flow {
        val rxSmooth = RateSmoother()
        val txSmooth = RateSmoother()
        var history = emptyList<RatePoint>()
        var lastRx = NetworkProbe.rxBytes()
        var lastTx = NetworkProbe.txBytes()
        var lastAt = SystemClock.elapsedRealtime()
        emit(NetworkSnapshot())
        while (true) {
            delay(PERIOD_MS)
            val rxBytes = NetworkProbe.rxBytes()
            val txBytes = NetworkProbe.txBytes()
            val now = SystemClock.elapsedRealtime()
            val rx = rxSmooth.push(rateBetween(lastRx, lastAt, rxBytes, now) ?: continue)
            val tx = txSmooth.push(rateBetween(lastTx, lastAt, txBytes, now) ?: 0.0)
            lastRx = rxBytes
            lastTx = txBytes
            lastAt = now
            history = history.pushPoint(RatePoint(rx, tx))
            emit(NetworkSnapshot(rx, tx, history))
        }
    }
}
