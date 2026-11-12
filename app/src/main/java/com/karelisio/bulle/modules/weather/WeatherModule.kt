package com.karelisio.bulle.modules.weather

import android.content.Context
import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Module « Météo » (Open-Meteo, sans clé) : alerte « Pluie dans N min » et, en option,
 * la température au repos. Désactivé par défaut. Seules des coordonnées arrondies
 * à 2 décimales quittent le téléphone ; la dernière réponse reste en cache hors ligne.
 */
object WeatherModule : IslandModule {
    const val ID = "weather"
    override val id = ID
    override val titleRes = R.string.weather_module_title
    override val enabledByDefault = false

    /** Réévaluation locale (pluie, repos) : sans réseau tant que le cache a moins de 30 min. */
    private const val TICK_MS = 5 * 60_000L

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        val context = env.context.applicationContext
        val episodes = RainEpisodes()
        var rainAlert: WeatherRainContent? = null
        return env.settings
            .map { WeatherSettingsBinding.read(it) }
            .distinctUntilChanged()
            .transformLatest { settings ->
                if (!settings.rainAlert && !settings.idleTemperature) {
                    emit(emptyList())
                    return@transformLatest
                }
                while (true) {
                    val forecast = load(context, settings)
                    val now = System.currentTimeMillis()
                    val contents = buildList<IslandContent> {
                        if (forecast != null && settings.rainAlert) {
                            val threshold = settings.rainThresholdMm.toDouble()
                            val soon = RainForecast.rainSoon(forecast, now, threshold)
                            episodes.update(soon, RainForecast.rainingNow(forecast, threshold))?.let {
                                rainAlert = WeatherRainContent(it, forecast, SystemClock.elapsedRealtime())
                            }
                            if (soon == null) rainAlert = null
                            rainAlert?.let { add(it) }
                        }
                        // Température au repos : seulement si la prévision n'est pas trop ancienne (3 h).
                        if (forecast != null && settings.idleTemperature && forecast.temperature != null &&
                            now - forecast.fetchedAt < 3 * 60 * 60_000L
                        ) {
                            add(WeatherIdleContent(forecast, placeLabel(settings)))
                        }
                    }
                    emit(contents)
                    delay(TICK_MS)
                }
            }
    }

    private fun placeLabel(settings: WeatherSettings): String? =
        if (settings.source == WeatherLocationSource.CITY) settings.city?.name else null

    /** Prévision pour la position choisie ; le cache frais évite toute localisation et tout réseau. */
    internal suspend fun load(context: Context, settings: WeatherSettings, force: Boolean = false): Forecast? {
        val cache = WeatherRepository.cached(context)
        val coords = when (settings.source) {
            WeatherLocationSource.CITY -> settings.city?.let { it.latitude to it.longitude }
            WeatherLocationSource.DEVICE -> {
                val fresh = cache != null && cache.fromDevice && System.currentTimeMillis() - cache.fetchedAt in 0 until WeatherRepository.REFRESH_MS
                if (fresh && !force) return cache
                WeatherLocation.current(context)
            }
        } ?: return cache
        return WeatherRepository.forecast(context, coords.first, coords.second, settings.source == WeatherLocationSource.DEVICE, force)
    }
}
