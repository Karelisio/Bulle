package com.karelisio.bulle.system

import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.ui.MainActivity
import com.karelisio.bulle.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Un module activable depuis une tuile (Réglages rapides du système et Volet). */
enum class ModuleSwitch(
    @param:StringRes val labelRes: Int,
    val route: String,
    val read: (BulleSettings) -> Boolean,
    val write: (BulleSettings, Boolean) -> BulleSettings,
) {
    HALO(R.string.halo_title, Routes.HALO, { it.halo.enabled }, { s, on -> s.copy(halo = s.halo.copy(enabled = on)) }),
    EDGES(R.string.edges_title, Routes.EDGES, { it.edges.enabled }, { s, on -> s.copy(edges = s.edges.copy(enabled = on)) }),
    STANDBY(R.string.standby_title, Routes.STANDBY, { it.standby.enabled }, { s, on -> s.copy(standby = s.standby.copy(enabled = on)) });

    /** Bascule le module (écriture des réglages en arrière-plan). */
    fun toggle(context: Context, on: Boolean) {
        val app = context.applicationContext
        MainScope().launch {
            SettingsRepository.update(app) { write(it, on) }
            IslandLog.log("Tuile ${app.getString(labelRes)} : " + if (on) "activé" else "désactivé")
        }
    }

    /** Ouvre l'écran de réglages du module. */
    fun settingsIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    companion object {
        /** Module d'une tuile du système (préférences de tuile : appui long). */
        fun ofTileClass(className: String?): ModuleSwitch? = when (className) {
            HaloTileService::class.java.name -> HALO
            EdgesTileService::class.java.name -> EDGES
            StandbyTileService::class.java.name -> STANDBY
            else -> null
        }
    }
}

/** Tuile Réglages rapides d'un module : un toucher l'active ou le coupe, l'appui long ouvre ses réglages. */
abstract class ModuleTileService(private val module: ModuleSwitch) : TileService() {
    private var scope: CoroutineScope? = null
    private var enabled = false

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = MainScope().apply {
            launch {
                SettingsRepository.flow(this@ModuleTileService).map(module.read).distinctUntilChanged().collect { on ->
                    enabled = on
                    qsTile?.apply {
                        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        label = getString(module.labelRes)
                        subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
                        updateTile()
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        module.toggle(this, !enabled)
    }
}

class HaloTileService : ModuleTileService(ModuleSwitch.HALO)
class EdgesTileService : ModuleTileService(ModuleSwitch.EDGES)
class StandbyTileService : ModuleTileService(ModuleSwitch.STANDBY)
