package com.karelisio.bulle.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.karelisio.bulle.island.IslandAccessibilityService
import java.util.concurrent.TimeUnit

/** Tout ce qui garde l'île en vie : service de maintien, surveillance, réactivation. */
object Reliability {

    private const val TAG = "Reliability"
    private const val WATCHDOG = "bulle-watchdog"

    fun accessibilityComponent(context: Context) =
        ComponentName(context, IslandAccessibilityService::class.java)

    /** Le service est-il coché dans les réglages d'accessibilité ? */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val me = accessibilityComponent(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** WRITE_SECURE_SETTINGS accordée via adb ? */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Réactive le service d'accessibilité (nécessite WRITE_SECURE_SETTINGS).
     * Avec [forceRebind], le retire puis le remet pour forcer le système à le relier.
     */
    fun reenableAccessibility(context: Context, forceRebind: Boolean = false): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val cr = context.contentResolver
            val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val me = accessibilityComponent(context)
            val others = Settings.Secure.getString(cr, key).orEmpty()
                .split(':')
                .filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }
            if (forceRebind) Settings.Secure.putString(cr, key, others.joinToString(":"))
            Settings.Secure.putString(cr, key, (others + me.flattenToString()).joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Réactivation refusée", e)
            false
        }
    }

    /** Démarre le service de maintien et programme la surveillance. Idempotent. */
    fun ensureRunning(context: Context) {
        KeepAliveService.start(context)
        scheduleWatchdog(context)
    }

    fun scheduleWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
