package com.karelisio.bulle.island

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.Modules
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private data class Item(override val key: String, override val moduleId: String, override val priority: Priority) : IslandContent() {
    override val postedAt: Long = 0

    @Composable
    override fun Leading() = Unit
}

private class ListModule(override val id: String) : IslandModule {
    override val titleRes = com.karelisio.bulle.R.string.app_name
    val items = MutableStateFlow<List<IslandContent>>(emptyList())
    override fun contents(env: ModuleEnv) = items
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IslandControllerTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f)

    @Test
    fun windowFollowsGeometryMoveWithoutNewRequest() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val window = IslandWindow(activity)
        val controller = IslandController(activity, scope, window)
        controller.updateScreen { it.copy(geometry = geometry) }
        controller.applyWindow(WindowRequest(width = 200, height = 100, touchable = true, watchOutside = false))
        assertEquals(440, window.params.x)
        assertEquals(10, window.params.y)

        // Réglage fin X/Y : même taille, position différente → la fenêtre doit suivre.
        controller.updateScreen { it.copy(geometry = geometry.copy(centerX = 600, top = 30)) }
        assertEquals(500, window.params.x)
        assertEquals(30, window.params.y)
        assertEquals(200, window.params.width)
        scope.cancel()
    }

    @Test
    fun swapWithStalePairSwapsOnFirstTap() {
        Modules.registerAll()
        val low = ListModule("test-low").also(ModuleRegistry::register)
        val mid = ListModule("test-mid").also(ModuleRegistry::register)
        val high = ListModule("test-high").also(ModuleRegistry::register)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen { it.copy(geometry = geometry) }
        mid.items.value = listOf(Item("mid", "test-mid", Priority.ALERT))
        low.items.value = listOf(Item("low", "test-low", Priority.MEDIA))
        idle()
        assertEquals("mid", controller.state.value.primary?.key)

        controller.swapBubbles()
        idle()
        assertEquals("low", controller.state.value.primary?.key)

        // Un contenu plus prioritaire arrive : l'échange mémorisé (mid, low) n'est plus affiché.
        high.items.value = listOf(Item("high", "test-high", Priority.CALL))
        idle()
        assertEquals("high", controller.state.value.primary?.key)
        assertEquals("mid", controller.state.value.secondary?.key)

        // Un seul toucher suffit à échanger la nouvelle paire.
        controller.swapBubbles()
        idle()
        assertEquals("mid", controller.state.value.primary?.key)
        assertEquals("high", controller.state.value.secondary?.key)

        controller.swapBubbles()
        idle()
        assertEquals("high", controller.state.value.primary?.key)

        listOf(low, mid, high).forEach { it.items.value = emptyList() }
        idle()
        scope.cancel()
    }
}
