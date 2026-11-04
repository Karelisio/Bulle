package com.karelisio.bulle.modules.volet.history

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import android.util.Log
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Contenu utile d'une notification, extrait pour l'historique. */
data class HistoryCandidate(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val channelId: String?,
    val ongoing: Boolean,
    val groupSummary: Boolean,
)

/** Logique pure de l'historique (testable sans appareil). */
object HistoryLogic {

    /** Durée de conservation. */
    val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(7)

    /** Intervalle minimal entre deux purges déclenchées par une insertion. */
    val PURGE_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(6)

    fun cutoff(now: Long): Long = now - RETENTION_MS

    /**
     * Faut-il enregistrer [c] ? Non si l'historique est coupé, si la notification est en cours
     * (lecture, minuteur, téléchargement…), si c'est un simple résumé de groupe, si elle est vide,
     * ou si c'est une mise à jour identique à la dernière entrée de la même clé.
     */
    fun shouldRecord(enabled: Boolean, c: HistoryCandidate, last: NotificationHistoryEntity?): Boolean {
        if (!enabled || c.ongoing || c.groupSummary) return false
        if (c.title.isBlank() && c.text.isBlank()) return false
        if (last != null && last.title == c.title && last.text == c.text) return false
        return true
    }

    /** Échappe les jokers de LIKE (la requête utilise ESCAPE '\'). */
    fun escapeLike(query: String): String =
        query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Regroupe des entrées (déjà triées, récentes d'abord) par jour local. */
    fun groupByDay(
        entries: List<NotificationHistoryEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<LocalDate, List<NotificationHistoryEntity>>> =
        entries.groupBy { Instant.ofEpochMilli(it.postedAt).atZone(zone).toLocalDate() }.toList()
}

/**
 * Enregistreur de l'historique des notifications du Volet. L'orchestrateur appelle
 * [start] depuis le service d'accessibilité ; rien n'est enregistré tant que le Volet
 * et `VoletSettings.historyEnabled` ne sont pas activés.
 */
object NotificationHistory {

    private const val TAG = "VoletHistory"

    @Volatile
    private var lastPurge = 0L

    fun dao(context: Context): NotificationHistoryDao = BulleDatabase.get(context).notificationHistoryDao()

    /**
     * Démarre l'écoute de [NotificationRepository.events] dans [scope]. Purge au démarrage,
     * puis au plus toutes les 6 h lors d'une insertion. Renvoie le Job (à annuler à l'arrêt).
     */
    fun start(context: Context, scope: CoroutineScope, settingsFlow: Flow<VoletSettings>): Job {
        val app = context.applicationContext
        return scope.launch(Dispatchers.IO) {
            val enabled = AtomicBoolean(false)
            launch { settingsFlow.collect { enabled.set(it.enabled && it.historyEnabled) } }
            launch { purge(app) }
            NotificationRepository.events.filterIsInstance<NotificationEvent.Posted>().collect { event ->
                if (!enabled.get() || event.sbn.packageName == app.packageName) return@collect
                runCatching { record(app, candidateOf(event.sbn)) }
                    .onFailure { Log.w(TAG, "Enregistrement impossible", it) }
            }
        }
    }

    /** Enregistre [c] si [HistoryLogic.shouldRecord] l'accepte. Renvoie vrai si une ligne a été ajoutée. */
    suspend fun record(context: Context, c: HistoryCandidate, now: Long = System.currentTimeMillis()): Boolean {
        val dao = dao(context)
        if (!HistoryLogic.shouldRecord(true, c, dao.latestFor(c.key))) return false
        dao.insert(
            NotificationHistoryEntity(
                notificationKey = c.key,
                packageName = c.packageName,
                appName = appLabel(context, c.packageName),
                title = c.title,
                text = c.text,
                postedAt = c.postedAt,
                channelId = c.channelId,
            ),
        )
        if (now - lastPurge > HistoryLogic.PURGE_INTERVAL_MS) purge(context, now)
        return true
    }

    /** Supprime les entrées de plus de 7 jours. */
    suspend fun purge(context: Context, now: Long = System.currentTimeMillis()) {
        lastPurge = now
        runCatching { dao(context).purgeOlderThan(HistoryLogic.cutoff(now)) }
            .onFailure { Log.w(TAG, "Purge impossible", it) }
    }

    suspend fun clear(context: Context) = dao(context).clear()

    fun candidateOf(sbn: StatusBarNotification): HistoryCandidate {
        val n = sbn.notification
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        return HistoryCandidate(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title.trim(),
            text = text.trim(),
            postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            channelId = n.channelId,
            ongoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    private fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}
