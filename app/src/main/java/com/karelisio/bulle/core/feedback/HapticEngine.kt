package com.karelisio.bulle.core.feedback

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** Ce que le vibreur va réellement jouer (indépendant d'Android, donc testable). */
sealed interface HapticPlan {
    data object Silent : HapticPlan
    data class Composed(val primitives: List<HapticPrimitive>) : HapticPlan
    data class Waveform(val timings: List<Long>, val amplitudes: List<Int>) : HapticPlan
}

/** Abstraction du vibreur : le vrai ([AndroidVibratorPort]) ou un faux dans les tests. */
interface VibratorPort {
    val hasVibrator: Boolean
    val hasAmplitudeControl: Boolean
    fun supportsAll(primitiveIds: IntArray): Boolean
    fun play(plan: HapticPlan)
}

object HapticEngine {
    /**
     * Choisit la forme jouée : primitives riches si le vibreur les gère toutes, sinon repli
     * en forme d'onde. Sans contrôle d'amplitude, toute amplitude non nulle devient « défaut ».
     */
    fun plan(pattern: HapticPattern, port: VibratorPort): HapticPlan {
        val design = HapticDesigns.of(pattern) ?: return HapticPlan.Silent
        if (!port.hasVibrator) return HapticPlan.Silent
        if (port.supportsAll(design.primitiveIds)) return HapticPlan.Composed(design.primitives)
        val amps = if (port.hasAmplitudeControl) {
            design.amplitudes
        } else {
            design.amplitudes.map { if (it == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }
        }
        return HapticPlan.Waveform(design.timings, amps)
    }

    fun play(pattern: HapticPattern, port: VibratorPort) {
        val p = plan(pattern, port)
        if (p != HapticPlan.Silent) port.play(p)
    }
}

/** Vibreur réel du téléphone. */
class AndroidVibratorPort(private val vibrator: Vibrator) : VibratorPort {
    override val hasVibrator: Boolean get() = vibrator.hasVibrator()
    override val hasAmplitudeControl: Boolean get() = vibrator.hasAmplitudeControl()
    // Les identifiants viennent exclusivement de HapticDesigns (constantes PRIMITIVE_* valides).
    @SuppressLint("WrongConstant")
    override fun supportsAll(primitiveIds: IntArray): Boolean = vibrator.areAllPrimitivesSupported(*primitiveIds)

    @SuppressLint("WrongConstant")
    override fun play(plan: HapticPlan) {
        val effect = when (plan) {
            HapticPlan.Silent -> return
            is HapticPlan.Composed -> VibrationEffect.startComposition().apply {
                plan.primitives.forEach { addPrimitive(it.id, it.scale.coerceIn(0f, 1f), it.delayMs) }
            }.compose()
            is HapticPlan.Waveform -> VibrationEffect.createWaveform(plan.timings.toLongArray(), plan.amplitudes.toIntArray(), -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            vibrator.vibrate(effect)
        }
    }
}
