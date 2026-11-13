package com.karelisio.bulle.modules.music.karaoke

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.sync.AudioOutputTracker
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.ui.ArtworkImage
import com.karelisio.bulle.modules.music.ui.PlayPauseButton
import com.karelisio.bulle.modules.music.ui.WavyScrubber
import com.karelisio.bulle.modules.music.ui.formatTime
import com.karelisio.bulle.modules.music.ui.lineAlpha
import com.karelisio.bulle.modules.music.visualizer.AudioWave
import com.karelisio.bulle.modules.music.visualizer.rememberRealSpectrum
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/** Taille de base des paroles karaoké (multipliée par la taille choisie dans les réglages). */
private const val KARAOKE_FONT_SP = 32f
private const val KARAOKE_LINE_HEIGHT_SP = 40f

/** Opacité de la partie pas encore chantée de la ligne courante. */
private const val UNSUNG_ALPHA = 0.36f

/** Échelle des lignes voisines par rapport à la ligne courante. */
private const val INACTIVE_SCALE = 0.86f

/** Distance de glissement vers le bas (dp) au-delà de laquelle la vue se ferme. */
private const val DISMISS_DP = 140f

/**
 * Vue karaoké : fond = pochette floutée + dégradé aux couleurs de la pochette, paroles très grandes
 * dont la ligne courante se remplit au fil du chant, contrôles discrets en bas.
 * [active] : vue réellement à l'écran (activité démarrée) — conditionne le visualiseur audio.
 */
@Composable
internal fun KaraokeScreen(content: MusicContent, onClose: () -> Unit, active: Boolean = true) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val controls = content.controller.transportControls
    val duration = content.track.durationMs
    val accent = content.accent
    val density = LocalDensity.current

    // État de lecture local, modifié de façon optimiste (lecture/pause, recherche).
    var base by remember(content.playback) { mutableStateOf(content.playback) }
    val position = remember { mutableLongStateOf(base.positionAt(SystemClock.elapsedRealtime(), duration)) }
    LaunchedEffect(base, duration, reduceMotion, active) {
        while (true) {
            position.longValue = base.positionAt(SystemClock.elapsedRealtime(), duration)
            if (!base.isPlaying || !active) break
            if (reduceMotion) delay(250) else withFrameMillis { }
        }
    }
    val playing = base.isPlaying
    fun now() = SystemClock.elapsedRealtime()
    fun seekTo(target: Long) {
        controls.seekTo(target)
        base = base.copy(positionMs = target, updatedAt = now())
        position.longValue = target
    }
    fun togglePlay() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (playing) controls.pause() else controls.play()
        val p = base.positionAt(now(), duration)
        base = base.copy(
            state = if (playing) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
            positionMs = p,
            updatedAt = now(),
            speed = base.speed.takeIf { it > 0f } ?: 1f,
        )
    }

    // Décalage de synchronisation de la sortie audio courante (suivie en direct).
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val initialOutput = remember(appContext) { AudioOutputTracker.current(appContext) }
    val output by remember(appContext) { AudioOutputTracker.observe(appContext) }.collectAsState(initialOutput)
    val offset = content.lyricsPrefs.offsetFor(output.key)

    // Glisser vers le bas pour fermer ; entrée par le bas au ressort.
    val drag = remember { Animatable(0f) }
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(density) { DISMISS_DP.dp.toPx() }

    val spectrum = rememberRealSpectrum(content.realVisualizer && !reduceMotion, playing, active)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInputDismiss(drag, dismissPx, onClose, scope),
    ) {
        KaraokeBackground(content.artwork?.image, accent, reduceMotion)
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val e = enter.value
                    translationY = drag.value + (1f - e) * 80.dp.toPx()
                    alpha = (e * (1f - drag.value / (dismissPx * 2.5f))).coerceIn(0f, 1f)
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            KaraokeHeader(content, accent, onClose)
            val lyrics = (content.lyrics as? LyricsState.Found)?.lyrics
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics == null -> Text(
                        stringResource(R.string.music_karaoke_no_lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    lyrics.synced -> KaraokeLyrics(
                        lyrics = lyrics,
                        position = position,
                        offsetMs = offset,
                        textScale = content.lyricsPrefs.textScale,
                        reduceMotion = reduceMotion,
                        onSeekLine = { line ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            seekTo(KaraokeProgress.seekTargetFor(line, offset))
                        },
                    )
                    else -> PlainKaraokeLyrics(lyrics, content.lyricsPrefs.textScale)
                }
            }
            KaraokeControls(
                content = content,
                base = base,
                playing = playing,
                position = position,
                accent = accent,
                spectrum = spectrum,
                onSeek = ::seekTo,
                onTogglePlay = ::togglePlay,
            )
        }
    }
}

