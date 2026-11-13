package com.karelisio.bulle.modules.rules

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Une position mesurée (degrés, précision en mètres, 0 = inconnue). */
data class GeoFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float = 0f)

/**
 * Géorepérage léger, sans Play services : distance haversine et hystérésis d'entrée/sortie.
 * On entre dans un cercle quand la distance passe sous le rayon, on n'en sort qu'au-delà du
 * rayon + une marge : une position qui oscille au bord du cercle ne fait pas clignoter la règle.
 */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Marge de sortie minimale (m). */
    const val MIN_EXIT_MARGIN_M = 50.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun exitMargin(radius: Double): Double = max(MIN_EXIT_MARGIN_M, radius * 0.25)

    /**
     * Nouvel état « dedans » pour un cercle. Une position trop imprécise (précision plus large
     * que le diamètre du cercle) ne change rien.
     */
    fun inside(wasInside: Boolean, distance: Double, radius: Double, accuracy: Float = 0f): Boolean {
        if (accuracy > 0 && accuracy > radius * 2 + exitMargin(radius)) return wasInside
        return if (wasInside) distance <= radius + exitMargin(radius) else distance <= radius
    }

    /** Met à jour l'ensemble des lieux « dedans » à partir d'une position. */
    fun update(previous: Set<String>, places: List<RuleTrigger.Place>, fix: GeoFix): Set<String> =
        places.filter { p ->
            val d = distanceMeters(fix.latitude, fix.longitude, p.latitude, p.longitude)
            inside(p.id in previous, d, p.radiusMeters.toDouble(), fix.accuracyMeters)
        }.mapTo(HashSet()) { it.id }
}
