package com.karelisio.bulle.modules.music

import com.karelisio.bulle.modules.music.lyrics.LrclibClient
import com.karelisio.bulle.modules.music.lyrics.LrclibTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrclibWordSyncTest {
    private val plain = LrclibTrack(duration = 200.0, syncedLyrics = "[00:01.00]Bonjour tout le monde")
    private val enhanced = LrclibTrack(duration = 201.0, syncedLyrics = "[00:01.00]<00:01.00>Bonjour <00:01.50>tout <00:01.80>le <00:02.00>monde")
    private val wrongDuration = LrclibTrack(duration = 260.0, syncedLyrics = enhanced.syncedLyrics)

    @Test
    fun picksOnlyWordTaggedLyricsWithMatchingDuration() {
        assertEquals(enhanced.syncedLyrics, LrclibClient.pickWordSynced(listOf(plain, enhanced), 200))
        assertNull(LrclibClient.pickWordSynced(listOf(plain), 200))
        assertNull(LrclibClient.pickWordSynced(listOf(wrongDuration), 200))
    }
}
