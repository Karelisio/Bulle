package com.karelisio.bulle.island

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.volet.VoletStrips
import com.karelisio.bulle.testing.ComposeDispatcherReset
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private data class LandscapeContent(override val postedAt: Long = 1) : IslandContent() {
    override val key = "push:landscape"
    override val moduleId = PushModule.id
    override val priority = Priority.ALERT

    @Composable
    override fun Leading() = Text("A")

    @Composable
    override fun Trailing() = Text("B")

    @Composable
    override fun Expanded(host: IslandHost) {
        Column { repeat(4) { Text("Ligne $it") } }
    }
}

/** Paysage, caméra sur le côté : compacte verticale, carte dépliée centrée sur la caméra, zone tactile qui la couvre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LandscapeIslandRenderTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() = ComposeDispatcherReset.unstick()

    @Test
    fun verticalIslandExpandsAroundTheCamera() {
        Modules.registerAll()
        val built = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = built.get()
        val scope = MainScope()
        val window = IslandWindow(activity)
        val controller = IslandController(activity, scope, window)
        val g = CutoutLocator.sideGeometry(24, 475, 134, 805, 2800, 1280, 3f, cameraLeft = true)
        controller.updateScreen { it.copy(geometry = g) }
        activity.setContent { IslandScene(controller) }
        idle()

        PushModule.push(LandscapeContent())
        idle()
        val compact = VoletStrips.islandEnvelope.value!!
        // Compacte : allongée le long du bord, autour de la caméra.
        assertTrue("compacte au-dessus de la caméra", compact.y <= g.top)
        assertTrue("compacte sous la caméra", compact.bottom >= g.top + g.height)

        controller.onGesture(IslandGesture.TAP)
        idle()
        assertTrue(controller.state.value.expanded)
        val expanded = VoletStrips.islandEnvelope.value!!
        // Carte : dans l'écran, centrée verticalement sur la caméra, ouverte vers la droite.
        assertTrue("carte dans l'écran (haut)", expanded.y >= 0)
        assertTrue("carte dans l'écran (bas) : ${expanded.bottom}", expanded.bottom <= 1280 + 4)
        val center = (expanded.y + expanded.bottom) / 2
        assertTrue("carte centrée sur la caméra ($center)", kotlin.math.abs(center - g.centerY) <= 40 || expanded.y == 0)
        assertTrue("carte vers l'intérieur", expanded.right > g.centerX + 300)
        assertEquals(true, expanded.touchable)

        controller.onOutsideTouch()
        idle()
        PushModule.remove("push:landscape")
        idle()
        window.destroy()
        scope.cancel()
        built.pause().stop().destroy()
    }

    @Test
    fun portraitEnvelopeIsUnchanged() {
        Modules.registerAll()
        val built = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = built.get()
        val scope = MainScope()
        val window = IslandWindow(activity)
        val controller = IslandController(activity, scope, window)
        val g = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f)
        controller.updateScreen { it.copy(geometry = g) }
        activity.setContent { IslandScene(controller) }
        PushModule.push(LandscapeContent())
        idle()
        controller.onGesture(IslandGesture.TAP)
        idle()
        // Portrait : la zone tactile part toujours du haut de l'île.
        assertEquals(10, VoletStrips.islandEnvelope.value!!.y)
        PushModule.remove("push:landscape")
        idle()
        window.destroy()
        scope.cancel()
        built.pause().stop().destroy()
    }
}
