package com.karelisio.bulle.core.feedback

import android.os.VibrationEffect
import kotlinx.serialization.Serializable

/**
 * Motifs haptiques originaux proposés aux alertes (et réutilisables par Messages, Appels…).
 * Ajouter un motif = ajouter une entrée ici ET son dessin dans [HapticDesigns].
 */
@Serializable
enum class HapticPattern {
    /** Aucune vibration (comportement historique). */
    NONE,

    /** Un tic à peine perceptible. */
    LIGHT,

    /** Deux tapotements nets et rapprochés. */
    DOUBLE_TAP,

    /** Deux battements sourds, le second plus faible. */
    HEARTBEAT,

    /** Une montée progressive conclue par un clic. */
    RISE,

    /** Deux houles douces qui vont et viennent. */
    WAVE,

    /** Trois clics francs suivis d'un tourbillon : pour ne rien rater. */
    INSISTENT,

    /** Un clic sec et court. */
    CRISP,
}

/** Une primitive de [VibrationEffect.Composition] : identifiant, intensité 0..1, pause avant (ms). */
data class HapticPrimitive(val id: Int, val scale: Float, val delayMs: Int = 0)

/**
 * Dessin complet d'un motif : la version « composée » (primitives riches) et son repli
 * en forme d'onde (durées ms / amplitudes 0..255, même longueur, première pause comprise).
 */
data class HapticDesign(
    val primitives: List<HapticPrimitive>,
    val timings: List<Long>,
    val amplitudes: List<Int>,
) {
    init {
        require(timings.size == amplitudes.size) { "timings et amplitudes doivent avoir la même taille" }
    }

    /** Identifiants distincts des primitives utilisées (pour areAllPrimitivesSupported). */
    val primitiveIds: IntArray get() = primitives.map { it.id }.distinct().toIntArray()

    /** Durée approximative du repli (ms). */
    val waveformDurationMs: Long get() = timings.sum()
}

/** Dessins des motifs. Les constantes de primitives sont inlinées à la compilation (testables en JVM). */
object HapticDesigns {
    private const val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
    private const val TICK = VibrationEffect.Composition.PRIMITIVE_TICK
    private const val THUD = VibrationEffect.Composition.PRIMITIVE_THUD
    private const val SPIN = VibrationEffect.Composition.PRIMITIVE_SPIN
    private const val QUICK_RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    private const val SLOW_RISE = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
    private const val LOW_TICK = VibrationEffect.Composition.PRIMITIVE_LOW_TICK

    /** Dessin d'un motif, null pour [HapticPattern.NONE]. */
    fun of(pattern: HapticPattern): HapticDesign? = when (pattern) {
        HapticPattern.NONE -> null
        HapticPattern.LIGHT -> HapticDesign(
            listOf(HapticPrimitive(TICK, 0.5f)),
            listOf(0L, 18L), listOf(0, 90),
        )
        HapticPattern.DOUBLE_TAP -> HapticDesign(
            listOf(HapticPrimitive(CLICK, 0.8f), HapticPrimitive(CLICK, 0.8f, 90)),
            listOf(0L, 22L, 90L, 22L), listOf(0, 200, 0, 200),
        )
        HapticPattern.HEARTBEAT -> HapticDesign(
            listOf(HapticPrimitive(THUD, 1f), HapticPrimitive(THUD, 0.55f, 120)),
            listOf(0L, 40L, 110L, 30L), listOf(0, 255, 0, 140),
        )
        HapticPattern.RISE -> HapticDesign(
            listOf(HapticPrimitive(SLOW_RISE, 0.8f), HapticPrimitive(CLICK, 0.9f, 20)),
            listOf(0L, 60L, 60L, 60L, 60L, 40L), listOf(0, 40, 90, 150, 210, 255),
        )
        HapticPattern.WAVE -> HapticDesign(
            listOf(
                HapticPrimitive(QUICK_RISE, 0.5f),
                HapticPrimitive(LOW_TICK, 0.7f, 60),
                HapticPrimitive(QUICK_RISE, 0.7f, 120),
                HapticPrimitive(LOW_TICK, 0.9f, 60),
            ),
            listOf(0L, 80L, 80L, 80L, 80L, 80L), listOf(0, 70, 150, 50, 190, 60),
        )
        HapticPattern.INSISTENT -> HapticDesign(
            listOf(
                HapticPrimitive(CLICK, 1f),
                HapticPrimitive(CLICK, 1f, 80),
                HapticPrimitive(CLICK, 1f, 80),
                HapticPrimitive(SPIN, 0.8f, 150),
            ),
            listOf(0L, 30L, 60L, 30L, 60L, 30L, 120L, 120L), listOf(0, 255, 0, 255, 0, 255, 0, 200),
        )
        HapticPattern.CRISP -> HapticDesign(
            listOf(HapticPrimitive(CLICK, 1f), HapticPrimitive(TICK, 0.4f, 30)),
            listOf(0L, 14L), listOf(0, 255),
        )
    }
}
