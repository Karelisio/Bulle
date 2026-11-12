package com.karelisio.bulle.modules.volet.settings

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import com.karelisio.bulle.R
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.MainActivity

/** Problème de fiabilité qui empêche le Volet de fonctionner. */
enum class VoletIssue {
    /** Service d'accessibilité décoché (ou retiré par MagicOS). */
    ACCESSIBILITY_DISABLED,

    /** Coché mais tué : le système ne l'a pas relié. */
    ACCESSIBILITY_KILLED,

    /** Accès aux notifications retiré. */
    LISTENER_DISABLED,

    /** Accès accordé mais l'écoute n'est pas reliée. */
    LISTENER_DISCONNECTED,
}

/** Instantané de l'état des services dont dépend le Volet. */
data class VoletServiceState(
    val voletEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityRunning: Boolean,
    val listenerGranted: Boolean,
    val listenerConnected: Boolean,
)

/** Fiabilité du Volet face aux surcouches agressives (MagicOS) : diagnostic et alertes. */
object VoletReliability {

    const val CHANNEL = "volet_reliability"
    private const val NOTIFICATION_ID = 0x7E1E7

    /** Logique pure : rien si le Volet est éteint, sinon les problèmes dans l'ordre de gravité. */
    fun issues(s: VoletServiceState): List<VoletIssue> {
        if (!s.voletEnabled) return emptyList()
        return buildList {
            when {
                !s.accessibilityEnabled -> add(VoletIssue.ACCESSIBILITY_DISABLED)
                !s.accessibilityRunning -> add(VoletIssue.ACCESSIBILITY_KILLED)
            }
            when {
                !s.listenerGranted -> add(VoletIssue.LISTENER_DISABLED)
                !s.listenerConnected -> add(VoletIssue.LISTENER_DISCONNECTED)
            }
        }
    }

    /**
     * Délai laissé au système, au démarrage du processus, pour relier l'accessibilité et l'écoute
     * des notifications : avant, « tué » / « non relié » sont de faux positifs.
     */
    const val STARTUP_GRACE_MS = 5_000L

    /** Problème qui peut n'être qu'un état de démarrage (service coché / accès accordé, pas encore relié). */
    fun isTransient(issue: VoletIssue) =
        issue == VoletIssue.ACCESSIBILITY_KILLED || issue == VoletIssue.LISTENER_DISCONNECTED

    /** Temps restant avant de croire aux problèmes transitoires (logique pure). */
    fun graceRemaining(processAgeMs: Long, graceMs: Long = STARTUP_GRACE_MS): Long =
        (graceMs - processAgeMs).coerceAtLeast(0L)

    /** Problèmes à signaler : les transitoires seulement une fois le délai de démarrage écoulé. */
    fun reportable(issues: List<VoletIssue>, graceElapsed: Boolean): List<VoletIssue> =
        if (graceElapsed) issues else issues.filterNot(::isTransient)

    /** L'accessibilité est-elle en cause (sinon : l'écoute des notifications) ? */
    fun isAccessibility(issue: VoletIssue) =
        issue == VoletIssue.ACCESSIBILITY_DISABLED || issue == VoletIssue.ACCESSIBILITY_KILLED

    fun currentState(context: Context, voletEnabled: Boolean) = VoletServiceState(
        voletEnabled = voletEnabled,
        accessibilityEnabled = Reliability.isAccessibilityEnabled(context),
        accessibilityRunning = IslandAccessibilityService.running.value,
        listenerGranted = NotificationRepository.isAccessGranted(context),
        listenerConnected = NotificationRepository.connected.value,
    )

