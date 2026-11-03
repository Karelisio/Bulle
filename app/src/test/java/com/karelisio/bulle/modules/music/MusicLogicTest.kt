package com.karelisio.bulle.modules.music

import android.media.session.PlaybackState
import com.karelisio.bulle.modules.music.lyrics.LrclibClient
import com.karelisio.bulle.modules.music.lyrics.LrclibTrack
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.SessionCandidate
import com.karelisio.bulle.modules.music.media.extrapolatePosition
import com.karelisio.bulle.modules.music.media.pickSession
import com.karelisio.bulle.modules.music.ui.barHeight
import com.karelisio.bulle.modules.music.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLogicTest {

    private fun c(id: String, state: Int, active: Long, content: Boolean = true) = SessionCandidate(id, state, content, active)

    @Test
    fun playingSessionWinsOverMoreRecentPaused() {
        val picked = pickSession(
            listOf(
                c("paused", PlaybackState.STATE_PAUSED, 2_000),
                c("playing", PlaybackState.STATE_PLAYING, 1_000),
            ),
        )
        assertEquals("playing", picked?.id)
    }

    @Test
    fun mostRecentAmongSameKind() {
        val picked = pickSession(
            listOf(
                c("a", PlaybackState.STATE_PAUSED, 1_000),
                c("b", PlaybackState.STATE_PAUSED, 3_000),
                c("c", PlaybackState.STATE_PAUSED, 2_000),
            ),
        )
        assertEquals("b", picked?.id)
    }

    @Test
    fun ignoresStoppedAndEmptySessions() {
        assertNull(
            pickSession(
                listOf(
                    c("stopped", PlaybackState.STATE_STOPPED, 5_000),
                    c("none", PlaybackState.STATE_NONE, 5_000),
                    c("error", PlaybackState.STATE_ERROR, 5_000),
                    c("empty", PlaybackState.STATE_PLAYING, 5_000, content = false),
                ),
            ),
        )
        assertEquals("buffering", pickSession(listOf(c("buffering", PlaybackState.STATE_BUFFERING, 1)))?.id)
    }

    @Test
    fun extrapolatesPosition() {
        assertEquals(15_000L, extrapolatePosition(10_000, 1_000, 1f, true, 6_000, 200_000))
        assertEquals(20_000L, extrapolatePosition(10_000, 1_000, 2f, true, 6_000, 200_000))
        // En pause : figée.
        assertEquals(10_000L, extrapolatePosition(10_000, 1_000, 1f, false, 60_000, 200_000))
        // Bornée par la durée et par zéro.
        assertEquals(200_000L, extrapolatePosition(190_000, 0 + 1, 1f, true, 60_000, 200_000))
        assertEquals(0L, extrapolatePosition(1_000, 1_000, -1f, true, 10_000, 200_000))
    }

    @Test
    fun zeroSpeedWhilePlayingIsTreatedAsNormal() {
        val s = PlaybackSnapshot(PlaybackState.STATE_PLAYING, 1_000, 1_000, 0f, 0)
        assertEquals(3_000L, s.positionAt(3_000, 0))
        val buffering = PlaybackSnapshot(PlaybackState.STATE_BUFFERING, 1_000, 1_000, 0f, 0)
        assertEquals(1_000L, buffering.positionAt(3_000, 0))
    }

    @Test
    fun formatsTime() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:00", formatTime(-5))
        assertEquals("1:05", formatTime(65_900))
        assertEquals("1:01:01", formatTime(3_661_000))
    }

    @Test
    fun equalizerBarsStayInRange() {
        for (i in 0 until 4) {
            var t = 0f
            while (t < 20f) {
                val h = barHeight(i, t, 1f)
                assertTrue(h in 0f..1f)
                t += 0.037f
            }
        }
        // Niveau nul : hauteurs de repos, indépendantes du temps.
        assertEquals(barHeight(1, 0f, 0f), barHeight(1, 12.3f, 0f), 0f)
    }

    @Test
    fun cleansTitlesForSearch() {
        assertEquals("Song", LrclibClient.cleanTitle("Song (feat. Someone)"))
        assertEquals("Song", LrclibClient.cleanTitle("Song - Remastered 2011"))
        assertEquals("Song", LrclibClient.cleanTitle("Song - 2009 Remaster"))
        assertEquals("Live Forever", LrclibClient.cleanTitle("Live Forever"))
        assertEquals("(feat. X)", LrclibClient.cleanTitle("(feat. X)"))
    }

    @Test
    fun picksCloseSyncedSearchResult() {
        val results = listOf(
            LrclibTrack(duration = 300.0, syncedLyrics = "[00:01.00]loin"),
            LrclibTrack(duration = 201.0, plainLyrics = "brut"),
            LrclibTrack(duration = 199.0, syncedLyrics = "[00:01.00]proche"),
        )
        assertEquals("[00:01.00]proche", LrclibClient.pickSearchResult(results, 200)?.syncedLyrics)
        assertNull(LrclibClient.pickSearchResult(results.take(1), 200))
        assertEquals("[00:01.00]loin", LrclibClient.pickSearchResult(results, 0)?.syncedLyrics)
    }
}
