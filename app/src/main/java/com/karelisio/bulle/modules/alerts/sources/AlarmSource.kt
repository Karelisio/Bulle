package com.karelisio.bulle.modules.alerts.sources

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.alerts.AlertsModule
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.ShakingIcon
import com.karelisio.bulle.modules.alerts.ui.rememberClockText
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val AlarmOrange = Color(0xFFFFB347)

/**
 * Alarme (ou minuteur) qui sonne : notification CATEGORY_ALARM en cours de l'appli Horloge.
 * État persistant : affichée tant que la notification est là. Nécessite l'accès aux notifications.
 */
object AlarmSource : AlertSource {
    override val id = "alarm"

    override fun isEnabled(settings: AlertSettings) = settings.alarm

    /** Une alarme « qui sonne » : catégorie alarme, et plein écran ou en cours (pas un simple rappel). */
    fun isRinging(n: Notification): Boolean {
        if (n.category != Notification.CATEGORY_ALARM) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.fullScreenIntent != null) return true
        // Minuteur qui tourne (compte à rebours, sans plein écran) : il est repris par le module
        // « live » (voir LiveParser). Sans cette exclusion, il s'affichait aussi comme une alarme
        // qui sonne, en doublon et étendue pendant une heure.
        val extras = n.extras
        val runningTimer = extras != null && n.`when` > 0 &&
            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) &&
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        if (runningTimer) return false
        return n.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = NotificationRepository.active
        .map { all -> all.values.filter { isRinging(it.notification) }.sortedByDescending { it.postTime }.take(1) }
        .distinctUntilChanged { a, b -> a.map { it.key to it.postTime } == b.map { it.key to it.postTime } }
        .map { list -> list.map { AlarmContent(it, it.key) } }
}

/** Envoie un PendingIntent de notification depuis le service, en autorisant le lancement d'activité. */
internal fun PendingIntent.sendFromIsland() {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Android 16 : MODE_..._ALLOWED est déprécié et ne vaut plus que « si visible » ;
            // comme NotificationIntents, on transmet explicitement notre autorisation.
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            send(null, 0, null, null, null, null, opts.toBundle())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            send(null, 0, null, null, null, null, opts.toBundle())
        } else {
            send()
        }
    }
}

/** Alarme qui sonne : réveil qui tremble, boutons de la notification (Arrêter / Répéter…). */
data class AlarmContent(
    val sbn: StatusBarNotification,
    val notifKey: String,
) : IslandContent() {
    override val key = "alerts:alarm:$notifKey"
    override val notificationKey: String get() = notifKey
    override val moduleId = AlertsModule.ID
    override val priority = Priority.ALARM
    override val postedAt = android.os.SystemClock.elapsedRealtime()
    override val accent = AlarmOrange
    override val showWhenHidden = true
    override val canBeSecondary = false

    /** Étendue tant qu'elle sonne (le contenu disparaît avec la notification). */
    override val autoExpandMs = 60L * 60L * 1000L
    override val compactSlotWidth = 64.dp

    private val title: String?
        get() = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val text: String?
        get() = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Composable
    override fun Leading() = ShakingIcon(Icons.Rounded.Alarm, AlarmOrange)

    @Composable
    override fun Trailing() {
        // Heure qui avance pendant que l'alarme sonne (minute pile).
        AlertLabel(rememberClockText(), color = AlarmOrange)
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val actions = sbn.notification.actions.orEmpty().filter { it.actionIntent != null }.take(3)
        AlertExpanded(
            accent = AlarmOrange,
            title = title ?: stringResource(R.string.alerts_alarm),
            subtitle = text,
            glyph = { ShakingIcon(Icons.Rounded.Alarm, AlarmOrange, size = 28.dp) },
            actions = if (actions.isEmpty()) null else {
                {
                    actions.forEachIndexed { i, a ->
                        val label = a.title?.toString().orEmpty()
                        val onClick = { a.actionIntent.sendFromIsland() }
                        if (i == actions.lastIndex) {
                            Button(
                                onClick = onClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AlarmOrange, contentColor = Color.Black),
                            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else {
                            FilledTonalButton(onClick = onClick) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            },
        )
    }

    override fun onExpandedTap(host: IslandHost): Boolean {
        val pi = sbn.notification.contentIntent ?: sbn.notification.fullScreenIntent ?: return false
        pi.sendFromIsland()
        return true
    }
}
