package com.karelisio.bulle.modules

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.island.ui.Burst
import com.karelisio.bulle.modules.edges.EdgeBus
import com.karelisio.bulle.modules.edges.EdgeFeedback
import com.karelisio.bulle.modules.edges.EdgeSide
import com.karelisio.bulle.modules.edges.EdgeSlider
import com.karelisio.bulle.modules.edges.EdgeTouchState
import com.karelisio.bulle.modules.edges.ui.EdgeSettingsScreen
import com.karelisio.bulle.modules.halo.HaloCanvas
import com.karelisio.bulle.modules.halo.HaloKind
import com.karelisio.bulle.modules.halo.HaloLook
import com.karelisio.bulle.modules.halo.HaloOutline
import com.karelisio.bulle.modules.halo.HaloCamera
import com.karelisio.bulle.modules.halo.HaloDirection
import com.karelisio.bulle.modules.halo.HaloStart
import com.karelisio.bulle.modules.halo.HaloRect
import com.karelisio.bulle.modules.halo.HaloRequest
import com.karelisio.bulle.modules.halo.HaloScreen
import com.karelisio.bulle.modules.halo.HaloSettings
import com.karelisio.bulle.modules.halo.HaloStyle
import com.karelisio.bulle.modules.halo.drawHalo
import com.karelisio.bulle.modules.halo.ui.HaloSettingsScreen
import com.karelisio.bulle.modules.standby.EchoThought
import com.karelisio.bulle.modules.standby.StandbyClock
import com.karelisio.bulle.modules.standby.StandbyPage
import com.karelisio.bulle.modules.standby.StandbySettings
import com.karelisio.bulle.modules.standby.ui.StandbyClockView
import com.karelisio.bulle.modules.standby.ui.StandbyInputs
import com.karelisio.bulle.modules.standby.ui.StandbyScreen
import com.karelisio.bulle.modules.standby.ui.StandbySettingsScreen
import com.karelisio.bulle.testing.ComposeDispatcherReset
import com.karelisio.bulle.ui.onboarding.PermissionsScreen
import com.karelisio.bulle.ui.theme.BulleTheme
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

/** Rendu des écrans et des dessins de Halo, Bords et Veille : aucun ne doit planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V090RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        ComposeDispatcherReset.unstick()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit): ActivityController<ComponentActivity> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        idle()
        assertFalse(controller.get().isFinishing)
        return controller
    }

    @Test
    fun haloSettingsRender() {
        render { HaloSettingsScreen(onBack = {}) }
    }

    @Test
    fun edgeSettingsTintZonesWhileShown() {
        val c = render { EdgeSettingsScreen(onBack = {}) }
        assertTrue(EdgeBus.preview.value)
        c.pause().stop().destroy()
        idle()
        assertFalse(EdgeBus.preview.value)
    }

    @Test
    fun standbySettingsRender() {
        render { StandbySettingsScreen(onBack = {}) }
    }

    @Test
    fun permissionsListRender() {
        render { PermissionsScreen(onBack = {}) }
    }

    @Test
    fun everyHaloStyleDrawsAtEveryStep() {
        val screen = HaloScreen(
            1080f, 2400f, 90f, 90f, 90f, 90f,
            cutout = HaloRect(500f, 30f, 580f, 110f), density = 3f,
        )
        render {
            Box(Modifier.size(360.dp, 800.dp)) {
                Canvas(Modifier.size(360.dp, 800.dp)) {
                    for (camera in HaloCamera.entries) for (dir in HaloDirection.entries) for (start in HaloStart.entries) {
                        val look = HaloLook.of(
                            HaloSettings(camera = camera, direction = dir, start = start, secondColor = 0xFFFF8FB8, glow = if (dir == HaloDirection.BOTH) 1f else 0f),
                            3f,
                        )
                        val outline = HaloOutline.of(screen, look.thicknessPx, 6f, camera)
                        for (kind in HaloKind.entries) for (style in HaloStyle.entries) {
                            val req = HaloRequest(kind, 0xFF7FD3FF.toInt(), style, level = 0.6f, id = 7)
                            val total = look.totalMs(kind)
                            for (step in 0..6) for (reduce in listOf(false, true)) {
                                drawHalo(outline, req, look, total * step / 6, total, reduce, Burst(1, Color.Cyan), 3f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun haloCanvasAnimatesAndFinishes() {
        var done = false
        render {
            HaloCanvas(
                HaloRequest(HaloKind.TEST, 0xFFFF8FB8.toInt(), HaloStyle.SIGNATURE),
                HaloScreen(1080f, 2400f, density = 3f),
                HaloLook.of(HaloSettings(durationMs = 600), 3f),
                onFinished = { done = true },
            )
        }
        // Sans animations (tests), le bord statique reste affiché la durée du halo (attente réelle).
        repeat(40) {
            if (!done) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
                Thread.sleep(100)
            }
        }
        assertTrue(done)
    }

    @Test
    fun edgeFeedbackWithDropAndGauge() {
        val state = EdgeTouchState().apply {
            touching = true
            x = 20f; y = 400f; stretch = 60f
            slider = EdgeSlider.BRIGHTNESS
            value = 0.42f
        }
        render { Box(Modifier.size(140.dp, 800.dp)) { EdgeFeedback(state, EdgeSide.LEFT, liquid = true) } }
        render { Box(Modifier.size(140.dp, 800.dp)) { EdgeFeedback(state.apply { slider = EdgeSlider.VOLUME }, EdgeSide.RIGHT, liquid = false) } }
    }

    @Test
    fun standbyPagesRenderDayAndNight() {
        val all = StandbySettings(pages = StandbyPage.entries.toList(), albumId = "1")
        for (night in listOf(false, true)) {
            render {
                StandbyScreen(
                    StandbyInputs(all, night, echo = EchoThought("Léa", "Pense à toi", System.currentTimeMillis())),
                    exiting = false,
                    onExited = {},
                )
            }
        }
    }

    @Test
    fun standbyPageEditorAndCustomLayoutRender() {
        val custom = StandbySettings(
            layouts = mapOf(
                StandbyPage.CLOCKS to com.karelisio.bulle.modules.standby.StandbyLayout(
                    listOf(com.karelisio.bulle.modules.standby.StandbyWidget.MUSIC),
                    com.karelisio.bulle.modules.standby.StandbyWidget.entries.take(6),
                    1.5f,
                ),
            ),
        )
        render { com.karelisio.bulle.modules.standby.ui.StandbyPageEditor(StandbyPage.CLOCKS, custom) {} }
        render { StandbyScreen(StandbyInputs(custom, night = false), exiting = false, onExited = {}) }
    }

    @Test
    fun standbyExitPlaysTheReverseAnimation() {
        var exited = false
        render { StandbyScreen(StandbyInputs(StandbySettings(), night = false), exiting = true, onExited = { exited = true }) }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertTrue(exited)
    }

    @Test
    fun everyClockStyleRenders() {
        val now = System.currentTimeMillis()
        StandbyClock.entries.forEach { style ->
            render { Box(Modifier.size(500.dp, 300.dp)) { StandbyClockView(style, now) } }
        }
    }
}
