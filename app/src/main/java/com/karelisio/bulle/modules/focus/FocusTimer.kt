package com.karelisio.bulle.modules.focus

import kotlinx.serialization.Serializable
import java.util.Locale

/** Nature de la phase en cours. */
@Serializable
enum class FocusPhase { TIMER, WORK, SHORT_BREAK, LONG_BREAK }

/**
 * État d'un minuteur, sérialisable (survit au redémarrage du service).
 *
 * Le temps est basé sur l'horloge murale ([endAtWall], System.currentTimeMillis) et non sur un
 * compteur : la fin prévue reste juste après un redémarrage du service, voire du téléphone.
 *  - en marche : [endAtWall] non null ;
 *  - en pause : [endAtWall] null et [pausedRemainingMs] = temps restant figé.
 */
@Serializable
data class FocusState(
    val phase: FocusPhase = FocusPhase.TIMER,
    /** Numéro de la séance de travail en cours (1..[cycles]), 1 pour un minuteur simple. */
    val cycle: Int = 1,
    val cycles: Int = 1,
    /** Durée totale de la phase (ms). */
    val durationMs: Long = 0,
    val endAtWall: Long? = null,
    val pausedRemainingMs: Long = 0,
) {
    val running: Boolean get() = endAtWall != null
    val pomodoro: Boolean get() = phase != FocusPhase.TIMER
    val isBreak: Boolean get() = phase == FocusPhase.SHORT_BREAK || phase == FocusPhase.LONG_BREAK
}

/** Choix rapides du panneau. */
sealed interface FocusPreset {
    data class Minutes(val minutes: Int) : FocusPreset
    data object Pomodoro : FocusPreset

    companion object {
        val quick: List<FocusPreset> = listOf(Minutes(5), Minutes(10), Minutes(15), Minutes(25), Pomodoro)
    }
}

/** Calculs purs du minuteur (horloge injectée : testables sans Android). */
object FocusTimer {

    private const val MINUTE = 60_000L

    fun start(preset: FocusPreset, settings: FocusSettings, now: Long): FocusState {
        val s = settings.sanitized()
        return when (preset) {
            is FocusPreset.Minutes -> running(FocusState(FocusPhase.TIMER, 1, 1, preset.minutes.coerceAtLeast(1) * MINUTE), now)
            FocusPreset.Pomodoro -> running(FocusState(FocusPhase.WORK, 1, s.cycles, s.workMinutes * MINUTE), now)
        }
    }

    private fun running(state: FocusState, now: Long) = state.copy(endAtWall = now + state.durationMs, pausedRemainingMs = 0)

    /** Temps restant (ms, jamais négatif). */
    fun remaining(state: FocusState, now: Long): Long =
        (state.endAtWall?.let { it - now } ?: state.pausedRemainingMs).coerceIn(0, state.durationMs.coerceAtLeast(0))

    /** Avancement de la phase, de 0 (début) à 1 (terminée). */
    fun progress(state: FocusState, now: Long): Float {
        if (state.durationMs <= 0) return 1f
        return (1f - remaining(state, now).toFloat() / state.durationMs).coerceIn(0f, 1f)
    }

    fun isFinished(state: FocusState, now: Long): Boolean = state.running && remaining(state, now) <= 0

    fun pause(state: FocusState, now: Long): FocusState =
        if (!state.running) state else state.copy(endAtWall = null, pausedRemainingMs = remaining(state, now))

    fun resume(state: FocusState, now: Long): FocusState =
        if (state.running) state else state.copy(endAtWall = now + state.pausedRemainingMs, pausedRemainingMs = 0)

    /**
     * Phase qui suit [state] (null = cycle terminé).
     * Ordre Pomodoro : travail 1 → pause courte → travail 2 → … → travail n → pause longue → fin.
     * La phase suivante démarre à [now] si [autoStart], sinon elle attend en pause, durée entière.
     */
    fun next(state: FocusState, settings: FocusSettings, now: Long, autoStart: Boolean): FocusState? {
        val s = settings.sanitized()
        val following = when (state.phase) {
            FocusPhase.TIMER, FocusPhase.LONG_BREAK -> return null
            FocusPhase.WORK ->
                if (state.cycle >= state.cycles) state.copy(phase = FocusPhase.LONG_BREAK, durationMs = s.longBreakMinutes * MINUTE)
                else state.copy(phase = FocusPhase.SHORT_BREAK, durationMs = s.shortBreakMinutes * MINUTE)
            FocusPhase.SHORT_BREAK -> state.copy(phase = FocusPhase.WORK, cycle = state.cycle + 1, durationMs = s.workMinutes * MINUTE)
        }
        return if (autoStart) running(following, now) else following.copy(endAtWall = null, pausedRemainingMs = following.durationMs)
    }

    /** Texte du compte à rebours : « 4:05 », « 1:02:03 » au-delà d'une heure. */
    fun format(ms: Long): String {
        // Arrondi à la seconde supérieure : on affiche « 0:01 » jusqu'à la toute fin.
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}
