package com.karelisio.bulle.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Tuile Réglages rapides : active ou désactive l'île. */
class IslandTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = MainScope().apply {
            launch {
                combine(SettingsRepository.flow(this@IslandTileService), IslandAccessibilityService.running) { s, running ->
                    s.islandEnabled to running
                }.collect { (enabled, running) -> render(enabled, running) }
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
        if (!Reliability.isAccessibilityEnabled(this) && !Reliability.reenableAccessibility(this)) {
            openApp()
            return
        }
        MainScope().launch {
            SettingsRepository.update(this@IslandTileService) { it.copy(islandEnabled = !it.islandEnabled) }
        }
    }

    private fun render(enabled: Boolean, running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running && enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(
            when {
                !running -> R.string.tile_service_off
                enabled -> R.string.tile_on
                else -> R.string.tile_off
            },
        )
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