    /** Page système qui règle [issue]. */
    fun fixIntent(context: Context, issue: VoletIssue): Intent =
        if (isAccessibility(issue)) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    NotificationRepository.component(context).flattenToString(),
                )
        }

    /**
     * Tente une réparation automatique (accessibilité via WRITE_SECURE_SETTINGS), sinon
     * ouvre la page système concernée. Utilisé par la bannière.
     */
    fun fix(context: Context, issue: VoletIssue) {
        if (isAccessibility(issue) &&
            Reliability.reenableAccessibility(context, forceRebind = issue == VoletIssue.ACCESSIBILITY_KILLED)
        ) {
            return
        }
        val intent = fixIntent(context, issue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = if (isAccessibility(issue)) {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.vset_rel_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.vset_rel_channel_desc)
            },
        )
    }

    /** Après un balayage, on ne repose pas l'alerte du même problème avant ce délai. */
    const val SNOOZE_MS = 12 * 60 * 60 * 1000L

    private const val PREFS = "volet_reliability_alert"
    private const val KEY_POSTED = "posted"
    private const val KEY_DISMISSED_AT = "dismissedAt"
    private const val KEY_DISMISSED = "dismissed"

    /** Nature d'un problème : l'accessibilité ou l'écoute des notifications. */
    fun natureOf(issue: VoletIssue): String = if (isAccessibility(issue)) "accessibility" else "listener"

    /**
     * Mémoire de l'alerte entre deux passages de la surveillance.
     * @property posted problème affiché lors de notre dernier passage (null : rien d'affiché).
     * @property dismissedAt quand l'alerte a été balayée (0 : jamais).
     * @property dismissedNature nature du problème balayé.
     */
    data class AlertMemory(
        val posted: VoletIssue? = null,
        val dismissedAt: Long = 0L,
        val dismissedNature: String? = null,
    )

    enum class AlertAction {
        /** Poser l'alerte (elle sonne). */
        POST,
        /** Mettre à jour l'alerte affichée sans sonner (setOnlyAlertOnce). */
        UPDATE_SILENTLY,
        /** Ne rien faire (déjà affichée telle quelle, ou balayée récemment). */
        NONE,
        /** Retirer l'alerte : tout va bien. */
        CANCEL,
    }

    data class AlertDecision(val action: AlertAction, val memory: AlertMemory)

    /**
     * Logique pure de l'alerte de fiabilité.
     * - Tout va bien : on la retire et on oublie le balayage.
     * - Déjà affichée : rien, ou mise à jour silencieuse si le problème a changé.
     * - Affichée au passage précédent mais disparue : l'utilisateur l'a balayée (ou touchée) ;
     *   on ne la repose pas avant [SNOOZE_MS], sauf si le problème a changé de nature.
     */
    fun decide(issue: VoletIssue?, showing: Boolean, memory: AlertMemory, now: Long): AlertDecision {
        if (issue == null) return AlertDecision(AlertAction.CANCEL, AlertMemory())
        if (showing) {
            val action = if (memory.posted == issue) AlertAction.NONE else AlertAction.UPDATE_SILENTLY
            return AlertDecision(action, memory.copy(posted = issue))
        }
        val m = if (memory.posted != null) {
            AlertMemory(posted = null, dismissedAt = now, dismissedNature = natureOf(memory.posted))
        } else {
            memory
        }
        val snoozed = m.dismissedNature == natureOf(issue) && now - m.dismissedAt in 0 until SNOOZE_MS
        return if (snoozed) {
            AlertDecision(AlertAction.NONE, m)
        } else {
            AlertDecision(AlertAction.POST, m.copy(posted = issue))
        }
    }

    /** POST_NOTIFICATIONS n'est une autorisation d'exécution qu'à partir d'Android 13 (API 33). */
    fun needsRuntimePermission(sdk: Int): Boolean = sdk >= 33

    private fun readMemory(context: Context): AlertMemory {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AlertMemory(
            posted = p.getString(KEY_POSTED, null)?.let { name -> VoletIssue.entries.firstOrNull { it.name == name } },
            dismissedAt = p.getLong(KEY_DISMISSED_AT, 0L),
            dismissedNature = p.getString(KEY_DISMISSED, null),
        )
    }

    private fun writeMemory(context: Context, m: AlertMemory) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_POSTED, m.posted?.name)
            putLong(KEY_DISMISSED_AT, m.dismissedAt)
            putString(KEY_DISMISSED, m.dismissedNature)
        }
    }

    /**
     * Appelé par le WatchdogWorker : pose, met à jour ou retire l'alerte selon [issues].
     * Une seule notification, qui ne sonne qu'à sa première pose (voir [decide]).
     */
    fun notifyIfNeeded(context: Context, issues: List<VoletIssue>, now: Long = System.currentTimeMillis()) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val issue = issues.firstOrNull()
        val showing = runCatching { nm.activeNotifications.any { it.id == NOTIFICATION_ID } }.getOrDefault(false)
        val decision = decide(issue, showing, readMemory(context), now)
        when (decision.action) {
            AlertAction.CANCEL -> nm.cancel(NOTIFICATION_ID)
            AlertAction.NONE -> Unit
            AlertAction.POST, AlertAction.UPDATE_SILENTLY -> {
                // POST_NOTIFICATIONS n'existe qu'à partir d'Android 13 : avant, checkSelfPermission la dit refusée
                // et l'alerte n'était jamais postée (Android 12 / 12L).
                if (needsRuntimePermission(Build.VERSION.SDK_INT) &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                nm.notify(NOTIFICATION_ID, build(context, nm, requireNotNull(issue)))
            }
        }
        writeMemory(context, decision.memory)
    }

    private fun build(context: Context, nm: NotificationManager, issue: VoletIssue): Notification {
        ensureChannel(context, nm)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val fix = PendingIntent.getActivity(context, 1, fixIntent(context, issue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val text = context.getString(
            if (isAccessibility(issue)) R.string.vset_rel_text_accessibility else R.string.vset_rel_text_listener,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bulle)
            .setContentTitle(context.getString(R.string.vset_rel_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.vset_rel_action), fix).build())
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()
    }
}
