package com.karelisio.bulle.modules.volet.qs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Hôte de test : états Compose modifiables, actions enregistrées. */
private class TestHost(override val context: Context) : VoletHost {
    override var expansion by mutableFloatStateOf(1f)
    override val zone = VoletZone.QUICK_SETTINGS
    override var settings by mutableStateOf(VoletSettings(enabled = true))
    override val locked = false
    override val focusKey: String? = null
    override var editingTiles by mutableStateOf(false)
    val started = mutableListOf<Intent>()
    val actions = mutableListOf<GestureAction>()

    override fun close() = Unit
    override fun expandFull() { expansion = 2f }
    override fun collapseToHalf() { expansion = 1f }
    override fun dragBy(dyPx: Float) = Unit
    override fun settle(velocityPxPerSec: Float) = Unit
    override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = false
    override fun startActivity(intent: Intent) { started += intent }
    override fun runAction(action: GestureAction) { actions += action }
    override fun requestKeyboard(show: Boolean) = Unit
    override fun openSystemShade() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickSettingsRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Animations coupées : pas de boucle infinie dans le test.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun rendersCollapsedExpandedAndEditing() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = TestHost(activity)
        activity.setContent {
            BulleTheme { QuickSettingsSection(host, Modifier.fillMaxWidth()) }
        }
        idle()
        // Volet à demi ouvert (qsExpansion = 0), puis déplié (1), puis entre les deux.
        for (e in listOf(1f, 2f, 1.5f)) {
            host.expansion = e
            idle()
        }
        host.settings = host.settings.copy(collapsedTileCount = 4)
        idle()
        host.editingTiles = true
        idle()
        host.editingTiles = false
        idle()
    }

    @Test
    fun registryOnDevice() {
        // Robolectric n'annonce pas tout le matériel : les tuiles sans matériel sont écartées sans planter.
        val current = QsTileRegistry.current(context, emptyList())
        assertTrue(current.isNotEmpty())
        val available = QsTileRegistry.available(context, current.map { it.id })
        assertTrue(available.none { it in current })
        // Sans Shizuku : indisponible, sans exception.
        assertEquals(ShizukuStatus.UNAVAILABLE, ShizukuShell.status())
    }

    @Test
    fun toggleRoutes() {
        val host = TestHost(context)
        val toggler = QsToggler(host)
        // Rotation : bascule directe avec WRITE_SETTINGS, sinon page d'autorisation (ni Shizuku ici).
        if (Settings.System.canWrite(context)) {
            assertEquals(ToggleMethod.DIRECT, toggler.methodFor(RotationTile))
            toggler.tap(RotationTile, TileState(active = false))
            assertEquals(1, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0))
        } else {
            assertEquals(ToggleMethod.GRANT, toggler.methodFor(RotationTile))
            toggler.tap(RotationTile, TileState(active = false))
            assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, host.started.single().action)
        }
        // Wi-Fi sans Shizuku : panneau flottant.
        assertEquals(ToggleMethod.PANEL, toggler.methodFor(WifiTile))
        // Capture : action de geste.
        toggler.tap(ScreenshotTile, TileState())
        assertEquals(ScreenshotTile.action, host.actions.single())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
