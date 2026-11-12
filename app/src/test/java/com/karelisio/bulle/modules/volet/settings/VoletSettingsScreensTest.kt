package com.karelisio.bulle.modules.volet.settings

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.modules.volet.VoletController
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.history.NotificationHistoryScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Rendu des écrans du Volet (réglages, historique, bannière, aperçu) : aucun ne doit planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoletSettingsScreensTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun tearDown() = BulleDatabase.resetForTests()

    private fun render(content: @Composable () -> Unit): ActivityController<ComponentActivity> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.get().isFinishing)
        return controller
    }

    @Test
    fun settingsRenderAndTintZonesWhileShown() {
        val controller = render { VoletSettingsScreen(onBack = {}) }
        assertTrue(VoletController.previewFlow.value)
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(VoletController.previewFlow.value)
    }

    @Test
    fun zonesNotTintedWhileAppInBackground() {
        val controller = render { VoletSettingsScreen(onBack = {}) }
        assertTrue(VoletController.previewFlow.value)
        // « Accueil » : l'activité est arrêtée sans être détruite.
        controller.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(VoletController.previewFlow.value)
        controller.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(VoletController.previewFlow.value)
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun historyRenders() {
        render { NotificationHistoryScreen(onBack = {}) }
    }

    @Test
    fun bannerRendersNothingWhenVoletOff() {
        render { VoletReliabilityBanner() }
    }

    @Test
    fun previewRendersEveryVariant() {
        render {
            VoletMiniPreview(VoletSettings(density = VoletDensity.COMPACT, collapsedTileCount = 4, blurRadiusDp = 0f))
            VoletMiniPreview(VoletSettings(cornerRadiusDp = 40f, scrimOpacity = 0.95f))
        }
    }
}
