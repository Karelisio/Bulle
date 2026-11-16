package com.karelisio.bulle.modules.volet.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.karelisio.bulle.island.IslandLog

/**
 * Relais invisible du choix de la sortie audio. Le sélecteur système (MediaRouter2,
 * Android 14+) n'accepte que l'appli au premier plan : depuis l'île ou le Volet (overlays),
 * il renvoie « false ». Cette activité transparente amène Bulle au premier plan le temps
 * d'ouvrir le sélecteur, puis se ferme aussitôt (le sélecteur est une fenêtre du système).
 */
class OutputSwitchActivity : Activity() {

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransitionCompat()
    }

    override fun onResume() {
        super.onResume()
        if (done) return
        done = true
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        val shown = MediaOutput.showRouterSwitcher(this)
        IslandLog.log("Sortie audio : MediaRouter2 (premier plan) → $shown")
        if (!shown) MediaOutput.openFallback(this, pkg) { startActivity(it) }
        finish()
        overridePendingTransitionCompat()
    }

    @Suppress("DEPRECATION")
    private fun overridePendingTransitionCompat() = overridePendingTransition(0, 0)

    companion object {
        const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, OutputSwitchActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
