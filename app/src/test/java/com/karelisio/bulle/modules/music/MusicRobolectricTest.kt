package com.karelisio.bulle.modules.music

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricsEntity
import com.karelisio.bulle.modules.music.lyrics.LyricsKey
import com.karelisio.bulle.modules.music.lyrics.LyricsRepository
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.ArtworkLoader
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = BulleDatabase.resetForTests()

    private fun track(lyrics: String? = null) = TrackInfo(
        packageName = "com.example.player",
        title = "Titre",
        artist = "Artiste",
        album = "Album",
        durationMs = 200_000,
        artBitmap = null,
        artUris = emptyList(),
        playerLyrics = lyrics,
    )

    @Test
    fun playerLyricsComeFirst() = runBlocking {
        val state = LyricsRepository.load(context, track("[00:01.00]a\n[00:02.00]b"), online = false)
        assertTrue(state is LyricsState.Found)
        assertEquals(LyricsSource.PLAYER, (state as LyricsState.Found).lyrics.source)
    }

    @Test
    fun cacheIsUsedAndNotFoundRemembered() = runBlocking {
        val dao = BulleDatabase.get(context).lyricsDao()
        val t = track()
        val key = LyricsKey.of(t.artist, t.title, t.durationMs)
        // Introuvable mémorisé : pas de paroles, même si la recherche en ligne est permise (pas de réseau en test).
        dao.put(LyricsEntity(key, "", LyricsSource.LRCLIB.name, notFound = true, fetchedAt = System.currentTimeMillis()))
        assertEquals(LyricsState.NotFound, LyricsRepository.load(context, t, online = true))

        dao.put(LyricsEntity(key, "[00:01.00]x\n[00:03.00]y", LyricsSource.LRCLIB.name, notFound = false, fetchedAt = 0))
        val found = LyricsRepository.load(context, t, online = false) as LyricsState.Found
        assertEquals(2, found.lyrics.lines.size)
        assertEquals(1, LyricsRepository.cachedCount(context))

        LyricsRepository.clearCache(context)
        assertEquals(0, LyricsRepository.cachedCount(context))
        assertEquals(LyricsState.NotFound, LyricsRepository.load(context, t, online = false))
    }

    @Test
    fun accentStaysReadableOnBlack() {
        val dark = ArtworkLoader.readableOnBlack(0xFF101030.toInt())
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dark, hsl)
        assertTrue(hsl[2] >= 0.6f)
    }

    /** Rend toutes les vues du contenu (compacte, minimale, étendue) pour vérifier qu'aucune ne plante. */
    @Test
    fun rendersAllViews() {
        // Animations coupées : pas de boucle infinie dans le test.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val session = MediaSession(activity, "test")
        val host = object : IslandHost {
            override val context: Context = activity
            override fun expand(key: String) = Unit
            override fun collapse() = Unit
            override fun dismiss(key: String) = Unit
            override fun openPanel(panel: IslandPanel) = Unit
            override fun closePanel() = Unit
            override fun requestInput(enabled: Boolean) = Unit
            override fun runAction(action: com.karelisio.bulle.core.actions.GestureAction) = Unit
        }
        val lyrics = LrcParser.parse("[00:01.00]un\n[00:05.00]deux\n[00:09.00]trois", LyricsSource.LRCLIB)!!
        for (playing in listOf(false, true)) {
            val content = MusicContent(
                controller = session.controller,
                track = track(),
                playback = PlaybackSnapshot(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    6_000, 1, 1f, PlaybackState.ACTION_SEEK_TO,
                ),
                artwork = null,
                lyrics = LyricsState.Found(lyrics),
                postedAt = 1,
                timeoutMs = null,
            )
            activity.setContent {
                Column {
                    content.Leading()
                    content.Trailing()
                    content.Minimal()
                    content.Expanded(host)
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        session.release()
    }
}
