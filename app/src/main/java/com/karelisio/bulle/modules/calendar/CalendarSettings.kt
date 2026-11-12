package com.karelisio.bulle.modules.calendar

import com.karelisio.bulle.core.settings.BulleSettings
import kotlinx.serialization.Serializable

/** Réglages du module « Agenda ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class CalendarSettings(
    /** Délai du rappel avant le début d'un événement, en minutes (5, 10, 15 ou 30). */
    val reminderMinutes: Int = DEFAULT_REMINDER,
    /** Pendant un événement, afficher discrètement le temps restant. */
    val showOngoing: Boolean = true,
) {
    /** Délai effectif : une valeur inconnue (sauvegarde bricolée) retombe sur le défaut. */
    val effectiveReminderMinutes: Int get() = reminderMinutes.takeIf { it in REMINDER_CHOICES } ?: DEFAULT_REMINDER

    companion object {
        const val DEFAULT_REMINDER = 10
        val REMINDER_CHOICES = listOf(5, 10, 15, 30)
    }
}

/**
 * Branchement sur [BulleSettings] : seul endroit à modifier quand le champ
 * `val calendar: CalendarSettings = CalendarSettings()` est ajouté à BulleSettings.
 * Remplacer alors les deux corps par `calendar` et `copy(calendar = value)`.
 */
internal object CalendarSettingsBinding {
    fun read(settings: BulleSettings): CalendarSettings = CalendarSettings()

    @Suppress("UNUSED_PARAMETER")
    fun write(settings: BulleSettings, value: CalendarSettings): BulleSettings = settings
}
