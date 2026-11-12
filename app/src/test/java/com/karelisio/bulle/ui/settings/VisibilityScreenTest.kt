package com.karelisio.bulle.ui.settings

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.settings.VisibilitySettings
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Compose l'écran Masquage, listes vides puis remplies, plein écran actif puis coupé. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisibilityScreenTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun noAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun compose() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { VisibilityScreen(onBack = {}) } }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }

    @Test
    fun composesWithDefaults() = compose()

    @Test
    fun composesWithExceptions() {
        runBlocking {
            SettingsRepository.update(context) {
                it.copy(
                    visibility = VisibilitySettings(
                        fullscreenIgnoredApps = setOf("com.video", "com.maps"),
                        hiddenApps = setOf("com.video", "com.game"),
                        ignoreFullscreenOnLauncher = false,
                    ),
                )
            }
        }
        compose()
        runBlocking { SettingsRepository.update(context) { it.copy(visibility = it.visibility.copy(hideInFullscreen = false)) } }
        compose()
    }
}
