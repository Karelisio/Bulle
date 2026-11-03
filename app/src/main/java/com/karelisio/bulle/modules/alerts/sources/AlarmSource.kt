package com.karelisio.bulle.modules.alerts.sources

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Date

private val AlarmOrange = Color(0xFFFFB347)

/**
 * Alarme (ou minuteur) qui sonne : notification CATEGORY_ALARM en cours de l'appli Horloge.
 * État persistant : affichée tant que la notification est là. Nécessite l'accès aux notifications.
 */
object AlarmSource : AlertSource {
    override val id = "alarm"

    override fun isEnabled(settings: AlertSettings) = settings.alarm

    /** Une alarme « qui sonne » : catégorie alarme, et plein écran ou en cours (pas un simple rappel). */
    fun isRinging(n: Notification): Boolean =
        n.category == Notification.CATEGORY_ALARM &&
            (n.fullScreenIntent != null || n.flags and Notification.FLAG_ONGOING_EVENT != 0) &&
            n.flags and Notification.FLAG_GROUP_SUMMARY == 0

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = NotificationRepository.active
        .map { all -> all.values.filter { isRinging(it.notification) }.sortedByDescending { it.postTime }.take(1) }
        .distinctUntilChanged { a, b -> a.map { it.key to it.postTime } == b.map { it.key to it.postTime } }
        .map { list -> list.map { AlarmContent(it, it.key) } }
}

/** Envoie un PendingIntent de notification depuis le service, en autorisant le lancement d'activité. */
internal fun PendingIntent.sendFromIsland() {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
        val ctx = LocalContext.current
        AlertLabel(DateFormat.getTimeFormat(ctx).format(Date()), color = AlarmOrange)
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
