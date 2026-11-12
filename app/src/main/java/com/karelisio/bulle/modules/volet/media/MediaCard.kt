package com.karelisio.bulle.modules.volet.media

import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.ui.LocalVoletHaptics
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.ui.PlayPauseButton
import com.karelisio.bulle.modules.music.ui.WavyScrubber
import com.karelisio.bulle.modules.music.ui.formatTime
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Action personnalisée de la session (PlaybackState.CustomAction), icône déjà chargée. */
data class MediaCustomAction(val id: String, val name: String, val icon: ImageBitmap?)

/** Tout ce qu'affiche la carte média : indépendant de MediaController (testable). */
@Stable
data class MediaCardData(
    val title: String,
    val artist: String,
    /** Identité du morceau : déclenche le fondu croisé au changement. */
    val trackKey: String,
    val durationMs: Long,
    val playback: PlaybackSnapshot,
    val appName: String,
    val appIcon: ImageBitmap?,
    val artwork: ImageBitmap?,
    val scheme: MediaScheme,
    val customActions: List<MediaCustomAction> = emptyList(),
    val output: AudioOutput = AudioOutput(OutputKind.PHONE, null),
)

/** Commandes de la carte (reliées au MediaController par la section). */
interface MediaCardActions {
    fun open()
    fun play()
    fun pause()
    fun previous()
    fun next()
    fun seekTo(positionMs: Long)
    fun custom(id: String)
    fun output()
}

/**
 * Carte média du volet, style Android 16 : pochette floutée en fond et en vignette, couleurs tirées
 * de la pochette, barre de progression ondulée, contrôles morphés, sortie audio. Respire doucement
 * pendant la lecture (coupé si « réduire les animations »).
 */
