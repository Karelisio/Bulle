package com.karelisio.bulle.modules.focusmode

import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.IslandArbiter
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.BulleJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Fake(
    override val key: String,
    override val moduleId: String,
    override val priority: Priority,
    override val showWhenHidden: Boolean = false,
    override val postedAt: Long = 0,
) : IslandContent() {
    @Composable
    override fun Leading() = Unit
}

class FocusLogicTest {

    private val music = Fake("music", "music", Priority.MEDIA)
    private val live = Fake("live", "live", Priority.LIVE)
    private val alert = Fake("alert", "alerts", Priority.ALERT)
    private val alarm = Fake("alarm", "live", Priority.ALARM)
    private val call = Fake("call", "call", Priority.CALL)
    private val timer = Fake("timer", "focus", Priority.LIVE)
    private val all = listOf(music, live, alert, alarm, call, timer)

    private fun on(s: FocusModeSettings = FocusModeSettings()) = FocusState(active = true, source = FocusSource.MANUAL, settings = s)

    @Test
    fun defaultThresholdKeepsCallsAlarmsAndTimer() {
        val visible = FocusLogic.visible(all, on())
        assertEquals(listOf(alarm, call, timer), visible)
        assertEquals(listOf(music, live, alert), FocusLogic.held(all, on()))
    }

    @Test
    fun thresholdAndModuleModes() {
        val s = FocusModeSettings(minPriority = Priority.ALERT.value, modules = mapOf("music" to FocusModuleMode.ALWAYS, "call" to FocusModuleMode.NEVER))
        val visible = FocusLogic.visible(all, on(s))
        assertTrue(music in visible)
        assertTrue(alert in visible)
        assertFalse(live in visible)
        // Module « jamais » : retenu même au-dessus du seuil…
        assertFalse(call in visible)
        // … sauf un contenu qui passe outre le masquage (appel entrant).
        val ringing = call.copy(key = "ring", showWhenHidden = true)
        assertTrue(ringing in FocusLogic.visible(listOf(ringing), on(s)))
    }

    @Test
    fun heldContentsComeBackWhenFocusEnds() {
        val during = FocusLogic.visible(all, on())
        assertFalse(music in during)
        val after = FocusLogic.visible(all, FocusState.Off)
        assertEquals(all, after)
        assertTrue(FocusLogic.held(all, FocusState.Off).isEmpty())
        // L'arbitre choisit alors la musique comme avant.
        assertEquals("music", IslandArbiter.arrange(listOf(music)).primary?.key)
        assertNull(IslandArbiter.arrange(listOf(music), filter = { FocusLogic.isEssential(it, FocusModeSettings()) }).primary)
    }

    @Test
    fun slotsAcrossMidnight() {
        val night = FocusSlot(days = setOf(1), startMinute = 22 * 60, endMinute = 7 * 60) // lundi soir
        assertTrue(FocusLogic.inSlot(night, 1, 23 * 60))
        assertTrue(FocusLogic.inSlot(night, 2, 3 * 60)) // mardi 3 h
        assertFalse(FocusLogic.inSlot(night, 2, 7 * 60))
        assertFalse(FocusLogic.inSlot(night, 1, 3 * 60)) // lundi 3 h = dimanche soir
        assertFalse(FocusLogic.inSlot(night, 2, 23 * 60))
        val sunday = FocusSlot(days = setOf(7), startMinute = 23 * 60, endMinute = 60)
        assertTrue(FocusLogic.inSlot(sunday, 1, 30)) // lundi 0 h 30
        val day = FocusSlot(days = setOf(3), startMinute = 9 * 60, endMinute = 12 * 60)
        assertTrue(FocusLogic.inSlot(day, 3, 9 * 60))
        assertFalse(FocusLogic.inSlot(day, 3, 12 * 60))
        assertFalse(FocusLogic.inSlot(day.copy(enabled = false), 3, 10 * 60))
        assertTrue(FocusLogic.inSlot(FocusSlot(days = setOf(5), startMinute = 0, endMinute = 0), 5, 15 * 60))
    }

    @Test
    fun automaticSources() {
        val s = FocusModeSettings(apps = setOf("com.read"), slots = listOf(FocusSlot(setOf(1), 9 * 60, 10 * 60)))
        assertEquals(FocusSource.RULE, FocusLogic.automatic(s, true, "com.read", 1, 9 * 60))
        assertEquals(FocusSource.APP, FocusLogic.automatic(s, false, "com.read", 1, 9 * 60))
        assertEquals(FocusSource.SCHEDULE, FocusLogic.automatic(s, false, "x", 1, 9 * 60))
        assertNull(FocusLogic.automatic(s, false, "x", 1, 11 * 60))
        assertNull(FocusLogic.automatic(FocusModeSettings(), false, null, 1, 0))
    }

    @Test
    fun manualOverrideLastsUntilAutomaticChanges() {
        val s = FocusModeSettings()
        // Bascule manuelle hors créneau.
        val manual = ManualOverride(on = true, automaticWhenSet = false)
        assertEquals(FocusSource.MANUAL, FocusLogic.resolve(s, null, manual).source)
        // Coupé pendant un créneau : reste coupé tant que le créneau dure…
        val off = ManualOverride(on = false, automaticWhenSet = true)
        assertFalse(FocusLogic.resolve(s, FocusSource.SCHEDULE, off).active)
        // … puis le choix expire : le créneau suivant réactive le focus.
        assertFalse(FocusLogic.resolve(s, null, off).active)
        assertTrue(FocusLogic.resolve(s, FocusSource.SCHEDULE, ManualOverride(false, automaticWhenSet = false)).active)
        assertTrue(FocusLogic.resolve(s, FocusSource.RULE, null).active)
    }

    @Test
    fun toggleFlipsState() {
        FocusMode.resetForTests()
        assertFalse(FocusMode.state.value.active)
        FocusMode.toggle()
        assertTrue(FocusMode.state.value.active)
        assertEquals(FocusSource.MANUAL, FocusMode.state.value.source)
        FocusMode.toggle()
        assertFalse(FocusMode.state.value.active)
        FocusMode.resetForTests()
    }

    @Test
    fun settingsJsonDefaults() {
        assertEquals(FocusModeSettings(), BulleJson.decodeFromString(FocusModeSettings.serializer(), "{}"))
        val s = FocusModeSettings(slots = listOf(FocusSlot()), apps = setOf("a"), modules = mapOf("music" to FocusModuleMode.NEVER))
        assertEquals(s, BulleJson.decodeFromString(FocusModeSettings.serializer(), BulleJson.encodeToString(FocusModeSettings.serializer(), s)))
    }
}
