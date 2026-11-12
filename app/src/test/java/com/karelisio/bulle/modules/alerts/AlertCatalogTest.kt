package com.karelisio.bulle.modules.alerts

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCatalogTest {

    @Test
    fun everyAlertIsInExactlyOneCategory() {
        val inCategories = AlertCategory.entries.flatMap { it.alertIds }
        assertEquals(inCategories.size, inCategories.toSet().size)
        assertEquals(AlertCatalog.defs.map { it.id }.toSet(), inCategories.toSet())
    }

    @Test
    fun everySourceHasACatalogEntry() {
        val ids = AlertCatalog.defs.map { it.id }.toSet()
        // Batterie : une source, deux réglages (lowBattery, charging).
        AlertsModule.sources.map { it.id }.filter { it != "battery" }.forEach {
            assertTrue("$it absent du catalogue", it in ids)
        }
    }

    @Test
    fun newAlertsAreOffByDefault() {
        val s = AlertSettings()
        listOf("cellular", "fiveG", "vpn", "hotspot", "ringer", "mediaVolume", "usb", "cameraInUse", "micInUse").forEach {
            assertFalse("$it activée par défaut", AlertCatalog.def(it).get(s))
        }
    }

    @Test
    fun setterAndGetterMatch() {
        AlertCatalog.defs.forEach { def ->
            assertTrue(def.id, def.get(def.set(AlertSettings(), true)))
            assertFalse(def.id, def.get(def.set(AlertSettings(), false)))
        }
    }

    @Test
    fun countsPerCategory() {
        val s = AlertSettings(cellular = true, fiveG = true, wifi = true, airplane = false)
        val noPerm: (String) -> Boolean = { false }
        val network = AlertCatalog.count(AlertCategory.NETWORK, s, noPerm, notificationAccess = true)
        // wifi + cellular + 5G cochées sur 6 ; seule la 5G manque de sa permission
        // (le Wi-Fi marche aussi sans la localisation : il n'est pas compté).
        assertEquals(CategoryCount(enabled = 3, total = 6, missing = 1), network)

        val withPerm = AlertCatalog.count(AlertCategory.NETWORK, s, { it == Manifest.permission.READ_PHONE_STATE }, true)
        assertEquals(0, withPerm.missing)
    }

    @Test
    fun notificationAccessCountsAsMissing() {
        val s = AlertSettings() // alarme et Écho cochées par défaut
        assertEquals(1, AlertCatalog.count(AlertCategory.SOUND, s, { true }, notificationAccess = false).missing)
        assertEquals(0, AlertCatalog.count(AlertCategory.SOUND, s, { true }, notificationAccess = true).missing)
        assertEquals(CategoryCount(1, 1, 1), AlertCatalog.count(AlertCategory.ECHO, s, { true }, notificationAccess = false))
        // Décochée : jamais « sans autorisation ».
        assertEquals(0, AlertCatalog.count(AlertCategory.ECHO, s.copy(echoEnabled = false), { true }, false).missing)
    }
}
