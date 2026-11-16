package com.karelisio.bulle.modules.standby.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

/** Musique : pochette qui respire, titre, commandes (module Musique). */
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
        Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight(0.82f).aspectRatio(1f)
                    .graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }
                    .clip(RoundedCornerShape(28.dp))
                    .background(music.accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                music.artwork?.image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(music.track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(music.track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controls.skipToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.action_previous_track), Modifier.size(36.dp)) }
                    PlayPauseButton(
                        playing = music.playback.isPlaying,
                        accent = music.accent,
                        contentDescription = stringResource(R.string.action_play_pause),
                        onClick = { if (music.playback.isPlaying) controls.pause() else controls.play() },
                        size = 64.dp,
                    )
                    IconButton(onClick = { controls.skipToNext() }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.action_next_track), Modifier.size(36.dp)) }
                }
            }
        }
    }
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
