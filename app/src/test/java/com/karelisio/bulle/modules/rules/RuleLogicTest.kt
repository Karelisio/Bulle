package com.karelisio.bulle.modules.rules

import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLogicTest {
    @Test
    fun timeRangeAcrossMidnight() {
        assertTrue(RuleLogic.inRange(23 * 60, 22 * 60, 7 * 60))
        assertTrue(RuleLogic.inRange(60, 22 * 60, 7 * 60))
        assertFalse(RuleLogic.inRange(12 * 60, 22 * 60, 7 * 60))
        assertTrue(RuleLogic.inRange(9 * 60, 8 * 60, 18 * 60))
        assertFalse(RuleLogic.inRange(18 * 60, 8 * 60, 18 * 60))
    }

    @Test
    fun effectsLastActiveWins() {
        val rules = listOf(
            Rule(trigger = RuleTrigger.Charging, action = RuleAction.HideIsland),
            Rule(trigger = RuleTrigger.AppOpened("com.game", "Jeu"), action = RuleAction.ShowIsland),
            Rule(trigger = RuleTrigger.HeadsetConnected, action = RuleAction.SetMotionStyle(MotionStyle.SNAPPY)),
        )
        assertNull(RuleLogic.effects(rules, RuleWorld()).visibility)
        assertEquals(false, RuleLogic.effects(rules, RuleWorld(charging = true)).visibility)
        assertEquals(true, RuleLogic.effects(rules, RuleWorld(charging = true, foregroundPackage = "com.game")).visibility)
        assertEquals(MotionStyle.SNAPPY, RuleLogic.effects(rules, RuleWorld(headset = true)).motionStyle)
    }

    @Test
    fun wifiSsid() {
        val any = RuleTrigger.WifiConnected(null)
        val home = RuleTrigger.WifiConnected("Maison")
        assertTrue(RuleLogic.isActive(any, RuleWorld(wifiConnected = true)))
        assertFalse(RuleLogic.isActive(home, RuleWorld(wifiConnected = true, wifiSsid = "Bureau")))
        assertTrue(RuleLogic.isActive(home, RuleWorld(wifiConnected = true, wifiSsid = "Maison")))
    }

    @Test
    fun rulesSurviveJson() {
        val s = BulleSettings(rules = RulesSettings(listOf(Rule(trigger = RuleTrigger.TimeRange(1, 2), action = RuleAction.HideIsland))))
        val json = BulleJson.encodeToString(BulleSettings.serializer(), s)
        assertEquals(s, BulleJson.decodeFromString(BulleSettings.serializer(), json))
    }
}
