package com.karelisio.bulle.modules.weather

/** Pluie attendue prochainement. */
data class RainSoon(
    /** Début estimé de la pluie (epoch ms). */
    val startsAt: Long,
    /** Minutes avant le début (0 = imminente). */
    val minutes: Int,
    /** Cumul maximal prévu sur un quart d'heure dans l'horizon (mm). */
    val peakMm: Double,
)

/** Détection « pluie dans N min » (fonctions pures). */
object RainForecast {
    const val QUARTER_MS = 15 * 60_000L
    const val HORIZON_MS = 2 * 60 * 60_000L

    /** Il pleut déjà : précipitations actuelles au-dessus du seuil, ou temps « mouillé ». */
    fun rainingNow(f: Forecast, thresholdMm: Double): Boolean = f.precipitationNowMm >= thresholdMm || f.kind.wet

    /**
     * Premier quart d'heure à venir dont les précipitations atteignent [thresholdMm], s'il ne pleut pas
     * déjà. Une valeur Open-Meteo couvre le quart d'heure qui se termine à son horodatage.
     */
    fun rainSoon(f: Forecast, now: Long, thresholdMm: Double, horizonMs: Long = HORIZON_MS): RainSoon? {
        if (rainingNow(f, thresholdMm)) return null
        val upcoming = f.quarters.filter { it.endsAt > now && it.endsAt - QUARTER_MS <= now + horizonMs }
        val first = upcoming.firstOrNull { it.precipitationMm >= thresholdMm } ?: return null
        val startsAt = (first.endsAt - QUARTER_MS).coerceAtLeast(now)
        return RainSoon(
            startsAt = startsAt,
            minutes = ((startsAt - now) / 60_000L).toInt(),
            peakMm = upcoming.maxOf { it.precipitationMm },
        )
    }
}

/**
 * Une seule alerte par épisode de pluie : l'épisode commence à la première détection et ne se
 * termine que lorsque plus aucune pluie n'est prévue ni en cours.
 */
class RainEpisodes {
    private var inEpisode = false

    /** Retourne la pluie à annoncer si un nouvel épisode commence, sinon null. */
    fun update(soon: RainSoon?, rainingNow: Boolean): RainSoon? {
        if (soon == null) {
            if (!rainingNow) inEpisode = false
            return null
        }
        if (inEpisode) return null
        inEpisode = true
        return soon
    }
}
