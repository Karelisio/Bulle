package com.karelisio.bulle.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Redémarrage au boot et après mise à jour de l'appli. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                if (!Reliability.isAccessibilityEnabled(context)) Reliability.reenableAccessibility(context)
                Reliability.ensureRunning(context)
            }
        }
    }
}