@Composable
fun MediaCard(
    data: MediaCardData,
    actions: MediaCardActions,
    cornerRadius: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current
    val haptics = LocalVoletHaptics.current
    val duration = data.durationMs

    // Couleurs : ressort lent, pour un fondu doux d'un morceau à l'autre.
    val colorSpec = spring<Color>(stiffness = Spring.StiffnessVeryLow)
    val container by animateColorAsState(Color(data.scheme.container), colorSpec, label = "container")
    val onContainer by animateColorAsState(Color(data.scheme.onContainer), colorSpec, label = "on")
    val onVariant by animateColorAsState(Color(data.scheme.onContainerVariant), colorSpec, label = "onVariant")
    val accent by animateColorAsState(Color(data.scheme.accent), colorSpec, label = "accent")
    val onAccent by animateColorAsState(Color(data.scheme.onAccent), colorSpec, label = "onAccent")
    val trackColor by animateColorAsState(Color(data.scheme.track), colorSpec, label = "track")

    // État local : remplacé à chaque état du lecteur, modifié de façon optimiste au toucher.
    var base by remember(data.playback) { mutableStateOf(data.playback) }
    val position = remember { mutableLongStateOf(base.positionAt(SystemClock.elapsedRealtime(), duration)) }
    LaunchedEffect(base, duration, reduceMotion) {
        while (true) {
            position.longValue = base.positionAt(SystemClock.elapsedRealtime(), duration)
            if (!base.isPlaying) break
            if (reduceMotion) delay(500) else withFrameMillis { }
        }
    }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val playing = base.isPlaying

    fun now() = SystemClock.elapsedRealtime()
    fun setPlaying(play: Boolean) {
        base = base.copy(
            state = if (play) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            positionMs = base.positionAt(now(), duration),
            updatedAt = now(),
            speed = if (play) base.speed.takeIf { it > 0f } ?: 1f else base.speed,
        )
    }

    // Respiration : légère pulsation d'échelle et de halo, seulement en lecture.
    val breath = remember { Animatable(0f) }
    LaunchedEffect(playing, reduceMotion) {
        if (playing && !reduceMotion) {
            while (isActive) {
                breath.animateTo(1f, tween(1_700))
                breath.animateTo(0f, tween(1_700))
            }
        } else {
            breath.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val pad = if (compact) 14.dp else 18.dp
    val openLabel = stringResource(R.string.volet_media_open_player, data.appName)

    Box(
        modifier
            .graphicsLayer {
                val s = 1f + 0.012f * breath.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                // Halo coloré autour de la carte, qui suit la respiration.
                val b = breath.value
                if (b > 0.01f) {
                    for (i in 1..3) {
                        val g = (3 + i * 3).dp.toPx() * b
                        drawRoundRect(
                            accent.copy(alpha = 0.10f * b / i),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(cornerRadius.toPx() + g),
                        )
                    }
                }
            }
            .clip(shape)
            .background(container)
            .clickable(onClickLabel = openLabel, role = Role.Button) {
                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                actions.open()
            },
    ) {
        // Fond : pochette floutée, fondu croisé au changement de morceau.
        Crossfade(data.artwork, animationSpec = tween(600), modifier = Modifier.matchParentSize(), label = "bgArt") { art ->
            if (art != null) {
                Image(
                    art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = MediaColors.ARTWORK_ALPHA,
                    modifier = Modifier.fillMaxSize().blur(28.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(pad)) {
            TopRow(data, onVariant, onContainer, actions, Modifier.cascade(0))
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            TrackRow(data, onContainer, onVariant, accent, reduceMotion, compact, Modifier.cascade(1))
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

            val seekable = duration > 0 && (base.can(PlaybackState.ACTION_SEEK_TO) || base.actions == 0L)
            Column(Modifier.cascade(2)) {
                WavyScrubber(
                    fraction = { if (duration > 0) position.longValue.toFloat() / duration else 0f },
                    playing = playing,
                    accent = accent,
                    seekable = seekable,
                    onScrub = { scrub = it },
                    onSeek = { f ->
                        val target = (f * duration).toLong()
                        actions.seekTo(target)
                        base = base.copy(positionMs = target, updatedAt = now())
                        position.longValue = target
                    },
                    trackColor = trackColor,
                    thumbColor = onContainer,
                )
                if (duration > 0) {
                    TimeRow(
                        elapsed = { scrub?.let { (it * duration).toLong() } ?: position.longValue },
                        duration = duration,
                        color = onVariant,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
            Row(
                Modifier.fillMaxWidth().cascade(3),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomSlot(data.customActions.getOrNull(0), onContainer, actions)
                ControlButton(
                    Icons.Rounded.SkipPrevious,
                    stringResource(R.string.volet_media_previous),
                    onContainer,
                    enabled = base.can(PlaybackState.ACTION_SKIP_TO_PREVIOUS) || base.actions == 0L,
                ) { actions.previous() }
                PlayPauseButton(
                    playing = playing,
                    accent = accent,
                    contentDescription = stringResource(if (playing) R.string.volet_media_pause else R.string.volet_media_play),
                    onClick = {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (playing) actions.pause() else actions.play()
                        setPlaying(!playing)
                    },
                    size = if (compact) 52.dp else 60.dp,
                    iconColor = onAccent,
                )
                ControlButton(
                    Icons.Rounded.SkipNext,
                    stringResource(R.string.volet_media_next),
                    onContainer,
                    enabled = base.can(PlaybackState.ACTION_SKIP_TO_NEXT) || base.actions == 0L,
                ) { actions.next() }
                CustomSlot(data.customActions.getOrNull(1), onContainer, actions)
            }
        }
    }
}

/** En-tête : icône et nom de l'appli à gauche, sortie audio à droite. */
@Composable
private fun TopRow(data: MediaCardData, onVariant: Color, onContainer: Color, actions: MediaCardActions, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (data.appIcon != null) {
            Image(data.appIcon, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = onVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            data.appName,
            style = MaterialTheme.typography.labelLarge,
            color = onVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OutputChip(data.output, onContainer, actions)
    }
}

/** Pastille de sortie audio : icône + nom de l'appareil ; ouvre le sélecteur de sortie. */
@Composable
private fun OutputChip(output: AudioOutput, color: Color, actions: MediaCardActions) {
    val view = LocalView.current
    val haptics = LocalVoletHaptics.current
    val label = outputLabel(output)
    val description = stringResource(R.string.volet_media_output_switch, label)
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClickLabel = description, role = Role.Button) {
                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                actions.output()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(outputIcon(output.kind), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        AnimatedContent(label, label = "output") { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

@Composable
internal fun outputLabel(output: AudioOutput): String = output.name ?: stringResource(
    when (output.kind) {
        OutputKind.PHONE -> R.string.volet_media_output_phone
        OutputKind.HEADPHONES -> R.string.volet_media_output_headphones
        OutputKind.BLUETOOTH -> R.string.volet_media_output_bluetooth
        OutputKind.USB -> R.string.volet_media_output_usb
        OutputKind.TV -> R.string.volet_media_output_tv
        OutputKind.CAST -> R.string.volet_media_output_cast
    },
)

private fun outputIcon(kind: OutputKind): ImageVector = when (kind) {
    OutputKind.PHONE -> Icons.Rounded.PhoneAndroid
    OutputKind.HEADPHONES -> Icons.Rounded.Headphones
    OutputKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    OutputKind.USB -> Icons.Rounded.Usb
    OutputKind.TV -> Icons.Rounded.Tv
    OutputKind.CAST -> Icons.Rounded.Cast
}

/** Vignette de la pochette, titre et artiste (glissement vertical au changement de morceau). */
@Composable
private fun TrackRow(
    data: MediaCardData,
    onContainer: Color,
    onVariant: Color,
    accent: Color,
    reduceMotion: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val thumb = if (compact) 52.dp else 64.dp
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Crossfade(data.artwork, animationSpec = tween(500), label = "thumb") { art ->
            Box(
                Modifier.size(thumb).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = onContainer, modifier = Modifier.size(thumb / 2))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        AnimatedContent(
            targetState = Triple(data.trackKey, data.title, data.artist),
            transitionSpec = {
                (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(260)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
            },
            modifier = Modifier.weight(1f),
            label = "title",
        ) { (_, title, artist) ->
            Column {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = if (reduceMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (reduceMotion) Modifier else Modifier.basicMarquee(iterations = 2, initialDelayMillis = 1_500),
                )
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRow(elapsed: () -> Long, duration: Long, color: Color) {
    // Recomposé une fois par seconde seulement.
    val elapsedFn by rememberUpdatedState(elapsed)
    val elapsedSec by remember { derivedStateOf { elapsedFn() / 1000 } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = MaterialTheme.typography.labelSmall
        Text(formatTime(elapsedSec * 1000), style = style, color = color)
        Text(formatTime(duration), style = style, color = color)
    }
}

/** Bouton précédent / suivant : l'icône file dans sa direction au toucher puis revient au ressort. */
@Composable
private fun ControlButton(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val haptics = LocalVoletHaptics.current
    val direction = if (icon == Icons.Rounded.SkipPrevious) -1f else 1f
    IconButton(
        onClick = {
            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
            scope.launch {
                nudge.snapTo(direction)
                nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
        },
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { translationX = nudge.value * 6.dp.toPx() },
        )
    }
}

/** Emplacement d'action personnalisée (vide mais réservé pour garder les contrôles centrés). */
@Composable
private fun CustomSlot(action: MediaCustomAction?, color: Color, actions: MediaCardActions) {
    if (action == null) {
        Spacer(Modifier.size(48.dp))
        return
    }
    val view = LocalView.current
    val haptics = LocalVoletHaptics.current
    IconButton(
        onClick = {
            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            actions.custom(action.id)
        },
        modifier = Modifier.size(48.dp),
    ) {
        if (action.icon != null) {
            Icon(action.icon, contentDescription = action.name, tint = color, modifier = Modifier.size(24.dp))
        } else {
            Text(action.name.take(2), style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}
