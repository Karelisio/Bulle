package com.karelisio.bulle.modules.music

import com.karelisio.bulle.modules.music.karaoke.KaraokeProgress
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.LyricWord
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remplissage karaoké (fonction pure) et balises mot à mot du parseur LRC. */
class KaraokeProgressTest {

    private fun parse(text: String): Lyrics = LrcParser.parse(text, LyricsSource.LRCLIB)!!

    @Test
    fun interpolatesBetweenLineAndNext() {
        // 10 caractères, ligne suivante 2 s plus tard : fin à 92 % de l'écart (1 840 ms), sous la durée naturelle.
        val line = LyricLine(10_000, "abcdefghij")
        val next = 12_000L
        assertEquals(0f, KaraokeProgress.filledChars(line, next, 9_000), 0f)
        assertEquals(0f, KaraokeProgress.filledChars(line, next, 10_000), 0f)
        assertEquals(5f, KaraokeProgress.filledChars(line, next, 10_920), 0.01f)
        assertEquals(10f, KaraokeProgress.filledChars(line, next, 11_840), 0.01f)
        assertEquals(10f, KaraokeProgress.filledChars(line, next, 11_999), 0f)
        assertEquals(1f, KaraokeProgress.fraction(line, next, 20_000), 0f)
    }

    @Test
    fun wordsWithoutTagsFollowSyllablesNotLetters() {
        // « the » et « strength » : une syllabe chacun → la moitié du temps chacun (et non 3/11 contre 8/11).
        assertEquals(4f, KaraokeProgress.estimatedFill("the strength", 0.5f), 0.01f)
        assertEquals(0f, KaraokeProgress.estimatedFill("the strength", 0f), 0f)
        assertEquals(12f, KaraokeProgress.estimatedFill("the strength", 1f), 0f)
        assertEquals(3, KaraokeProgress.syllables("beautiful"))
        assertEquals(1, KaraokeProgress.syllables("rêve"))
        // Monotone sur toute la ligne.
        var last = 0f
        for (i in 0..100) {
            val f = KaraokeProgress.estimatedFill("Je t'aime, mon amour, toujours", i / 100f)
            assertTrue(f >= last)
            last = f
        }
    }

    @Test
    fun longGapIsCappedToNaturalDuration() {
        // Pont instrumental de 30 s après une ligne courte : remplissage en durée « naturelle ».
        val line = LyricLine(0, "abcd")
        val end = KaraokeProgress.lineEnd(line, 30_000)
        assertEquals(KaraokeProgress.BASE_MS + 4 * KaraokeProgress.PER_CHAR_MS, end)
        assertEquals(4f, KaraokeProgress.filledChars(line, 30_000, end), 0f)
        // Dernière ligne (pas de suivante) : durée naturelle aussi.
        assertEquals(end, KaraokeProgress.lineEnd(line, null))
        // Une fin explicite (balise finale) l'emporte.
        assertEquals(2_000L, KaraokeProgress.lineEnd(line.copy(endMs = 2_000), 30_000))
    }

    @Test
    fun progressIsMonotonic() {
        val line = LyricLine(1_000, "Une ligne assez longue pour tester")
        var prev = -1f
        for (t in 0L..8_000L step 50) {
            val f = KaraokeProgress.filledChars(line, 6_000, t)
            assertTrue("t=$t", f >= prev)
            prev = f
        }
        assertEquals(line.text.length.toFloat(), prev, 0f)
    }

    @Test
    fun wordTagsDriveTheFill() {
        val lyrics = parse("[00:01.00]<00:01.00>Mot <00:01.50>par <00:02.00>mot<00:02.40>\n[00:05.00]suite")
        val line = lyrics.lines[0]
        assertEquals("Mot par mot", line.text)
        assertEquals(listOf(LyricWord(1_000, "Mot "), LyricWord(1_500, "par "), LyricWord(2_000, "mot")), line.words)
        assertEquals(2_400L, line.endMs)
        assertEquals(line.text, line.words.joinToString("") { it.text })
        // Mi-chemin du premier mot (4 caractères « Mot ») : 2 caractères.
        assertEquals(2f, KaraokeProgress.filledCharsAt(lyrics, 0, 1_250), 0.01f)
        // Début du deuxième mot : le premier est entièrement rempli.
        assertEquals(4f, KaraokeProgress.filledCharsAt(lyrics, 0, 1_500), 0.01f)
        // Dernier mot jusqu'à la balise finale (2,4 s).
        assertEquals(8f + 1.5f, KaraokeProgress.filledCharsAt(lyrics, 0, 2_200), 0.01f)
        assertEquals(11f, KaraokeProgress.filledCharsAt(lyrics, 0, 2_400), 0f)
        // Hors bornes : rien.
        assertEquals(0f, KaraokeProgress.filledCharsAt(lyrics, 7, 2_400), 0f)
    }

    @Test
    fun lastWordWithoutEndTagStopsBeforeNextLine() {
        val lyrics = parse("[00:01.00]<00:01.00>la <00:01.20>fin\n[00:01.50]vite")
        val line = lyrics.lines[0]
        assertEquals(-1L, line.endMs)
        // Le dernier mot (« fin ») ne déborde pas sur la ligne suivante (1,5 s).
        assertEquals(line.text.length.toFloat(), KaraokeProgress.filledCharsAt(lyrics, 0, 1_500), 0f)
    }

    @Test
    fun wordTagsFollowOffsetsAndRepeats() {
        val lyrics = parse("[offset:+500]\n[00:10.00][00:20.00]<00:10.00>re <00:10.50>frain")
        assertEquals(listOf(9_500L, 19_500L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf(9_500L, 10_000L), lyrics.lines[0].words.map { it.timeMs })
        assertEquals(listOf(19_500L, 20_000L), lyrics.lines[1].words.map { it.timeMs })
    }

    @Test
    fun textBeforeFirstTagAndSpacesAreNormalized() {
        val l = parse("[00:01.00]Oh   <00:01.40>  oui <00:01.80> <00:02.00>!").lines[0]
        assertEquals("Oh oui !", l.text)
        assertEquals(1_000L, l.words.first().timeMs)
        assertEquals(l.text, l.words.joinToString("") { it.text })
    }

    @Test
    fun emptyLineIsFilledAtOnce() {
        val line = LyricLine(1_000, "")
        assertEquals(0f, KaraokeProgress.fraction(line, 2_000, 500), 0f)
        assertEquals(1f, KaraokeProgress.fraction(line, 2_000, 1_000), 0f)
    }

    @Test
    fun seekTargetAppliesOutputOffset() {
        val line = LyricLine(10_000, "x")
        assertEquals(10_250L, KaraokeProgress.seekTargetFor(line, 250))
        assertEquals(9_800L, KaraokeProgress.seekTargetFor(line, -200))
        assertEquals(0L, KaraokeProgress.seekTargetFor(LyricLine(100, "x"), -500))
    }
}
