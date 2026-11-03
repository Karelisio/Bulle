package com.karelisio.bulle.modules.music

import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricsKey
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    private val BOM = Char(0xFEFF).toString()

    private fun parse(text: String) = LrcParser.parse(text, LyricsSource.LRCLIB)!!

    @Test
    fun parsesStandardLines() {
        val l = parse("[ar:Artiste]\n[ti:Titre]\n[00:12.34]Bonjour\n[01:02.5]Monde\n[02:03]Fin")
        assertTrue(l.synced)
        assertEquals(listOf(12_340L, 62_500L, 123_000L), l.lines.map { it.timeMs })
        assertEquals(listOf("Bonjour", "Monde", "Fin"), l.lines.map { it.text })
    }

    @Test
    fun handlesMillisecondsAndColonFraction() {
        val l = parse("[00:01.123]a\n[00:02:45]b\n[100:00.00]c")
        assertEquals(listOf(1_123L, 2_450L, 6_000_000L), l.lines.map { it.timeMs })
    }

    @Test
    fun multipleTimestampsOnOneLineAreSorted() {
        val l = parse("[00:10.00][00:30.00]Refrain\n[00:20.00]Couplet")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), l.lines.map { it.timeMs })
        assertEquals(listOf("Refrain", "Couplet", "Refrain"), l.lines.map { it.text })
    }

    @Test
    fun appliesOffset() {
        val positive = parse("[offset:+500]\n[00:10.00]a\n[00:00.20]b")
        assertEquals(listOf(0L, 9_500L), positive.lines.map { it.timeMs })
        val negative = parse("[offset:-250]\n[00:10.00]a")
        assertEquals(10_250L, negative.lines.single().timeMs)
    }

    @Test
    fun stripsWordTagsBomAndCarriageReturns() {
        val l = parse(BOM + "[00:01.00]<00:01.00>Mot <00:01.50>par <00:02.00>mot\r\n[00:03.00]  ")
        assertEquals(1, l.lines.size)
        assertEquals("Mot par mot", l.lines[0].text)
    }

    @Test
    fun keepsInstrumentalGapsInside() {
        val l = parse("[00:01.00]a\n[00:05.00]\n[00:09.00]b")
        assertEquals(listOf("a", "", "b"), l.lines.map { it.text })
    }

    @Test
    fun plainTextIsUnsynced() {
        val l = parse("\nPremière ligne\n\n\n\nDeuxième\n")
        assertFalse(l.synced)
        assertEquals(listOf("Première ligne", "", "Deuxième"), l.lines.map { it.text })
        assertEquals(-1, l.indexAt(10_000))
    }

    @Test
    fun emptyGivesNull() {
        assertNull(LrcParser.parse("  \n\n", LyricsSource.PLAYER))
        assertNull(LrcParser.parse("[ar:x]\n[ti:y]", LyricsSource.PLAYER))
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val l = parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, l.indexAt(500))
        assertEquals(0, l.indexAt(1_000))
        assertEquals(0, l.indexAt(4_999))
        assertEquals(1, l.indexAt(5_000))
        assertEquals(2, l.indexAt(60_000))
    }

    @Test
    fun detectsLrc() {
        assertTrue(LrcParser.looksLikeLrc("[00:01.00]a\n[00:02.00]b"))
        assertFalse(LrcParser.looksLikeLrc("[00:01.00]une seule"))
        assertFalse(LrcParser.looksLikeLrc("du texte\nsans horodatage"))
    }

    @Test
    fun cacheKeyIsNormalized() {
        assertEquals(LyricsKey.of("Daft Punk", "One More Time!", 320_400), LyricsKey.of("daft  punk", "one more time", 319_600))
        assertEquals("a|b|0", LyricsKey.of("A", "B", 0))
    }
}
