package com.karelisio.bulle

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.system.ModuleUsage
import com.karelisio.bulle.ui.home.DashState
import com.karelisio.bulle.ui.home.Usage
import com.karelisio.bulle.ui.home.UsageSetup
import com.karelisio.bulle.ui.home.dashState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accueil en tableau de bord, assistant par usage, modules inutilisés. */
class V096UxTest {

    @Test
    fun dashboardStates() {
        assertEquals(DashState.OFF, dashState(enabled = false, running = true))
        assertEquals(DashState.ON, dashState(enabled = true, running = true))
        assertEquals(DashState.ON, dashState(enabled = true, running = null))
        assertEquals(DashState.STOPPED, dashState(enabled = true, running = false))
    }

    @Test
    fun usageAssistantTurnsOnWithoutTurningOff() {
        val s = BulleSettings(edges = BulleSettings().edges.copy(enabled = true))
        val r = UsageSetup.apply(s, setOf(Usage.MUSIC, Usage.BEDSIDE))
        assertEquals(true, r.modules["music"])
        assertEquals(true, r.modules["volume"])
        assertTrue(r.standby.enabled)
        assertTrue(r.music.lyricsEnabled)
        // Rien n'est coupé, et ce qui n'est pas choisi reste tel quel.
        assertTrue(r.edges.enabled)
        assertFalse(r.volet.enabled)
        val n = UsageSetup.apply(BulleSettings(), setOf(Usage.NOTIFICATIONS, Usage.GESTURES))
        assertTrue(n.volet.enabled && n.halo.enabled && n.edges.enabled)
        assertEquals(true, n.modules["messages"])
    }

    @Test
    fun idleModulesAfterSevenDays() {
        val day = 24 * 3_600_000L
        assertFalse(ModuleUsage.isIdle(enabled = true, lastUsed = 0, enabledSince = 0, snoozedUntil = 0, now = 6 * day))
        assertTrue(ModuleUsage.isIdle(true, 0, 0, 0, 8 * day))
        assertFalse("utilisé récemment", ModuleUsage.isIdle(true, 7 * day, 0, 0, 8 * day))
        assertFalse("question repoussée", ModuleUsage.isIdle(true, 0, 0, 30 * day, 8 * day))
        assertFalse("coupé", ModuleUsage.isIdle(false, 0, 0, 0, 30 * day))
    }
}
