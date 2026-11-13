package com.karelisio.bulle.modules.music

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.music.karaoke.KaraokeActivity
import com.karelisio.bulle.modules.music.karaoke.KaraokeBridge
import com.karelisio.bulle.modules.music.karaoke.KaraokeLyrics
import com.karelisio.bulle.modules.music.karaoke.KaraokeScreen
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import com.karelisio.bulle.modules.music.ui.CompactEqualizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rendu de la vue karaoké, de la carte étendue avec son bouton, et lancement de l'activité. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaraokeRenderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var activity: ComponentActivity
    private lateinit var session: MediaSession

    private val synced = LyricsState.Found(
        LrcParser.parse(
            "[00:01.00]<00:01.00>Une <00:01.40>ligne <00:01.90>chantée<00:02.60>\n" +
                "[00:05.00]Une ligne assez longue pour passer à la ligne sur un écran de téléphone étroit\n" +
                "[00:09.00]\n[00:12.00]fin",
            LyricsSource.LRCLIB,
        )!!,
    )

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        session = MediaSession(activity, "test")
    }

    @After
    fun tearDown() {
        session.release()
        KaraokeBridge.publish(null)
        BulleDatabase.resetForTests()
    }

    private fun content(lyrics: LyricsState = synced, playing: Boolean = true, visualizer: Boolean = false) = MusicContent(
        controller = session.controller,
        track = TrackInfo("com.example.player", "Titre", "Artiste", "Album", 200_000, null, emptyList(), null),
        playback = PlaybackSnapshot(
            if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            6_000, 1, 1f, PlaybackState.ACTION_SEEK_TO,
        ),
        artwork = null,
        lyrics = lyrics,
        postedAt = 1,
        timeoutMs = null,
        realVisualizer = visualizer,
    )

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

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun karaokeScreenRendersAllStates() {
        val fresh = { Robolectric.buildActivity(ComponentActivity::class.java).setup().get() }
        fresh().setContent { KaraokeScreen(content(), onClose = {}) }
        idle()
        fresh().setContent { KaraokeScreen(content(playing = false, visualizer = true), onClose = {}, active = false) }
        idle()
        fresh().setContent { KaraokeScreen(content(LyricsState.Loading), onClose = {}) }
        idle()
        val plain = LyricsState.Found(LrcParser.parse("texte\nlibre", LyricsSource.PLAYER)!!)
        fresh().setContent { KaraokeScreen(content(plain), onClose = {}) }
        idle()
    }

    @Test
    fun karaokeLyricsDrawProgressiveFill() {
        // Animations non réduites : la ligne courante est dessinée avec le remplissage mot à mot.
        val lyrics = synced.lyrics
        for (pos in listOf(0L, 1_200L, 2_000L, 6_500L, 13_000L)) {
            val position = mutableLongStateOf(pos)
            val a = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            a.setContent {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    KaraokeLyrics(lyrics, position, offsetMs = 250, textScale = 1f, reduceMotion = false, onSeekLine = {})
                }
            }
            idle()
            // Dessin réel de la vue (passe atténuée + passes découpées).
            val view = a.window.decorView
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(640, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 360, 640)
            view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(360, 640, android.graphics.Bitmap.Config.ARGB_8888)))
        }
    }

    @Test
    fun expandedCardWithKaraokeButtonRenders() {
        val a = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        a.setContent { Box(Modifier.width(360.dp)) { content(visualizer = true).Expanded(host) } }
        idle()
        val b = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        b.setContent { Box(Modifier.width(80.dp).height(30.dp)) { CompactEqualizer(true, content().accent, realSpectrum = true) } }
        idle()
    }

    @Test
    fun bridgeOpensFullScreenActivityInNewTask() {
        val c = content()
        KaraokeBridge.open(context, c)
        assertEquals(c, KaraokeBridge.content.value)
        val started: Intent = shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(KaraokeActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun activityKeepsScreenOnAndClosesWithoutMusic() {
        KaraokeBridge.publish(content())
        val shown = Robolectric.buildActivity(KaraokeActivity::class.java).setup().get()
        idle()
        assertTrue(shown.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertNotNull(KaraokeBridge.content.value)

        KaraokeBridge.publish(null)
        assertNull(KaraokeBridge.content.value)
        val empty = Robolectric.buildActivity(KaraokeActivity::class.java).setup().get()
        // Le délai de grâce (600 ms) peut courir en temps réel (delay hors horloge d'images).
        idle()
        Thread.sleep(900)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertTrue(empty.isFinishing)
    }
}
