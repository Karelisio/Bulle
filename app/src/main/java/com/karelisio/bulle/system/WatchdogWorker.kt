package com.karelisio.bulle.system

import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.notifications.NotificationRepository

/** Surveillance périodique : relance le maintien et, si possible, l'accessibilité. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val enabled = Reliability.isAccessibilityEnabled(ctx)
        when {
            // Coupé par le système : on le recoche (si WRITE_SECURE_SETTINGS).
            !enabled -> Reliability.reenableAccessibility(ctx)
            // Coché mais pas relié : on force une reliaison.
            !IslandAccessibilityService.running.value -> Reliability.reenableAccessibility(ctx, forceRebind = true)
        }
        // Accès aux notifications accordé mais listener non relié : on demande une reliaison.
        if (NotificationRepository.isAccessGranted(ctx) && !NotificationRepository.connected.value) {
            runCatching { NotificationListenerService.requestRebind(NotificationRepository.component(ctx)) }
        }
        KeepAliveService.start(ctx)
        return Result.success()
    }
}
