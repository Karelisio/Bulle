package com.karelisio.bulle.system

import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.modules.volet.history.NotificationHistory
import com.karelisio.bulle.modules.volet.settings.VoletReliability
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.delay

/** Surveillance périodique : relance le maintien et, si possible, l'accessibilité. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // Processus qui vient d'être (re)lancé : le système est peut-être encore en train de relier les services.
        val processAge = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        val enabled = Reliability.isAccessibilityEnabled(ctx)
        var repaired = false
        when {
            // Coupé par le système : on le recoche (si WRITE_SECURE_SETTINGS).
            !enabled -> repaired = Reliability.reenableAccessibility(ctx)
            // Coché mais pas relié : on force une reliaison.
            !IslandAccessibilityService.running.value && processAge >= STARTUP_GRACE_MS ->
                repaired = Reliability.reenableAccessibility(ctx, forceRebind = true)
        }
        // Accès aux notifications accordé mais listener non relié : on demande une reliaison.
        if (NotificationRepository.isAccessGranted(ctx) && !NotificationRepository.connected.value) {
            runCatching { NotificationListenerService.requestRebind(NotificationRepository.component(ctx)) }
            repaired = true
        }
        KeepAliveService.start(ctx)
        checkVolet(ctx, waitForRebind = repaired, processAge = processAge)
        return Result.success()
    }

    /**
     * Volet activé mais service tué ou désactivé (MagicOS) : alerte « Réactiver ».
     * On laisse d'abord au système le temps de relier les services après une réparation.
     */
    private suspend fun checkVolet(ctx: Context, waitForRebind: Boolean, processAge: Long) {
        val volet = runCatching { SettingsRepository.current(ctx).volet }.getOrNull() ?: return
        if (volet.enabled && volet.historyEnabled) NotificationHistory.purge(ctx)
        if (volet.enabled) {
            // Pas de fausse alerte juste après le démarrage ou une réparation.
            val wait = maxOf(
                if (waitForRebind) REBIND_GRACE_MS else 0L,
                VoletReliability.graceRemaining(processAge, STARTUP_GRACE_MS),
            )
            if (wait > 0) delay(wait)
        }
        VoletReliability.notifyIfNeeded(ctx, VoletReliability.issues(VoletReliability.currentState(ctx, volet.enabled)))
    }

    private companion object {
        const val REBIND_GRACE_MS = 10_000L
        const val STARTUP_GRACE_MS = 30_000L
    }
}
