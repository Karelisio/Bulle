package com.karelisio.bulle.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.karelisio.bulle.system.Reliability

/** Écoute les notifications (et donne accès aux sessions média). */
class BulleNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.listener = this
        val all = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        NotificationRepository.onConnected(all, packageName)
        Reliability.ensureRunning(this)
    }

    override fun onListenerDisconnected() {
        NotificationRepository.listener = null
        NotificationRepository.onDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        NotificationRepository.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null || sbn.packageName == packageName) return
        NotificationRepository.onRemoved(sbn, reason)
    }
}
