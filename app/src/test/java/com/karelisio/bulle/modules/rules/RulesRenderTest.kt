package com.karelisio.bulle.modules.rules

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.ContentHub
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.focusmode.FocusMode
import com.karelisio.bulle.modules.focusmode.FocusModePrefs
import com.karelisio.bulle.modules.focusmode.FocusModeSettings
import com.karelisio.bulle.modules.focusmode.FocusSlot
import com.karelisio.bulle.modules.focusmode.FocusSource
import com.karelisio.bulle.modules.focusmode.FocusState
import com.karelisio.bulle.modules.focusmode.ui.FocusIndicator
import com.karelisio.bulle.modules.focusmode.ui.FocusModeSettingsScreen
import com.karelisio.bulle.modules.rules.ui.RuleEditorSheet
import com.karelisio.bulle.modules.rules.ui.RulesScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Rendu de l'éditeur de règles, de la liste des règles et de l'écran du mode focus ; filtrage du hub. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RulesRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)) }

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        FocusModePrefs.resetForTests()
        FocusMode.resetForTests()
        Modules.registerAll()
    }

    @After
    fun tearDown() {
        FocusModePrefs.resetForTests()
        FocusMode.resetForTests()
    }

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        assertFalse(activity.isFinishing)
        return activity
    }

    private val richRule = Rule(
        id = "r",
        conditions = listOf(
            RuleCondition(RuleTrigger.Charging),
            RuleCondition(RuleTrigger.BatteryLevel(30, below = true)),
            RuleCondition(RuleTrigger.TimeRange(22 * 60, 7 * 60), negated = true),
            RuleCondition(RuleTrigger.DaysOfWeek(setOf(6, 7))),
            RuleCondition(RuleTrigger.Place(48.85, 2.35, 250, "Maison", id = "p")),
            RuleCondition(RuleTrigger.BluetoothDevice("AA:BB", "Voiture")),
            RuleCondition(RuleTrigger.WifiConnected("Maison")),
            RuleCondition(RuleTrigger.ScreenLocked),
        ),
        match = RuleMatch.ANY,
        actions = listOf(RuleAction.FocusMode, RuleAction.SetModule("music", false), RuleAction.SetMotionStyle(com.karelisio.bulle.core.settings.MotionStyle.SOBER)),
    )

    @Test
    fun editorRendersEveryConditionKind() {
        var draft: Rule? = null
        render {
            RuleEditorSheet(
                initial = richRule,
                world = RuleWorld(charging = true),
                onDraft = { draft = it },
                onDismiss = {},
                onSave = {},
            )
        }
        idle()
        assertEquals(richRule, draft)
    }

    @Test
    fun editorRendersNewRule() {
        var draft: Rule? = null
        render { RuleEditorSheet(initial = null, world = RuleWorld(), onDraft = { draft = it }, onDismiss = {}, onSave = {}) }
        assertEquals(listOf(RuleCondition(RuleTrigger.Charging)), draft?.conditions)
    }

    @Test
    fun rulesListRenders() {
        render { RulesScreen(onBack = {}) }
    }

    @Test
    fun focusScreenRendersAndToggles() {
        FocusModePrefs.update(context) { FocusModeSettings(slots = listOf(FocusSlot()), apps = setOf("com.example")) }
        render { FocusModeSettingsScreen(onBack = {}) }
        FocusMode.toggle()
        idle()
        assertTrue(FocusMode.state.value.active)
        render { FocusIndicator(FocusState(active = true, source = FocusSource.MANUAL)) }
    }

    private data class Fake(override val key: String, override val moduleId: String, override val priority: com.karelisio.bulle.core.island.Priority) : IslandContent() {
        @Composable
        override fun Leading() = Unit
    }

    @Test
    fun hubHoldsNonEssentialDuringFocus() {
        val scope = MainScope()
        val focus = MutableStateFlow(FocusState.Off)
        val music = Fake("m", "music", com.karelisio.bulle.core.island.Priority.MEDIA)
        val call = Fake("c", "call", com.karelisio.bulle.core.island.Priority.CALL)
        val registry = com.karelisio.bulle.core.island.ModuleRegistry
        val fakeModule = object : com.karelisio.bulle.core.island.IslandModule {
            override val id = "fake_focus_test"
            override val titleRes = com.karelisio.bulle.R.string.rules_title

            // Désactivé par défaut : n'interfère pas avec les autres tests qui partagent le registre.
            override val enabledByDefault = false
            override fun contents(env: ModuleEnv) = kotlinx.coroutines.flow.flowOf(listOf(music, call))
        }
        registry.register(fakeModule)
        val settings = MutableStateFlow(BulleSettings(modules = registry.modules.associate { it.id to (it.id == fakeModule.id) }))
        val hub = ContentHub(ModuleEnv(context, scope, settings), registry, focus)
        idle()
        assertEquals(setOf("m", "c"), hub.contents.value.map { it.key }.toSet())
        focus.value = FocusState(active = true, source = FocusSource.MANUAL)
        idle()
        assertEquals(listOf("c"), hub.contents.value.map { it.key })
        focus.value = FocusState.Off
        idle()
        assertEquals(setOf("m", "c"), hub.contents.value.map { it.key }.toSet())
        scope.cancel()
    }
}
