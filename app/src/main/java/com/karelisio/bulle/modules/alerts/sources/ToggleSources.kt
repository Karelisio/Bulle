package com.karelisio.bulle.modules.alerts.sources

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Interrupteurs système signalés par l'île. */
enum class ToggleKind { AIRPLANE, DND }

/**
 * Source générique « un interrupteur système a changé » : lit l'état courant au démarrage
 * (référence, sans alerte), puis alerte à chaque changement effectif.
 */
abstract class ToggleSource(
    override val id: String,
    private val kind: ToggleKind,
    private val action: String,
) : AlertSource {
    abstract fun read(context: Context): Boolean

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        var last = runCatching { read(ctx) }.getOrNull()
        broadcasts(ctx, IntentFilter(action)).collect {
            val now = runCatching { read(ctx) }.getOrNull() ?: return@collect
            if (now != last) {
                last = now
                emit(ToggleContent(kind, now, alertNow()))
            }
        }
    }.latestAlive()
}

/** Mode avion (ACTION_AIRPLANE_MODE_CHANGED). */
object AirplaneSource : ToggleSource("airplane", ToggleKind.AIRPLANE, Intent.ACTION_AIRPLANE_MODE_CHANGED) {
    override fun isEnabled(settings: AlertSettings) = settings.airplane
    override fun read(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
}

/** Ne pas déranger : lecture du filtre d'interruption, sans permission. */
object DndSource : ToggleSource("dnd", ToggleKind.DND, NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
    override fun isEnabled(settings: AlertSettings) = settings.dnd
    override fun read(context: Context): Boolean {
        val f = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}

/** Mode avion / Ne pas déranger activé ou désactivé. */
data class ToggleContent(
    val kind: ToggleKind,
    val on: Boolean,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:${kind.name.lowercase()}:$postedAt"
    override val compactSlotWidth = 84.dp
    override val timeoutMs = 3_000L
    override val accent: Color = when {
        !on -> Color(0xFFB8C0CC)
        kind == ToggleKind.AIRPLANE -> Color(0xFFFFA24C)
        else -> Color(0xFFB39CFF)
    }

    private val icon: ImageVector
        get() = when (kind) {
            ToggleKind.AIRPLANE -> if (on) Icons.Rounded.AirplanemodeActive else Icons.Rounded.AirplanemodeInactive
            ToggleKind.DND -> if (on) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.DoNotDisturbOff
        }

    @Composable
    private fun title() = stringResource(if (kind == ToggleKind.AIRPLANE) R.string.alerts_airplane else R.string.alerts_dnd)

    @Composable
    private fun state() = stringResource(if (on) R.string.alerts_on else R.string.alerts_off)

    @Composable
    override fun Leading() = PopIcon(icon, accent)

    @Composable
    override fun Trailing() = AlertLabel(state(), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(accent = accent, title = title(), subtitle = state(), glyph = { PopIcon(icon, accent, size = 26.dp) })
    }
}
