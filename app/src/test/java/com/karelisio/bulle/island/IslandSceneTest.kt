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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private data class TestContent(override val postedAt: Long = 1) : IslandContent() {
    override val key = "push:test"
    override val moduleId = PushModule.id
    override val priority = Priority.ALERT

    @Composable
    override fun Leading() = Text("A")

    @Composable
    override fun Trailing() = Text("B")

    @Composable
    override fun Expanded(host: IslandHost) {
        Column { Text("Titre"); Text("Texte") }
    }
}

/** Fait passer l'île par tous ses états pour vérifier qu'aucun ne plante. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IslandSceneTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun idleCompactExpandedCollapse() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()
        assertTrue(controller.state.value.visible)

        PushModule.push(TestContent())
        idle()
        assertEquals("push:test", controller.state.value.primary?.key)
        assertFalse(controller.state.value.expanded)

        controller.onGesture(IslandGesture.TAP)
        idle()
        assertTrue(controller.state.value.expanded)

        controller.onGesture(IslandGesture.TAP)
        idle()
        assertFalse(controller.state.value.expanded)

        controller.onGesture(IslandGesture.SWIPE_DOWN)
        idle()
        assertTrue(controller.state.value.expanded)

        controller.onOutsideTouch()
        idle()
        assertFalse(controller.state.value.expanded)

        controller.dismiss("push:test")
        idle()
        assertEquals(null, controller.state.value.primary)
        PushModule.remove("push:test")
        scope.cancel()
    }
}
