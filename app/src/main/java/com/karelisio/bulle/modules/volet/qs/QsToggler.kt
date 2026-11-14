package com.karelisio.bulle.modules.volet.qs

import android.content.Intent
import com.karelisio.bulle.modules.volet.VoletHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Exécute le toucher et l'appui long d'une tuile selon la méthode choisie par [ToggleChooser]. */
internal class QsToggler(private val host: VoletHost) {
    companion object {
        /** Portée propre : la commande aboutit même si le volet se ferme entre-temps. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private val context get() = host.context

    /** Méthode qu'utiliserait un toucher (lue à chaque fois : droits et Shizuku changent). */
    fun methodFor(tile: QsTile): ToggleMethod = ToggleChooser.choose(tile, context, ShizukuShell.status())

    fun tap(tile: QsTile, state: TileState) {
        val enable = !state.active
        when (val method = methodFor(tile)) {
            ToggleMethod.ACTION -> tile.action?.let(host::runAction)
            ToggleMethod.DIRECT -> runCatching { tile.direct?.set(context, enable) }
            ToggleMethod.SHIZUKU -> {
                val command = tile.shizukuCommand(enable) ?: return
                // La demande de permission Shizuku est une activité : le volet (overlay) la masquerait.
                if (ShizukuShell.status() == ShizukuStatus.NEEDS_PERMISSION) host.close()
                scope.launch {
                    // Échec (refus, Shizuku arrêté…) : repli sur le panneau ou la page système.
                    if (ShizukuShell.run(context, command) == null) {
                        open(tile, ToggleChooser.fallback(tile.direct != null, tile.panelIntent() != null))
                    }
                }
            }
            else -> open(tile, method)
        }
    }

    fun longPress(tile: QsTile) {
        tile.longPressIntent(context)?.let(::start)
    }

    private fun open(tile: QsTile, method: ToggleMethod) {
        val intent = when (method) {
            ToggleMethod.GRANT -> tile.direct?.grantIntent(context)
            ToggleMethod.PANEL -> tile.panelIntent()
            else -> null
        } ?: tile.settingsIntent(context)
        intent?.let(::start)
    }

    private fun start(intent: Intent) = host.startActivity(intent)
}
