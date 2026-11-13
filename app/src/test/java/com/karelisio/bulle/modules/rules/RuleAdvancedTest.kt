package com.karelisio.bulle.modules.rules

import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RuleAdvancedTest {

    // --- Migration JSON ---

    private val legacyJson = """
        {"rules":{"rules":[
          {"id":"a","enabled":true,"trigger":{"type":"charging"},"action":{"type":"hide"}},
          {"id":"b","enabled":false,"trigger":{"type":"time","startMinute":1320,"endMinute":420},"action":{"type":"style","style":"SNAPPY"}},
          {"id":"c","trigger":{"type":"app","packageName":"com.game","label":"Jeu"},"action":{"type":"run","action":{"type":"TOGGLE_EXPAND"}}}
        ]}}
    """.trimIndent()

    @Test
    fun legacyRulesAreReadAsIs() {
        val s = BulleJson.decodeFromString(BulleSettings.serializer(), legacyJson)
        val rules = s.rules.rules
        assertEquals(3, rules.size)
        assertEquals(Rule(RuleTrigger.Charging, RuleAction.HideIsland, id = "a"), rules[0])
        assertEquals(
            Rule(RuleTrigger.TimeRange(1320, 420), RuleAction.SetMotionStyle(MotionStyle.SNAPPY), id = "b", enabled = false),
            rules[1],
        )
        assertEquals(RuleTrigger.AppOpened("com.game", "Jeu"), rules[2].trigger)
        assertEquals(RuleMatch.ALL, rules[2].match)
        assertTrue(rules[2].actions.single() is RuleAction.Run)
    }

    @Test
    fun newFormatIsWrittenAndReread() {
        val s = BulleJson.decodeFromString(BulleSettings.serializer(), legacyJson)
        val json = BulleJson.encodeToString(BulleSettings.serializer(), s)
        assertTrue(json.contains("\"conditions\""))
        assertFalse(json.contains("\"trigger\":{\"type\":\"charging\"},\"action\""))
        assertEquals(s, BulleJson.decodeFromString(BulleSettings.serializer(), json))
    }

    @Test
    fun richRuleSurvivesJson() {
        val rule = Rule(
            conditions = listOf(
                RuleCondition(RuleTrigger.BatteryLevel(15, below = true)),
                RuleCondition(RuleTrigger.Place(48.85, 2.35, 300, "Maison", id = "p"), negated = true),
                RuleCondition(RuleTrigger.DaysOfWeek(setOf(6, 7))),
                RuleCondition(RuleTrigger.BluetoothDevice("AA:BB", "Voiture")),
                RuleCondition(RuleTrigger.DarkMode),
                RuleCondition(RuleTrigger.ScreenLocked),
                RuleCondition(RuleTrigger.InCall),
            ),
            match = RuleMatch.ANY,
            actions = listOf(RuleAction.FocusMode, RuleAction.SetModule("music", false), RuleAction.HideIsland),
        )
        val s = BulleSettings(rules = RulesSettings(listOf(rule)))
        val json = BulleJson.encodeToString(BulleSettings.serializer(), s)
        assertEquals(s, BulleJson.decodeFromString(BulleSettings.serializer(), json))
    }

    // --- ET / OU / NON ---

    private val charging = RuleCondition(RuleTrigger.Charging)
    private val dark = RuleCondition(RuleTrigger.DarkMode)

    @Test
    fun andOrNot() {
        val and = Rule(conditions = listOf(charging, dark), match = RuleMatch.ALL, actions = listOf(RuleAction.HideIsland))
        val or = and.copy(match = RuleMatch.ANY)
        val notDark = and.copy(conditions = listOf(charging, dark.copy(negated = true)))
        val w = RuleWorld(charging = true, darkMode = false)
        assertFalse(RuleLogic.isActive(and, w))
        assertTrue(RuleLogic.isActive(or, w))
        assertTrue(RuleLogic.isActive(notDark, w))
        assertTrue(RuleLogic.isActive(and, w.copy(darkMode = true)))
        assertFalse(RuleLogic.isActive(or, RuleWorld()))
        // Sans condition : jamais active.
        assertFalse(RuleLogic.isActive(Rule(actions = listOf(RuleAction.HideIsland)), w))
    }

    // --- Nouveaux déclencheurs ---

    @Test
    fun battery() {
        val low = RuleTrigger.BatteryLevel(20, below = true)
        val high = RuleTrigger.BatteryLevel(80, below = false)
        assertTrue(RuleLogic.isActive(low, RuleWorld(batteryLevel = 20)))
        assertFalse(RuleLogic.isActive(low, RuleWorld(batteryLevel = 21)))
        assertTrue(RuleLogic.isActive(high, RuleWorld(batteryLevel = 80)))
        assertFalse(RuleLogic.isActive(high, RuleWorld(batteryLevel = 79)))
        // Niveau inconnu : jamais vrai.
        assertFalse(RuleLogic.isActive(low, RuleWorld(batteryLevel = -1)))
    }

    @Test
    fun bluetoothDevice() {
        val t = RuleTrigger.BluetoothDevice("aa:bb:cc:dd:ee:ff", "Voiture")
        assertTrue(RuleLogic.isActive(t, RuleWorld(bluetoothDevices = setOf("AA:BB:CC:DD:EE:FF"))))
        assertFalse(RuleLogic.isActive(t, RuleWorld(bluetoothDevices = setOf("11:22:33:44:55:66"))))
    }

    @Test
    fun darkLockedCall() {
        assertTrue(RuleLogic.isActive(RuleTrigger.DarkMode, RuleWorld(darkMode = true)))
        assertFalse(RuleLogic.isActive(RuleTrigger.DarkMode, RuleWorld()))
        assertTrue(RuleLogic.isActive(RuleTrigger.ScreenLocked, RuleWorld(screenLocked = true)))
        // Déverrouillé = verrouillé + négation.
        assertTrue(RuleLogic.isActive(RuleCondition(RuleTrigger.ScreenLocked, negated = true), RuleWorld(screenLocked = false)))
        assertTrue(RuleLogic.isActive(RuleTrigger.InCall, RuleWorld(inCall = true)))
        assertTrue(RuleWorldSources.isCallMode(android.media.AudioManager.MODE_IN_CALL))
        assertTrue(RuleWorldSources.isCallMode(android.media.AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(RuleWorldSources.isCallMode(android.media.AudioManager.MODE_NORMAL))
        assertFalse(RuleWorldSources.isCallMode(android.media.AudioManager.MODE_RINGTONE))
    }

    @Test
    fun place() {
        val p = RuleTrigger.Place(48.0, 2.0, id = "home")
        assertTrue(RuleLogic.isActive(p, RuleWorld(insidePlaces = setOf("home"))))
        assertFalse(RuleLogic.isActive(p, RuleWorld()))
    }

    @Test
    fun daysOfWeek() {
        val weekend = RuleTrigger.DaysOfWeek(setOf(6, 7))
        assertTrue(RuleLogic.isActive(weekend, RuleWorld(dayOfWeek = 7)))
        assertFalse(RuleLogic.isActive(weekend, RuleWorld(dayOfWeek = 1)))
        assertEquals(1, RuleLogic.isoDay(Calendar.MONDAY))
        assertEquals(7, RuleLogic.isoDay(Calendar.SUNDAY))
        assertEquals(6, RuleLogic.isoDay(Calendar.SATURDAY))
    }

    // --- Actions ---

    @Test
    fun moduleAndFocusEffects() {
        val rules = listOf(
            Rule(conditions = listOf(charging), actions = listOf(RuleAction.SetModule("music", false), RuleAction.FocusMode)),
            Rule(conditions = listOf(dark), actions = listOf(RuleAction.SetModule("music", true))),
        )
        val e1 = RuleLogic.effects(rules, RuleWorld(charging = true))
        assertEquals(mapOf("music" to false), e1.modules)
        assertTrue(e1.focus)
        val e2 = RuleLogic.effects(rules, RuleWorld(charging = true, darkMode = true))
        assertEquals(mapOf("music" to true), e2.modules)
        val applied = e1.copy(motionStyle = MotionStyle.SOBER).applyTo(BulleSettings(modules = mapOf("weather" to true)))
        assertEquals(mapOf("weather" to true, "music" to false), applied.modules)
        assertEquals(MotionStyle.SOBER, applied.motion.style)
        assertEquals(BulleSettings(), RuleEffects().applyTo(BulleSettings()))
    }

    @Test
    fun runsOnRisingEdgeOnly() {
        val run = GestureAction(ActionType.TOGGLE_EXPAND)
        val rules = listOf(Rule(id = "r", conditions = listOf(charging), actions = listOf(RuleAction.Run(run), RuleAction.HideIsland)))
        val (active1, runs1) = RuleLogic.risingRuns(rules, emptySet(), RuleWorld(charging = true))
        assertEquals(listOf(run), runs1)
        val (_, runs2) = RuleLogic.risingRuns(rules, active1, RuleWorld(charging = true))
        assertTrue(runs2.isEmpty())
        val (active3, _) = RuleLogic.risingRuns(rules, active1, RuleWorld())
        assertTrue(active3.isEmpty())
    }

    @Test
    fun sanitizeBounds() {
        val r = Rule(
            conditions = listOf(
                RuleCondition(RuleTrigger.TimeRange(-5, 9999)),
                RuleCondition(RuleTrigger.BatteryLevel(500)),
                RuleCondition(RuleTrigger.Place(200.0, -500.0, 1, id = "x")),
                RuleCondition(RuleTrigger.DaysOfWeek(setOf(0, 3, 9))),
            ),
        ).sanitized()
        assertEquals(RuleTrigger.TimeRange(0, 1439), r.conditions[0].trigger)
        assertEquals(RuleTrigger.BatteryLevel(100), r.conditions[1].trigger)
        assertEquals(RuleTrigger.Place(90.0, -180.0, RuleTrigger.Place.MIN_RADIUS_M, id = "x"), r.conditions[2].trigger)
        assertEquals(RuleTrigger.DaysOfWeek(setOf(3)), r.conditions[3].trigger)
    }
}
