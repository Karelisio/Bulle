package com.karelisio.bulle.modules.screentime

import com.karelisio.bulle.modules.countdown.PrivateJsonStore
import kotlinx.serialization.Serializable

/** Réglages du module « Temps d'écran ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class ScreenTimeSettings(
    /** Contenu ambiant au repos avec le temps d'écran du jour (désactivé par défaut). */
    val showAmbient: Boolean = false,
    /** Rappel de pause après une utilisation continue. */
    val breakReminder: Boolean = true,
    /** Durée d'utilisation continue avant le rappel (minutes). */
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
    /** Proposer une courte respiration depuis le rappel. */
    val breakBreathing: Boolean = true,
    /** Limites quotidiennes par appli (paquet → minutes). */
    val appLimits: Map<String, Int> = emptyMap(),
    /** Limites déjà signalées (« paquet@jourEpoch »), pour n'alerter qu'une fois par jour. */
    val limitAlerted: Set<String> = emptySet(),
) {
    val breakMs: Long get() = breakMinutes.coerceAtLeast(1) * 60_000L

    companion object {
        const val DEFAULT_BREAK_MINUTES = 30
        val BREAK_CHOICES = listOf(20, 30, 45, 60)
        val LIMIT_CHOICES = listOf(0, 15, 30, 60, 90, 120, 180)
    }
}

/** Réglages du module, dans un fichier privé. */
object ScreenTimePrefs {
    const val FILE_NAME = "screentime.json"
    val store = PrivateJsonStore(FILE_NAME, ScreenTimeSettings.serializer()) { ScreenTimeSettings() }
}

/** Limites atteintes et pas encore signalées aujourd'hui (fonction pure). */
fun reachedLimits(settings: ScreenTimeSettings, usage: DayUsage, epochDay: Long): List<Pair<String, Int>> =
    settings.appLimits.filter { (pkg, minutes) ->
        minutes > 0 && (usage.perApp[pkg] ?: 0L) >= minutes * 60_000L && "$pkg@$epochDay" !in settings.limitAlerted
    }.map { it.key to it.value }
