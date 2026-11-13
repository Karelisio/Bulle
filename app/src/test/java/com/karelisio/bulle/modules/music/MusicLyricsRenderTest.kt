package com.karelisio.bulle.modules.music

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import com.karelisio.bulle.modules.music.sync.AudioOutput
import com.karelisio.bulle.modules.music.sync.AudioOutputTracker
import com.karelisio.bulle.modules.music.sync.LyricsPrefs
import com.karelisio.bulle.modules.music.ui.LyricsPreview
import com.karelisio.bulle.modules.music.ui.MusicSettingsScreen
import com.karelisio.bulle.modules.music.ui.lyricsWindowHeight
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rendu de la carte étendue (tailles, mode paroles, décalage) et de l'écran de réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicLyricsRenderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var activity: ComponentActivity
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        // Animations coupées : pas de boucle infinie dans le test.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        session = MediaSession(activity, "test")
    }

    @After
    fun tearDown() {
        session.release()
        BulleDatabase.resetForTests()
    }

    private val host
        get() = object : IslandHost {
            override val context: Context = activity
            override fun expand(key: String) = Unit
            override fun collapse() = Unit
            override fun dismiss(key: String) = Unit
            override fun openPanel(panel: IslandPanel) = Unit
            override fun closePanel() = Unit
            override fun requestInput(enabled: Boolean) = Unit
            override fun runAction(action: GestureAction) = Unit
        }

    private fun content(lyrics: LyricsState, prefs: LyricsPrefs) = MusicContent(
        controller = session.controller,
        track = TrackInfo("com.example.player", "Titre", "Artiste", "Album", 200_000, null, emptyList(), null),
        playback = PlaybackSnapshot(PlaybackState.STATE_PLAYING, 6_000, 1, 1f, PlaybackState.ACTION_SEEK_TO),
        artwork = null,
        lyrics = lyrics,
        postedAt = 1,
        timeoutMs = null,
        lyricsPrefs = prefs,
    )

    private fun renderExpanded(c: MusicContent) {
        activity.setContent { Box(Modifier.width(360.dp)) { c.Expanded(host) } }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun expandedCardRendersAtAllSizes() {
        val synced = LyricsState.Found(LrcParser.parse("[00:01.00]un\n[00:05.00]deux\n[00:09.00]trois", LyricsSource.LRCLIB)!!)
        val plain = LyricsState.Found(Lyrics(listOf(LyricLine(-1, "texte libre")), synced = false, source = LyricsSource.PLAYER))
        renderExpanded(content(synced, LyricsPrefs(textScale = 0.8f, visibleLines = 3)))
        renderExpanded(content(synced, LyricsPrefs(textScale = 1.6f, visibleLines = 9)))
        // Hauteur de la fenêtre : croît avec le nombre de lignes et la taille du texte.
        var heights = emptyList<Float>()
        // Activité neuve : enchaîner plusieurs setContent sur la même activité sous Robolectric
        // peut laisser la composition sans effet quand toute la suite tourne.
        val fresh = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        fresh.setContent {
            heights = listOf(
                lyricsWindowHeight(3, 0.8f).value,
                lyricsWindowHeight(5, 1f).value,
                lyricsWindowHeight(9, 1f).value,
                lyricsWindowHeight(9, 1.6f).value,
            )
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(4, heights.size)
        assertTrue("hauteurs croissantes : $heights", heights.zipWithNext().all { (a, b) -> b > a })
        renderExpanded(content(synced, LyricsPrefs(offsets = mapOf(AudioOutput.SPEAKER_KEY to 400))))
        renderExpanded(content(synced, LyricsPrefs(focusMode = true)))
        renderExpanded(content(plain, LyricsPrefs(textScale = 1.2f, visibleLines = 6, focusMode = true)))
        renderExpanded(content(LyricsState.Loading, LyricsPrefs(focusMode = true)))
    }

    @Test
    fun outputTrackerFallsBackToSpeaker() {
        // Robolectric n'annonce aucun casque : la sortie retenue est le haut-parleur.
        assertEquals(AudioOutput.SPEAKER_KEY, AudioOutputTracker.current(context).key)
    }

    @Test
    fun settingsScreenAndPreviewRender() {
        activity.setContent { MusicSettingsScreen(onBack = {}) }
        shadowOf(Looper.getMainLooper()).idle()
        for ((scale, lines) in listOf(0.8f to 3, 1f to 5, 1.6f to 9)) {
            activity.setContent { LyricsPreview(scale, lines, focused = lines == 9) }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
