package com.karelisio.bulle.modules.volet.settings

import com.karelisio.bulle.modules.volet.history.HistoryCandidate
import com.karelisio.bulle.modules.volet.history.HistoryLogic
import com.karelisio.bulle.modules.volet.history.NotificationHistoryEntity
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.onboarding.PermissionKey
import com.karelisio.bulle.ui.onboarding.PermissionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Logique pure : bannière de fiabilité, historique, bornes, autorisations du Volet. */
class VoletSettingsLogicTest {

    private val ok = VoletServiceState(
        voletEnabled = true,
        accessibilityEnabled = true,
        accessibilityRunning = true,
        listenerGranted = true,
        listenerConnected = true,
    )

    @Test
    fun noIssueWhenAllGoodOrVoletOff() {
        assertTrue(VoletReliability.issues(ok).isEmpty())
        val broken = VoletServiceState(false, false, false, false, false)
        assertTrue(VoletReliability.issues(broken).isEmpty())
    }

    @Test
    fun issuesAreDiagnosed() {
        assertEquals(listOf(VoletIssue.ACCESSIBILITY_DISABLED), VoletReliability.issues(ok.copy(accessibilityEnabled = false, accessibilityRunning = false)))
        assertEquals(listOf(VoletIssue.ACCESSIBILITY_KILLED), VoletReliability.issues(ok.copy(accessibilityRunning = false)))
        assertEquals(listOf(VoletIssue.LISTENER_DISABLED), VoletReliability.issues(ok.copy(listenerGranted = false, listenerConnected = false)))
        assertEquals(listOf(VoletIssue.LISTENER_DISCONNECTED), VoletReliability.issues(ok.copy(listenerConnected = false)))
        // L'accessibilité passe en premier (c'est elle qui affiche le volet).
        val both = VoletReliability.issues(ok.copy(accessibilityRunning = false, listenerConnected = false))
        assertEquals(VoletIssue.ACCESSIBILITY_KILLED, both.first())
        assertTrue(VoletReliability.isAccessibility(both.first()))
        assertFalse(VoletReliability.isAccessibility(both.last()))
    }

    private val candidate = HistoryCandidate("k", "p", "Titre", "Texte", 1L, null, ongoing = false, groupSummary = false)

    @Test
    fun recordingRules() {
        assertTrue(HistoryLogic.shouldRecord(true, candidate, null))
        assertFalse(HistoryLogic.shouldRecord(false, candidate, null))
        assertFalse(HistoryLogic.shouldRecord(true, candidate.copy(ongoing = true), null))
        assertFalse(HistoryLogic.shouldRecord(true, candidate.copy(groupSummary = true), null))
        assertFalse(HistoryLogic.shouldRecord(true, candidate.copy(title = " ", text = ""), null))
        val last = NotificationHistoryEntity(1, "k", "p", "App", "Titre", "Texte", 0L, null)
        assertFalse(HistoryLogic.shouldRecord(true, candidate, last))
        assertTrue(HistoryLogic.shouldRecord(true, candidate.copy(text = "Autre"), last))
    }

    @Test
    fun likeEscapingAndCutoff() {
        assertEquals("50\\% \\_x\\\\", HistoryLogic.escapeLike(" 50% _x\\ "))
        assertEquals(1_000L, HistoryLogic.cutoff(1_000L + HistoryLogic.RETENTION_MS))
    }

    @Test
    fun groupsByLocalDay() {
        val day = 86_400_000L
        val entries = listOf(2 * day + 10, 2 * day + 5, day + 3).mapIndexed { i, t ->
            NotificationHistoryEntity(i.toLong(), "$i", "p", "A", "t", "", t, null)
        }
        val groups = HistoryLogic.groupByDay(entries, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(1970, 1, 3), LocalDate.of(1970, 1, 2)), groups.map { it.first })
        assertEquals(2, groups.first().second.size)
    }

    @Test
    fun triggerHeightSnapsToAuto() {
        assertEquals(0f, VoletSettingsBounds.triggerHeight(5f))
        assertEquals(12f, VoletSettingsBounds.triggerHeight(12.2f))
        assertEquals(64f, VoletSettingsBounds.triggerHeight(70f))
    }

    @Test
    fun voletPermissionsOnlyWhenEnabled() {
        val off = PermissionLogic.onboardingSteps(35, OemSettings.Family.HONOR)
        assertFalse(PermissionKey.DND_ACCESS in off)
        assertFalse(PermissionKey.WRITE_SETTINGS in off)
        val on = PermissionLogic.onboardingSteps(35, OemSettings.Family.HONOR, volet = true)
        assertEquals(off + listOf(PermissionKey.WRITE_SETTINGS, PermissionKey.DND_ACCESS), on)
        assertTrue(PermissionKey.DND_ACCESS in PermissionLogic.onDemand)
        assertTrue(PermissionLogic.voletRequired.containsAll(listOf(PermissionKey.ACCESSIBILITY, PermissionKey.NOTIFICATION_ACCESS)))
    }
}
