package com.karelisio.bulle.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.backup.BackupScreen
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone
import com.karelisio.bulle.modules.volet.ui.VoletHeader
import com.karelisio.bulle.testing.ComposeDispatcherReset
import com.karelisio.bulle.ui.home.HomeScreen
import com.karelisio.bulle.ui.onboarding.ReliabilityScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class HeaderHost(override val context: Context) : VoletHost {
    override var expansion by mutableFloatStateOf(1f)
    override val zone = VoletZone.NOTIFICATIONS
    override var settings by mutableStateOf(VoletSettings(enabled = true))
    override val locked = false
    override val focusKey: String? = null
    override var editingTiles by mutableStateOf(false)
    override fun close() = Unit
    override fun expandFull() = Unit
    override fun collapseToHalf() = Unit
    override fun dragBy(dyPx: Float) = Unit
    override fun settle(velocityPxPerSec: Float) = Unit
    override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = false
    override fun startActivity(intent: Intent) = Unit
    override fun runAction(action: GestureAction) = Unit
    override fun requestKeyboard(show: Boolean) = Unit
    override fun openSystemShade() = Unit
}

/**
 * Écrans clés sur petit écran, grande police et paysage : aucun ne doit planter (les débordements
 * de l'en-tête du Volet venaient de ces cas).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
class V096RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ComposeDispatcherReset.unstick()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun renderAll(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val screens: List<@Composable () -> Unit> = listOf(
            { VoletHeader(HeaderHost(context)) },
            { VoletHeader(HeaderHost(context), compact = true) },
            { HomeScreen() },
            { ReliabilityScreen(onBack = {}) },
            { BackupScreen(onBack = {}) },
        )
        screens.forEach { screen ->
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent { BulleTheme { screen() } }
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    fun smallScreenNormalFont() = renderAll(1f)

    @Test
    fun smallScreenLargeFont() = renderAll(1.3f)

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun landscapeLargeFont() = renderAll(1.3f)
}
