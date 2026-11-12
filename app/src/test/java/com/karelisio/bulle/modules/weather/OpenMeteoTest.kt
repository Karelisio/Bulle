package com.karelisio.bulle.modules.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {

    private val forecastJson = """
        {"latitude":48.86,"longitude":2.3399997,"generationtime_ms":0.1,"utc_offset_seconds":3600,
         "timezone":"Europe/Paris","current_units":{"temperature_2m":"°C"},
         "current":{"time":1762956000,"interval":900,"temperature_2m":11.4,"weather_code":3,"is_day":1,"precipitation":0.0},
         "minutely_15":{"time":[1762956000,1762956900,1762957800,1762958700],"precipitation":[0.0,0.0,0.6,1.2]}}
    """.trimIndent()

    @Test
    fun parsesForecast() {
        val f = OpenMeteo.parseForecast(forecastJson, fetchedAt = 42)
        assertNotNull(f)
        f!!
        assertEquals(42L, f.fetchedAt)
        assertEquals(11.4, f.temperature!!, 1e-9)
        assertEquals(WeatherKind.CLOUDY, f.kind)
        assertTrue(f.isDay)
        assertEquals(4, f.quarters.size)
        assertEquals(1_762_957_800_000L, f.quarters[2].endsAt)
        assertEquals(0.6, f.quarters[2].precipitationMm, 1e-9)
    }

    @Test
    fun parsesIsoTimesWithOffset() {
        val iso = """{"latitude":1.0,"longitude":2.0,"utc_offset_seconds":3600,
            "current":{"temperature_2m":-3.2,"weather_code":71,"is_day":0},
            "minutely_15":{"time":["2026-11-12T10:00"],"precipitation":[null]}}"""
        val f = OpenMeteo.parseForecast(iso, 0)!!
        assertEquals(WeatherKind.SNOW, f.kind)
        assertFalse(f.isDay)
        // 10:00 à UTC+1 = 09:00 UTC.
        assertEquals(java.time.Instant.parse("2026-11-12T09:00:00Z").toEpochMilli(), f.quarters.single().endsAt)
        assertEquals(0.0, f.quarters.single().precipitationMm, 0.0)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(OpenMeteo.parseForecast("not json", 0))
        assertNull(OpenMeteo.parseForecast("""{"error":true,"reason":"bad"}""", 0))
    }

    @Test
    fun parsesGeocoding() {
        val body = """{"results":[
            {"id":2988507,"name":"Paris","latitude":48.85341,"longitude":2.3488,"country":"France","admin1":"Île-de-France"},
            {"id":4717560,"name":"Paris","latitude":33.66094,"longitude":-95.55551,"country":"États-Unis","admin1":"Texas"},
            {"id":1,"latitude":1.0}],"generationtime_ms":0.5}"""
        val cities = OpenMeteo.parseGeocoding(body)
        assertEquals(2, cities.size)
        assertEquals("Paris, Île-de-France, France", cities[0].label)
        assertEquals(-95.55551, cities[1].longitude, 1e-9)
        assertTrue(OpenMeteo.parseGeocoding("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test
    fun roundsCoordinatesToTwoDecimals() {
        assertEquals(48.86, OpenMeteo.round2(48.856613), 0.0)
        assertEquals(2.35, OpenMeteo.round2(2.352222), 0.0)
        assertEquals(-0.13, OpenMeteo.round2(-0.1278), 0.0)
        assertEquals(1.01, OpenMeteo.round2(1.005), 0.0)
        val url = OpenMeteo.forecastUrl(48.856613, -122.419418)
        assertTrue(url, url.contains("latitude=48.86&longitude=-122.42&"))
        // Jamais plus de 2 décimales, même en locale française (virgule décimale).
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            assertTrue(OpenMeteo.forecastUrl(45.764043, 4.835659).contains("latitude=45.76&longitude=4.84&"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
        assertTrue(OpenMeteo.geocodingUrl("Saint-Étienne").contains("name=Saint-%C3%89tienne&count=5&language=fr"))
    }

    @Test
    fun mapsWmoCodes() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.of(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherKind.of(2))
        assertEquals(WeatherKind.FOG, WeatherKind.of(48))
        assertEquals(WeatherKind.DRIZZLE, WeatherKind.of(53))
        assertEquals(WeatherKind.RAIN, WeatherKind.of(81))
        assertEquals(WeatherKind.STORM, WeatherKind.of(96))
        assertEquals("−3°", formatTemperature(-3.4))
        assertEquals("12°", formatTemperature(11.6))
    }
}
