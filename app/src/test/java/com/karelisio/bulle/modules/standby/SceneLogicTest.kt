package com.karelisio.bulle.modules.standby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLogicTest {

    @Test
    fun sceneRotation() {
        val s = SceneSettings(scene = StandbyScene.AURORA)
        assertEquals(StandbyScene.AURORA, SceneLogic.sceneAt(s, 123_456_789L))
        val cycle = s.copy(cycleMinutes = 10)
        val all = StandbyScene.entries
        val base = 10 * 60_000L * 600 // multiple de la période et du nombre de scènes
        assertEquals(StandbyScene.AURORA, SceneLogic.sceneAt(cycle, base))
        assertEquals(all[(all.indexOf(StandbyScene.AURORA) + 1) % all.size], SceneLogic.sceneAt(cycle, base + 10 * 60_000L))
        // Toutes les scènes passent sur un tour complet.
        val seen = (0 until all.size).map { SceneLogic.sceneAt(cycle, base + it * 10 * 60_000L) }.toSet()
        assertEquals(all.toSet(), seen)
    }

    @Test
    fun frameRateAndDensity() {
        assertEquals(0L, SceneLogic.frameIntervalMs(powerSave = false, eco = true, reduceMotion = false))
        assertEquals(33L, SceneLogic.frameIntervalMs(powerSave = true, eco = true, reduceMotion = false))
        assertEquals(0L, SceneLogic.frameIntervalMs(powerSave = true, eco = false, reduceMotion = false))
        assertEquals(1000L, SceneLogic.frameIntervalMs(powerSave = false, eco = true, reduceMotion = true))
        val s = SceneSettings(density = 2f)
        assertEquals(2f, SceneLogic.effectiveDensity(s, powerSave = false), 1e-4f)
        assertEquals(1.2f, SceneLogic.effectiveDensity(s, powerSave = true), 1e-4f)
        assertEquals(2f, SceneLogic.effectiveDensity(s.copy(eco = false), powerSave = true), 1e-4f)
        assertEquals(1, SceneLogic.count(3, 0.1f))
        assertEquals(14, SceneLogic.count(7, 2f))
    }

    @Test
    fun pulseEnvelope() {
        assertEquals(0f, SceneLogic.pulse(-5), 0f)
        assertEquals(0.5f, SceneLogic.pulse(90), 1e-3f)
        assertEquals(1f, SceneLogic.pulse(180), 1e-3f)
        assertTrue(SceneLogic.pulse(1_000) in 0.2f..0.4f)
        assertEquals(0f, SceneLogic.pulse(SceneLogic.PULSE_MS), 0f)
    }

    @Test
    fun clockMovesEveryFiveMinutesAndStaysOnScreen() {
        val a = SceneLogic.clockAnchor(0)
        assertEquals(a, SceneLogic.clockAnchor(4 * 60_000L))
        assertTrue(a != SceneLogic.clockAnchor(5 * 60_000L))
        repeat(12) { k ->
            val (x, y) = SceneLogic.clockAnchor(k * 5 * 60_000L)
            assertTrue(x in 0.1f..0.9f && y in 0.1f..0.9f)
        }
    }

    @Test
    fun randomIsStableAndInRange() {
        repeat(500) { i ->
            val r = SceneLogic.rand(i, 7)
            assertTrue(r >= 0f && r < 1f)
            assertEquals(r, SceneLogic.rand(i, 7), 0f)
        }
        assertTrue(SceneLogic.rand(1, 1) != SceneLogic.rand(1, 2))
    }

    @Test
    fun bubbleRisesThenPops() {
        var popped = false
        var rising = false
        var t = 0f
        while (t < 40f) {
            val (prog, pop) = SceneLogic.bubbleLife(3, t)
            assertTrue(prog in 0f..1f)
            if (pop == null) rising = true else {
                popped = true
                assertEquals(1f, prog, 0f)
                assertTrue(pop in 0f..1f)
            }
            t += 0.1f
        }
        assertTrue(rising && popped)
        assertNull(SceneLogic.fixedPalette(ScenePalette.DYNAMIC))
        assertEquals(4, SceneLogic.fixedPalette(ScenePalette.OCEAN)?.size)
    }

    @Test
    fun sanitizeAndDefaults() {
        val bad = SceneSettings(speed = Float.NaN, density = 99f, cycleMinutes = -3).sanitized()
        assertEquals(1f, bad.speed, 0f)
        assertEquals(SceneSettings.DENSITY.endInclusive, bad.density, 0f)
        assertEquals(0, bad.cycleMinutes)
        val st = StandbySettings(scene = SceneSettings(speed = 9f)).sanitized()
        assertEquals(SceneSettings.SPEED.endInclusive, st.scene.speed, 0f)
        // Ancien réglage sans scène : valeurs par défaut.
        assertEquals(SceneSettings(), StandbySettings().scene)
        assertEquals(StandbyWidget.SCENE, StandbyLogic.preset(StandbyPage.SCENE).left.single())
    }
}
