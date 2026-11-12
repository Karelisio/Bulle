package com.karelisio.bulle.modules.volume.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.music.media.MediaSessionSource
import com.karelisio.bulle.modules.volume.StreamLevel
import com.karelisio.bulle.modules.volume.VolumeContent
import com.karelisio.bulle.modules.volume.VolumeLogic
import com.karelisio.bulle.modules.volume.VolumeMixer
import com.karelisio.bulle.modules.volume.VolumeStream
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.roundToInt

/** Appli qui joue (nom + icône), affichée sur la ligne média. */
data class PlayingApp(val label: String, val icon: ImageBitmap?)

/**
 * Toutes les sources de volume, en curseurs épais empilés. [keyStream] = flux des touches
 * (ligne mise en évidence). Se met à jour quand le volume change ailleurs.
 */
@Composable
fun VolumeMixerList(
    context: Context,
    keyStream: VolumeStream,
    /** Change à chaque niveau publié par les touches : relit les autres sources. */
    keyTick: Any?,
    accent: Color,
    onBlockedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Session média (module musique) : lecture en cours et appli qui joue.
    val media by remember(context) { MediaSessionSource.observe(context) }.collectAsState(initial = null)
    val playing = media?.playback?.isPlaying == true
    val playingPkg = media?.controller?.packageName?.takeIf { playing }
    val app = remember(playingPkg) { playingPkg?.let { loadApp(context, it) } }

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val stop = VolumeMixer.watch(context) { tick++ }
        onDispose(stop)
    }
    val mixer = remember(tick, playing) { VolumeMixer.context(context, playing) }
    val streams = remember(mixer) { VolumeLogic.streamsToShow(mixer) }
    val levels = remember(tick, keyTick, streams) { VolumeMixer.read(context, streams) }
    val highlighted = VolumeLogic.highlighted(keyStream, streams)

    Column(
        modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        levels.forEach { l ->
            val label = when {
                l.stream == VolumeStream.RING && mixer.notificationLinked -> stringResource(R.string.volume_stream_ring_notification)
                else -> stringResource(VolumeContent.labelOf(l.stream))
            }
            StreamRow(
                level = l,
                label = label,
                app = app.takeIf { l.stream == VolumeStream.MEDIA },
                highlighted = l.stream == highlighted,
                accent = accent,
                onLevel = { v -> if (!VolumeMixer.setLevel(context, l.stream, v)) tick++ },
                onToggleMute = if (VolumeLogic.canMute(l.stream)) ({ VolumeMixer.toggleMute(context, l.stream); tick++ }) else null,
                onBlockedClick = onBlockedClick,
            )
        }
    }
}

private fun loadApp(context: Context, pkg: String): PlayingApp? = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(pkg, 0)
    val icon = runCatching { pm.getApplicationIcon(info).toBitmap(48, 48).asImageBitmap() }.getOrNull()
    PlayingApp(pm.getApplicationLabel(info).toString(), icon)
}.getOrNull()

