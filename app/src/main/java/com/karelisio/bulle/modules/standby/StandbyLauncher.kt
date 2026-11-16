package com.karelisio.bulle.modules.standby

import android.content.Context
import android.content.Intent
import com.karelisio.bulle.island.IslandLog

/** Lancement de Veille (geste, tuile, test de l'écran Fiabilité, détection automatique). */
object StandbyLauncher {
    internal const val EXTRA_AUTO = "auto"

    /** Lance Veille tout de suite ; elle reste affichée jusqu'à un balayage vers le bas ou Retour. */
    fun launchNow(context: Context, reason: String) = launch(context, auto = false, reason = reason)

    /**
     * Lancement automatique (branché + horizontale) : Veille se ferme d'elle-même au débranchement
     * ou au retour à la verticale.
     */
    internal fun launchAuto(context: Context) = launch(context, auto = true, reason = "branché à l'horizontale")

    private fun launch(context: Context, auto: Boolean, reason: String): Boolean {
        val intent = Intent(context, StandbyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_AUTO, auto)
        return runCatching { context.startActivity(intent) }
            .onSuccess { IslandLog.log("Veille : lancement ($reason)") }
            .onFailure { IslandLog.log("Veille : lancement refusé (${it.javaClass.simpleName}) — fenêtres pop-up en arrière-plan ?") }
            .isSuccess
    }
}
