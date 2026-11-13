package com.karelisio.bulle.core.feedback

import android.os.VibrationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FeedbackEngineTest {

    /** Faux vibreur : capacités réglables, enregistre ce qui est joué. */
    private class FakeVibrator(
        override val hasVibrator: Boolean = true,
        override val hasAmplitudeControl: Boolean = true,
        private val primitives: Boolean = true,
    ) : VibratorPort {
        val played = mutableListOf<HapticPlan>()
        override fun supportsAll(primitiveIds: IntArray) = primitives
        override fun play(plan: HapticPlan) {
            played += plan
        }
    }

    @Test
    fun everyPatternButNoneHasAConsistentDesign() {
        assertNull(HapticDesigns.of(HapticPattern.NONE))
        HapticPattern.entries.filter { it != HapticPattern.NONE }.forEach { p ->
            val d = HapticDesigns.of(p)!!
            assertTrue("$p sans primitive", d.primitives.isNotEmpty())
            assertEquals(d.timings.size, d.amplitudes.size)
            assertTrue("$p amplitudes hors bornes", d.amplitudes.all { it in 0..255 })
            assertTrue("$p : au moins un temps actif", d.amplitudes.any { it > 0 })
            assertTrue("$p trop long", d.waveformDurationMs in 1..1_000)
            assertTrue(d.primitives.all { it.scale in 0f..1f && it.delayMs >= 0 })
        }
        assertEquals(8, HapticPattern.entries.size)
    }

    @Test
    fun richPrimitivesAreUsedWhenSupported() {
        val v = FakeVibrator()
        HapticEngine.play(HapticPattern.HEARTBEAT, v)
        val plan = v.played.single() as HapticPlan.Composed
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, plan.primitives.first().id)
    }

    @Test
    fun fallsBackToWaveformWhenPrimitivesUnsupported() {
        val v = FakeVibrator(primitives = false)
        val plan = HapticEngine.plan(HapticPattern.DOUBLE_TAP, v) as HapticPlan.Waveform
        assertEquals(listOf(0L, 22L, 90L, 22L), plan.timings)
        assertEquals(listOf(0, 200, 0, 200), plan.amplitudes)
    }

    @Test
    fun waveformWithoutAmplitudeControlUsesDefaultAmplitude() {
        val v = FakeVibrator(primitives = false, hasAmplitudeControl = false)
        val plan = HapticEngine.plan(HapticPattern.RISE, v) as HapticPlan.Waveform
        assertEquals(0, plan.amplitudes.first())
        assertTrue(plan.amplitudes.drop(1).all { it == VibrationEffect.DEFAULT_AMPLITUDE })
    }

    @Test
    fun noneOrNoVibratorPlaysNothing() {
        val v = FakeVibrator(hasVibrator = false)
        HapticEngine.play(HapticPattern.INSISTENT, v)
        HapticEngine.play(HapticPattern.NONE, FakeVibrator())
        assertTrue(v.played.isEmpty())
        assertEquals(HapticPlan.Silent, HapticEngine.plan(HapticPattern.NONE, FakeVibrator()))
    }

    private val chime = FeedbackSound(SoundKind.CHIME)

    private fun decide(
        ringer: RingerState = RingerState.NORMAL,
        dnd: DndState = DndState.OFF,
        haptics: Boolean = true,
        haptic: HapticPattern = HapticPattern.LIGHT,
        sound: FeedbackSound = chime,
        volume: Int = 5,
    ) = FeedbackPolicy.decide(haptic, sound, ringer, dnd, haptics, volume)

    @Test
    fun policyPlaysBothInNormalMode() {
        assertEquals(FeedbackDecision(vibrate = true, sound = true), decide())
    }

    @Test
    fun vibrateModeMutesSoundButKeepsVibration() {
        assertEquals(FeedbackDecision(vibrate = true, sound = false), decide(ringer = RingerState.VIBRATE))
    }

    @Test
    fun totalSilenceBlocksEverything() {
        assertFalse(decide(ringer = RingerState.SILENT).any)
        assertFalse(decide(dnd = DndState.TOTAL).any)
        assertFalse(decide(dnd = DndState.ALARMS).any)
    }

    @Test
    fun dndPriorityMutesSoundOnly() {
        assertEquals(FeedbackDecision(vibrate = true, sound = false), decide(dnd = DndState.PRIORITY))
    }

    @Test
    fun globalHapticsSwitchAndChoicesAreRespected() {
        assertEquals(FeedbackDecision(vibrate = false, sound = true), decide(haptics = false))
        assertFalse(decide(haptic = HapticPattern.NONE).vibrate)
        assertFalse(decide(sound = FeedbackSound.None).sound)
        assertFalse(decide(volume = 0).sound)
    }

    @Test
    fun androidStatesAreMapped() {
        assertEquals(RingerState.SILENT, RingerState.fromAudioManager(0))
        assertEquals(RingerState.VIBRATE, RingerState.fromAudioManager(1))
        assertEquals(RingerState.NORMAL, RingerState.fromAudioManager(2))
        assertEquals(DndState.OFF, DndState.fromInterruptionFilter(1))
        assertEquals(DndState.OFF, DndState.fromInterruptionFilter(0))
        assertEquals(DndState.PRIORITY, DndState.fromInterruptionFilter(2))
        assertEquals(DndState.TOTAL, DndState.fromInterruptionFilter(3))
        assertEquals(DndState.ALARMS, DndState.fromInterruptionFilter(4))
    }

    @Test
    fun synthesizedSoundsHaveExpectedLengthAndNoClipping() {
        FeedbackSound.synthesized.forEach { k ->
            val pcm = SoundSynth.render(k)
            val expected = SoundSynth.durationMs(k) * SoundSynth.SAMPLE_RATE / 1000
            assertEquals("$k durée", expected, pcm.size)
            assertTrue("$k trop long", SoundSynth.durationMs(k) in 50..1_000)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$k sature ($peak)", peak < 32_000)
            assertTrue("$k muet ($peak)", peak > 15_000)
            // Fondus : ni clic au début ni à la fin.
            assertTrue("$k clic initial", abs(pcm.first().toInt()) < 200)
            assertTrue("$k clic final", abs(pcm.last().toInt()) < 200)
        }
    }

    @Test
    fun nonSynthesizedKindsRenderNothing() {
        assertEquals(0, SoundSynth.render(SoundKind.NONE).size)
        assertEquals(0, SoundSynth.render(SoundKind.SYSTEM).size)
    }

    @Test
    fun renderingIsDeterministic() {
        assertTrue(SoundSynth.render(SoundKind.POP).contentEquals(SoundSynth.render(SoundKind.POP)))
    }
}
