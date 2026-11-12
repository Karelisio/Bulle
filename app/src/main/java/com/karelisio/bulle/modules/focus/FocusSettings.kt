package com.karelisio.bulle.modules.focus

import kotlinx.serialization.Serializable

/** Réglages du minuteur / Pomodoro. Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class FocusSettings(
    /** Durée d'une séance de travail Pomodoro (minutes). */
    val workMinutes: Int = 25,
    /** Pause courte entre deux séances (minutes). */
    val shortBreakMinutes: Int = 5,
    /** Pause longue après la dernière séance (minutes). */
    val longBreakMinutes: Int = 15,
    /** Nombre de séances de travail d'un cycle complet. */
    val cycles: Int = 4,
    /** Enchaîner automatiquement les phases Pomodoro (sinon la phase suivante attend en pause). */
    val autoAdvance: Boolean = true,
    /** Son de notification à la fin d'une phase (désactivé par défaut). */
    val sound: Boolean = false,
    /** Vibration légère à la fin d'une phase (si le retour haptique de l'île est aussi actif). */
    val vibrate: Boolean = true,
) {
    /** Valeurs ramenées dans des bornes raisonnables (réglages corrompus, sauvegarde ancienne…). */
    fun sanitized(): FocusSettings = copy(
        workMinutes = workMinutes.coerceIn(1, 180),
        shortBreakMinutes = shortBreakMinutes.coerceIn(1, 60),
        longBreakMinutes = longBreakMinutes.coerceIn(1, 90),
        cycles = cycles.coerceIn(1, 12),
    )
}
