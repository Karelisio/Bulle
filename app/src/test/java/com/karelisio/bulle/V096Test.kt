package com.karelisio.bulle

import com.karelisio.bulle.core.backup.BackupStore
import com.karelisio.bulle.modules.battery.PowerSource
import com.karelisio.bulle.modules.standby.Posture
import com.karelisio.bulle.modules.standby.StandbyCharger
import com.karelisio.bulle.modules.standby.StandbyLogic
import com.karelisio.bulle.modules.volet.background.BackgroundApp
import com.karelisio.bulle.modules.volet.background.BackgroundApps
import com.karelisio.bulle.modules.volet.background.ServiceNotif
import com.karelisio.bulle.modules.volet.ui.timeScale
import com.karelisio.bulle.system.DiagnosticReport
import com.karelisio.bulle.ui.home.SelfTest
import com.karelisio.bulle.ui.home.SelfTestInputs
import com.karelisio.bulle.ui.home.SelfTestIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Améliorations « medium » : en-tête du Volet, applis en fond, auto-test, rapport, sauvegarde, Veille. */
class V096Test {

    @Test
    fun headerTimeShrinksWithLargeFonts() {
        assertEquals(1f, timeScale(1f), 0.001f)
        assertTrue(timeScale(1.3f) < 1f)
        assertEquals(0.7f, timeScale(3f), 0.001f)
    }

    @Test
    fun backgroundAppsHiddenProtectedAndSince() {
        val apps = BackgroundApps.group(
            listOf(
                ServiceNotif("com.music", "Lecture", null, 0, protected = true),
                ServiceNotif("com.sync", "Sync", null, 0),
                ServiceNotif("com.launcher", "Accueil", null, 0),
            ),
            own = "com.karelisio.bulle",
            hidden = setOf("com.launcher"),
        )
        assertEquals(listOf("com.music", "com.sync"), apps.map { it.packageName }.sorted())
        assertEquals(listOf("com.sync"), BackgroundApps.stoppable(apps).map { it.packageName })
        assertEquals(120L, BackgroundApps.sinceMinutes(0L + 1, 120 * 60_000L + 1))
        assertNull(BackgroundApps.sinceMinutes(Long.MAX_VALUE, 10))
        assertNull(BackgroundApps.sinceMinutes(500, 100))
        assertFalse(BackgroundApp("x", emptyList(), 0, null).protected)
    }

    @Test
    fun selfTestListsOnlyRealProblems() {
        val ok = SelfTestInputs(
            serviceRunning = true, haloEnabled = true, haloRunning = true, edgesEnabled = false, edgesRunning = false,
            standbyEnabled = true, standbyPermissions = true, standbyPhotosNeeded = false, photosGranted = false,
            voletEnabled = false, voletPermissions = false,
        )
        assertTrue(SelfTest.issues(ok).isEmpty())
        val bad = ok.copy(haloRunning = false, standbyPermissions = false, standbyPhotosNeeded = true, voletEnabled = true)
        assertEquals(
            listOf(SelfTestIssue.HALO_STOPPED, SelfTestIssue.STANDBY_PERMISSIONS, SelfTestIssue.STANDBY_PHOTOS, SelfTestIssue.VOLET_PERMISSIONS),
            SelfTest.issues(bad),
        )
        // Service de l'île arrêté : sa propre carte le dit, pas de doublon.
        assertTrue(SelfTest.issues(bad.copy(serviceRunning = false)).none { it == SelfTestIssue.HALO_STOPPED })
    }

    @Test
    fun reportHeaderHasNoPersonalData() {
        val h = DiagnosticReport.header("0.9.6", "HONOR PTP-N49", 35, "HONOR", listOf("Halo" to "oui"))
        assertTrue(h.startsWith("Rapport Bulle 0.9.6"))
        assertTrue("Halo : oui" in h)
    }

    @Test
    fun folderBackupNameIsDaily() {
        assertTrue(BackupStore.folderFileName(0L).matches(Regex("bulle-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }

    @Test
    fun standbyScheduleSkipsTheOrientation() {
        assertFalse(StandbyLogic.conditions(true, PowerSource.AC, StandbyCharger.ANY, Posture.FLAT, idle = true))
        assertTrue(StandbyLogic.conditions(true, PowerSource.AC, StandbyCharger.ANY, Posture.FLAT, idle = true, scheduled = true))
        assertFalse(StandbyLogic.conditions(true, PowerSource.NONE, StandbyCharger.ANY, Posture.FLAT, idle = true, scheduled = true))
    }

    @Test
    fun standbyStaysUntilUnpluggedOrUpright() {
        assertTrue(StandbyLogic.keep(PowerSource.AC, StandbyCharger.ANY, Posture.LANDSCAPE))
        // Posé à plat ou posture inconnue (vibration d'une notification) : on reste.
        assertTrue(StandbyLogic.keep(PowerSource.AC, StandbyCharger.ANY, Posture.FLAT))
        assertTrue(StandbyLogic.keep(PowerSource.AC, StandbyCharger.ANY, Posture.UNKNOWN))
        assertFalse(StandbyLogic.keep(PowerSource.AC, StandbyCharger.ANY, Posture.PORTRAIT))
        assertFalse(StandbyLogic.keep(PowerSource.NONE, StandbyCharger.ANY, Posture.LANDSCAPE))
    }

    @Test
    fun musicCutByStandbyIsResumedOnce() {
        assertTrue(StandbyLogic.shouldResume(wasPlaying = true, playingNow = false, startedAt = 1_000, now = 3_000, lastTouch = 0))
        // Pause voulue (commandes de Veille touchées), trop tard, ou musique qui ne jouait pas : rien.
        assertFalse(StandbyLogic.shouldResume(true, false, 1_000, 3_000, lastTouch = 2_000))
        assertFalse(StandbyLogic.shouldResume(true, false, 1_000, 20_000, 0))
        assertFalse(StandbyLogic.shouldResume(false, false, 1_000, 3_000, 0))
    }
}
