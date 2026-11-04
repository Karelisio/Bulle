package com.karelisio.bulle.modules.volet.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.modules.music.media.MediaSnapshot
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rendu de la carte média (lecture / pause, clair / sombre) et de la section sans session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaCardRenderTest {

    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        // Animations coupées : aucune boucle infinie (respiration, onde) pendant le test.
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    /** Hôte factice : enregistre les ouvertures demandées. */
    private class TestHost(override val context: Context) : VoletHost {
        override val expansion = 1f
        override val zone = VoletZone.NOTIFICATIONS
        override val settings = VoletSettings(enabled = true)
        override val locked = false
        override val focusKey: String? = null
        override var editingTiles = false
        var started = 0
        var closed = 0
        override fun close() { closed++ }
        override fun expandFull() = Unit
        override fun collapseToHalf() = Unit
        override fun dragBy(dyPx: Float) = Unit
        override fun settle(velocityPxPerSec: Float) = Unit
        override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = false
        override fun startActivity(intent: Intent) { started++ }
        override fun runAction(action: GestureAction) = Unit
        override fun requestKeyboard(show: Boolean) = Unit
        override fun openSystemShade() = Unit
    }

    private class CountingActions : MediaCardActions {
        var count = 0
        override fun open() { count++ }
        override fun play() { count++ }
        override fun pause() { count++ }
        override fun previous() { count++ }
        override fun next() { count++ }
        override fun seekTo(positionMs: Long) { count++ }
        override fun custom(id: String) { count++ }
        override fun output() { count++ }
    }

    private fun playback(playing: Boolean) = PlaybackSnapshot(
        if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        42_000, 1, 1f,
        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PLAY_PAUSE,
    )

    private fun data(playing: Boolean, dark: Boolean, title: String = "Titre") = MediaCardData(
        title = title,
        artist = "Artiste",
        trackKey = title,
        durationMs = 215_000,
        playback = playback(playing),
        appName = "Lecteur",
        appIcon = null,
        artwork = null,
        scheme = MediaColors.scheme(0xFF3F51B5.toInt(), 0xFFFF4081.toInt(), dark, 0xFF6750A4.toInt()),
        customActions = listOf(MediaCustomAction("like", "J'aime", null)),
        output = AudioOutput(OutputKind.BLUETOOTH, "Buds"),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun rendersPlayingAndPausedCards() {
        val actions = CountingActions()
        var state by mutableStateOf(data(playing = false, dark = true))
        activity.setContent {
            BulleTheme(dark = true, dynamic = false) {
                MediaCard(state, actions, cornerRadius = 28.dp, compact = false, modifier = Modifier.fillMaxWidth())
            }
        }
        idle()
        // Lecture, changement de morceau (fondu croisé), puis version compacte et thème clair.
        state = data(playing = true, dark = true)
        idle()
        state = data(playing = true, dark = false, title = "Autre titre")
        idle()
        activity.setContent {
            BulleTheme(dark = false, dynamic = false) {
                Column {
                    MediaCard(data(playing = false, dark = false), actions, cornerRadius = 16.dp, compact = true)
                    MediaCard(data(playing = true, dark = false).copy(customActions = emptyList(), durationMs = 0), actions, 16.dp, compact = true)
                }
            }
        }
        idle()
        assertEquals(0, actions.count)
    }

    @Test
    fun sectionRendersNothingWithoutSessionAndCardWithSession() {
        val host = TestHost(activity)
        val session = MediaSession(activity, "test")
        val track = TrackInfo(
            packageName = activity.packageName,
            title = "Titre",
            artist = "Artiste",
            album = "Album",
            durationMs = 180_000,
            artBitmap = null,
            artUris = emptyList(),
            playerLyrics = null,
        )
        activity.setContent {
            BulleTheme(dynamic = false) {
                Column {
                    MediaSection(host, Modifier.fillMaxWidth())
                    MediaSessionCard(host, MediaSnapshot(session.controller, track, playback(true), 1))
                }
            }
        }
        idle()
        session.release()
        assertEquals(0, host.started)
    }
}