/** Geste « glisser vers le bas pour fermer » : suit le doigt puis ferme ou revient au ressort. */
private fun Modifier.pointerInputDismiss(
    drag: Animatable<Float, AnimationVector1D>,
    dismissPx: Float,
    onClose: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dy ->
                change.consume()
                scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
            },
            onDragEnd = {
                if (drag.value > dismissPx) {
                    onClose()
                } else {
                    scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
                }
            },
            onDragCancel = { scope.launch { drag.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) } },
        )
    },
)

/** Fond : pochette très floutée et agrandie, sous un dégradé aux couleurs de la pochette. */
@Composable
private fun KaraokeBackground(image: ImageBitmap?, accent: Color, reduceMotion: Boolean) {
    val deep = lerp(accent, Color.Black, 0.55f)
    Box(Modifier.fillMaxSize()) {
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                        alpha = 0.75f
                    }
                    .blur(if (reduceMotion) 48.dp else 72.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.55f),
                        0.45f to deep.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )
    }
}

@Composable
private fun KaraokeHeader(content: MusicContent, accent: Color, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            content.artwork?.image,
            accent,
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                content.track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.track.artist.isNotBlank()) {
                Text(
                    content.track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.music_karaoke_close),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Style d'une ligne karaoké : très grand, très gras. */
@Composable
private fun karaokeStyle(scale: Float): TextStyle = MaterialTheme.typography.displaySmall.copy(
    fontSize = (KARAOKE_FONT_SP * scale).sp,
    lineHeight = (KARAOKE_LINE_HEIGHT_SP * scale).sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.3).sp,
)

/** Mesures partagées entre la mise en page et l'animation du défilement. */
private class KaraokeScroll {
    val offset = Animatable(0f)
    val target = mutableFloatStateOf(Float.NaN)
}

/**
 * Paroles synchronisées en très grand : défilement doux au ressort, ligne courante remplie
 * progressivement (mot à mot si le LRC le permet), toucher une ligne = aller à ce moment.
 */
