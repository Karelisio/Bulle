package com.karelisio.bulle.modules.network

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.battery.BatteryDetailContent
import com.karelisio.bulle.modules.battery.BatteryDetails
import com.karelisio.bulle.modules.battery.BatteryHealth
import com.karelisio.bulle.modules.battery.BatteryInfo
import com.karelisio.bulle.modules.battery.BtAccessory
import com.karelisio.bulle.modules.battery.ChargeStatus
import com.karelisio.bulle.modules.battery.LevelSample
import com.karelisio.bulle.modules.battery.LiveBattery
import com.karelisio.bulle.modules.battery.PowerSource
import com.karelisio.bulle.modules.battery.ui.BatterySettingsScreen
import com.karelisio.bulle.modules.network.ui.NetworkSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rendu des contenus Réseau et Batterie détaillée (compact puis étendu) et de leurs écrans de réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkBatteryRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        assertFalse(activity.isFinishing)
        return activity
    }

    private val history = (0 until 60).map { RatePoint(rx = 2_000_000.0 + it * 10_000, tx = 50_000.0) }
    private val snapshot = NetworkSnapshot(2_400_000.0, 80_000.0, history, ConnectionInfo(NetKind.WIFI, vpn = true))
    private val charging = BatteryInfo(64, ChargeStatus.CHARGING, PowerSource.AC, 31.4, 4_210, BatteryHealth.GOOD)

    @Test
    fun contentsRenderInIsland() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        val contents: List<IslandContent> = listOf(
            NetworkRateContent(snapshot, RateUnit.BYTES, postedAt = 1),
            NetworkRateContent(snapshot.copy(rx = 1_000.0, tx = 900_000.0, history = emptyList()), RateUnit.BITS, postedAt = 2),
            BatteryDetailContent(charging, "Plein dans 45 min", showAccessories = true, postedAt = 3),
            BatteryDetailContent(
                charging.copy(level = 15, status = ChargeStatus.DISCHARGING, source = PowerSource.NONE, temperatureC = null, voltageMv = null),
                null, showAccessories = false, postedAt = 4,
            ),
        )
        for (c in contents) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            if (!controller.state.value.expanded) controller.onGesture(IslandGesture.TAP)
            idle()
            assertTrue(controller.state.value.expanded)
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }
        scope.cancel()
    }

    @Test
    fun compactSubtitleShowsOtherDirection() {
        val c = NetworkRateContent(snapshot, RateUnit.BYTES, postedAt = 1)
        assertTrue(c.compactSubtitle.startsWith("↑ "))
        val up = NetworkRateContent(snapshot.copy(rx = 10.0, tx = 5_000_000.0), RateUnit.BYTES, postedAt = 1)
        assertTrue(up.compactSubtitle.startsWith("↓ "))
    }

    @Test
    fun detailBodiesRenderOnLightSurface() {
        render {
            androidx.compose.foundation.layout.Column {
                NetworkDetails(snapshot, RateUnit.BITS, UsageState(day = 0, wifiBytes = 1_200_000_000, mobileBytes = 34_000_000, partial = true, since = 0), onDark = false)
                BatteryDetails(
                    charging,
                    LiveBattery(currentMicroA = 2_100_000, chargeTimeMs = null),
                    (0..6).map { LevelSample(System.currentTimeMillis() - (6 - it) * 240_000L, 58 + it, true) },
                    listOf(BtAccessory("AA", "Montre", BtKind.WATCH, 72), BtAccessory("BB", null, BtKind.HEADPHONES, null)),
                    onDark = false,
                )
            }
        }
    }

    @Test
    fun settingsScreensRender() {
        render { NetworkSettingsScreen(onBack = {}) }
        render { BatterySettingsScreen(onBack = {}) }
    }
}
