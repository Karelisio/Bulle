package com.karelisio.bulle.island.ui

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Path
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.settings.PositionScreen
import com.karelisio.bulle.ui.settings.StyleScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
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

/** Contenu avec un titre long et une seconde ligne compacte. */
private data class LongContent(override val postedAt: Long = 1) : IslandContent() {
    override val key = "push:options"
    override val moduleId = PushModule.id
    override val priority = Priority.ALERT
    override val compactSubtitle = "encore 4 min"

    @Composable
    override fun Leading() = Text("A")

    @Composable
    override fun Trailing() = Text("Un titre bien trop long pour l'emplacement compact", maxLines = 1)

    @Composable
    override fun Expanded(host: IslandHost) {
        Column { Text("Titre"); Text("Texte") }
    }
}

/** Rendu de l'île et des écrans Style / Position avec les nouvelles options. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IslandOptionsRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun noAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun resetSettings() = runBlocking {
        SettingsRepository.update(context) { com.karelisio.bulle.core.settings.BulleSettings() }
    }

    private fun enableAllOptions(direction: OpenDirection) = runBlocking {
        SettingsRepository.update(context) {
            it.copy(
                position = it.position.copy(openDirection = direction),
                motion = it.motion.copy(style = MotionStyle.PLAYFUL, blurTransitions = false, haptics = false),
                style = it.style.copy(
                    cornerSmoothing = 0.8f,
                    font = IslandFont.SERIF,
                    textScale = 1.2f,
                    marquee = true,
                    twoLineCompact = true,
                    outline = true,
                ),
            )
        }
    }

    private fun runScene(direction: OpenDirection) {
        enableAllOptions(direction)
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(
                geometry = IslandGeometry(
                    centerX = if (direction == OpenDirection.RIGHT) 200 else 880, top = 10, width = 180, height = 110,
                    screenWidth = 1080, density = 3f, openDirection = direction,
                ),
            )
        }
        activity.setContent { IslandScene(controller) }
        idle()
        // Les réglages sont relus de DataStore en arrière-plan : on attend qu'ils arrivent.
        val deadline = System.currentTimeMillis() + 5_000
        while (controller.state.value.style.cornerSmoothing != 0.8f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            idle()
        }
        assertTrue(controller.state.value.visible)
        assertEquals(0.8f, controller.state.value.style.cornerSmoothing, 0f)
        assertEquals(direction, controller.state.value.geometry.openDirection)

        PushModule.push(LongContent())
        idle()
        assertEquals("push:options", controller.state.value.primary?.key)

        controller.onGesture(IslandGesture.TAP)
        idle()
        assertTrue(controller.state.value.expanded)
        controller.onOutsideTouch()
        idle()
        assertFalse(controller.state.value.expanded)

        controller.dismiss("push:options")
        idle()
        PushModule.remove("push:options")
        scope.cancel()
    }

    @Test
    fun sceneOpensToTheLeftWithAllOptions() = runScene(OpenDirection.LEFT)

    @Test
    fun sceneOpensToTheRightWithAllOptions() = runScene(OpenDirection.RIGHT)

    @Test
    fun sceneCentredWithAllOptions() = runScene(OpenDirection.CENTER)

    @Test
    fun smoothPathFillsItsRectangle() {
        for (s in listOf(0f, 0.5f, 1f)) {
            val path = Path().apply { addSmoothRoundRect(10f, 20f, 210f, 80f, 30f, 12f, s) }
            val b = path.getBounds()
            assertEquals(10f, b.left, 0.5f)
            assertEquals(20f, b.top, 0.5f)
            assertEquals(210f, b.right, 0.5f)
            assertEquals(80f, b.bottom, 0.5f)
        }
    }

    private fun compose(content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        idle()
        controller.pause().stop().destroy()
    }

    @Test
    fun styleScreenComposesWithDefaultsAndOptions() {
        compose { StyleScreen(onBack = {}) }
        enableAllOptions(OpenDirection.LEFT)
        compose { StyleScreen(onBack = {}) }
    }

    @Test
    fun positionScreenComposesWithEachDirection() {
        compose { PositionScreen(onBack = {}) }
        enableAllOptions(OpenDirection.RIGHT)
        compose { PositionScreen(onBack = {}) }
    }
}