/** Une source : curseur épais (icône à gauche selon le niveau, nom, valeur), lien Ne pas déranger si refusé. */
@Composable
fun StreamRow(
    level: StreamLevel,
    label: String,
    app: PlayingApp?,
    highlighted: Boolean,
    accent: Color,
    onLevel: (Int) -> Unit,
    onToggleMute: (() -> Unit)?,
    onBlockedClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shownLevel = dragging ?: level.level
    val fraction = if (dragging == null && level.muted) 0f else VolumeLogic.fraction(shownLevel, level.min, level.max)
    val subtitle = when {
        level.blocked -> stringResource(R.string.volume_blocked_dnd)
        level.fixed -> stringResource(R.string.volume_fixed)
        else -> app?.label
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ThickSlider(
            fraction = fraction,
            min = level.min,
            max = level.max,
            enabled = level.adjustable,
            fillColor = if (highlighted) accent else Color(0xFFE6E6EB),
            highlighted = highlighted,
            accent = accent,
            icon = levelIcon(level.stream, fraction, level.muted && dragging == null),
            label = label,
            subtitle = subtitle,
            appIcon = app?.icon?.takeIf { !level.blocked },
            valueText = "${(fraction * 100).roundToInt()} %",
            stateText = if (highlighted) stringResource(R.string.volume_keys_stream) else null,
            onLevel = { v ->
                if (v != shownLevel) {
                    val edge = v == level.min || v == level.max
                    haptic.performHapticFeedback(if (edge) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentFrequentTick)
                }
                dragging = v
                onLevel(v)
            },
            onRelease = { dragging = null },
            muteLabel = stringResource(if (level.muted) R.string.volume_unmute else R.string.volume_mute),
            onIconTap = onToggleMute?.let { toggle ->
                {
                    haptic.performHapticFeedback(if (level.muted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    toggle()
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (level.blocked) {
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onBlockedClick) {
                Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = stringResource(R.string.volume_dnd_access))
            }
        }
    }
}

/**
 * Curseur M3 Expressive épais : piste arrondie, remplissage au ressort (lu au dessin),
 * contenu blanc sur la piste et noir sur la partie remplie (découpe au dessin).
 */
@Composable
fun ThickSlider(
    fraction: Float,
    min: Int,
    max: Int,
    enabled: Boolean,
    fillColor: Color,
    highlighted: Boolean,
    accent: Color,
    icon: ImageVector,
    label: String,
    subtitle: String?,
    appIcon: ImageBitmap?,
    valueText: String,
    stateText: String?,
    onLevel: (Int) -> Unit,
    onRelease: () -> Unit,
    muteLabel: String,
    onIconTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val fill = remember { Animatable(fraction) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(fraction, reduceMotion, held) {
        // Sous le doigt : suit sans retard ; sinon ressort (rien si « réduire les animations »).
        if (reduceMotion || held) fill.snapTo(fraction)
        else fill.animateTo(fraction, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
    }
    val onLevelNow by rememberUpdatedState(onLevel)
    val onReleaseNow by rememberUpdatedState(onRelease)
    val onIconTapNow by rememberUpdatedState(onIconTap)
    val range = (max - min).coerceAtLeast(1)
    val shape = RoundedCornerShape(18.dp)
    val description = listOfNotNull(label, subtitle).joinToString(", ")

    fun levelAt(x: Float, width: Int): Int = min + ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * range).roundToInt()

    Box(
        modifier
            .height(52.dp)
            .fillMaxWidth()
            .clip(shape)
            .then(if (highlighted) Modifier.border(1.5.dp, accent.copy(alpha = 0.9f), shape) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .background(Color.White.copy(alpha = 0.12f))
            .drawBehind {
                // Remplissage : au moins la largeur d'un bouton arrondi, pour garder la forme.
                val w = size.width * fill.value.coerceIn(0f, 1f)
                if (w > 0.5f) {
                    drawRoundRect(fillColor, size = Size(w.coerceAtLeast(size.height * 0.9f), size.height), cornerRadius = CornerRadius(18.dp.toPx()))
                }
            }
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(min, max) {
                        val iconZone = 52.dp.toPx()
                        detectTapGestures(
                            onTap = { p ->
                                val tap = onIconTapNow
                                if (tap != null && p.x < iconZone) tap()
                                else {
                                    onLevelNow(levelAt(p.x, size.width))
                                    onReleaseNow()
                                }
                            },
                        )
                    }
                    .pointerInput(min, max) {
                        detectHorizontalDragGestures(
                            onDragStart = { p -> held = true; onLevelNow(levelAt(p.x, size.width)) },
                            onDragEnd = { held = false; onReleaseNow() },
                            onDragCancel = { held = false; onReleaseNow() },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                onLevelNow(levelAt(change.position.x, size.width))
                            },
                        )
                    },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateText?.let { stateDescription = "$valueText, $it" } ?: run { stateDescription = valueText }
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f, steps = (range - 1).coerceAtLeast(0))
                if (enabled) {
                    onIconTap?.let { tap -> customActions = listOf(CustomAccessibilityAction(muteLabel) { tap(); true }) }
                    setProgress { target ->
                        onLevelNow(min + (target.coerceIn(0f, 1f) * range).roundToInt())
                        onReleaseNow()
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        SliderLabels(icon, label, subtitle, appIcon, valueText, Color.White, Modifier.matchParentSize())
        // Même contenu en noir, découpé à la partie remplie (lecture du ressort au dessin seulement).
        SliderLabels(
            icon, label, subtitle, appIcon, valueText, Color(0xFF101114),
            Modifier.matchParentSize().drawWithContent {
                val w = size.width * fill.value.coerceIn(0f, 1f)
                val cut = if (w > 0.5f) w.coerceAtLeast(size.height * 0.9f) else 0f
                clipRect(right = cut) { this@drawWithContent.drawContent() }
            },
        )
    }
}

@Composable
private fun SliderLabels(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    appIcon: ImageBitmap?,
    valueText: String,
    color: Color,
    modifier: Modifier,
) {
    Row(modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (appIcon != null) {
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(valueText, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Icône qui suit le niveau de la source. */
fun levelIcon(stream: VolumeStream, fraction: Float, muted: Boolean): ImageVector {
    val off = muted || fraction <= 0f
    return when (stream) {
        VolumeStream.MEDIA -> when {
            off -> Icons.AutoMirrored.Rounded.VolumeOff
            fraction < 0.34f -> Icons.AutoMirrored.Rounded.VolumeMute
            fraction < 0.67f -> Icons.AutoMirrored.Rounded.VolumeDown
            else -> Icons.AutoMirrored.Rounded.VolumeUp
        }
        VolumeStream.RING -> if (off) Icons.Rounded.Vibration else Icons.Rounded.RingVolume
        VolumeStream.NOTIFICATION -> when {
            off -> Icons.Rounded.NotificationsOff
            fraction < 0.34f -> Icons.Rounded.NotificationsNone
            fraction < 0.67f -> Icons.Rounded.Notifications
            else -> Icons.Rounded.NotificationsActive
        }
        VolumeStream.ALARM -> if (fraction < 0.5f) Icons.Rounded.Alarm else Icons.Rounded.AlarmOn
        VolumeStream.CALL -> if (fraction < 0.5f) Icons.Rounded.Call else Icons.Rounded.PhoneInTalk
        VolumeStream.ACCESSIBILITY -> Icons.Rounded.AccessibilityNew
    }
}
