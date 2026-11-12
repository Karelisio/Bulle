package com.karelisio.bulle.modules.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/** Précipitations cumulées sur le quart d'heure qui se termine à [endsAt] (epoch ms). */
data class Quarter(val endsAt: Long, val precipitationMm: Double)

/** Prévision utile à l'île, extraite d'une réponse Open-Meteo. */
data class Forecast(
    val fetchedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double?,
    val weatherCode: Int?,
    val isDay: Boolean = true,
    val precipitationNowMm: Double = 0.0,
    val quarters: List<Quarter> = emptyList(),
    /** Prévision obtenue pour la position de l'appareil (et non une ville choisie). */
    val fromDevice: Boolean = false,
) {
    val kind: WeatherKind get() = WeatherKind.of(weatherCode)
}

/** Requêtes et lecture des réponses d'Open-Meteo (API gratuite, sans clé). Fonctions pures. */
object OpenMeteo {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    private val json = Json { ignoreUnknownKeys = true }

    /** Arrondi à 2 décimales (≈ 1 km) : seule précision jamais envoyée sur le réseau. */
    fun round2(value: Double): Double = BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toDouble()

    private fun coord(value: Double) = String.format(Locale.ROOT, "%.2f", round2(value))

    fun forecastUrl(latitude: Double, longitude: Double): String =
        "$FORECAST?latitude=${coord(latitude)}&longitude=${coord(longitude)}" +
            "&current=temperature_2m,weather_code,is_day,precipitation" +
            "&minutely_15=precipitation&forecast_minutely_15=12&timezone=auto&timeformat=unixtime"

    fun geocodingUrl(name: String, language: String = "fr"): String =
        "$GEOCODING?name=${URLEncoder.encode(name.trim(), "UTF-8")}&count=5&language=$language&format=json"

    /** Lit une réponse « forecast ». null si le JSON est illisible ou incomplet. */
    fun parseForecast(body: String, fetchedAt: Long): Forecast? = runCatching {
        val root = json.parseToJsonElement(body) as JsonObject
        val offset = (root["utc_offset_seconds"] as? JsonPrimitive)?.intOrNull ?: 0
        val current = root["current"] as? JsonObject
        val minutely = root["minutely_15"] as? JsonObject
        val times = (minutely?.get("time") as? JsonArray).orEmpty()
        val values = (minutely?.get("precipitation") as? JsonArray).orEmpty()
        val quarters = times.indices.mapNotNull { i ->
            val t = epochMillis(times[i], offset) ?: return@mapNotNull null
            Quarter(t, values.getOrNull(i)?.number() ?: 0.0)
        }
        Forecast(
            fetchedAt = fetchedAt,
            latitude = (root["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@runCatching null,
            longitude = (root["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@runCatching null,
            temperature = current?.get("temperature_2m")?.number(),
            weatherCode = (current?.get("weather_code") as? JsonPrimitive)?.intOrNull,
            isDay = ((current?.get("is_day") as? JsonPrimitive)?.intOrNull ?: 1) != 0,
            precipitationNowMm = current?.get("precipitation")?.number() ?: 0.0,
            quarters = quarters,
        )
    }.getOrNull()

    /** Lit une réponse de géocodage (liste vide si aucun résultat). */
    fun parseGeocoding(body: String): List<WeatherCity> = runCatching {
        val root = json.parseToJsonElement(body) as JsonObject
        (root["results"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            WeatherCity(
                name = o.string("name") ?: return@mapNotNull null,
                admin = o.string("admin1"),
                country = o.string("country"),
                latitude = o["latitude"]?.number() ?: return@mapNotNull null,
                longitude = o["longitude"]?.number() ?: return@mapNotNull null,
            )
        }
    }.getOrDefault(emptyList())

    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** Instant d'un pas de temps : secondes Unix (timeformat=unixtime) ou ISO local + décalage UTC. */
    private fun epochMillis(e: JsonElement, offsetSeconds: Int): Long? {
        val p = e as? JsonPrimitive ?: return null
        if (!p.isString) return p.longOrNull?.times(1000)
        return runCatching {
            LocalDateTime.parse(p.content).toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000
        }.getOrNull()
    }
}
