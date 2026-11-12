package com.karelisio.bulle.modules.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.weather.ui.WeatherGlyph
import kotlin.math.roundToInt

/** Priorité très basse : sous la musique, affichée seulement quand l'île n'a rien d'autre. */
val IdleWeatherPriority = Priority(50)

/** « 12° » (arrondi, signe moins typographique). */
fun formatTemperature(celsius: Double): String {
    val t = celsius.roundToInt()
    return (if (t < 0) "−${-t}" else "$t") + "°"
}

/** Alerte ponctuelle « Pluie dans N min » (une fois par épisode). */
data class WeatherRainContent(
    val rain: RainSoon,
    val forecast: Forecast,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "weather:rain:${rain.startsAt / RainForecast.QUARTER_MS}"
    override val moduleId = WeatherModule.ID
    override val priority = Priority.ALERT
    override val timeoutMs: Long = 15_000L
    override val autoExpandMs: Long = 5_000L
    override val accent: Color = RainAccent
    override val compactSlotWidth: Dp = 60.dp

    @Composable
    override fun Leading() = WeatherGlyph(WeatherKind.RAIN, isDay = forecast.isDay, size = 24.dp)

    @Composable
    override fun Trailing() = Text(
        stringResource(R.string.weather_minutes_short, rain.minutes),
        style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
        color = accent,
        maxLines = 1,
    )

    @Composable
    override fun Expanded(host: IslandHost) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { WeatherGlyph(WeatherKind.RAIN, isDay = forecast.isDay, size = 32.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.weather_module_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        if (rain.minutes <= 0) {
                            stringResource(R.string.weather_rain_imminent)
                        } else {
                            stringResource(R.string.weather_rain_in, rain.minutes)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.weather_rain_peak, String.format(java.util.Locale.getDefault(), "%.1f", rain.peakMm)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            RainBars(forecast, accent)
        }
    }

    companion object {
        val RainAccent = Color(0xFF7CC4FF)
    }
}

/** Histogramme des précipitations des prochains quarts d'heure. */
@Composable
private fun RainBars(forecast: Forecast, accent: Color) {
    val now = System.currentTimeMillis()
    val quarters = forecast.quarters.filter { it.endsAt > now }.take(8)
    if (quarters.isEmpty()) return
    val max = quarters.maxOf { it.precipitationMm }.coerceAtLeast(0.5)
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (quarters.size - 1)) / quarters.size
        quarters.forEachIndexed { i, q ->
            val h = (q.precipitationMm / max).toFloat().coerceIn(0.06f, 1f) * size.height
            drawRoundRect(
                accent.copy(alpha = if (q.precipitationMm > 0) 0.9f else 0.25f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
}

/** Température au repos : icône + température en compact quand rien d'autre n'est affiché. */
data class WeatherIdleContent(
    val forecast: Forecast,
    val place: String?,
) : IslandContent() {
    override val key = "weather:idle"
    override val moduleId = WeatherModule.ID
    override val priority = IdleWeatherPriority
    override val canBeSecondary = false
    override val compactSlotWidth: Dp = 44.dp
    override val accent: Color = Color(0xFFFFD25E)
    override val postedAt: Long = 0L

    @Composable
    override fun Leading() = WeatherGlyph(forecast.kind, forecast.isDay, size = 22.dp, description = stringResource(forecast.kind.labelRes))

    @Composable
    override fun Trailing() = Text(
        forecast.temperature?.let(::formatTemperature).orEmpty(),
        style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
        color = Color.White,
        maxLines = 1,
    )

    @Composable
    override fun Expanded(host: IslandHost) {
        val context = LocalContext.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(forecast.kind, forecast.isDay, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(place, stringResource(R.string.weather_updated_at, android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(forecast.fetchedAt))))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(forecast.kind.labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                forecast.temperature?.let(::formatTemperature).orEmpty(),
                style = MaterialTheme.typography.headlineMedium.merge(CompactDigits),
                color = Color.White,
            )
        }
    }
}
