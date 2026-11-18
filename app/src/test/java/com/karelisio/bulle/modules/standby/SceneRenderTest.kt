package com.karelisio.bulle.modules.standby

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.modules.standby.ui.SceneFrame
import com.karelisio.bulle.modules.standby.ui.StandbyInputs
import com.karelisio.bulle.modules.standby.ui.StandbyPageEditor
import com.karelisio.bulle.modules.standby.ui.StandbySceneSettings
import com.karelisio.bulle.modules.standby.ui.StandbySceneView
import com.karelisio.bulle.modules.standby.ui.StandbyScreen
import com.karelisio.bulle.modules.standby.ui.drawAurora
import com.karelisio.bulle.modules.standby.ui.drawBubbles
import com.karelisio.bulle.modules.standby.ui.drawLava
import com.karelisio.bulle.modules.standby.ui.drawLiquidClock
import com.karelisio.bulle.modules.standby.ui.drawPulse
import com.karelisio.bulle.modules.standby.ui.drawStars
import com.karelisio.bulle.modules.standby.ui.drawWaves
import com.karelisio.bulle.testing.ComposeDispatcherReset
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Scènes de Veille : chacune se dessine (pixels non noirs) et chaque écran se compose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        ComposeDispatcherReset.unstick()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        assertFalse(controller.get().isFinishing)
    }

    private val colors = listOf(Color(0xFF1E88E5), Color(0xFFEC407A), Color(0xFFFFB300), Color(0xFF43A047))

    /** Dessine une image dans un bitmap noir et compte les pixels allumés. */
    private fun lit(block: DrawScope.() -> Unit): Int {
        val bmp = ImageBitmap(320, 160)
        val canvas = Canvas(bmp)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(320f, 160f)) {
            drawRect(Color.Black)
            block()
        }
        val px = bmp.toPixelMap()
        var n = 0
        for (x in 0 until px.width) for (y in 0 until px.height) {
            val c = px[x, y]
            if (c.red + c.green + c.blue > 0.15f) n++
        }
        return n
    }

    @Test
    fun everySceneDrawsSomething() {
        for (pulse in listOf(0f, 1f)) for (t in listOf(0f, 7.3f, 3_600f)) {
            val f = SceneFrame(t, colors, 1f, pulse, Color.White, playing = t > 1f)
            assertTrue("lave", lit { drawLava(f) } > 200)
            assertTrue("bulles", lit { drawBubbles(f) } > 50)
            assertTrue("aurore", lit { drawAurora(f) } > 200)
            assertTrue("vagues", lit { drawWaves(f) } > 500)
            assertTrue("étoiles", lit { drawStars(f) } > 30)
        }
        assertTrue("onde", lit { drawPulse(SceneFrame(0f, colors, 1f, 1f, Color.White)) } > 200)
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(2f), LayoutDirection.Ltr)
        val style = androidx.compose.ui.text.TextStyle(fontSize = 40.sp)
        val f = SceneFrame(4f, colors, 1f, 0f, Color.White)
        assertTrue("horloge", lit { drawLiquidClock(f, measurer.measure("12:34", style), measurer.measure("12:33", style), 0.5f) } > 200)
    }

    @Test
    fun everySceneRendersFullScreenAndInSplitView() {
        for (scene in StandbyScene.entries) for (palette in listOf(ScenePalette.DYNAMIC, ScenePalette.SUNSET)) {
            val s = SceneSettings(scene = scene, palette = palette, density = 2f)
            render { Box(Modifier.size(800.dp, 370.dp)) { StandbySceneView(s, music = null, fullScreen = true) } }
            render { Box(Modifier.size(300.dp, 300.dp)) { StandbySceneView(s, music = null, fullScreen = false) } }
        }
    }

    @Test
    fun scenePageAndWidgetInStandby() {
        val split = StandbySettings(
            pages = listOf(StandbyPage.CLOCKS),
            layouts = mapOf(StandbyPage.CLOCKS to StandbyLayout(listOf(StandbyWidget.CLOCK), listOf(StandbyWidget.SCENE))),
        )
        for (night in listOf(false, true)) {
            render { StandbyScreen(StandbyInputs(StandbySettings(pages = listOf(StandbyPage.SCENE)), night), exiting = false, onExited = {}) }
            render { StandbyScreen(StandbyInputs(split, night), exiting = false, onExited = {}) }
        }
    }

    @Test
    fun sceneSettingsRender() {
        render { StandbySceneSettings(SceneSettings(cycleMinutes = 15)) {} }
        render { StandbyPageEditor(StandbyPage.SCENE, StandbySettings()) {} }
    }
}
