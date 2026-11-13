package com.karelisio.bulle.core.feedback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Synthèse des sons courts originaux : sinusoïdes et harmoniques, enveloppes exponentielles,
 * glissandos. Pur Kotlin (aucune API Android), mono PCM 16 bits.
 */
object SoundSynth {
    const val SAMPLE_RATE = 44_100

    /** Crête visée après normalisation (marge contre la saturation). */
    const val PEAK = 0.7f

    /** Durée totale de chaque son (ms). */
    fun durationMs(kind: SoundKind): Int = when (kind) {
        SoundKind.CHIME -> 700
        SoundKind.BUBBLE -> 220
        SoundKind.POP -> 110
        SoundKind.SOFT_DING -> 800
        SoundKind.TWINKLE -> 520
        SoundKind.NONE, SoundKind.SYSTEM -> 0
    }

    /** Échantillons du son ; tableau vide pour les sons non synthétisés. */
    fun render(kind: SoundKind, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val n = durationMs(kind) * sampleRate / 1000
        if (n == 0) return ShortArray(0)
        val buf = FloatArray(n)
        when (kind) {
            SoundKind.CHIME -> {
                tone(buf, sampleRate, 0.000, 1318.5, 0.45, decay = 7.0, harmonics = doubleArrayOf(1.0, 0.25, 0.08))
                tone(buf, sampleRate, 0.110, 1975.5, 0.55, decay = 6.0, harmonics = doubleArrayOf(1.0, 0.2, 0.06))
            }
            SoundKind.BUBBLE -> tone(buf, sampleRate, 0.0, 320.0, 0.22, decay = 9.0, sweepTo = 980.0)
            SoundKind.POP -> tone(buf, sampleRate, 0.0, 900.0, 0.11, decay = 30.0, sweepTo = 180.0, harmonics = doubleArrayOf(1.0, 0.3))
            SoundKind.SOFT_DING -> tone(buf, sampleRate, 0.0, 880.0, 0.8, decay = 5.0, harmonics = doubleArrayOf(1.0, 0.12, 0.04))
            SoundKind.TWINKLE -> {
                tone(buf, sampleRate, 0.00, 2093.0, 0.30, decay = 14.0, harmonics = doubleArrayOf(1.0, 0.15))
                tone(buf, sampleRate, 0.08, 2637.0, 0.30, decay = 14.0, harmonics = doubleArrayOf(1.0, 0.15))
                tone(buf, sampleRate, 0.16, 3136.0, 0.36, decay = 10.0, harmonics = doubleArrayOf(1.0, 0.15))
            }
            SoundKind.NONE, SoundKind.SYSTEM -> Unit
        }
        fadeEdges(buf, sampleRate)
        return normalize(buf)
    }

    /**
     * Ajoute une note à [buf] : départ [startSec], fréquence [freq] (glissant vers [sweepTo]),
     * durée [durSec], attaque de 4 ms puis décroissance exponentielle de taux [decay] (1/s).
     */
    private fun tone(
        buf: FloatArray,
        rate: Int,
        startSec: Double,
        freq: Double,
        durSec: Double,
        decay: Double,
        sweepTo: Double? = null,
        harmonics: DoubleArray = doubleArrayOf(1.0),
    ) {
        val start = (startSec * rate).toInt()
        val len = min((durSec * rate).toInt(), buf.size - start)
        if (len <= 0) return
        val attack = 0.004 * rate
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / rate
            val f = if (sweepTo == null) freq else freq + (sweepTo - freq) * (i.toDouble() / len)
            phase += 2 * PI * f / rate
            val env = min(1.0, i / attack) * exp(-decay * t)
            var s = 0.0
            harmonics.forEachIndexed { h, a -> s += a * sin(phase * (h + 1)) }
            buf[start + i] += (s * env).toFloat()
        }
    }

    /** Fondu de 3 ms au début et à la fin : aucun clic à l'arrêt. */
    private fun fadeEdges(buf: FloatArray, rate: Int) {
        val f = min(buf.size / 2, (0.003 * rate).toInt())
        for (i in 0 until f) {
            val g = i.toFloat() / f
            buf[i] *= g
            buf[buf.size - 1 - i] *= g
        }
    }

    private fun normalize(buf: FloatArray): ShortArray {
        val max = buf.maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        val gain = PEAK / max
        return ShortArray(buf.size) { (buf[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
