package com.karelisio.bulle.modules.alerts

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.alerts.sources.BluetoothContent
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.alerts.sources.ChargingContent
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.sources.LowBatteryContent
import com.karelisio.bulle.modules.alerts.sources.PackageContent
import com.karelisio.bulle.modules.alerts.sources.PrivacyContent
import com.karelisio.bulle.modules.alerts.sources.PrivacyKind
import com.karelisio.bulle.modules.alerts.sources.RingerContent
import com.karelisio.bulle.modules.alerts.sources.ScreenshotContent
import com.karelisio.bulle.modules.alerts.sources.SignalContent
import com.karelisio.bulle.modules.alerts.sources.SignalKind
import com.karelisio.bulle.modules.alerts.sources.ToggleContent
import com.karelisio.bulle.modules.alerts.sources.ToggleKind
import com.karelisio.bulle.modules.alerts.sources.TorchContent
import com.karelisio.bulle.modules.alerts.sources.UsbContent
import com.karelisio.bulle.modules.alerts.sources.VolumeContent
import com.karelisio.bulle.modules.alerts.sources.WifiContent
import com.karelisio.bulle.modules.alerts.ui.AlertIcons
import com.karelisio.bulle.modules.alerts.ui.AlertStyleEditor
import com.karelisio.bulle.modules.alerts.ui.AlertStyleSheet
import com.karelisio.bulle.modules.alerts.ui.AlertUis
import com.karelisio.bulle.modules.alerts.ui.AlertsSettingsScreen
import com.karelisio.bulle.modules.alerts.ui.StyledAlert
import com.karelisio.bulle.modules.alerts.ui.alertIdOf
import com.karelisio.bulle.modules.alerts.ui.styled
import com.karelisio.bulle.modules.echo.EchoContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        override fun runAction(action: com.karelisio.bulle.core.actions.GestureAction) = Unit
    }

    @Test
    fun allContentsCompose() {
        val base: List<IslandContent> = listOf(
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
            SignalContent(SignalKind.CELL_LOST, 1),
            SignalContent(SignalKind.CELL_BACK, 1),
            SignalContent(SignalKind.FIVE_G, 1),
            SignalContent(SignalKind.VPN_ON, 1),
            SignalContent(SignalKind.VPN_OFF, 1),
            SignalContent(SignalKind.HOTSPOT_ON, 1),
            SignalContent(SignalKind.HOTSPOT_OFF, 1),
            RingerContent(RingerKind.SILENT, 1),
            RingerContent(RingerKind.VIBRATE, 1),
            RingerContent(RingerKind.NORMAL, 1),
            VolumeContent(0, 15, 1, 1),
            VolumeContent(12, 15, 1, 2),
            UsbContent(UsbContent.Event.CABLE_ON, UsbMode.FILES, null, 1),
            UsbContent(UsbContent.Event.DEVICE_ON, null, "Clavier", 1),
            UsbContent(UsbContent.Event.CABLE_OFF, null, null, 1),
            // Alerte combinée USB + charge (ordinateur qui charge le téléphone).
            UsbContent(UsbContent.Event.CABLE_ON, UsbMode.FILES, null, 1, chargePercent = 64),
            PrivacyContent(PrivacyKind.CAMERA),
            PrivacyContent(PrivacyKind.MIC),
        )
        // Chaque contenu aussi habillé d'un style complet, puis de chaque animation d'entrée.
        val fullStyle = base.mapNotNull { c ->
            val id = alertIdOf(c) ?: return@mapNotNull null
            styled(c, mapOf(id to AlertStyle(AlertIcons.choicesFor(id).firstOrNull(), 0xFFFF6FA5, AlertEntrance.BOUNCE, 5)))
        }
        val entrances = AlertEntrance.entries.map { e ->
            styled(ClipboardContent(1), mapOf("clipboard" to AlertStyle(entrance = e)))
        }
        val contents = base + fullStyle + entrances
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
        // Toutes les alertes (Écho compris) sont personnalisables.
        assertEquals(base.size, fullStyle.size)
        assertTrue(fullStyle.all { it is StyledAlert })
    }

    @Test
    fun settingsScreenComposes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AlertsSettingsScreen(onBack = {}) }
        idle()
    }

    @Test
    fun settingsScreenWithAllCategoriesExpandedComposes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AlertsSettingsScreen(onBack = {}, initiallyExpanded = AlertCategory.entries.toSet()) }
        idle()
    }

    @Test
    fun styleEditorComposesForEveryAlert() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val styles = listOf(
            AlertStyle(),
            AlertStyle(icon = "bolt", accent = 0xFF5ED6E8, entrance = AlertEntrance.PULSE, durationSec = 9),
        )
        activity.setContent {
            Column {
                AlertCatalog.defs.forEachIndexed { i, def ->
                    AlertStyleEditor(AlertUis.of(def.id), def, styles[i % styles.size], onChange = {})
                }
            }
        }
        idle()
    }

    @Test
    fun styleSheetComposesAndFollowsChanges() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var style by mutableStateOf(AlertStyle(entrance = AlertEntrance.BOUNCE))
        activity.setContent {
            AlertStyleSheet(AlertUis.of("wifi"), AlertCatalog.def("wifi"), style, onChange = { style = it }, onDismiss = {})
        }
        idle()
        style = AlertStyle(icon = "router", durationSec = 3)
        idle()
    }
}
