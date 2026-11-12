package com.karelisio.bulle.modules.alerts.sources

import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.karelisio.bulle.modules.alerts.AlertStyleBook
import com.karelisio.bulle.modules.alerts.RingerKind
import com.karelisio.bulle.modules.alerts.SignalRules
import com.karelisio.bulle.modules.alerts.VolumeBurst
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.LevelBar
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import com.karelisio.bulle.modules.alerts.ui.ShakingIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Silencieux / vibreur / sonnerie (RINGER_MODE_CHANGED_ACTION, sans permission).
 * La position lue au démarrage sert de référence : la diffusion collante initiale ne déclenche rien.
 */
object RingerSource : AlertSource {
    override val id = "ringer"

    override fun isEnabled(settings: AlertSettings) = settings.ringer

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        val am = ctx.getSystemService(AudioManager::class.java)
        var last = runCatching { SignalRules.ringerKind(am.ringerMode) }.getOrNull()
        broadcasts(ctx, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)).collect { intent ->
            val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1).takeIf { it >= 0 } ?: am.ringerMode
            val now = SignalRules.ringerKind(mode)
            if (now != last) {
                last = now
                emit(RingerContent(now, alertNow()))
            }
        }
    }.latestAlive()
}

/**
 * Volume multimédia (diffusion système « android.media.VOLUME_CHANGED_ACTION », flux STREAM_MUSIC).
 * Les rafales (touche maintenue) donnent UNE alerte qui se met à jour, avec sa barre de niveau.
 */
object MediaVolumeSource : AlertSource {
    override val id = "mediaVolume"
    private const val ACTION = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    private const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    private const val EXTRA_PREVIOUS = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"

    override fun isEnabled(settings: AlertSettings) = settings.mediaVolume

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
        var burst: VolumeBurst? = null
        broadcasts(ctx, IntentFilter(ACTION)).collect { intent ->
            if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return@collect
            val value = intent.getIntExtra(EXTRA_VALUE, -1)
            if (value < 0 || value == intent.getIntExtra(EXTRA_PREVIOUS, -1)) return@collect
            val now = alertNow()
            val window = AlertStyleBook.timeout(id, VolumeContent.BASE_TIMEOUT_MS) ?: VolumeContent.BASE_TIMEOUT_MS
            val next = VolumeBurst.next(burst, value, max, now, window)
            burst = next
            emit(VolumeContent(next.level, next.max, next.startedAt, next.lastAt))
        }
    }.latestAlive()
}

/** Changement de position de la sonnerie : le vibreur tremble, le silencieux barre la cloche. */
data class RingerContent(
    val kind: RingerKind,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:ringer:$postedAt"
    override val alertId = "ringer"
    override val baseTimeoutMs: Long? = 3_000L
    override val compactSlotWidth = 84.dp
    override val accent: Color = when (kind) {
        RingerKind.SILENT -> Color(0xFFFF8A80)
        RingerKind.VIBRATE -> Color(0xFFB39CFF)
        RingerKind.NORMAL -> Color(0xFF7BE0A8)
    }

    @Composable
    private fun Glyph(size: Int) = when (kind) {
        RingerKind.VIBRATE -> ShakingIcon(Icons.Rounded.Vibration, accent, size = size.dp)
        RingerKind.SILENT -> PopIcon(Icons.Rounded.NotificationsOff, accent, size = size.dp)
        RingerKind.NORMAL -> PopIcon(Icons.Rounded.NotificationsActive, accent, size = size.dp)
    }

    @Composable
    private fun label() = stringResource(
        when (kind) {
            RingerKind.SILENT -> R.string.alerts_ringer_silent
            RingerKind.VIBRATE -> R.string.alerts_ringer_vibrate
            RingerKind.NORMAL -> R.string.alerts_ringer_normal
        },
    )

    @Composable
    override fun Leading() = Glyph(20)

    @Composable
    override fun Trailing() = AlertLabel(label(), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(accent = accent, title = label(), subtitle = stringResource(R.string.alerts_ringer), glyph = { Glyph(26) })
    }
}

/** Volume multimédia : haut-parleur + barre au ressort. Même clé pendant toute une rafale. */
data class VolumeContent(
    val level: Int,
    val max: Int,
    val burstId: Long,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:volume:$burstId"
    override val alertId = "mediaVolume"
    override val baseTimeoutMs: Long? = BASE_TIMEOUT_MS
    override val compactSlotWidth = 64.dp
    override val accent = VolumeBlue

    private val fraction: Float get() = if (max <= 0) 0f else level / max.toFloat()
    private val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)

    private val icon: ImageVector
        get() = when {
            level <= 0 -> Icons.AutoMirrored.Rounded.VolumeOff
            fraction < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
            else -> Icons.AutoMirrored.Rounded.VolumeUp
        }

    @Composable
    override fun Leading() = PopIcon(icon, VolumeBlue)

    @Composable
    override fun Trailing() = LevelBar(fraction, VolumeBlue, width = 44.dp)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = VolumeBlue,
            title = stringResource(R.string.alerts_volume),
            subtitle = stringResource(R.string.alerts_percent, percent),
            glyph = { PopIcon(icon, VolumeBlue, size = 26.dp) },
            extra = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    LevelBar(fraction, VolumeBlue, width = 240.dp, height = 8.dp)
                }
            },
        )
    }

    companion object {
        const val BASE_TIMEOUT_MS = 2_500L
        private val VolumeBlue = Color(0xFF6AB0FF)
    }
}
