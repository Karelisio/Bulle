package com.karelisio.bulle.modules.volume

import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/**
 * Module « Volume » : curseur de volume dans l'île à la place du panneau système.
 * Désactivé par défaut (il intercepte les touches volume via l'accessibilité).
 */
object VolumeModule : IslandModule {
    const val ID = "volume"
    override val id = ID
    override val titleRes = R.string.module_volume
    override val enabledByDefault = false

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> =
        combine(
            VolumeKeys.overlay,
            VolumePrefs.data.onStart { VolumePrefs.ensureLoaded(env.context) },
        ) { overlay, prefs ->
            listOfNotNull(overlay?.let { VolumeContent(it, prefs.timeoutMs) })
        }
}
