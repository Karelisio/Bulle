package com.karelisio.bulle.modules.battery

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Un relevé du niveau de batterie (instant mural en ms, %, en charge ?). */
@Serializable
data class LevelSample(val at: Long, val level: Int, val charging: Boolean)

/** Historique local des niveaux (quelques heures), base des estimations. */
@Serializable
data class BatteryHistory(val samples: List<LevelSample> = emptyList())

/** Règles pures du module « Batterie détaillée » (estimations, normalisations), testées unitairement. */
object BatteryMath {

    const val HOUR_MS = 3_600_000L
    /** Durée d'historique conservée. */
    const val KEEP_MS = 12 * HOUR_MS
    const val MAX_SAMPLES = 400

    /** Fenêtre utilisée pour la pente de décharge / de charge. */
    const val DISCHARGE_WINDOW_MS = 6 * HOUR_MS
    const val CHARGE_WINDOW_MS = 2 * HOUR_MS

    /** Durée minimale couverte par les relevés pour oser une estimation. */
    const val MIN_SPAN_MS = 15 * 60_000L

    /** Plafond d'une estimation d'autonomie (au-delà, elle n'a plus de sens). */
    const val MAX_ESTIMATE_MS = 7 * 24 * HOUR_MS

    /**
     * Ajoute un relevé s'il apporte une information (niveau ou état de charge changé),
     * oublie ceux de plus de [KEEP_MS] et garde au plus [MAX_SAMPLES] relevés.
     */
    fun record(history: List<LevelSample>, sample: LevelSample): List<LevelSample> {
        val last = history.lastOrNull()
        val next = if (last != null && last.level == sample.level && last.charging == sample.charging) history else history + sample
        return next.filter { sample.at - it.at in 0..KEEP_MS }.takeLast(MAX_SAMPLES)
    }

    /** Segment final homogène (même état de charge que le dernier relevé), limité à [windowMs]. */
    private fun tailSegment(samples: List<LevelSample>, charging: Boolean, now: Long, windowMs: Long): List<LevelSample> {
        val tail = samples.takeLastWhile { it.charging == charging }
        return tail.filter { now - it.at in 0..windowMs }
    }

    /** Pente (en % par heure) par moindres carrés ; null si moins de 2 relevés ou durée nulle. */
    fun slopePerHour(samples: List<LevelSample>): Double? {
        if (samples.size < 2) return null
        val t0 = samples.first().at
        val xs = samples.map { (it.at - t0).toDouble() / HOUR_MS }
        val ys = samples.map { it.level.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den <= 0.0) return null
        return num / den
    }

    /**
     * Autonomie restante estimée (ms) à partir de la pente de décharge des dernières heures,
     * ou null si les données sont insuffisantes (moins de 15 min ou moins de 2 % perdus).
     */
    fun dischargeRemainingMs(samples: List<LevelSample>, level: Int, now: Long): Long? {
        val seg = tailSegment(samples, charging = false, now = now, windowMs = DISCHARGE_WINDOW_MS)
        if (seg.size < 2 || seg.last().at - seg.first().at < MIN_SPAN_MS) return null
        if (seg.first().level - seg.last().level < 2) return null
        val slope = slopePerHour(seg) ?: return null
        if (slope >= -0.05) return null
        return (level / -slope * HOUR_MS).toLong().coerceAtMost(MAX_ESTIMATE_MS)
    }

    /**
     * Temps jusqu'à la charge complète : valeur système si connue (> 0), sinon pente de charge
     * des 2 dernières heures. null si inconnu ; 0 si déjà plein.
     */
    fun chargeRemainingMs(systemMs: Long?, samples: List<LevelSample>, level: Int, now: Long): Long? {
        if (level >= 100) return 0L
        if (systemMs != null && systemMs > 0) return systemMs
        val seg = tailSegment(samples, charging = true, now = now, windowMs = CHARGE_WINDOW_MS)
        if (seg.size < 2 || seg.last().at - seg.first().at < 5 * 60_000L) return null
        val slope = slopePerHour(seg) ?: return null
        if (slope <= 0.1) return null
        return ((100 - level) / slope * HOUR_MS).toLong().coerceAtMost(MAX_ESTIMATE_MS)
    }

    /**
     * BATTERY_PROPERTY_CURRENT_NOW normalisé en µA, positif en charge et négatif en décharge.
     * Les constructeurs varient : unité µA (documentée) ou mA, et signe inversé chez certains.
     * Valeur absolue ≤ 15 000 → lue comme des mA (aucun téléphone ne tire 15 A).
     * null si inconnue (0 ou Int.MIN_VALUE).
     */
    fun normalizeCurrentMicroAmps(raw: Long, charging: Boolean): Long? {
        if (raw == 0L || raw == Int.MIN_VALUE.toLong() || raw == Long.MIN_VALUE) return null
        val magnitude = abs(raw).let { if (it <= 15_000L) it * 1_000L else it }
        if (magnitude > 30_000_000L) return null // valeur aberrante (> 30 A)
        return if (charging) magnitude else -magnitude
    }

    /** EXTRA_VOLTAGE normalisé en mV (certains appareils donnent des V ou des µV). null si absent. */
    fun normalizeVoltageMillivolts(raw: Int): Int? = when {
        raw <= 0 -> null
        raw < 100 -> raw * 1000
        raw > 100_000 -> raw / 1000
        else -> raw
    }

    /** Puissance instantanée (W) = |courant| × tension ; null si une des deux mesures manque. */
    fun powerWatts(currentMicroA: Long?, voltageMv: Int?): Double? {
        if (currentMicroA == null || voltageMv == null) return null
        return abs(currentMicroA) / 1_000_000.0 * voltageMv / 1000.0
    }

    /** EXTRA_TEMPERATURE (dixièmes de °C) en °C ; null si absent. */
    fun temperatureCelsius(raw: Int): Double? = if (raw == Int.MIN_VALUE || raw <= -500) null else raw / 10.0

    /** Découpe une durée en (heures, minutes), arrondie à la minute. */
    fun hoursMinutes(ms: Long): Pair<Long, Long> {
        val minutes = (ms + 30_000L) / 60_000L
        return minutes / 60 to minutes % 60
    }
}
