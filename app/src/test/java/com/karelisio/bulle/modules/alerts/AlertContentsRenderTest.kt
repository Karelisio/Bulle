package com.karelisio.bulle.modules.alerts

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.alerts.sources.BluetoothContent
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.alerts.sources.ChargingContent
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.sources.LowBatteryContent
import com.karelisio.bulle.modules.alerts.sources.PackageContent
import com.karelisio.bulle.modules.alerts.sources.ScreenshotContent
import com.karelisio.bulle.modules.alerts.sources.ToggleContent
import com.karelisio.bulle.modules.alerts.sources.ToggleKind
import com.karelisio.bulle.modules.alerts.sources.TorchContent
import com.karelisio.bulle.modules.alerts.sources.WifiContent
import com.karelisio.bulle.modules.alerts.ui.AlertsSettingsScreen
import com.karelisio.bulle.modules.echo.EchoContent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Vérifie que chaque vue (compacte, minimale, étendue) des alertes se compose sans planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertContentsRenderTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun noAnimations() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun host(ctx: Context) = object : IslandHost {
        override val context = ctx
        override fun expand(key: String) = Unit
        override fun collapse() = Unit
        override fun dismiss(key: String) = Unit
        override fun openPanel(panel: IslandPanel) = Unit
        override fun closePanel() = Unit
        override fun requestInput(enabled: Boolean) = Unit
    }

    @Test
    fun allContentsCompose() {
        val contents: List<IslandContent> = listOf(
            ChargingContent(64, ChargeKind.FAST, 1),
            LowBatteryContent(9, 1),
            BluetoothContent("00:11", "Buds", BtKind.HEADPHONES, 80, 1),
            BluetoothContent("00:12", null, BtKind.OTHER, null, 1),
            WifiContent("Maison", 1),
            WifiContent(null, 1),
            TorchContent("0", 3, 5),
            TorchContent("0", 1, 1),
            ToggleContent(ToggleKind.AIRPLANE, true, 1),
            ToggleContent(ToggleKind.DND, false, 1),
            ScreenshotContent(Uri.parse("content://media/external/images/media/1"), 1),
            PackageContent("com.example", "Exemple", true, 1),
            PackageContent("com.example", null, false, 1),
            ClipboardContent(1),
            EchoContent("Coucou", "Texte", null, 1),
        )
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val h = host(activity)
        activity.setContent {
            Column {
                contents.forEach { c ->
                    Row { c.Leading(); c.Trailing(); c.Minimal() }
                    c.Expanded(h)
                }
            }
        }
        idle()
        assertEquals(contents.size, contents.map { it.key }.size)
    }

    @Test
    fun settingsScreenComposes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AlertsSettingsScreen(onBack = {}) }
        idle()
    }
}