@Composable
internal fun KaraokeLyrics(
    lyrics: Lyrics,
    position: MutableLongState,
    offsetMs: Int,
    textScale: Float,
    reduceMotion: Boolean,
    onSeekLine: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOffset by rememberUpdatedState(offsetMs)
    val index by remember(lyrics) {
        derivedStateOf { LyricsSync.currentLine(lyrics, position.longValue, currentOffset) }
    }
    val scroll = remember(lyrics) { KaraokeScroll() }
    LaunchedEffect(scroll, reduceMotion) {
        var first = true
        snapshotFlow { scroll.target.floatValue }.collect { t ->
            if (t.isNaN()) return@collect
            if (first || reduceMotion) {
                scroll.offset.snapTo(t)
                first = false
            } else {
                scroll.offset.animateTo(t, spring(dampingRatio = 0.82f, stiffness = 120f))
            }
        }
    }
    val style = karaokeStyle(textScale)
    val spacing = 18.dp
    val seekLabel = stringResource(R.string.music_karaoke_seek)
    Layout(
        content = {
            lyrics.lines.forEachIndexed { i, line ->
                val current = i == index
                KaraokeLine(
                    text = line.text.ifBlank { "♪" },
                    distance = if (index < 0) i + 1 else abs(i - index),
                    style = style,
                    reduceMotion = reduceMotion,
                    // Le remplissage est lu dans la phase de dessin, à chaque image.
                    filledChars = if (current && line.text.isNotBlank() && !reduceMotion) {
                        { KaraokeProgress.filledCharsAt(lyrics, i, LyricsSync.lyricsPosition(position.longValue, currentOffset)) }
                    } else {
                        null
                    },
                    onClickLabel = seekLabel,
                    onClick = { onSeekLine(line) },
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .fadingEdges(),
    ) { measurables, constraints ->
        val lineConstraints = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(lineConstraints) }
        val gap = spacing.roundToPx()
        val tops = IntArray(placeables.size)
        var y = 0
        placeables.forEachIndexed { i, p -> tops[i] = y; y += p.height + gap }
        val viewport = constraints.maxHeight
        if (placeables.isNotEmpty()) {
            val idx = index.coerceIn(0, placeables.lastIndex)
            scroll.target.floatValue = tops[idx] + placeables[idx].height / 2f - viewport * 0.36f
        }
        layout(constraints.maxWidth, viewport) {
            val off = scroll.offset.value
            placeables.forEachIndexed { i, p ->
                val top = tops[i] - off
                if (top + p.height >= -viewport && top <= viewport * 2) p.place(0, top.toInt())
            }
        }
    }
}

/** Fondu en haut et en bas de la zone de paroles. */
private fun Modifier.fadingEdges(edge: Float = 0.12f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                edge to Color.Black,
                (1f - edge) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Mise en page du texte, gardée hors de l'état Compose (lue au dessin seulement). */
private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * Une ligne karaoké. Ligne courante avec [filledChars] : partie chantée en blanc plein, reste atténué,
 * la frontière avançant caractère par caractère (sur plusieurs lignes visuelles si le texte passe à
 * la ligne). Sans [filledChars] (autres lignes, ou animations réduites) : ligne entière à son opacité.
 */
@Composable
private fun KaraokeLine(
    text: String,
    distance: Int,
    style: TextStyle,
    reduceMotion: Boolean,
    filledChars: (() -> Float)?,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val spec = if (reduceMotion) snap() else spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val active by animateFloatAsState(if (distance == 0) 1f else 0f, spec, label = "active")
    val alpha by animateFloatAsState(lineAlpha(distance), spec, label = "alpha")
    val holder = remember { LayoutHolder() }
    val dimPaint = remember { Paint().apply { this.alpha = UNSUNG_ALPHA } }
    Text(
        text,
        style = style,
        color = Color.White,
        onTextLayout = { holder.layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .graphicsLayer {
                this.alpha = alpha
                val s = INACTIVE_SCALE + (1f - INACTIVE_SCALE) * active
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .drawWithContent {
                val layout = holder.layout
                if (filledChars == null || layout == null) {
                    drawContent()
                    return@drawWithContent
                }
                val filled = filledChars()
                // Passe atténuée : toute la ligne.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), dimPaint)
                    drawContent()
                    canvas.restore()
                }
                // Passe pleine : découpée ligne visuelle par ligne visuelle jusqu'à la frontière.
                for (l in 0 until layout.lineCount) {
                    val start = layout.getLineStart(l)
                    val end = layout.getLineEnd(l)
                    if (filled <= start) break
                    val right = if (filled >= end) {
                        layout.getLineRight(l)
                    } else {
                        val c = floor(filled).toInt().coerceIn(start, end - 1)
                        val box = layout.getBoundingBox(c)
                        box.left + box.width * (filled - c)
                    }
                    clipRect(left = 0f, top = layout.getLineTop(l), right = right, bottom = layout.getLineBottom(l)) {
                        this@drawWithContent.drawContent()
                    }
                }
            },
    )
}

/** Paroles non synchronisées : grand texte défilant au doigt. */
@Composable
private fun PlainKaraokeLyrics(lyrics: Lyrics, textScale: Float) {
    val style = karaokeStyle(textScale * 0.8f)
    Column(
        Modifier
            .fillMaxSize()
            .fadingEdges()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lyrics.lines.forEach { line ->
            Text(line.text, style = style, color = Color.White.copy(alpha = if (line.text.isBlank()) 0f else 0.9f))
        }
    }
}

/** Barre de lecture, onde du vrai son (si activée) et contrôles discrets. */
@Composable
private fun KaraokeControls(
    content: MusicContent,
    base: PlaybackSnapshot,
    playing: Boolean,
    position: MutableLongState,
    accent: Color,
    spectrum: androidx.compose.runtime.State<FloatArray?>?,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
) {
    val duration = content.track.durationMs
    var scrub by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        val hasWave by remember(spectrum) { derivedStateOf { spectrum?.value != null } }
        AnimatedVisibility(
            visible = hasWave && spectrum != null,
            enter = fadeIn(tween(300)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(200)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            if (spectrum != null) {
                AudioWave(spectrum, accent, Modifier.fillMaxWidth().height(36.dp).padding(bottom = 8.dp))
            }
        }
        val seekable = duration > 0 && (base.can(PlaybackState.ACTION_SEEK_TO) || base.actions == 0L)
        WavyScrubber(
            fraction = { if (duration > 0) position.longValue.toFloat() / duration else 0f },
            playing = playing,
            accent = accent,
            seekable = seekable,
            onScrub = { scrub = it },
            onSeek = { f -> onSeek((f * duration).toLong()) },
        )
        val elapsedSec by remember { derivedStateOf { (scrub?.let { (it * duration).toLong() } ?: position.longValue) / 1000 } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelSmall
            val color = Color.White.copy(alpha = 0.6f)
            Text(formatTime(elapsedSec * 1000), style = style, color = color)
            if (duration > 0) Text("-" + formatTime(duration - elapsedSec * 1000), style = style, color = color)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val controls = content.controller.transportControls
            IconButton(
                onClick = { controls.skipToPrevious() },
                enabled = base.can(PlaybackState.ACTION_SKIP_TO_PREVIOUS) || base.actions == 0L,
            ) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.music_previous), tint = Color.White.copy(alpha = 0.85f))
            }
            PlayPauseButton(
                playing = playing,
                accent = accent,
                contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play),
                onClick = onTogglePlay,
                size = 52.dp,
            )
            IconButton(
                onClick = { controls.skipToNext() },
                enabled = base.can(PlaybackState.ACTION_SKIP_TO_NEXT) || base.actions == 0L,
            ) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.music_next), tint = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
