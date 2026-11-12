package com.karelisio.bulle.modules.music

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Île gardée ouverte / visible pendant des paroles synchronisées, selon la lecture et les réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicLyricsBehaviorTest {
    private val session = MediaSession(ApplicationProvider.getApplicationContext<Context>(), "test")

    @After
    fun release() = session.release()

    private val synced = LyricsState.Found(LrcParser.parse("[00:01.00]un\n[00:05.00]deux", LyricsSource.LRCLIB)!!)
    private val plain = LyricsState.Found(Lyrics(listOf(LyricLine(-1, "texte")), synced = false, source = LyricsSource.PLAYER))

    private fun content(
        lyrics: LyricsState,
        playing: Boolean = true,
        keepOpen: Boolean = true,
        stayVisible: Boolean = true,
        positionMs: Long = 0,
    ) = MusicContent(
        controller = session.controller,
        track = TrackInfo("com.example.player", "Titre", "Artiste", "Album", 200_000, null, emptyList(), null),
        playback = PlaybackSnapshot(
            if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            positionMs, 1, 1f, 0,
        ),
        artwork = null,
        lyrics = lyrics,
        postedAt = 1,
        timeoutMs = null,
        keepLyricsOpen = keepOpen,
        stayVisibleWithLyrics = stayVisible,
    )

    @Test
    fun syncedLyricsWhilePlayingKeepIslandOpenAndVisible() {
        val c = content(synced)
        assertTrue(c.hasSyncedLyrics)
        assertTrue(c.stayExpandedOnOutsideTouch)
        assertTrue(c.showWhenHidden)
    }

    @Test
    fun pausedKeepsOpenButDoesNotForceVisibility() {
        val c = content(synced, playing = false)
        assertTrue(c.stayExpandedOnOutsideTouch)
        assertFalse(c.showWhenHidden)
    }

    @Test
    fun noSyncedLyricsMeansDefaultBehavior() {
        for (state in listOf(plain, LyricsState.Loading, LyricsState.NotFound, LyricsState.Off)) {
            val c = content(state)
            assertFalse(state.toString(), c.hasSyncedLyrics)
            assertFalse(state.toString(), c.stayExpandedOnOutsideTouch)
            assertFalse(state.toString(), c.showWhenHidden)
        }
    }

    @Test
    fun settingsTurnBehaviorOff() {
        assertFalse(content(synced, keepOpen = false).stayExpandedOnOutsideTouch)
        assertTrue(content(synced, keepOpen = false).showWhenHidden)
        assertFalse(content(synced, stayVisible = false).showWhenHidden)
        assertTrue(content(synced, stayVisible = false).stayExpandedOnOutsideTouch)
    }

    @Test
    fun positionTicksDoNotChangeFlags() {
        val a = content(synced, positionMs = 1_000)
        val b = content(synced, positionMs = 6_000)
        assertEquals(a.stayExpandedOnOutsideTouch, b.stayExpandedOnOutsideTouch)
        assertEquals(a.showWhenHidden, b.showWhenHidden)
    }
}
