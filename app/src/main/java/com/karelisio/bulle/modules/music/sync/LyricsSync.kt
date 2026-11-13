package com.karelisio.bulle.modules.music.sync

import com.karelisio.bulle.modules.music.MusicSettings
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import kotlin.math.roundToInt

/**
 * Synchronisation des paroles : décalage par sortie audio et calcul de la ligne courante.
 *
 * Un casque Bluetooth fait entendre le son avec un retard (tampon du codec) : la position annoncée
 * par le lecteur est en avance sur ce qu'on entend. Avec un décalage positif de N ms, les paroles
 * suivent la position − N, donc s'allument N ms plus tard.
 */
object LyricsSync {
    const val MIN_OFFSET_MS = -2000
    const val MAX_OFFSET_MS = 3000
    const val STEP_MS = 50

    /** La ligne s'allume un instant avant d'être chantée (confort de lecture, comportement historique). */
    const val LEAD_MS = 250L

    const val MIN_TEXT_SCALE = 0.8f
    const val MAX_TEXT_SCALE = 1.6f
    const val MIN_LINES = 3
    const val MAX_LINES = 9
    const val DEFAULT_LINES = 5

    /** Décalage arrondi au pas de 50 ms et borné. */
    fun clampOffset(ms: Int): Int =
        ((ms.toFloat() / STEP_MS).roundToInt() * STEP_MS).coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    fun clampTextScale(scale: Float): Float =
        if (scale.isNaN()) 1f else ((scale * 10f).roundToInt() / 10f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

    fun clampLines(lines: Int): Int = lines.coerceIn(MIN_LINES, MAX_LINES)

    /** Position « entendue » à utiliser pour les paroles : position extrapolée − décalage. */
    fun lyricsPosition(positionMs: Long, offsetMs: Int): Long = positionMs - offsetMs

    /** Ligne courante des paroles à [positionMs] (position du lecteur) avec le décalage [offsetMs]. */
    fun currentLine(lyrics: Lyrics, positionMs: Long, offsetMs: Int): Int =
        lyrics.indexAt(lyricsPosition(positionMs, offsetMs) + LEAD_MS)

    /** Décalage affiché : « +250 ms », « 0 ms », « −150 ms » (vrai signe moins). */
    fun formatOffset(ms: Int): String = when {
        ms > 0 -> "+$ms ms"
        ms < 0 -> "−${-ms} ms"
        else -> "0 ms"
    }
}

/** Préférences d'affichage et de synchronisation des paroles, figées dans le contenu de l'île. */
data class LyricsPrefs(
    val offsets: Map<String, Int> = emptyMap(),
    val defaultOffsetMs: Int = 0,
    val textScale: Float = 1f,
    val visibleLines: Int = LyricsSync.DEFAULT_LINES,
    val focusMode: Boolean = false,
) {
    /** Décalage de la sortie [key] : le sien, sinon 0 pour le haut-parleur et le défaut global ailleurs. */
    fun offsetFor(key: String): Int = LyricsSync.clampOffset(
        offsets[key] ?: if (key == AudioOutput.SPEAKER_KEY) 0 else defaultOffsetMs,
    )
}

/** Préférences des paroles tirées des réglages (bornées : un JSON modifié à la main reste sûr). */
fun MusicSettings.lyricsPrefs(): LyricsPrefs = LyricsPrefs(
    offsets = lyricsOffsets,
    defaultOffsetMs = LyricsSync.clampOffset(lyricsDefaultOffsetMs),
    textScale = LyricsSync.clampTextScale(lyricsTextScale),
    visibleLines = LyricsSync.clampLines(lyricsVisibleLines),
    focusMode = lyricsFocusMode,
)

/** Mémorise le décalage de [output] (et son nom, pour l'affichage dans les réglages). */
fun MusicSettings.withOffset(output: AudioOutput, ms: Int): MusicSettings = copy(
    lyricsOffsets = lyricsOffsets + (output.key to LyricsSync.clampOffset(ms)),
    lyricsOutputNames = if (output.name.isBlank()) lyricsOutputNames else lyricsOutputNames + (output.key to output.name),
)

/** Oublie le décalage de la sortie [key] (elle reprend la valeur par défaut). */
fun MusicSettings.withoutOffset(key: String): MusicSettings = copy(
    lyricsOffsets = lyricsOffsets - key,
    lyricsOutputNames = lyricsOutputNames - key,
)
