package com.karelisio.bulle.modules.standby.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.battery.BatteryReader
import com.karelisio.bulle.modules.battery.BluetoothBatteries
import com.karelisio.bulle.modules.calendar.CalendarEvent
import com.karelisio.bulle.modules.calendar.CalendarSource
import com.karelisio.bulle.modules.echo.EchoHeart
import com.karelisio.bulle.modules.echo.EchoPink
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.sync.AudioOutputTracker
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.ui.PlayPauseButton
import com.karelisio.bulle.modules.music.ui.SyncedLyrics
import com.karelisio.bulle.modules.music.ui.outputIcon
import com.karelisio.bulle.modules.music.ui.outputLabel
import com.karelisio.bulle.modules.standby.EchoThought
import com.karelisio.bulle.modules.standby.StandbyState
import com.karelisio.bulle.modules.weather.WeatherKind
import com.karelisio.bulle.modules.weather.WeatherRepository
import com.karelisio.bulle.modules.weather.formatTemperature
import com.karelisio.bulle.modules.weather.ui.WeatherGlyph
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Carte d'un panneau de Veille (fond très sombre, grands coins M3 Expressive). */
@Composable
fun StandbyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Placeholder(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Météo : module Météo existant (dernière prévision, en cache hors ligne). */
@Composable
fun WeatherPanel() {
    val context = LocalContext.current
    val forecast by WeatherRepository.latest.collectAsState()
    LaunchedEffect(Unit) { if (WeatherRepository.latest.value == null) WeatherRepository.cached(context) }
    val f = forecast
    StandbyCard {
        if (f?.temperature == null) {
            Placeholder(Icons.Rounded.Event, stringResource(R.string.standby_weather_none))
        } else {
            val kind = WeatherKind.of(f.weatherCode)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherGlyph(kind, f.isDay, size = 96.dp)
                Text(formatTemperature(f.temperature), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(kind.labelRes), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Prochain événement de l'agenda (24 h), si l'accès à l'agenda est accordé. */
@Composable
fun NextEventPanel(now: Long) {
    val context = LocalContext.current
    var event by remember { mutableStateOf<CalendarEvent?>(null) }
    var allowed by remember { mutableStateOf(true) }
    LaunchedEffect(now / 60_000L) {
        allowed = CalendarSource.hasPermission(context)
        event = if (allowed) withContext(Dispatchers.IO) {
            runCatching { CalendarSource.query(context, now, now + 24 * 3_600_000L) }.getOrDefault(emptyList())
                .filter { !it.allDay && it.end > now }.minByOrNull { it.begin }
        } else null
    }
    StandbyCard {
        val e = event
        if (e == null) {
            Placeholder(Icons.Rounded.Event, stringResource(if (allowed) R.string.standby_event_none else R.string.standby_event_permission))
        } else {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.standby_event_next), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Text(e.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.begin)) + " – " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.end)),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                )
                e.location?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Batterie du téléphone et des écouteurs Bluetooth (module Batterie). */
@Composable
fun BatteryPanel(now: Long) {
    val context = LocalContext.current
    val phone = remember(now / 30_000L) { BatteryReader.current(context) }
    val accessories by remember {
        if (BluetoothBatteries.granted(context)) BluetoothBatteries.accessories(context) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    StandbyCard {
        Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            phone?.let { b ->
                BatteryLine(Icons.Rounded.BatteryChargingFull, stringResource(R.string.standby_battery_phone), b.level)
            }
            accessories.filter { it.level != null }.take(3).forEach { a ->
                BatteryLine(Icons.Rounded.Headphones, a.name ?: stringResource(R.string.standby_battery_accessory), a.level ?: 0)
            }
        }
    }
}

@Composable
private fun BatteryLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { level / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Text("$level %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Activité en cours (minuteur, chrono, navigation, livraison) : sa vue étendue de l'île, telle
 * quelle, sur fond noir. Sans activité : invitation discrète.
 */
@Composable
fun LivePanel(content: IslandContent?, host: IslandHost?) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)).background(androidx.compose.ui.graphics.Color.Black).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null && host != null) {
            content.Expanded(host)
        } else {
            Placeholder(Icons.Rounded.HourglassEmpty, stringResource(R.string.standby_live_none))
        }
    }
}

/**
 * Musique : pochette qui respire, titre, barre de lecture qu'on fait glisser, retour / avance de
 * 10 s, morceau précédent / suivant (module Musique).
 */
