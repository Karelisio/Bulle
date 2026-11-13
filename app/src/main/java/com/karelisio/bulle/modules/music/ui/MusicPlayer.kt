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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.Dp
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.music.sync.AudioOutput
import com.karelisio.bulle.modules.music.sync.AudioOutputTracker
import com.karelisio.bulle.modules.music.sync.LyricsPrefs
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.sync.withOffset
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

    // Sortie audio courante, suivie en direct (casque branché/débranché) sans recréer le contenu.
    val appContext = host.context.applicationContext
    val initialOutput = remember(appContext) { AudioOutputTracker.current(appContext) }
    val output by remember(appContext) { AudioOutputTracker.observe(appContext) }.collectAsState(initialOutput)
    val prefs = content.lyricsPrefs
    val scope = rememberCoroutineScope()
    // Mode paroles : appliqué tout de suite au toucher, puis mémorisé dans les réglages.
    var focus by remember(prefs.focusMode) { mutableStateOf(prefs.focusMode) }
    val focused = focus && content.lyrics is LyricsState.Found

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
        val togglePlay = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (playing) controls.pause() else controls.play()
            setPlaying(!playing)
        }
        Header(
            content, playing, accent, reduceMotion,
            compact = focused,
            onTogglePlay = togglePlay,
        ) {
            content.openPlayer(host.context)
            host.collapse()
        }

        // En mode paroles, le reste du lecteur se replie pour laisser la place au texte.
        AnimatedVisibility(
            visible = !focused,
            enter = fadeIn(tween(220, 80)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(120)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Column(Modifier.fillMaxWidth()) {
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
                        onClick = togglePlay,
                    )
                    SkipButton(
                        icon = Icons.Rounded.SkipNext,
                        label = stringResource(R.string.music_next),
                        direction = 1f,
                        enabled = base.can(PlaybackState.ACTION_SKIP_TO_NEXT) || base.actions == 0L,
                    ) { controls.skipToNext() }
                }
            }
        }

        LyricsSection(
            state = content.lyrics,
            position = position,
            prefs = prefs,
            output = output,
            focused = focused,
            onOffsetChange = { ms ->
                scope.launch { SettingsRepository.update(appContext) { it.copy(music = it.music.withOffset(output, ms)) } }
            },
            onToggleFocus = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                val next = !focus
                focus = next
                scope.launch { SettingsRepository.update(appContext) { it.copy(music = it.music.copy(lyricsFocusMode = next)) } }
            },
        )
    }
}

@Composable
private fun Header(
    content: MusicContent,
    playing: Boolean,
    accent: Color,
    reduceMotion: Boolean,
    compact: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val sizeSpec = if (reduceMotion) snap() else spring<Dp>(stiffness = Spring.StiffnessMediumLow)
    val artSize by animateDpAsState(if (compact) 44.dp else 60.dp, sizeSpec, label = "artSize")
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(
            content.artwork?.image,
            accent,
            Modifier
                .size(artSize)
                .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                .clickable(onClickLabel = stringResource(R.string.music_open_player), onClick = onOpen),
        )
        Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
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
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = if (reduceMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (reduceMotion) Modifier else Modifier.basicMarquee(iterations = 2, initialDelayMillis = 1_500),
                )
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (compact) {
            // Mode paroles : seule la lecture/pause reste à portée de pouce.
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play),
                    tint = accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else {
            CompactEqualizer(playing, accent, Modifier.height(18.dp))
        }
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

/** Mode paroles : deux lignes de plus dans la fenêtre. */
internal fun lyricsWindowLines(visibleLines: Int, focused: Boolean): Int = visibleLines + if (focused) 2 else 0

@Composable
private fun LyricsSection(
    state: LyricsState,
    position: MutableLongState,
    prefs: LyricsPrefs,
    output: AudioOutput,
    focused: Boolean,
    onOffsetChange: (Int) -> Unit,
    onToggleFocus: () -> Unit,
) {
    val found = state as? LyricsState.Found
    // Garde les dernières paroles affichées pendant l'animation de sortie.
    var shown by remember { mutableStateOf(found?.lyrics) }
    if (found != null) shown = found.lyrics
    // Décalage de la sortie courante, modifié de façon optimiste au toucher (puis mémorisé).
    val saved = prefs.offsetFor(output.key)
    var offset by remember(output.key, saved) { mutableIntStateOf(saved) }
    // Bulle « Casque XYZ : +250 ms », affichée un instant après chaque réglage.
    var toastTick by remember { mutableIntStateOf(0) }
    var toastVisible by remember { mutableStateOf(false) }
    LaunchedEffect(toastTick) {
        if (toastTick == 0) return@LaunchedEffect
        toastVisible = true
        delay(1_800)
        toastVisible = false
    }
    AnimatedVisibility(
        visible = found != null,
        enter = fadeIn(tween(300, 120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(150)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        val lyrics = shown ?: return@AnimatedVisibility
        val height = lyricsWindowHeight(lyricsWindowLines(prefs.visibleLines, focused), prefs.textScale)
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(if (focused) 10.dp else 12.dp))
            Box(Modifier.fillMaxWidth()) {
                if (lyrics.synced) {
                    val currentOffset by rememberUpdatedState(offset)
                    val index by remember(lyrics) {
                        derivedStateOf { LyricsSync.currentLine(lyrics, position.longValue, currentOffset) }
                    }
                    SyncedLyrics(lyrics, index, height = height, textScale = prefs.textScale)
                } else {
                    PlainLyrics(lyrics, height = height, textScale = prefs.textScale)
                }
                val label = outputLabel(output.kind, output.name)
                androidx.compose.animation.AnimatedVisibility(
                    visible = toastVisible,
                    enter = fadeIn(tween(160)) + scaleIn(spring(stiffness = Spring.StiffnessMedium), initialScale = 0.9f),
                    exit = fadeOut(tween(400)),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Text(
                        stringResource(R.string.music_sync_toast, label, LyricsSync.formatOffset(offset)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xE6202020))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (lyrics.synced) {
                    OffsetStepper(
                        offsetMs = offset,
                        onChange = { ms ->
                            offset = ms
                            toastTick++
                            onOffsetChange(ms)
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(
                        R.string.music_lyrics_source,
                        stringResource(if (lyrics.source == LyricsSource.LRCLIB) R.string.music_source_lrclib else R.string.music_source_player),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                )
                IconButton(onClick = onToggleFocus, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (focused) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                        contentDescription = stringResource(if (focused) R.string.music_focus_off else R.string.music_focus_on),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
