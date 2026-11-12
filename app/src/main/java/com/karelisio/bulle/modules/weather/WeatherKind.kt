package com.karelisio.bulle.modules.weather

import androidx.annotation.StringRes
import com.karelisio.bulle.R

/** Familles de temps (codes WMO d'Open-Meteo regroupés pour l'icône et le libellé). */
enum class WeatherKind(@StringRes val labelRes: Int, val wet: Boolean) {
    CLEAR(R.string.weather_kind_clear, false),
    PARTLY_CLOUDY(R.string.weather_kind_partly, false),
    CLOUDY(R.string.weather_kind_cloudy, false),
    FOG(R.string.weather_kind_fog, false),
    DRIZZLE(R.string.weather_kind_drizzle, true),
    RAIN(R.string.weather_kind_rain, true),
    SNOW(R.string.weather_kind_snow, true),
    STORM(R.string.weather_kind_storm, true),
    ;

    companion object {
        /** Code WMO → famille (https://open-meteo.com/en/docs, « WMO Weather interpretation codes »). */
        fun of(code: Int?): WeatherKind = when (code) {
            null, 0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}