@Composable
fun MusicPanel(music: MusicContent?) {
    val reduce = rememberReduceMotion()
    StandbyCard {
        if (music == null) {
            Placeholder(Icons.Rounded.MusicNote, stringResource(R.string.standby_music_none))
            return@StandbyCard
        }
        val breathe: State<Float> = if (reduce || !music.playback.isPlaying) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "breathe").animateFloat(
                0.96f, 1.03f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "s",
            )
        }
        val controls = music.controller.transportControls
        val duration = music.track.durationMs
        var position by remember { mutableStateOf(0L) }
        var dragging by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(music.playback, duration) {
            while (true) {
                position = music.playback.positionAt(android.os.SystemClock.elapsedRealtime(), duration)
                delay(250)
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.fillMaxHeight(0.92f).aspectRatio(1f)
                        .graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }
                        .clip(RoundedCornerShape(24.dp))
                        .background(music.accent.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    music.artwork?.image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(music.track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(music.track.artist, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (duration > 0) {
                val shown = dragging ?: (position / duration.toFloat()).coerceIn(0f, 1f)
                Slider(
                    value = shown,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        StandbyState.markMediaTouch()
                        dragging?.let { controls.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    colors = SliderDefaults.colors(thumbColor = music.accent, activeTrackColor = music.accent),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime((shown * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Commandes sur toute la largeur (aucune n'est rognée dans un panneau étroit).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { StandbyState.markMediaTouch(); controls.skipToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.action_previous_track), Modifier.size(32.dp)) }
                IconButton(onClick = { controls.seekTo((position - 10_000L).coerceAtLeast(0L)) }) {
                    Icon(Icons.Rounded.Replay10, stringResource(R.string.standby_back10), Modifier.size(28.dp))
                }
                PlayPauseButton(
                    playing = music.playback.isPlaying,
                    accent = music.accent,
                    contentDescription = stringResource(R.string.action_play_pause),
                    onClick = { StandbyState.markMediaTouch(); if (music.playback.isPlaying) controls.pause() else controls.play() },
                    size = 56.dp,
                )
                IconButton(onClick = { controls.seekTo(if (duration > 0) (position + 10_000L).coerceAtMost(duration) else position + 10_000L) }) {
                    Icon(Icons.Rounded.Forward10, stringResource(R.string.standby_forward10), Modifier.size(28.dp))
                }
                IconButton(onClick = { StandbyState.markMediaTouch(); controls.skipToNext() }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.action_next_track), Modifier.size(32.dp)) }
            }
        }
    }
}

/** File d'attente du lecteur : toucher un titre pour y aller (titre en cours mis en avant). */
@Composable
fun QueuePanel(music: MusicContent?) {
    val queue = remember(music?.track?.identity) { runCatching { music?.controller?.queue.orEmpty() }.getOrDefault(emptyList()) }
    val activeId = music?.controller?.playbackState?.activeQueueItemId
    StandbyCard {
        if (music == null || queue.isEmpty()) {
            Placeholder(Icons.Rounded.MusicNote, stringResource(R.string.standby_queue_none))
            return@StandbyCard
        }
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(activeId) {
            val i = queue.indexOfFirst { it.queueId == activeId }
            if (i > 0) state.scrollToItem((i - 1).coerceAtLeast(0))
        }
        Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
            Text(
                stringResource(R.string.standby_queue_title),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                items(queue.size) { i ->
                    val item = queue[i]
                    val current = item.queueId == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { music.controller.transportControls.skipToQueueItem(item.queueId) }
                            .background(if (current) music.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.description.title?.toString().orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                color = if (current) music.accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            item.description.subtitle?.let {
                                Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(java.util.Locale.getDefault(), "%d:%02d", s / 60, s % 60)
}

/** Paroles synchronisées (module Paroles) et sortie audio en cours. */
@Composable
fun LyricsPanel(music: MusicContent?) {
    val context = LocalContext.current
    val output by remember { AudioOutputTracker.observe(context) }.collectAsState(AudioOutputTracker.current(context))
    var position by remember { mutableStateOf(0L) }
    LaunchedEffect(music?.playback) {
        while (music != null) {
            position = music.playback.positionAt(android.os.SystemClock.elapsedRealtime(), music.track.durationMs)
            delay(200)
        }
    }
    StandbyCard {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(outputIcon(output.kind), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(outputLabel(output.kind, output.name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            val lyrics = (music?.lyrics as? LyricsState.Found)?.lyrics
            if (lyrics != null && lyrics.synced) {
                val offset = music.lyricsPrefs.offsetFor(output.key)
                SyncedLyrics(lyrics, LyricsSync.currentLine(lyrics, position, offset), height = 260.dp, textScale = 1.25f)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Placeholder(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.standby_lyrics_none))
                }
            }
        }
    }
}

/** Écho : le cœur, et le dernier « pense à toi » reçu. */
@Composable
fun EchoPanel(thought: EchoThought?) {
    StandbyCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            EchoHeart(size = 150.dp, particles = true)
            Text(
                thought?.title ?: stringResource(R.string.standby_echo_none),
                style = MaterialTheme.typography.headlineSmall, color = EchoPink, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            thought?.let {
                Text(
                    it.text ?: DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.at)),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compte à rebours (module Compte à rebours) : le prochain événement, « J-12 ». */
@Composable
fun CountdownPanel(now: Long) {
    val context = LocalContext.current
    val data by remember { com.karelisio.bulle.modules.countdown.CountdownPrefs.store.loaded(context) }.collectAsState()
    val next = remember(data, now / 60_000L) {
        com.karelisio.bulle.modules.countdown.CountdownMath.upcoming(data.events, java.time.ZonedDateTime.now()).firstOrNull()
    }
    StandbyCard {
        if (next == null) {
            Placeholder(Icons.Rounded.Event, stringResource(R.string.standby_countdown_none))
        } else {
            val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), next.date)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                Text(next.event.emoji, style = MaterialTheme.typography.displaySmall)
                Text(
                    if (days <= 0) stringResource(R.string.standby_countdown_today) else stringResource(R.string.standby_countdown_days, days),
                    style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
                    color = androidx.compose.ui.graphics.Color(next.event.color.toInt()),
                )
                Text(next.event.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
