package com.karelisio.bulle.modules.music.visualizer

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Conversion des captures FFT de [android.media.audiofx.Visualizer] en hauteurs de barres
 * (logique pure, testée).
 *
 * Format Android d'une capture FFT de n octets : `fft[0]` = partie réelle de la composante continue,
 * `fft[1]` = partie réelle de la fréquence de Nyquist, puis pour k de 1 à n/2 − 1 :
 * `fft[2k]` = réel, `fft[2k + 1]` = imaginaire de la raie k.
 */
object SpectrumMath {
    /** Nombre de bandes calculées (l'onde les utilise toutes, l'égaliseur compact les regroupe). */
    const val BANDS = 24

    /** Plage dynamique affichée, en dB au-dessus du plancher. */
    private const val FLOOR_DB = 4f
    private const val RANGE_DB = 38f

    /** Compensation des aigus (dB ajoutés à la dernière bande). */
    private const val TILT_DB = 8f

    /** Réactivité du lissage : montée rapide, descente plus douce (barres « vivantes » sans scintiller). */
    const val ATTACK = 0.65f
    const val DECAY = 0.22f

    /**
     * Hauteurs (0..1) de [count] bandes réparties logarithmiquement sur les raies de [fft]
     * (graves à gauche). Chaque bande prend le maximum de ses raies, converti en dB et normalisé.
     */
    fun toBands(fft: ByteArray, count: Int = BANDS): FloatArray {
        val out = FloatArray(count)
        val bins = fft.size / 2 // raies 1 .. bins-1 exploitables
        if (count <= 0 || bins < 2) return out
        // On ignore la toute fin du spectre (au-delà de ~70 % de Nyquist) : quasi vide en musique.
        val maxBin = (bins * 0.7f).toInt().coerceIn(2, bins - 1)
        val ratio = maxBin.toDouble()
        for (b in 0 until count) {
            val lo = ratio.pow(b.toDouble() / count).toInt().coerceIn(1, maxBin)
            val hi = ratio.pow((b + 1).toDouble() / count).toInt().coerceIn(lo, maxBin)
            var peak = 0f
            for (k in lo..hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                val m = hypot(re, im)
                if (m > peak) peak = m
            }
            // Les aigus sont naturellement plus faibles : légère compensation pour une onde équilibrée.
            out[b] = magnitudeToLevel(peak, boostDb = TILT_DB * b / count)
        }
        return out
    }

    /** Amplitude d'une raie (0 à ~181) → niveau 0..1 en échelle logarithmique. */
    fun magnitudeToLevel(magnitude: Float, boostDb: Float = 0f): Float {
        if (magnitude <= 0f) return 0f
        val db = 20f * log10(magnitude) + boostDb
        return ((db - FLOOR_DB) / RANGE_DB).coerceIn(0f, 1f)
    }

    /**
     * Lissage temporel : [prev] évolue vers [next] vite à la montée ([attack]) et plus lentement à la
     * descente ([decay]). [prev] null (première capture) → [next] tel quel. Renvoie un nouveau tableau.
     */
    fun smooth(prev: FloatArray?, next: FloatArray, attack: Float = ATTACK, decay: Float = DECAY): FloatArray {
        if (prev == null || prev.size != next.size) return next.copyOf()
        return FloatArray(next.size) { i ->
            val p = prev[i]
            val n = next[i]
            p + (n - p) * if (n > p) attack else decay
        }
    }

    /** Regroupe [bands] en [count] barres (moyenne de chaque groupe), pour l'égaliseur compact. */
    fun regroup(bands: FloatArray, count: Int): FloatArray {
        if (count <= 0 || bands.isEmpty()) return FloatArray(count.coerceAtLeast(0))
        return FloatArray(count) { g ->
            val lo = g * bands.size / count
            val hi = ((g + 1) * bands.size / count).coerceAtLeast(lo + 1).coerceAtMost(bands.size)
            var sum = 0f
            for (i in lo until hi) sum += bands[i]
            sum / (hi - lo)
        }
    }

    /** Capture entièrement nulle (Visualizer muet : sortie déchargée vers le DSP, Bluetooth…). */
    fun isSilent(fft: ByteArray): Boolean = fft.all { it.toInt() == 0 }
}
