package com.karelisio.bulle.modules.echo

import android.app.Notification
import android.app.PendingIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.alerts.AlertStyleBook
import com.karelisio.bulle.modules.alerts.AlertsModule
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.ui.styled
import kotlinx.coroutines.flow.combine
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.sources.sendFromIsland
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Écho : chaque notification publiée par l'appli Écho déclenche un cœur pulsé dans l'île.
 * Nécessite l'accès aux notifications de Bulle.
 */
object EchoModule : IslandModule {
    const val ID = "echo"
    const val ECHO_PACKAGE = "com.karelisio.echo"

    override val id = ID
    override val titleRes = R.string.echo_module_title

    override fun isEnabled(settings: BulleSettings): Boolean =
        (settings.modules[id] ?: enabledByDefault) && settings.alerts.echoEnabled

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = combine(
        AlertsModule.styles(env),
        notifications(),
    ) { styles, list -> list.map { styled(it, styles) } }

    private fun notifications(): Flow<List<IslandContent>> = NotificationRepository.events
        .filterIsInstance<NotificationEvent.Posted>()
        .filter { it.sbn.packageName == ECHO_PACKAGE && it.sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        .map { e ->
            val n = e.sbn.notification
            EchoContent(
                title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                contentIntent = n.contentIntent,
                postedAt = alertNow(),
                sourceKey = e.sbn.key,
            ) as IslandContent?
        }
        .latestAlive()
        .onStart { emit(emptyList()) }
}

/** Notification d'Écho : cœur qui bat, halo rose, petits cœurs qui montent. */
data class EchoContent(
    val title: String?,
    val text: String?,
    val contentIntent: PendingIntent?,
    override val postedAt: Long,
    val sourceKey: String? = null,
) : IslandContent() {
    override val key = "echo:$postedAt"
    override val notificationKey: String? get() = sourceKey
    override val moduleId = EchoModule.ID
    override val priority = Priority.ALERT
    /** 4 s par défaut, ou la durée choisie pour l'alerte « echo ». */
    override val timeoutMs: Long? get() = AlertStyleBook.timeout("echo", 4_000L)
    override val autoExpandMs = 2_600L
    override val accent = EchoPink
    override val compactSlotWidth = 84.dp
    override val canBeSecondary = false

    @Composable
    override fun Leading() = EchoHeart(size = 24.dp)

    @Composable
    override fun Trailing() = AlertLabel(title ?: stringResource(R.string.echo_name), color = EchoPink)

    @Composable
    override fun Expanded(host: IslandHost) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EchoHeart(size = 72.dp, particles = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.echo_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = EchoPink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    title ?: stringResource(R.string.echo_default_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!text.isNullOrBlank()) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    override fun onExpandedTap(host: IslandHost): Boolean {
        val pi = contentIntent ?: return false
        pi.sendFromIsland()
        host.dismiss(key)
        return true
    }
}
