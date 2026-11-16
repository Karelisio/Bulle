package com.karelisio.bulle.modules.volet.qs

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.system.ModuleSwitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Tuile du Volet qui active / coupe un module de Bulle (Halo, Bords, Veille). */
private class ModuleTile(
    private val module: ModuleSwitch,
    override val id: String,
    override val icon: ImageVector,
) : QsTile {
    override val label: Int get() = module.labelRes

    override val direct: DirectToggle = object : DirectToggle {
        override fun permitted(context: Context) = true
        override fun grantIntent(context: Context): Intent = module.settingsIntent(context)
        override fun set(context: Context, enable: Boolean) = module.toggle(context, enable)
    }

    override fun settingsIntent(context: Context): Intent = module.settingsIntent(context)

    override fun observe(context: Context): Flow<TileState> =
        SettingsRepository.flow(context).map { TileState(module.read(it)) }.distinctUntilChanged()
}

val HaloQsTile: QsTile = ModuleTile(ModuleSwitch.HALO, "halo", Icons.Rounded.BlurOn)
val EdgesQsTile: QsTile = ModuleTile(ModuleSwitch.EDGES, "edges", Icons.Rounded.VerticalSplit)
val StandbyQsTile: QsTile = ModuleTile(ModuleSwitch.STANDBY, "standby", Icons.Rounded.Nightlight)
