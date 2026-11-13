package com.karelisio.bulle.modules.music

import com.karelisio.bulle.modules.music.visualizer.SpectrumMath
import com.karelisio.bulle.modules.music.visualizer.VisualizerPolicy
import com.karelisio.bulle.modules.music.visualizer.VisualizerPolicy.Action
import com.karelisio.bulle.modules.music.visualizer.approach
import com.karelisio.bulle.modules.music.ui.realBarHeight
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversion FFT → barres, lissage et décision d'ouvrir/libérer le Visualizer (fonctions pures). */
class VisualizerLogicTest {

    /** Capture FFT de [size] octets avec une raie d'amplitude [amp] (partie réelle) à l'indice [bin]. */
    private fun fftWithPeak(size: Int, bin: Int, amp: Int): ByteArray =
        ByteArray(size).also { it[2 * bin] = amp.toByte() }

    @Test
    fun lowFrequencyPeakLightsTheFirstBands() {
        val bands = SpectrumMath.toBands(fftWithPeak(512, 1, 100))
        assertEquals(SpectrumMath.BANDS, bands.size)
        assertTrue("grave allumé : ${bands.toList()}", bands[0] > 0.5f)
        assertEquals(0f, bands.last(), 0f)
        assertTrue(bands.all { it in 0f..1f })
    }

    @Test
    fun highFrequencyPeakLightsTheLastBands() {
        // 70 % de Nyquist (raie 179 sur 256) : dernière bande.
        val bands = SpectrumMath.toBands(fftWithPeak(512, 179, 100))
        assertTrue(bands.last() > 0.5f)
        assertEquals(0f, bands[0], 0f)
    }

    @Test
    fun silenceAndTinyCapturesAreSafe() {
        val silent = ByteArray(512)
        assertTrue(SpectrumMath.isSilent(silent))
        assertTrue(SpectrumMath.toBands(silent).all { it == 0f })
        assertFalse(SpectrumMath.isSilent(fftWithPeak(512, 3, 1)))
        assertEquals(4, SpectrumMath.toBands(ByteArray(2), 4).size)
        assertTrue(SpectrumMath.toBands(ByteArray(2), 4).all { it == 0f })
    }

    @Test
    fun levelIsLogarithmicAndBounded() {
        assertEquals(0f, SpectrumMath.magnitudeToLevel(0f), 0f)
        val a = SpectrumMath.magnitudeToLevel(10f)
        val b = SpectrumMath.magnitudeToLevel(40f)
        val c = SpectrumMath.magnitudeToLevel(181f)
        assertTrue(a < b && b <= c)
        assertTrue(c <= 1f)
    }

    @Test
    fun smoothingRisesFastAndFallsSlowly() {
        val first = SpectrumMath.smooth(null, floatArrayOf(0.5f))
        assertArrayEquals(floatArrayOf(0.5f), first, 0f)
        val up = SpectrumMath.smooth(floatArrayOf(0f), floatArrayOf(1f))
        val down = SpectrumMath.smooth(floatArrayOf(1f), floatArrayOf(0f))
        assertEquals(SpectrumMath.ATTACK, up[0], 1e-6f)
        assertEquals(1f - SpectrumMath.DECAY, down[0], 1e-6f)
        // Montée plus rapide que la descente.
        assertTrue(up[0] > 1f - down[0])
        // Taille différente : on repart de la nouvelle capture.
        assertArrayEquals(floatArrayOf(0.2f, 0.3f), SpectrumMath.smooth(floatArrayOf(1f), floatArrayOf(0.2f, 0.3f)), 0f)
    }

    @Test
    fun regroupAveragesBands() {
        val bands = FloatArray(24) { if (it < 6) 1f else 0f }
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f), SpectrumMath.regroup(bands, 4), 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f), SpectrumMath.regroup(floatArrayOf(0f, 1f), 1), 1e-6f)
        assertEquals(0, SpectrumMath.regroup(FloatArray(0), 0).size)
    }

    @Test
    fun displayedValuesApproachTarget() {
        var v = 0f
        repeat(60) { v = approach(v, 1f, 1f / 60f) }
        assertTrue(v > 0.99f)
        assertEquals(0.5f, approach(0.5f, 0.5f, 0.016f), 0f)
        // Pas de dépassement même avec une image très longue.
        assertTrue(approach(0f, 1f, 5f) <= 1f)
        assertEquals(0.18f, realBarHeight(0f, 1f), 1e-6f)
        assertEquals(1f, realBarHeight(1f, 1f), 1e-6f)
    }

    private val ok = VisualizerPolicy.Inputs(
        settingEnabled = true,
        permissionGranted = true,
        visibleConsumers = 1,
        playing = true,
        screenOn = true,
        unavailable = false,
    )

    @Test
    fun capturesOnlyWhenEverythingIsMet() {
        assertEquals(Action.CAPTURE, VisualizerPolicy.decide(ok))
        assertEquals(Action.CAPTURE, VisualizerPolicy.decide(ok.copy(visibleConsumers = 3)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(settingEnabled = false)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(permissionGranted = false)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(visibleConsumers = 0)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(playing = false)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(screenOn = false)))
        assertEquals(Action.RELEASE, VisualizerPolicy.decide(ok.copy(unavailable = true)))
    }

    @Test
    fun captureParametersStayInRange() {
        assertEquals(20_000, VisualizerPolicy.captureRate(20_000))
        assertEquals(25_000, VisualizerPolicy.captureRate(60_000))
        assertEquals(25_000, VisualizerPolicy.captureRate(0))
        assertEquals(512, VisualizerPolicy.captureSize(intArrayOf(128, 1024)))
        assertEquals(256, VisualizerPolicy.captureSize(intArrayOf(128, 256)))
        assertEquals(1024, VisualizerPolicy.captureSize(intArrayOf(1024, 2048)))
        assertEquals(512, VisualizerPolicy.captureSize(null))
    }
}
