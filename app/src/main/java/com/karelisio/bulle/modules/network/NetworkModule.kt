package com.karelisio.bulle.modules.network

import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Module « Réseau » : débit instantané pendant les gros transferts (contenu compact de basse
 * priorité) et consommation de données du jour, calculée localement sans permission spéciale.
 * Désactivé par défaut.
 *
 * Économie : le cumul journalier est événementiel (changement de réseau, écran allumé/éteint) ;
 * le débit n'est échantillonné (1 s) que si le compact est activé ET l'écran allumé, ou
 * pendant qu'une vue étendue / l'écran de réglages l'affiche.
 */
object NetworkModule : IslandModule {
    const val ID = "network"
    override val id = ID
    override val titleRes = R.string.network_module_title
    override val enabledByDefault = false

    /** Relevé du cumul pendant l'échantillonnage (en plus des événements). */
    private const val CHECKPOINT_MS = 60_000L

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = env.settings
        .map { NetworkSettingsBinding.read(it) }
        .distinctUntilChanged()
        .transformLatest { emitAll(run(env, it)) }

    private fun run(env: ModuleEnv, settings: NetworkSettings): Flow<List<IslandContent>> = channelFlow {
        val ctx = env.context.applicationContext
        val kind = MutableStateFlow(NetworkProbe.connection(ctx).kind)
        send(emptyList())
        // Cumul journalier : un relevé à chaque changement de connexion et d'état de l'écran.
        launch {
            NetworkProbe.connections(ctx).collect {
                NetworkUsageStore.checkpoint(ctx, kind.value) // l'intervalle écoulé appartient à l'ancienne connexion
                kind.value = it.kind
            }
        }
        val screen = NetworkProbe.screenOn(ctx)
        if (!settings.showCompact) {
            screen.collect { NetworkUsageStore.checkpoint(ctx, kind.value) }
            awaitCancellation()
        }
        screen.collectLatest { on ->
            NetworkUsageStore.checkpoint(ctx, kind.value)
            send(emptyList())
            if (!on) return@collectLatest
            val gate = HeavyTrafficGate(settings.thresholdBytesPerS)
            val suffixes = ctx.resources.getStringArray(
                if (settings.unit == RateUnit.BITS) R.array.network_units_bits else R.array.network_units_bytes,
            ).toList()
            val postedAt = SystemClock.elapsedRealtime()
            var lastCheckpoint = postedAt
            var shown = false
            combine(NetworkMonitor.snapshots(ctx), NetworkMonitor.viewers) { s, v -> s to v }.collect { (snap, viewers) ->
                val now = SystemClock.elapsedRealtime()
                val visible = gate.update(snap.total, now, pinned = viewers > 0)
                if (visible) {
                    send(listOf(NetworkRateContent(snap, settings.unit, postedAt, suffixes)))
                } else if (shown) {
                    send(emptyList())
                }
                shown = visible
                if (now - lastCheckpoint >= CHECKPOINT_MS) {
                    lastCheckpoint = now
                    NetworkUsageStore.checkpoint(ctx, kind.value)
                }
            }
        }
    }
}
