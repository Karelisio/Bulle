package com.karelisio.bulle.modules.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RainForecastTest {
    private val q = RainForecast.QUARTER_MS
    private val now = 1_800_000_000_000L

    /** Quarts d'heure se terminant à now+15, now+30… avec ces cumuls. */
    private fun forecast(vararg mm: Double, nowMm: Double = 0.0, code: Int = 3) = Forecast(
        fetchedAt = now, latitude = 0.0, longitude = 0.0, temperature = 10.0, weatherCode = code,
        precipitationNowMm = nowMm,
        quarters = mm.mapIndexed { i, v -> Quarter(now + (i + 1) * q, v) },
    )

    @Test
    fun detectsRainInNMinutes() {
        val soon = RainForecast.rainSoon(forecast(0.0, 0.0, 0.4, 1.0), now, 0.2)
        assertNotNull(soon)
        soon!!
        // Le 3e quart d'heure couvre [now+30, now+45] : pluie dans 30 min.
        assertEquals(30, soon.minutes)
        assertEquals(1.0, soon.peakMm, 0.0)
        assertEquals(0, RainForecast.rainSoon(forecast(0.5), now, 0.2)!!.minutes)
    }

    @Test
    fun ignoresLightRainAndAlreadyRaining() {
        assertNull(RainForecast.rainSoon(forecast(0.1, 0.1), now, 0.2))
        assertNull(RainForecast.rainSoon(forecast(0.0, 1.0, nowMm = 0.4), now, 0.2))
        assertNull(RainForecast.rainSoon(forecast(0.0, 1.0, code = 61), now, 0.2))
    }

    @Test
    fun ignoresPastAndBeyondHorizon() {
        val f = Forecast(
            now, 0.0, 0.0, 10.0, 1,
            quarters = listOf(Quarter(now - q, 3.0), Quarter(now + 3 * 60 * 60_000L, 3.0)),
        )
        assertNull(RainForecast.rainSoon(f, now, 0.2))
    }

    @Test
    fun alertsOncePerEpisode() {
        val episodes = RainEpisodes()
        val soon = RainSoon(now + 10 * 60_000L, 10, 1.0)
        assertEquals(soon, episodes.update(soon, rainingNow = false))
        assertNull(episodes.update(soon.copy(minutes = 5), rainingNow = false))
        // Il pleut : l'épisode continue.
        assertNull(episodes.update(null, rainingNow = true))
        assertNull(episodes.update(soon, rainingNow = false))
        // Fin de l'épisode puis nouvelle pluie : nouvelle alerte.
        assertNull(episodes.update(null, rainingNow = false))
        assertEquals(soon, episodes.update(soon, rainingNow = false))
    }
}
