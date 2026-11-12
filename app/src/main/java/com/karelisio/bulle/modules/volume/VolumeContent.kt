package com.karelisio.bulle.modules.volume

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.volet.media.MediaOutput
import com.karelisio.bulle.modules.volet.media.OutputKind
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Curseur de volume de l'île (alerte ponctuelle, prolongée à chaque appui). */
data class VolumeContent(
    val overlay: VolumeOverlay,
    override val timeoutMs: Long,
) : IslandContent() {
    override val key = KEY
    override val moduleId = VolumeModule.ID
    override val priority = Priority.ALERT
    override val postedAt: Long = overlay.shownAt
    override val canBeSecondary: Boolean = false
    override val accent: Color = Color(0xFF8AB4FF)
    override val compactSlotWidth: Dp = 72.dp

    @Composable
    override fun Leading() {
        Icon(iconOf(overlay), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }

    @Composable
    override fun Trailing() {
        VolumeBar(overlay.fraction, accent, Modifier.width(56.dp).height(6.dp))
    }

    @Composable
    override fun Minimal() = Leading()

    @Composable
    override fun Expanded(host: IslandHost) {
        val context = host.context
        val haptic = LocalHapticFeedback.current
        // Tant que la vue étendue est ouverte, le curseur ne disparaît pas.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                VolumeKeys.keepAlive()
            }
        }
        var dragging by remember { mutableStateOf<Float?>(null) }
        val output = remember { MediaOutput.current(context) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconOf(overlay), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(labelOf(overlay.stream)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${((dragging?.let { VolumeLogic.fraction(it.roundToInt(), overlay.min, overlay.max) } ?: overlay.fraction) * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { VolumeKeys.toggleMute(context, overlay.stream) }) {
                    Icon(
                        if (overlay.muted || overlay.level == overlay.min) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(if (overlay.muted) R.string.volume_unmute else R.string.volume_mute),
                    )
                }
                Slider(
                    value = dragging ?: (if (overlay.muted) overlay.min else overlay.level).toFloat(),
                    onValueChange = { v ->
                        if (dragging?.roundToInt() != v.roundToInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        dragging = v
                        VolumeKeys.setLevel(context, overlay.stream, v.roundToInt())
                    },
                    onValueChangeFinished = { dragging = null },
                    valueRange = overlay.min.toFloat()..overlay.max.coerceAtLeast(overlay.min + 1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                    modifier = Modifier.weight(1f).height(36.dp),
                )
                FilledTonalIconButton(onClick = { VolumeKeys.openOutputSwitcher(context, host::collapse) }) {
                    Icon(outputIcon(output.kind), contentDescription = stringResource(R.string.volume_output))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                VolumeStream.entries.forEach { s ->
                    FilledIconToggleButton(
                        checked = s == overlay.stream,
                        onCheckedChange = { VolumeKeys.select(context, s) },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(checkedContainerColor = accent, checkedContentColor = Color.Black),
                    ) {
                        Icon(streamIcon(s), contentDescription = stringResource(labelOf(s)))
                    }
                }
            }
        }
    }

    /** Toucher sur la vue étendue hors commandes : on garde le curseur ouvert. */
    override fun onExpandedTap(host: IslandHost): Boolean = true

    companion object {
        const val KEY = "volume:slider"

        fun labelOf(stream: VolumeStream): Int = when (stream) {
            VolumeStream.MEDIA -> R.string.volume_stream_media
            VolumeStream.RING -> R.string.volume_stream_ring
            VolumeStream.NOTIFICATION -> R.string.volume_stream_notification
            VolumeStream.ALARM -> R.string.volume_stream_alarm
        }

        fun streamIcon(stream: VolumeStream): ImageVector = when (stream) {
            VolumeStream.MEDIA -> Icons.Rounded.MusicNote
            VolumeStream.RING -> Icons.Rounded.RingVolume
            VolumeStream.NOTIFICATION -> Icons.Rounded.Notifications
            VolumeStream.ALARM -> Icons.Rounded.Alarm
        }

        fun iconOf(o: VolumeOverlay): ImageVector =
            if (o.muted || o.level <= o.min) Icons.AutoMirrored.Rounded.VolumeOff else streamIcon(o.stream)

        fun outputIcon(kind: OutputKind): ImageVector = when (kind) {
            OutputKind.PHONE -> Icons.Rounded.PhoneAndroid
            OutputKind.HEADPHONES -> Icons.Rounded.Headphones
            OutputKind.BLUETOOTH -> Icons.Rounded.Bluetooth
            OutputKind.USB -> Icons.Rounded.Usb
            OutputKind.TV -> Icons.Rounded.Tv
            OutputKind.CAST -> Icons.Rounded.Cast
        }
    }
}

/** Jauge compacte : remplissage au ressort, valeur animée lue seulement au dessin. */
@Composable
fun VolumeBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val fill = remember { Animatable(fraction) }
    LaunchedEffect(fraction, reduceMotion) {
        fill.animateTo(fraction, if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
    }
    Box(
        modifier.drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(color.copy(alpha = 0.25f), cornerRadius = r)
            val w = size.width * fill.value.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        },
    )
}
