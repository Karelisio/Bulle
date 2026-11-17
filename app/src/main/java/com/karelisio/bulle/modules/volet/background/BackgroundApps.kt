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
    /** Lecture multimédia ou appel : jamais arrêtée par « Tout arrêter ». */
    val protected: Boolean = false,
)

/** Une appli qui tourne en fond (au moins un service au premier plan). */
data class BackgroundApp(
    val packageName: String,
    /** Textes de ses services (« Synchronisation… »), sans doublon. */
    val details: List<String>,
    /** Depuis quand (plus ancienne notification, ms). */
    val since: Long,
    val contentIntent: PendingIntent?,
    /** Lecture multimédia ou appel en cours : épargnée par « Tout arrêter ». */
    val protected: Boolean = false,
)

/**
 * Applis en fond, comme la pastille du volet de MagicOS / le gestionnaire de tâches d'Android :
 * reconstruites à partir des notifications de service au premier plan (sans root). Avec Shizuku,
 * « Arrêter » force réellement l'arrêt ; sinon la fiche système de l'appli s'ouvre.
 */
object BackgroundApps {

    /** Regroupe par appli (logique pure, testée), les plus anciennes d'abord ; [own] exclu. */
    fun group(notifs: List<ServiceNotif>, own: String, hidden: Set<String> = emptySet()): List<BackgroundApp> =
        notifs.filter { it.packageName != own && it.packageName !in hidden }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                BackgroundApp(
                    packageName = pkg,
                    details = list.flatMap { listOfNotNull(it.title, it.text) }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3),
                    since = list.minOf { it.postTime },
                    contentIntent = list.firstNotNullOfOrNull { it.contentIntent },
                    protected = list.any { it.protected },
                )
            }
            .sortedBy { it.since }

    fun isForegroundService(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0

    fun fromActive(active: Collection<StatusBarNotification>, own: String, hidden: Set<String> = emptySet()): List<BackgroundApp> = group(
        active.filter(::isForegroundService).map { sbn ->
            val x = sbn.notification.extras
            ServiceNotif(
                sbn.packageName,
                x?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                x?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                sbn.postTime,
                sbn.notification.contentIntent,
                protected = x?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                    sbn.notification.category == Notification.CATEGORY_CALL,
            )
        },
        own,
        hidden,
    )

    /** Applis visées par « Tout arrêter » : toutes sauf les protégées (logique pure). */
    fun stoppable(apps: List<BackgroundApp>): List<BackgroundApp> = apps.filterNot { it.protected }

    /** « depuis 2 h », « depuis 5 min » ; null si inconnu (logique pure). */
    fun sinceMinutes(since: Long, now: Long): Long? =
        if (since <= 0L || since == Long.MAX_VALUE || since > now) null else (now - since) / 60_000L

    /**
     * Paquets qui ont au moins un service en cours d'exécution, lus dans `dumpsys activity services`
     * (logique pure, testée). Exemple de ligne : `* ServiceRecord{1a2b u0 com.app/.SyncService}`.
     */
    fun parseServices(dump: String): Set<String> =
        Regex("""ServiceRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/""").findAll(dump).map { it.groupValues[1] }.toSet()

    /**
     * Toutes les applis avec un service en cours (Shizuku prêt, sans demande de permission) :
     * notifications de service au premier plan + services sans notification. Applis système exclues.
     */
    suspend fun withServices(context: Context, fromNotifications: List<BackgroundApp>, hidden: Set<String> = emptySet()): List<BackgroundApp> {
        if (ShizukuShell.status() != ShizukuStatus.READY) return fromNotifications
        val dump = ShizukuShell.run(context, "dumpsys activity services") ?: return fromNotifications
        val known = fromNotifications.mapTo(HashSet()) { it.packageName }
        val pm = context.packageManager
        val extra = parseServices(dump)
            .filter { it != context.packageName && it !in known && it !in hidden }
            .filter { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@filter false
                info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 ||
                    info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            }
            .map { BackgroundApp(it, emptyList(), Long.MAX_VALUE, null) }
        return fromNotifications + extra.sortedBy { it.packageName }
    }

    /** Shizuku prêt : la liste complète est disponible. */
    fun fullListAvailable(): Boolean = ShizukuShell.status() == ShizukuStatus.READY

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
