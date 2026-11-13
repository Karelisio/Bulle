package com.karelisio.bulle.modules.network

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Unité d'affichage du débit. */
@kotlinx.serialization.Serializable
enum class RateUnit { BYTES, BITS }

/**
 * Formatage lisible des débits et des volumes (règles pures, testées).
 * Base 1000, comme le système depuis Android 8 (1 Ko = 1000 octets).
 */
object RateFormat {

    /** Suffixes par défaut (français) ; l'interface passe ceux des ressources. */
    val FR_BYTES_PER_S = listOf("o/s", "Ko/s", "Mo/s", "Go/s")
    val FR_BITS_PER_S = listOf("bit/s", "kbit/s", "Mbit/s", "Gbit/s")
    val FR_BYTES = listOf("o", "Ko", "Mo", "Go", "To")

    /** « 0 o/s », « 512 o/s », « 1,2 Ko/s », « 34 Mo/s » (une décimale sous 10). */
    fun rate(bytesPerSecond: Double, unit: RateUnit, suffixes: List<String>, locale: Locale = Locale.getDefault()): String {
        val value = if (unit == RateUnit.BITS) bytesPerSecond * 8 else bytesPerSecond
        return scaled(value.coerceAtLeast(0.0), suffixes, locale)
    }

    /** Volume de données : « 340 Mo », « 1,2 Go ». */
    fun amount(bytes: Long, suffixes: List<String>, locale: Locale = Locale.getDefault()): String =
        scaled(bytes.coerceAtLeast(0).toDouble(), suffixes, locale)

    private fun scaled(raw: Double, suffixes: List<String>, locale: Locale): String {
        var value = raw
        var index = 0
        // 999,5 o/s arrondi donnerait « 1000 o/s » : on passe au palier suivant dès 999,5.
        while (value >= 999.5 && index < suffixes.lastIndex) {
            value /= 1000.0
            index++
        }
        val number = when {
            index == 0 -> value.roundToLong().toString()
            value < 9.95 -> String.format(locale, "%.1f", value)
            else -> value.roundToLong().toString()
        }
        return "$number ${suffixes[index]}"
    }
}

/**
 * Débit en octets/s entre deux relevés de compteurs cumulés. null si l'intervalle est nul ;
 * 0 si le compteur a reculé (redémarrage, compteur indisponible).
 */
fun rateBetween(previousBytes: Long, previousAtMs: Long, bytes: Long, atMs: Long): Double? {
    val dt = atMs - previousAtMs
    if (dt <= 0) return null
    if (previousBytes < 0 || bytes < 0) return 0.0
    val delta = bytes - previousBytes
    if (delta < 0) return 0.0
    return delta * 1000.0 / dt
}

/**
 * Lissage exponentiel du débit : évite les sauts d'une seconde à l'autre tout en suivant
 * vite une vraie hausse. Sous 1 o/s, on revient franchement à zéro (pas de traîne infinie).
 */
class RateSmoother(private val alpha: Double = 0.5) {
    var value: Double = 0.0
        private set
    private var primed = false

    fun push(sample: Double): Double {
        value = if (!primed) sample else value + alpha * (sample - value)
        primed = true
        if (abs(value) < 1.0) value = 0.0
        return value
    }

    fun reset() {
        value = 0.0
        primed = false
    }
}

/** Un point du mini-graphe : débits lissés descendant / montant (octets/s). */
data class RatePoint(val rx: Double, val tx: Double)

/** Ajoute un point et ne garde que les [capacity] derniers (60 s par défaut). */
fun List<RatePoint>.pushPoint(point: RatePoint, capacity: Int = 60): List<RatePoint> =
    (this + point).takeLast(capacity)

/**
 * Décision d'affichage du contenu compact avec hystérésis : il apparaît quand le débit reste
 * au-dessus du seuil plus de [showAfterMs] (gros téléchargement), et disparaît après
 * [hideAfterMs] passées sous le seuil. Tant que [pinned] (vue étendue ouverte), il reste.
 */
class HeavyTrafficGate(
    private val thresholdBytesPerS: Double,
    private val showAfterMs: Long = 3_000L,
    private val hideAfterMs: Long = 5_000L,
) {
    var visible = false
        private set
    private var aboveSince: Long? = null
    private var belowSince: Long? = null

    fun update(rateBytesPerS: Double, nowMs: Long, pinned: Boolean = false): Boolean {
        if (rateBytesPerS >= thresholdBytesPerS) {
            belowSince = null
            val since = aboveSince ?: nowMs.also { aboveSince = it }
            if (nowMs - since >= showAfterMs) visible = true
        } else {
            aboveSince = null
            val since = belowSince ?: nowMs.also { belowSince = it }
            if (visible && !pinned && nowMs - since >= hideAfterMs) visible = false
        }
        return visible
    }

    fun reset() {
        visible = false
        aboveSince = null
        belowSince = null
    }
}
