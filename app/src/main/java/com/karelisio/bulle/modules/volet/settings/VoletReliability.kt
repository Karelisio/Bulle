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

    /**
     * Appelé par le WatchdogWorker : poste (ou retire) l'alerte selon [issues].
     * Une seule notification, mise à jour sans re-sonner.
     */
    fun notifyIfNeeded(context: Context, issues: List<VoletIssue>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val issue = issues.firstOrNull()
        if (issue == null) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        // POST_NOTIFICATIONS n'existe qu'à partir d'Android 13 : avant, checkSelfPermission la dit refusée
        // et l'alerte n'était jamais postée (Android 12 / 12L).
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context, nm)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val fix = PendingIntent.getActivity(context, 1, fixIntent(context, issue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val text = context.getString(
            if (isAccessibility(issue)) R.string.vset_rel_text_accessibility else R.string.vset_rel_text_listener,
        )
        val notification = Notification.Builder(context, CHANNEL)
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
        nm.notify(NOTIFICATION_ID, notification)
    }
}
