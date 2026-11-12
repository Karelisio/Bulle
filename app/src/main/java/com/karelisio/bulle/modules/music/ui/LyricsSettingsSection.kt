package com.karelisio.bulle.modules.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.music.MusicSettings
import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.sync.AudioOutput
import com.karelisio.bulle.modules.music.sync.AudioOutputTracker
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.sync.OutputKind
import com.karelisio.bulle.modules.music.sync.lyricsPrefs
import com.karelisio.bulle.modules.music.sync.withOffset
import com.karelisio.bulle.modules.music.sync.withoutOffset
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/** Titre de section des réglages. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp),
    )
}

/** Réglage à curseur : la valeur suit le doigt et n'est enregistrée qu'au relâchement. */
@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onDone: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(valueLabel)
                Slider(
                    value = value,
                    onValueChange = onValue,
                    onValueChangeFinished = onDone,
                    valueRange = range,
                    steps = steps,
                    enabled = enabled,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Paroles d'exemple pour l'aperçu (une ligne toutes les deux secondes). */
@Composable
private fun previewLyrics(): Lyrics {
    val lines = stringArrayResource(R.array.music_preview_lines)
    return remember(lines.toList()) {
        Lyrics(lines.mapIndexed { i, t -> LyricLine(i * 2_000L, t) }, synced = true, source = LyricsSource.LRCLIB)
    }
}

/** Aperçu de la zone de paroles telle qu'elle apparaît dans la carte étendue (fond de l'île). */
@Composable
internal fun LyricsPreview(textScale: Float, visibleLines: Int, focused: Boolean, modifier: Modifier = Modifier) {
    val lyrics = previewLyrics()
    val reduceMotion = rememberReduceMotion()
    var index by remember { mutableIntStateOf(1) }
    // Défile tout seul, sauf si les animations sont coupées (pas de boucle infinie).
    LaunchedEffect(reduceMotion, lyrics) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(2_000)
            index = (index + 1) % lyrics.lines.size
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A0A0C))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.music_preview),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(6.dp))
        SyncedLyrics(
            lyrics,
            currentIndex = index,
            height = lyricsWindowHeight(lyricsWindowLines(visibleLines, focused), textScale),
            textScale = textScale,
        )
    }
}

