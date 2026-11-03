package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.alerts.sources.AirplaneSource
import com.karelisio.bulle.modules.alerts.sources.AlarmSource
import com.karelisio.bulle.modules.alerts.sources.BatterySource
import com.karelisio.bulle.modules.alerts.sources.BluetoothSource
import com.karelisio.bulle.modules.alerts.sources.ClipboardSource
import com.karelisio.bulle.modules.alerts.sources.DndSource
import com.karelisio.bulle.modules.alerts.sources.PackageSource
import com.karelisio.bulle.modules.alerts.sources.ScreenshotSource
import com.karelisio.bulle.modules.alerts.sources.TorchSource
import com.karelisio.bulle.modules.alerts.sources.WifiSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Alertes système ponctuelles : batterie, charge, Bluetooth, Wi-Fi, alarme, lampe torche,
 * mode avion, Ne pas déranger, captures d'écran, applis installées, presse-papiers.
 *
 * Chaque [AlertSource] ne tourne que si son alerte est cochée : décocher une alerte
 * désinscrit immédiatement ses écouteurs système, sans toucher aux autres.
 */
object AlertsModule : IslandModule {
    const val ID = "alerts"

    override val id = ID
    override val titleRes = R.string.alerts_module_title

    val sources: List<AlertSource> = listOf(
        BatterySource,
        BluetoothSource,
        WifiSource,
        AlarmSource,
        TorchSource,
        AirplaneSource,
        DndSource,
        ScreenshotSource,
        PackageSource,
        ClipboardSource,
    )

    override fun isEnabled(settings: BulleSettings): Boolean =
        (settings.modules[id] ?: enabledByDefault) && settings.alerts.enabled

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = combine(
        sources.map { source ->
            env.settings
                .map { source.isEnabled(it.alerts) }
                .distinctUntilChanged()
                .flatMapLatest { on ->
                    if (!on) {
                        flowOf(emptyList())
                    } else {
                        source.contents(env)
                            .onStart { emit(emptyList()) }
                            // Une source en panne (permission retirée, service absent) n'arrête pas les autres.
                            .catch { emit(emptyList()) }
                    }
                }
        },
    ) { lists -> lists.flatMap { it } }
}
