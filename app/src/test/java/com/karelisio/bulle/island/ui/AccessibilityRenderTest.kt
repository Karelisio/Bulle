package com.karelisio.bulle.island.ui

import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.karelisio.bulle.R
import com.karelisio.bulle.core.a11y.IslandSpeech
import com.karelisio.bulle.core.island.IslandAccessibilityAction
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.settings.AccessibilitySettingsScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import com.karelisio.bulle.ui.theme.highContrastScheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private var played = 0

private data class SpeakingContent(override val postedAt: Long = 1) : IslandContent() {
    override val key = "push:a11y"
    override val moduleId = PushModule.id
    override val priority = Priority.MEDIA

    override fun describe(res: IslandSpeech.Res) = IslandSpeech.music(res, "Titre", "Artiste", playing = true)
    override fun accessibilityActions(host: IslandHost, res: IslandSpeech.Res) =
        listOf(IslandAccessibilityAction(res.str(R.string.a11y_action_pause)) { played++ })

    @Composable
    override fun Leading() = Text("A")

    @Composable
    override fun Trailing() = Text("Un texte compact assez long")

    @Composable
    override fun Expanded(host: IslandHost) = Text("Contenu étendu avec un texte agrandi")
}

/** Sémantique TalkBack de l'île et de l'appli, texte agrandi, contraste élevé. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityRenderTest {

    @Before
    fun noAnimations() {
        com.karelisio.bulle.testing.ComposeDispatcherReset.unstick()
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun root(view: View): RootForTest? {
        if (view is RootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) root(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun nodes(activity: ComponentActivity): List<SemanticsNode> {
        val owner = root(activity.window.decorView)!!.semanticsOwner
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(owner.rootSemanticsNode)
        return out
    }

    private fun startIsland(activity: ComponentActivity): Pair<IslandController, kotlinx.coroutines.CoroutineScope> {
        Modules.registerAll()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()
        PushModule.push(SpeakingContent())
        idle()
        return controller to scope
    }

    @Test
    fun islandExposesSummaryAndCustomActions() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val (controller, scope) = startIsland(activity)
        try {
            val expected = IslandSpeech.music(IslandSpeech.res(activity), "Titre", "Artiste", playing = true)
            val node = nodes(activity).firstOrNull {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(expected) == true
            }
            assertNotNull("résumé TalkBack absent", node)
            val actions = node!!.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
            assertTrue(actions.toString(), activity.getString(R.string.a11y_action_expand) in actions)
            assertTrue(actions.toString(), activity.getString(R.string.a11y_action_pause) in actions)
            assertEquals(activity.getString(R.string.a11y_state_collapsed), node.config.getOrNull(SemanticsProperties.StateDescription))
            // L'action personnalisée déclenche bien l'action du contenu.
            node.config.getOrNull(SemanticsActions.CustomActions)!!
                .first { it.label == activity.getString(R.string.a11y_action_pause) }.action()
            assertEquals(1, played)
            // « Déplier » ouvre l'île.
            node.config.getOrNull(SemanticsActions.CustomActions)!!
                .first { it.label == activity.getString(R.string.a11y_action_expand) }.action()
            idle()
            assertTrue(controller.state.value.expanded)
        } finally {
            PushModule.remove("push:a11y")
            scope.cancel()
        }
    }

    @Test
    fun mergedSwitchRowHasSwitchRoleAndState() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme(highContrast = false) { ModuleSwitchRow("Titre", "Résumé", checked = true) {} } }
        idle()
        val row = nodes(activity).firstOrNull { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
        assertNotNull(row)
        assertTrue(row!!.config.isMergingSemanticsOfDescendants)
        assertEquals(ToggleableState.On, row.config.getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    @Config(sdk = [35], qualifiers = "+w411dp")
    fun largeFontScaleRendersWithoutCrash() {
        RuntimeEnvironment.setFontScale(2f)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val (controller, scope) = startIsland(activity)
        try {
            controller.onGesture(IslandGesture.TAP)
            idle()
            assertTrue(controller.state.value.expanded)
            activity.setContent { BulleTheme { AccessibilitySettingsScreen(onBack = {}) } }
            idle()
        } finally {
            PushModule.remove("push:a11y")
            scope.cancel()
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun highContrastSchemeApplied() {
        val light = highContrastScheme(lightColorScheme(), dark = false)
        assertEquals(Color.Black, light.onSurface)
        assertEquals(Color.White, light.surface)
        val island = highContrastIslandScheme(lightColorScheme())
        assertEquals(Color.White, island.onSurface)
        // Gris : pas de teinte inventée par la saturation.
        val grey = saturate(Color(0xFF808080))
        assertEquals(grey.red, grey.green, 0.01f)

        var seen: Color? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme(dark = false, dynamic = false, highContrast = true) { seen = MaterialTheme.colorScheme.onSurface } }
        idle()
        assertEquals(Color.Black, seen)
    }
}
