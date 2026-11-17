package com.karelisio.bulle.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.modules.volet.qs.ShizukuShell
import com.karelisio.bulle.modules.volet.qs.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bouton « Réactiver » de l'alerte « service coupé » : réactive l'accessibilité en un toucher
 * (WRITE_SECURE_SETTINGS déjà accordée, sinon accordée d'abord via Shizuku s'il est prêt).
 */
class ReenableReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!Reliability.canWriteSecureSettings(app) && ShizukuShell.status() == ShizukuStatus.READY) {
                    ShizukuShell.run(app, "pm grant ${app.packageName} android.permission.WRITE_SECURE_SETTINGS")
                }
                val ok = Reliability.reenableAccessibility(app, forceRebind = true)
                IslandLog.log("Réactivation depuis la notification : " + if (ok) "réussie" else "refusée")
                if (ok) app.getSystemService(NotificationManager::class.java)?.cancel(intent.getIntExtra(EXTRA_ID, 0))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.karelisio.bulle.action.REENABLE"
        const val EXTRA_ID = "id"

        /** Réactivation possible en un toucher. */
        fun available(context: Context): Boolean =
            Reliability.canWriteSecureSettings(context) || ShizukuShell.status() == ShizukuStatus.READY
    }
}
