package com.karelisio.bulle.modules.weather

import android.content.Context
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Dernière réponse Open-Meteo conservée hors ligne (fichier JSON dans filesDir). */
@Serializable
internal data class WeatherCache(val fetchedAt: Long, val body: String, val fromDevice: Boolean = false)

/**
 * Accès réseau (hors thread principal, délais courts) et cache hors ligne de la météo.
 * La prévision n'est retéléchargée qu'au plus toutes les 30 minutes.
 */
object WeatherRepository {
    const val REFRESH_MS = 30 * 60_000L
    private const val TIMEOUT_MS = 10_000
    private const val CACHE_FILE = "weather_cache.json"

    private val mutex = Mutex()
    private val _latest = MutableStateFlow<Forecast?>(null)

    /** Dernière prévision connue (cache compris), pour l'écran de réglages. */
    val latest: StateFlow<Forecast?> = _latest.asStateFlow()

    /** Prévision en cache, même ancienne. */
    suspend fun cached(context: Context): Forecast? = withContext(Dispatchers.IO) {
        _latest.value ?: readCache(context)?.also { _latest.value = it }
    }

    /**
     * Prévision à jour pour ces coordonnées : le cache s'il a moins de 30 min et correspond
     * au même point (arrondi), sinon un téléchargement ; en cas d'échec, le cache même ancien.
     */
    suspend fun forecast(
        context: Context,
        latitude: Double,
        longitude: Double,
        fromDevice: Boolean,
        force: Boolean = false,
    ): Forecast? = mutex.withLock {
        val now = System.currentTimeMillis()
        val cache = cached(context)
        val samePlace = cache != null && cache.fromDevice == fromDevice && sameRoundedPlace(cache, latitude, longitude)
        if (!force && cache != null && samePlace && now - cache.fetchedAt in 0 until REFRESH_MS) return@withLock cache
        val fresh = withContext(Dispatchers.IO) {
            httpGet(OpenMeteo.forecastUrl(latitude, longitude))?.let { body ->
                OpenMeteo.parseForecast(body, now)?.copy(fromDevice = fromDevice)?.also { writeCache(context, WeatherCache(now, body, fromDevice)) }
            }
        }
        (fresh ?: cache)?.also { _latest.value = it }
    }

    /** Recherche de villes (géocodage Open-Meteo). Liste vide hors ligne. */
    suspend fun searchCity(name: String, language: String): List<WeatherCity> {
        if (name.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            httpGet(OpenMeteo.geocodingUrl(name, language))?.let { OpenMeteo.parseGeocoding(it) }.orEmpty()
        }
    }

    /** Même point à 2 décimales près (Open-Meteo renvoie la maille la plus proche, d'où une tolérance). */
    internal fun sameRoundedPlace(f: Forecast, latitude: Double, longitude: Double): Boolean =
        kotlin.math.abs(f.latitude - OpenMeteo.round2(latitude)) < 0.1 && kotlin.math.abs(f.longitude - OpenMeteo.round2(longitude)) < 0.1

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun readCache(context: Context): Forecast? = runCatching {
        val f = cacheFile(context)
        if (!f.exists()) return null
        val cache = BulleJson.decodeFromString(WeatherCache.serializer(), f.readText())
        OpenMeteo.parseForecast(cache.body, cache.fetchedAt)?.copy(fromDevice = cache.fromDevice)
    }.getOrNull()

    private fun writeCache(context: Context, cache: WeatherCache) {
        runCatching {
            val f = cacheFile(context)
            val tmp = File(f.parentFile, "$CACHE_FILE.tmp")
            tmp.writeText(BulleJson.encodeToString(WeatherCache.serializer(), cache))
            if (!tmp.renameTo(f)) tmp.delete()
        }
    }

    private fun httpGet(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
