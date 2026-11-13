package com.karelisio.bulle.modules.external

/** Une phase de respiration. */
enum class BreathPhase { INHALE, HOLD, EXHALE, HOLD_OUT }

/**
 * Exercices de respiration proposés.
 *  - [RELAX_478] : inspire 4 s, bloque 7 s, expire 8 s (19 s par cycle, 4 cycles ≈ 1 min 16) ;
 *  - [BOX] : respiration carrée 4-4-4-4 (16 s par cycle, 6 cycles ≈ 1 min 36).
 */
enum class BreathPattern(val wire: String, val steps: List<Pair<BreathPhase, Int>>, val defaultCycles: Int) {
    RELAX_478("478", listOf(BreathPhase.INHALE to 4, BreathPhase.HOLD to 7, BreathPhase.EXHALE to 8), 4),
    BOX("box", listOf(BreathPhase.INHALE to 4, BreathPhase.HOLD to 4, BreathPhase.EXHALE to 4, BreathPhase.HOLD_OUT to 4), 6),
    ;

    /** Durée d'un cycle, en millisecondes. */
    val cycleMs: Long get() = steps.sumOf { it.second } * 1000L

    companion object {
        fun parse(value: String?): BreathPattern? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.wire == v || it.name.lowercase() == v } }
    }
}

/**
 * État de l'exercice à un instant donné.
 * @property phaseProgress avancement 0..1 dans la phase courante.
 * @property secondsLeft secondes restantes dans la phase (arrondi supérieur, ≥ 1 tant que non fini).
 * @property cycle cycle courant, à partir de 1.
 */
data class BreathState(
    val phase: BreathPhase,
    val phaseProgress: Float,
    val secondsLeft: Int,
    val cycle: Int,
    val cycles: Int,
    val totalProgress: Float,
    val finished: Boolean,
)

/** Calculs purs de l'exercice de respiration (testés en JUnit). */
object Breathing {
    /** Taille minimale du cercle (expiré) relativement à sa taille maximale (inspiré). */
    const val MIN_SCALE = 0.55f

    fun totalMs(pattern: BreathPattern, cycles: Int): Long = pattern.cycleMs * cycles.coerceIn(1, 20)

    fun stateAt(pattern: BreathPattern, elapsedMs: Long, cycles: Int = pattern.defaultCycles): BreathState {
        val n = cycles.coerceIn(1, 20)
        val total = totalMs(pattern, n)
        if (elapsedMs >= total) {
            val last = pattern.steps.last().first
            return BreathState(last, 1f, 0, n, n, 1f, finished = true)
        }
        val t = elapsedMs.coerceAtLeast(0)
        val cycle = (t / pattern.cycleMs).toInt()
        var inCycle = t - cycle * pattern.cycleMs
        for ((phase, seconds) in pattern.steps) {
            val len = seconds * 1000L
            if (inCycle < len) {
                val left = ((len - inCycle + 999) / 1000).toInt().coerceAtLeast(1)
                return BreathState(phase, inCycle.toFloat() / len, left, cycle + 1, n, t.toFloat() / total, finished = false)
            }
            inCycle -= len
        }
        // Inaccessible (inCycle < cycleMs), mais on reste défensif.
        return BreathState(pattern.steps.last().first, 1f, 1, cycle + 1, n, t.toFloat() / total, finished = false)
    }

    /** Échelle du cercle (MIN_SCALE..1) : grossit à l'inspiration, reste plein en apnée, rétrécit à l'expiration. */
    fun scaleOf(state: BreathState): Float {
        val p = easeInOut(state.phaseProgress.coerceIn(0f, 1f))
        return when (state.phase) {
            BreathPhase.INHALE -> MIN_SCALE + (1f - MIN_SCALE) * p
            BreathPhase.HOLD -> 1f
            BreathPhase.EXHALE -> 1f - (1f - MIN_SCALE) * p
            BreathPhase.HOLD_OUT -> MIN_SCALE
        }
    }

    private fun easeInOut(x: Float): Float = x * x * (3 - 2 * x)
}
