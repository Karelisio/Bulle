package com.karelisio.bulle.core.backup

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.PositionTweak
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.core.update.UpdateSettings
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertStyle
import com.karelisio.bulle.modules.deck.DeckSettings
import com.karelisio.bulle.modules.deck.DeckTab
import com.karelisio.bulle.modules.music.MusicSettings
import com.karelisio.bulle.modules.rules.Rule
import com.karelisio.bulle.modules.rules.RuleAction
import com.karelisio.bulle.modules.rules.RuleTrigger
import com.karelisio.bulle.modules.rules.RulesSettings
import com.karelisio.bulle.modules.volet.VoletSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSanitizerTest {

    @Test
    fun defaultsAreUnchanged() {
        assertEquals(BulleSettings(), BulleSettings().sanitized())
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val s = BulleSettings(
            position = PositionTweak(offsetXDp = 500f, offsetYDp = -500f, extraWidthDp = Float.POSITIVE_INFINITY, extraHeightDp = Float.NaN),
            motion = MotionSettings(speed = 40f),
            style = StyleSettings(
                size = 12f, textScale = 0f, cornerSmoothing = -2f, outlineWidthDp = 99f,
                maxCompactWidth = 0f, maxExpandedWidth = 5f, customColor = -5L,
            ),
            volet = VoletSettings(
                triggerHeightDp = 500f, edgeInsetDp = -3f, blurRadiusDp = 1000f, scrimOpacity = 3f,
                cornerRadiusDp = 0f, collapsedTileCount = 42, tiles = listOf("wifi", "wifi", "bt"),
            ),
            alerts = AlertSettings(
                lowBatteryLevels = listOf(10, 200, -1, 20, 10),
                styles = mapOf("a" to AlertStyle(durationSec = 999, accent = 0x1_0000_0000_0L), "b" to AlertStyle(accent = -1L)),
            ),
            music = MusicSettings(pausedHideMinutes = -4),
        ).sanitized()
        assertEquals(60f, s.position.offsetXDp)
        assertEquals(-20f, s.position.offsetYDp)
        assertEquals(120f, s.position.extraWidthDp)
        assertEquals(0f, s.position.extraHeightDp)
        assertEquals(2f, s.motion.speed)
        assertEquals(1.6f, s.style.size)
        assertEquals(0.85f, s.style.textScale)
        assertEquals(0f, s.style.cornerSmoothing)
        assertEquals(3f, s.style.outlineWidthDp)
        assertEquals(0.3f, s.style.maxCompactWidth)
        assertEquals(1f, s.style.maxExpandedWidth)
        assertEquals(StyleSettings().customColor, s.style.customColor)
        assertEquals(64f, s.volet.triggerHeightDp)
        assertEquals(0f, s.volet.edgeInsetDp)
        assertEquals(80f, s.volet.blurRadiusDp)
        assertEquals(0.95f, s.volet.scrimOpacity)
        assertEquals(8f, s.volet.cornerRadiusDp)
        assertEquals(6, s.volet.collapsedTileCount)
        assertEquals(listOf("wifi", "bt"), s.volet.tiles)
        assertEquals(listOf(20, 10), s.alerts.lowBatteryLevels)
        assertEquals(AlertStyle(durationSec = AlertStyle.MAX_SECONDS), s.alerts.styles["a"])
        // Style devenu vide : retiré.
        assertTrue("b" !in s.alerts.styles)
        assertEquals(1, s.music.pausedHideMinutes)
    }

    @Test
    fun lowValuesClampedUp() {
        val s = BulleSettings(
            motion = MotionSettings(speed = 0f),
            style = StyleSettings(size = 0f),
            volet = VoletSettings(triggerHeightDp = 5f, collapsedTileCount = 0, scrimOpacity = 0f),
        ).sanitized()
        assertEquals(0.5f, s.motion.speed)
        assertEquals(0.5f, s.style.size)
        // Sous le minimum : retour à « auto ».
        assertEquals(0f, s.volet.triggerHeightDp)
        assertEquals(4, s.volet.collapsedTileCount)
        assertEquals(0.2f, s.volet.scrimOpacity)
    }

    @Test
    fun rulesMinutesAndDeckStructure() {
        val s = BulleSettings(
            rules = RulesSettings(listOf(Rule(id = "r", trigger = RuleTrigger.TimeRange(-10, 5000), action = RuleAction.HideIsland))),
            deck = DeckSettings(
                tabs = listOf(
                    DeckTab(id = "main", favorites = listOf("a/.A", "a/.A"), widgets = listOf(3, 3, -1)),
                    DeckTab(id = "main", widgets = listOf(3, 4)),
                ),
            ),
        ).sanitized()
        assertEquals(RuleTrigger.TimeRange(0, 1439), s.rules.rules.single().trigger)
        val tabs = s.deck.tabs
        assertEquals("main", tabs[0].id)
        assertTrue(tabs[1].id != "main")
        assertEquals(listOf("a/.A"), tabs[0].favorites)
        assertEquals(listOf(3), tabs[0].widgets)
        assertEquals(listOf(4), tabs[1].widgets)
        assertEquals(DeckSettings().tabs, BulleSettings(deck = DeckSettings(tabs = emptyList())).sanitized().deck.tabs)
    }

    @Test
    fun forDeviceDropsMissingAppsAndUnboundWidgets() {
        val s = BulleSettings(
            deck = DeckSettings(tabs = listOf(DeckTab(id = "main", favorites = listOf("ok/.A", "gone/.B"), widgets = listOf(1, 2, 3)))),
        ).forDevice(activityExists = { it.startsWith("ok") }, widgetBound = { it == 2 })
        assertEquals(listOf("ok/.A"), s.deck.tabs.single().favorites)
        assertEquals(listOf(2), s.deck.tabs.single().widgets)
    }

    @Test
    fun widgetPlanReleasesReplacedAndCountsMissing() {
        // Appareil : widgets 5 et 6. Sauvegarde : 6 (lié ici), 7 et 8 (autre téléphone).
        val plan = WidgetImportPlan.of(current = setOf(5, 6), imported = setOf(6, 7, 8), kept = setOf(6))
        assertEquals(setOf(5), plan.release)
        assertEquals(2, plan.missing)
        // Restauration sur le même appareil : rien à libérer, rien à rajouter.
        assertEquals(WidgetImportPlan(emptySet(), 0), WidgetImportPlan.of(setOf(1, 2), setOf(1, 2), setOf(1, 2)))
    }

    @Test
    fun mergeKeepsOnboardingAndClamps() {
        val current = BulleSettings(onboardingDone = true, update = UpdateSettings(lastCheck = 9))
        val merged = BackupCodec.merge(current, BulleSettings(onboardingDone = false, style = StyleSettings(size = 9f)))
        assertTrue(merged.onboardingDone)
        assertEquals(9L, merged.update.lastCheck)
        assertEquals(1.6f, merged.style.size)
        // Appareil neuf : la prise en main reste à faire, même si la sauvegarde dit le contraire.
        assertEquals(false, BackupCodec.merge(BulleSettings(), BulleSettings(onboardingDone = true)).onboardingDone)
    }
}
