package com.karelisio.bulle.modules.weather

import com.karelisio.bulle.core.settings.BulleSettings
import kotlinx.serialization.Serializable

/** D'où vient la position utilisée pour la météo. */
@Serializable
enum class WeatherLocationSource { DEVICE, CITY }

/** Ville choisie dans la recherche Open-Meteo. */
@Serializable
data class WeatherCity(
    val name: String,
    val admin: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** « Lyon, Auvergne-Rhône-Alpes, France ». */
    val label: String get() = listOfNotNull(name, admin, country).distinct().joinToString(", ")
}

/** Réglages du module « Météo ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class WeatherSettings(
    val source: WeatherLocationSource = WeatherLocationSource.DEVICE,
    val city: WeatherCity? = null,
    /** Alerte « Pluie dans N min ». */
    val rainAlert: Boolean = true,
    /** Seuil de précipitations par quart d'heure (mm) au-delà duquel on parle de pluie. */
    val rainThresholdMm: Float = DEFAULT_THRESHOLD,
    /** Température affichée en compact quand l'île n'a rien d'autre à montrer. */
    val idleTemperature: Boolean = false,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 0.2f
        val THRESHOLD_CHOICES = listOf(0.1f, 0.2f, 0.5f)
    }
}

/**
 * Branchement sur [BulleSettings] : seul endroit à modifier quand le champ
 * `val weather: WeatherSettings = WeatherSettings()` est ajouté à BulleSettings.
 * Remplacer alors les deux corps par `weather` et `copy(weather = value)`.
 */
internal object WeatherSettingsBinding {
    fun read(settings: BulleSettings): WeatherSettings = WeatherSettings()

    @Suppress("UNUSED_PARAMETER")
    fun write(settings: BulleSettings, value: WeatherSettings): BulleSettings = settings
}
