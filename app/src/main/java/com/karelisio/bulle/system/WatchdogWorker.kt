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
        val enabled = Reliability.isAccessibilityEnabled(ctx)
        var repaired = false
        when {
            // Coupé par le système : on le recoche (si WRITE_SECURE_SETTINGS).
            !enabled -> repaired = Reliability.reenableAccessibility(ctx)
            // Coché mais pas relié : on force une reliaison.
            !IslandAccessibilityService.running.value ->
                repaired = Reliability.reenableAccessibility(ctx, forceRebind = true)
        }
        // Accès aux notifications accordé mais listener non relié : on demande une reliaison.
        if (NotificationRepository.isAccessGranted(ctx) && !NotificationRepository.connected.value) {
            runCatching { NotificationListenerService.requestRebind(NotificationRepository.component(ctx)) }
            repaired = true
        }
        KeepAliveService.start(ctx)
        checkVolet(ctx, waitForRebind = repaired)
        return Result.success()
    }

    /**
     * Volet activé mais service tué ou désactivé (MagicOS) : alerte « Réactiver ».
     * On laisse d'abord au système le temps de relier les services après une réparation.
     */
    private suspend fun checkVolet(ctx: Context, waitForRebind: Boolean) {
        val volet = runCatching { SettingsRepository.current(ctx).volet }.getOrNull() ?: return
        if (volet.enabled && volet.historyEnabled) NotificationHistory.purge(ctx)
        if (volet.enabled && waitForRebind) delay(REBIND_GRACE_MS)
        VoletReliability.notifyIfNeeded(ctx, VoletReliability.issues(VoletReliability.currentState(ctx, volet.enabled)))
    }

    private companion object {
        const val REBIND_GRACE_MS = 10_000L
    }
}
