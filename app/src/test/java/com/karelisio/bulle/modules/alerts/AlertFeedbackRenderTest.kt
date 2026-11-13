package com.karelisio.bulle.modules.alerts

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.feedback.Feedback
import com.karelisio.bulle.core.feedback.FeedbackSound
import com.karelisio.bulle.core.feedback.HapticPattern
import com.karelisio.bulle.core.feedback.SoundKind
import com.karelisio.bulle.modules.alerts.ui.AlertFeedbackPickers
import com.karelisio.bulle.modules.alerts.ui.AlertStyleEditor
import com.karelisio.bulle.modules.alerts.ui.AlertUis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rendu des sélecteurs Vibration / Son et lecture réelle (vibreur simulé). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertFeedbackRenderTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun noAnimations() {
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun editorComposesWithEveryFeedbackChoice() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var style by mutableStateOf(AlertStyle())
        activity.setContent {
            AlertStyleEditor(AlertUis.of("wifi"), AlertCatalog.def("wifi"), style, onChange = { style = it })
        }
        idle()
        HapticPattern.entries.forEach {
            style = style.copy(haptic = it)
            idle()
        }
        (FeedbackSound.synthesized.map { FeedbackSound(it) } +
            FeedbackSound(SoundKind.SYSTEM) +
            FeedbackSound(SoundKind.SYSTEM, "content://media/internal/audio/media/7", "Cristal")).forEach {
            style = style.copy(sound = it)
            idle()
        }
    }

    @Test
    fun pickersComposeStandalone() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Column {
                AlertFeedbackPickers(
                    haptic = HapticPattern.WAVE,
                    sound = FeedbackSound(SoundKind.BUBBLE),
                    onHaptic = {},
                    onSound = {},
                    section = { Text(ctx.getString(it)) },
                )
            }
        }
        idle()
    }

    @Test
    fun playRespectsRingerMode() {
        val audio = ctx.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val normal = Feedback.play(ctx, HapticPattern.DOUBLE_TAP, FeedbackSound.None)
        assertTrue(normal.vibrate)
        assertFalse(normal.sound)

        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertFalse(Feedback.play(ctx, HapticPattern.DOUBLE_TAP, FeedbackSound(SoundKind.POP)).any)

        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertFalse(Feedback.play(ctx, HapticPattern.DOUBLE_TAP, FeedbackSound.None, hapticsEnabled = false).vibrate)
        idle()
    }
}