/** Section « Affichage des paroles » : taille du texte, hauteur de la fenêtre, mode paroles, aperçu. */
@Composable
internal fun LyricsDisplaySettings(music: MusicSettings, enabled: Boolean, update: ((MusicSettings) -> MusicSettings) -> Unit) {
    val prefs = music.lyricsPrefs()
    var scale by remember(prefs.textScale) { mutableFloatStateOf(prefs.textScale) }
    var lines by remember(prefs.visibleLines) { mutableFloatStateOf(prefs.visibleLines.toFloat()) }
    val shownLines = LyricsSync.clampLines(Math.round(lines))
    val shownScale = LyricsSync.clampTextScale(scale)

    SectionTitle(stringResource(R.string.music_section_display))
    SliderRow(
        icon = Icons.Rounded.FormatSize,
        title = stringResource(R.string.music_setting_text_size),
        valueLabel = "${Math.round(shownScale * 100)} %",
        value = scale,
        range = LyricsSync.MIN_TEXT_SCALE..LyricsSync.MAX_TEXT_SCALE,
        steps = 7,
        enabled = enabled,
        onValue = { scale = it },
        onDone = { update { it.copy(lyricsTextScale = shownScale) } },
    )
    SliderRow(
        icon = Icons.Rounded.Height,
        title = stringResource(R.string.music_setting_window),
        valueLabel = pluralStringResource(R.plurals.music_setting_window_lines, shownLines, shownLines),
        value = lines,
        range = LyricsSync.MIN_LINES.toFloat()..LyricsSync.MAX_LINES.toFloat(),
        steps = LyricsSync.MAX_LINES - LyricsSync.MIN_LINES - 1,
        enabled = enabled,
        onValue = { lines = it },
        onDone = { update { it.copy(lyricsVisibleLines = shownLines) } },
    )
    ListItem(
        leadingContent = { Icon(Icons.Rounded.OpenInFull, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.music_setting_focus)) },
        supportingContent = { Text(stringResource(R.string.music_setting_focus_summary)) },
        trailingContent = {
            Switch(
                checked = music.lyricsFocusMode,
                onCheckedChange = { on -> update { it.copy(lyricsFocusMode = on) } },
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    LyricsPreview(shownScale, shownLines, music.lyricsFocusMode, Modifier.padding(horizontal = 4.dp))
}

/** Ligne de la liste des sorties : null = valeur par défaut des sorties pas encore réglées. */
private data class SyncTarget(val key: String?, val kind: OutputKind, val name: String)

/** Section « Synchronisation des paroles » : décalage de chaque sortie connue, modifiable et réinitialisable. */
@Composable
internal fun LyricsSyncSettings(music: MusicSettings, enabled: Boolean, update: ((MusicSettings) -> MusicSettings) -> Unit) {
    val context = LocalContext.current.applicationContext
    val initial = remember(context) { AudioOutputTracker.current(context) }
    val current by remember(context) { AudioOutputTracker.observe(context) }.collectAsStateWithLifecycle(initial)
    val prefs = music.lyricsPrefs()
    var editing by remember { mutableStateOf<SyncTarget?>(null) }

    SectionTitle(stringResource(R.string.music_section_sync))
    Text(
        stringResource(R.string.music_sync_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    // Sortie en cours d'abord, puis le haut-parleur, puis les autres sorties mémorisées.
    val targets = buildList {
        add(SyncTarget(current.key, current.kind, current.name.ifBlank { music.lyricsOutputNames[current.key].orEmpty() }))
        if (current.key != AudioOutput.SPEAKER_KEY) add(SyncTarget(AudioOutput.SPEAKER_KEY, OutputKind.SPEAKER, ""))
        music.lyricsOffsets.keys.sorted()
            .filter { it != current.key && it != AudioOutput.SPEAKER_KEY }
            .forEach { add(SyncTarget(it, AudioOutput.kindOfKey(it), music.lyricsOutputNames[it].orEmpty())) }
        add(SyncTarget(null, OutputKind.OTHER, ""))
    }
    targets.forEach { t ->
        val key = t.key
        val isSet = if (key == null) prefs.defaultOffsetMs != 0 else key in music.lyricsOffsets
        val value = if (key == null) prefs.defaultOffsetMs else prefs.offsetFor(key)
        val label = if (key == null) stringResource(R.string.music_sync_default) else outputLabel(t.kind, t.name)
        val valueText = LyricsSync.formatOffset(value).let {
            if (key == null || isSet) it else stringResource(R.string.music_sync_not_set, it)
        }
        ListItem(
            leadingContent = { Icon(if (key == null) Icons.Rounded.Tune else outputIcon(t.kind), contentDescription = null) },
            headlineContent = { Text(label) },
            supportingContent = {
                Text(if (key == current.key) "$valueText · ${stringResource(R.string.music_sync_current)}" else valueText)
            },
            trailingContent = {
                if (isSet) {
                    IconButton(
                        enabled = enabled,
                        onClick = {
                            update { m -> if (key == null) m.copy(lyricsDefaultOffsetMs = 0) else m.withoutOffset(key) }
                        },
                    ) { Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.music_sync_reset)) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { editing = t },
        )
    }

    editing?.let { t ->
        val key = t.key
        val start = if (key == null) prefs.defaultOffsetMs else prefs.offsetFor(key)
        OffsetDialog(
            title = if (key == null) stringResource(R.string.music_sync_default) else outputLabel(t.kind, t.name),
            initialMs = start,
            onDismiss = { editing = null },
            onReset = {
                update { m -> if (key == null) m.copy(lyricsDefaultOffsetMs = 0) else m.withoutOffset(key) }
                editing = null
            },
            onConfirm = { ms ->
                update { m ->
                    if (key == null) m.copy(lyricsDefaultOffsetMs = LyricsSync.clampOffset(ms))
                    else m.withOffset(AudioOutput(key, t.kind, t.name), ms)
                }
                editing = null
            },
        )
    }
}

/** Boîte de réglage d'un décalage : curseur de −2000 à +3000 ms, ajustement fin par 50 ms. */
@Composable
private fun OffsetDialog(title: String, initialMs: Int, onDismiss: () -> Unit, onReset: () -> Unit, onConfirm: (Int) -> Unit) {
    var ms by remember(initialMs) { mutableIntStateOf(initialMs) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.music_sync_dialog_summary), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    LyricsSync.formatOffset(ms),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = ms.toFloat(),
                    onValueChange = { ms = LyricsSync.clampOffset(Math.round(it)) },
                    valueRange = LyricsSync.MIN_OFFSET_MS.toFloat()..LyricsSync.MAX_OFFSET_MS.toFloat(),
                    steps = (LyricsSync.MAX_OFFSET_MS - LyricsSync.MIN_OFFSET_MS) / LyricsSync.STEP_MS - 1,
                )
                OffsetStepper(ms, onChange = { ms = it }, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(ms) }) { Text(stringResource(R.string.music_sync_done)) } },
        dismissButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.music_sync_reset)) } },
    )
}
