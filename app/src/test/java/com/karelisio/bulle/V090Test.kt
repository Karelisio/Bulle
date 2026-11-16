package com.karelisio.bulle

import com.karelisio.bulle.core.actions.ActionExecutor
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.backup.sanitized
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.island.HiddenReason
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.ScreenEnv
import com.karelisio.bulle.island.VisibilityPolicy
import com.karelisio.bulle.modules.edges.EdgeSettings
import com.karelisio.bulle.modules.edges.EdgeSlider
import com.karelisio.bulle.modules.edges.EdgeZone
import com.karelisio.bulle.modules.halo.HaloSettings
import com.karelisio.bulle.modules.halo.HaloStyle
import com.karelisio.bulle.modules.standby.StandbyPage
import com.karelisio.bulle.modules.standby.StandbySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.9.0 : réglages Halo / Bords / Veille, journal, actions, masquage des overlays. */
class V090Test {

    @Test
    fun oldSettingsGetSafeDefaults() {
        val s = BulleJson.decodeFromString(BulleSettings.serializer(), """{"islandEnabled": true}""")
        assertFalse(s.halo.enabled)
        assertFalse(s.edges.enabled)
        assertFalse(s.standby.enabled)
        assertFalse("glissement vers l'intérieur coupé par défaut", s.edges.swipeInAllowed)
        assertFalse("halo écran éteint coupé par défaut", s.halo.screenOff)
    }

    @Test
    fun newSettingsRoundTrip() {
        val s = BulleSettings(
            halo = HaloSettings(enabled = true, style = HaloStyle.WAVE, appColors = mapOf("a" to 0xFF112233), screenOff = true),
            edges = EdgeSettings(enabled = true, right = EdgeZone(slider = EdgeSlider.VOLUME), disabledApps = setOf("com.game")),
            standby = StandbySettings(enabled = true, pages = listOf(StandbyPage.PHOTOS, StandbyPage.CLOCKS), albumId = "42"),
        )
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), BulleJson.encodeToString(BulleSettings.serializer(), s))
        assertEquals(s, back)
    }

    @Test
    fun unknownEnumFallsBackInsteadOfResettingEverything() {
        val s = BulleJson.decodeFromString(BulleSettings.serializer(), """{"halo": {"enabled": true, "style": "LASER"}}""")
        assertTrue(s.halo.enabled)
        assertEquals(HaloStyle.SIGNATURE, s.halo.style)
    }

    @Test
    fun backupImportIsSanitized() {
        val s = BulleSettings(
            halo = HaloSettings(thicknessDp = 500f),
            edges = EdgeSettings(left = EdgeZone(thicknessDp = 0f)),
            standby = StandbySettings(delaySeconds = -3),
        ).sanitized()
        assertEquals(12f, s.halo.thicknessDp)
        assertEquals(8f, s.edges.left.thicknessDp)
        assertEquals(0, s.standby.delaySeconds)
    }

    @Test
    fun journalFilters() {
        assertEquals(IslandLog.Category.HALO, IslandLog.category("Halo : com.chat"))
        assertEquals(IslandLog.Category.EDGES, IslandLog.category("Bords : zones actives (2)"))
        assertEquals(IslandLog.Category.STANDBY, IslandLog.category(IslandLog.PREVIOUS + "Veille : affichée"))
        assertEquals(IslandLog.Category.ISLAND, IslandLog.category("Île affichée"))
    }

    @Test
    fun newActions() {
        assertFalse(ActionExecutor.leavesIsland(ActionType.HALO_TEST))
        assertTrue(ActionExecutor.leavesIsland(ActionType.STANDBY))
        assertTrue(ActionExecutor.leavesIsland(ActionType.OPEN_VOLET))
    }

    @Test
    fun voletZonesHaveTheirOwnHeightInLandscape() {
        val strips = com.karelisio.bulle.modules.volet.VoletStrips
        assertNull("portrait : règle habituelle", strips.landscapeHeightPx(false, 10f, 3f))
        assertEquals(0, strips.landscapeHeightPx(true, 0f, 3f))
        assertEquals(30, strips.landscapeHeightPx(true, 10f, 3f))
        // Paysage : bande fine, plus basse que la barre d'état (les commandes de la vidéo restent libres).
        val zones = strips.zones(2400, statusBarHeight = 90, triggerHeightPx = 0, edgeInsetPx = 0, centerX = 1200, envelope = null, fixedHeightPx = 30)
        assertTrue(zones.isNotEmpty())
        assertTrue(zones.all { it.rect.y + it.rect.height <= 30 })
        val restored = BulleSettings(volet = BulleSettings().volet.copy(landscapeTriggerHeightDp = 500f)).sanitized()
        assertEquals(48f, restored.volet.landscapeTriggerHeightDp)
    }

    @Test
    fun settingsSearchFindsTheNewModules() {
        assertEquals("bulle_halo", com.karelisio.bulle.modules.search.SettingsShortcuts.filter("halo").first().id)
        assertEquals("bulle_edges", com.karelisio.bulle.modules.search.SettingsShortcuts.filter("bords").first().id)
        assertEquals("bulle_standby", com.karelisio.bulle.modules.search.SettingsShortcuts.filter("standby").first().id)
    }

    @Test
    fun overlaysFollowTheIslandRules() {
        val s = BulleSettings()
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.overlayHiddenReason(s, ScreenEnv(landscape = true, statusBarVisible = false), "v"))
        assertNull(VisibilityPolicy.overlayHiddenReason(s, ScreenEnv(landscape = true), "v"))
        val classic = s.copy(visibility = s.visibility.copy(landscapeNative = false, hideInLandscape = true))
        assertEquals(HiddenReason.LANDSCAPE, VisibilityPolicy.overlayHiddenReason(classic, ScreenEnv(landscape = true), "v"))
        assertNull(VisibilityPolicy.overlayHiddenReason(s.copy(islandEnabled = false), ScreenEnv(), "v"))
    }
}
