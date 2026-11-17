package com.karelisio.bulle.modules.volet.background

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.StatusBarNotification
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.modules.volet.qs.ShizukuShell
import com.karelisio.bulle.modules.volet.qs.ShizukuStatus

/** Une notification de service au premier plan, réduite à ce qui sert (logique pure). */
data class ServiceNotif(
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val contentIntent: PendingIntent? = null,
)

/** Une appli qui tourne en fond (au moins un service au premier plan). */
data class BackgroundApp(
    val packageName: String,
    /** Textes de ses services (« Synchronisation… »), sans doublon. */
    val details: List<String>,
    /** Depuis quand (plus ancienne notification, ms). */
    val since: Long,
    val contentIntent: PendingIntent?,
)

/**
 * Applis en fond, comme la pastille du volet de MagicOS / le gestionnaire de tâches d'Android :
 * reconstruites à partir des notifications de service au premier plan (sans root). Avec Shizuku,
 * « Arrêter » force réellement l'arrêt ; sinon la fiche système de l'appli s'ouvre.
 */
object BackgroundApps {

    /** Regroupe par appli (logique pure, testée), les plus anciennes d'abord ; [own] exclu. */
    fun group(notifs: List<ServiceNotif>, own: String): List<BackgroundApp> =
        notifs.filter { it.packageName != own }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                BackgroundApp(
                    packageName = pkg,
                    details = list.flatMap { listOfNotNull(it.title, it.text) }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3),
                    since = list.minOf { it.postTime },
                    contentIntent = list.firstNotNullOfOrNull { it.contentIntent },
                )
            }
            .sortedBy { it.since }

    fun isForegroundService(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0

    fun fromActive(active: Collection<StatusBarNotification>, own: String): List<BackgroundApp> = group(
        active.filter(::isForegroundService).map { sbn ->
            val x = sbn.notification.extras
            ServiceNotif(
                sbn.packageName,
                x?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                x?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                sbn.postTime,
                sbn.notification.contentIntent,
            )
        },
        own,
    )

    fun canForceStop(): Boolean = ShizukuShell.status() != ShizukuStatus.UNAVAILABLE

    /** Arrête l'appli : forçage via Shizuku si possible, sinon fiche système (bouton « Forcer l'arrêt »). */
    suspend fun stop(context: Context, packageName: String): Boolean {
        if (canForceStop() && ShizukuShell.ensurePermission()) {
            val ok = ShizukuShell.run(context, "am force-stop $packageName") != null
            IslandLog.log("Volet : arrêt forcé de $packageName " + if (ok) "réussi" else "refusé")
            if (ok) return true
        }
        openDetails(context, packageName)
        return false
    }

    fun detailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openDetails(context: Context, packageName: String) {
        runCatching { context.startActivity(detailsIntent(packageName)) }
    }
}
