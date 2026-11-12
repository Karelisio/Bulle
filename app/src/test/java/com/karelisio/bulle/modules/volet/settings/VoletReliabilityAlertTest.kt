package com.karelisio.bulle.modules.volet.settings

import com.karelisio.bulle.modules.volet.settings.VoletReliability.AlertAction
import com.karelisio.bulle.modules.volet.settings.VoletReliability.AlertMemory
import com.karelisio.bulle.modules.volet.settings.VoletReliability.SNOOZE_MS
import com.karelisio.bulle.modules.volet.settings.VoletReliability.decide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Décision de l'alerte de fiabilité : elle ne doit plus resonner à chaque passage. */
class VoletReliabilityAlertTest {

    private val now = 1_000_000_000L
    private val killed = VoletIssue.ACCESSIBILITY_KILLED

    @Test
    fun firstProblemIsPosted() {
        val d = decide(killed, showing = false, memory = AlertMemory(), now = now)
        assertEquals(AlertAction.POST, d.action)
        assertEquals(killed, d.memory.posted)
    }

    @Test
    fun alreadyShownIsNotReposted() {
        val d = decide(killed, showing = true, memory = AlertMemory(posted = killed), now = now)
        assertEquals(AlertAction.NONE, d.action)
    }

    @Test
    fun shownButChangedIsUpdatedSilently() {
        val d = decide(VoletIssue.ACCESSIBILITY_DISABLED, showing = true, memory = AlertMemory(posted = killed), now = now)
        assertEquals(AlertAction.UPDATE_SILENTLY, d.action)
        assertEquals(VoletIssue.ACCESSIBILITY_DISABLED, d.memory.posted)
    }

    @Test
    fun dismissedIsSnoozedForTwelveHours() {
        // Posée au passage précédent, absente maintenant : balayée.
        val d1 = decide(killed, showing = false, memory = AlertMemory(posted = killed), now = now)
        assertEquals(AlertAction.NONE, d1.action)
        assertNull(d1.memory.posted)
        assertEquals(now, d1.memory.dismissedAt)
        // Passages suivants : toujours rien, même nature (accessibilité) y compris DISABLED.
        val d2 = decide(VoletIssue.ACCESSIBILITY_DISABLED, false, d1.memory, now + SNOOZE_MS - 1)
        assertEquals(AlertAction.NONE, d2.action)
        assertEquals(now, d2.memory.dismissedAt)
        // 12 h plus tard : reposée.
        val d3 = decide(killed, false, d2.memory, now + SNOOZE_MS)
        assertEquals(AlertAction.POST, d3.action)
    }

    @Test
    fun dismissedButNatureChangedIsPosted() {
        val d1 = decide(killed, false, AlertMemory(posted = killed), now)
        val d2 = decide(VoletIssue.LISTENER_DISABLED, false, d1.memory, now + 60_000)
        assertEquals(AlertAction.POST, d2.action)
        assertEquals(VoletIssue.LISTENER_DISABLED, d2.memory.posted)
    }

    @Test
    fun allGoodCancelsAndForgets() {
        val d = decide(null, showing = true, memory = AlertMemory(posted = killed, dismissedAt = now, dismissedNature = "accessibility"), now = now)
        assertEquals(AlertAction.CANCEL, d.action)
        assertEquals(AlertMemory(), d.memory)
        // Problème qui revient après résolution : reposé immédiatement.
        assertEquals(AlertAction.POST, decide(killed, false, d.memory, now + 1).action)
    }

    @Test
    fun clockGoingBackwardsDoesNotSnoozeForever() {
        val m = AlertMemory(dismissedAt = now, dismissedNature = "accessibility")
        assertEquals(AlertAction.POST, decide(killed, false, m, now - 1).action)
    }

    @Test
    fun runtimePermissionOnlyFromAndroid13() {
        assertEquals(false, VoletReliability.needsRuntimePermission(31))
        assertEquals(false, VoletReliability.needsRuntimePermission(32))
        assertEquals(true, VoletReliability.needsRuntimePermission(33))
    }
}
