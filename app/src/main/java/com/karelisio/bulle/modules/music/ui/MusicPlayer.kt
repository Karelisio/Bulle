package com.karelisio.bulle.modules.music.ui

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Formate une durée en « m:ss » (ou « h:mm:ss »). */
internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Vue étendue : le lecteur complet. */
@Composable
internal fun MusicPlayer(content: MusicContent, host: IslandHost) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val controls = content.controller.transportControls
    val duration = content.track.durationMs
    val accent by animateColorAsState(content.accent, tween(700), label = "accent")

    // État de lecture local : remplacé à chaque nouvel état du lecteur, modifié de façon optimiste
    // (lecture/pause, recherche) pour une réponse immédiate au toucher.
    var base by remember(content.playback) { mutableStateOf(content.playback) }
    val position = remember { mutableLongStateOf(base.positionAt(SystemClock.elapsedRealtime(), duration)) }
    LaunchedEffect(base, duration, reduceMotion) {
        while (true) {
            position.longValue = base.positionAt(SystemClock.elapsedRealtime(), duration)
            if (!base.isPlaying) break
            // Rafraîchi à l'image près, uniquement tant que la vue étendue est affichée.
            if (reduceMotion) delay(250) else withFrameMillis { }
        }
    }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val playing = base.isPlaying

    fun now() = SystemClock.elapsedRealtime()
    fun setPlaying(play: Boolean) {
        val p = base.positionAt(now(), duration)
        base = base.copy(
            state = if (play) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            positionMs = p,
            updatedAt = now(),
            speed = if (play) base.speed.takeIf { it > 0f } ?: 1f else base.speed,
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // Halo coloré par la pochette, derrière l'en-tête.
                val r = size.width * 0.75f
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(30.dp.toPx(), 30.dp.toPx()),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(30.dp.toPx(), 30.dp.toPx()),
                )
            },
    ) {
        Header(content, playing, accent, reduceMotion) {
            content.openPlayer(host.context)
            host.collapse()
        }
        Spacer(Modifier.height(14.dp))

        val seekable = duration > 0 && (base.can(PlaybackState.ACTION_SEEK_TO) || base.actions == 0L)
        WavyScrubber(
            fraction = { if (duration > 0) position.longValue.toFloat() / duration else 0f },
            playing = playing,
            accent = accent,
            seekable = seekable,
            onScrub = { scrub = it },
            onSeek = { f ->
                val target = (f * duration).toLong()
                controls.seekTo(target)
                base = base.copy(positionMs = target, updatedAt = now())
                position.longValue = target
            },
        )
        TimeRow(
            elapsed = { scrub?.let { (it * duration).toLong() } ?: position.longValue },
            duration = duration,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(
                icon = Icons.Rounded.SkipPrevious,
                label = stringResource(R.string.music_previous),
                direction = -1f,
                enabled = base.can(PlaybackState.ACTION_SKIP_TO_PREVIOUS) || base.actions == 0L,
            ) { controls.skipToPrevious() }
            PlayPauseButton(
                playing = playing,
                accent = accent,
                contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (playing) controls.pause() else controls.play()
                    setPlaying(!playing)
                },
            )
            SkipButton(
                icon = Icons.Rounded.SkipNext,
                label = stringResource(R.string.music_next),
                direction = 1f,
                enabled = base.can(PlaybackState.ACTION_SKIP_TO_NEXT) || base.actions == 0L,
            ) { controls.skipToNext() }
        }

        LyricsSection(content.lyrics, position)
    }
}

@Composable
private fun Header(content: MusicContent, playing: Boolean, accent: Color, reduceMotion: Boolean, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(
            content.artwork?.image,
            accent,
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = stringResource(R.string.music_open_player), onClick = onOpen),
        )
        Spacer(Modifier.width(14.dp))
        AnimatedContent(
            targetState = content.track.title to content.track.artist,
            transitionSpec = {
                (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(260)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
            },
            modifier = Modifier.weight(1f),
            label = "title",
        ) { (title, artist) ->
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = if (reduceMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (reduceMotion) Modifier else Modifier.basicMarquee(iterations = 2, initialDelayMillis = 1_500),
                )
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        CompactEqualizer(playing, accent, Modifier.height(18.dp))
    }
}

@Composable
private fun TimeRow(elapsed: () -> Long, duration: Long) {
    // Recomposé une fois par seconde seulement.
    val elapsedFn by rememberUpdatedState(elapsed)
    val elapsedSec by remember { derivedStateOf { elapsedFn() / 1000 } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = MaterialTheme.typography.labelSmall
        val color = Color.White.copy(alpha = 0.58f)
        Text(formatTime(elapsedSec * 1000), style = style, color = color)
        if (duration > 0) Text("-" + formatTime(duration - elapsedSec * 1000), style = style, color = color)
    }
}

/** Bouton piste précédente/suivante : l'icône file dans sa direction au toucher puis revient au ressort. */
@Composable
private fun SkipButton(icon: ImageVector, label: String, direction: Float, enabled: Boolean, onClick: () -> Unit) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
            scope.launch {
                nudge.animateTo(direction, tween(90))
                nudge.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        },
        enabled = enabled,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { translationX = nudge.value * 8.dp.toPx() },
        )
    }
}

@Composable
private fun LyricsSection(state: LyricsState, position: androidx.compose.runtime.MutableLongState) {
    val found = state as? LyricsState.Found
    // Garde les dernières paroles affichées pendant l'animation de sortie.
    var shown by remember { mutableStateOf(found?.lyrics) }
    if (found != null) shown = found.lyrics
    AnimatedVisibility(
        visible = found != null,
        enter = fadeIn(tween(300, 120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(150)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        val lyrics = shown ?: return@AnimatedVisibility
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            if (lyrics.synced) {
                // Léger décalage : la ligne s'allume un instant avant d'être chantée.
                val index by remember(lyrics) { derivedStateOf { lyrics.indexAt(position.longValue + 250) } }
                SyncedLyrics(lyrics, index, height = 118.dp)
            } else {
                PlainLyrics(lyrics, height = 118.dp)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    stringResource(
                        R.string.music_lyrics_source,
                        stringResource(if (lyrics.source == LyricsSource.LRCLIB) R.string.music_source_lrclib else R.string.music_source_player),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.42f),
                )
            }
        }
    }
}
