package com.karelisio.bulle.modules.countdown

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.ui.components.rememberReduceMotion
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin

/** Priorité « ambiante » : au-dessus de la météo au repos, sous la batterie et le réseau. */
val AmbientCountdownPriority = Priority(55)

/** Libellé compact d'un état (« J-12 », « 5 h », « 42 min », « Aujourd'hui »). */
@Composable
fun countdownLabel(status: CountdownStatus): String = when (status) {
    is CountdownStatus.Days -> stringResource(R.string.countdown_days_short, status.days)
    is CountdownStatus.Hours -> stringResource(R.string.countdown_hours_short, status.hours)
    is CountdownStatus.Minutes -> stringResource(R.string.countdown_minutes_short, status.minutes)
    CountdownStatus.Today -> stringResource(R.string.countdown_today)
}

/** Libellé long pour la vue étendue (« dans 12 jours »). */
@Composable
fun countdownLongLabel(status: CountdownStatus): String = when (status) {
    is CountdownStatus.Days -> pluralStringResource(R.plurals.countdown_in_days, status.days, status.days)
    is CountdownStatus.Hours -> pluralStringResource(R.plurals.countdown_in_hours, status.hours, status.hours)
    is CountdownStatus.Minutes -> pluralStringResource(R.plurals.countdown_in_minutes, status.minutes, status.minutes)
    CountdownStatus.Today -> stringResource(R.string.countdown_today)
}

/**
 * Prochain événement épinglé, discret au repos : émoji et « J-12 » ; vue étendue avec la liste
 * des prochains événements. Le module ne republie qu'aux changements d'affichage (minuit,
 * passage aux heures, minute suivante dans la dernière heure).
 */
data class CountdownContent(
    val pinned: CountdownOccurrence,
    val upcoming: List<CountdownOccurrence>,
) : IslandContent() {
    override val key = KEY
    override val moduleId = CountdownModule.ID
    override val priority = AmbientCountdownPriority
    override val accent: Color = Color(pinned.event.color)
    override val compactSlotWidth: Dp = if (pinned.status == CountdownStatus.Today) 92.dp else 56.dp
    override val compactSubtitle: String = pinned.event.name

    @Composable
    override fun Leading() = EventEmoji(pinned.event, festive = pinned.status == CountdownStatus.Today, size = 22.dp)

    @Composable
    override fun Trailing() {
        Text(
            countdownLabel(pinned.status),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) = CountdownDetails(pinned, upcoming)

    companion object {
        const val KEY = "countdown:idle"
    }
}

/** Rappel ponctuel la veille : « Demain » + nom de l'événement. */
data class CountdownReminderContent(
    val occurrence: CountdownOccurrence,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "countdown:reminder:" + occurrence.reminderKey
    override val moduleId = CountdownModule.ID
    override val priority = Priority.ALERT
    override val timeoutMs: Long = TIMEOUT_MS
    override val expandable: Boolean = false
    override val canBeSecondary: Boolean = false
    override val accent: Color = Color(occurrence.event.color)
    override val compactSlotWidth: Dp = 96.dp
    override val compactSubtitle: String = occurrence.event.name

    @Composable
    override fun Leading() = EventEmoji(occurrence.event, festive = false, size = 22.dp)

    @Composable
    override fun Trailing() {
        Text(
            stringResource(R.string.countdown_reminder_tomorrow, occurrence.event.name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    companion object {
        const val TIMEOUT_MS = 6_000L
    }
}

/** Émoji de l'événement, avec une courte salve de confettis le jour J (coupée si « réduire les animations »). */
@Composable
fun EventEmoji(event: CountdownEvent, festive: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (festive) ConfettiBurst(Color(event.color), Modifier.size(size * 1.6f))
        Text(event.emoji.ifBlank { CountdownEvent.DEFAULT_EMOJI }, fontSize = (size.value * 0.78f).sp, maxLines = 1)
    }
}

/** Salve de confettis unique (1,4 s) : progression lue dans le dessin, aucune recomposition par image. */
@Composable
fun ConfettiBurst(color: Color, modifier: Modifier = Modifier) {
    if (rememberReduceMotion()) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1_400, easing = LinearOutSlowInEasing)) }
    val palette = remember(color) { listOf(color, Color(0xFFFFD54F), Color(0xFF6AD7FF), Color(0xFFFF7A9A), Color.White) }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val piece = Size(radius * 0.16f, radius * 0.08f)
        for (i in 0 until PIECES) {
            val angle = (i * 360f / PIECES + i * 17f) * (Math.PI / 180f)
            val dist = radius * (0.35f + 0.65f * p) * (0.7f + (i % 3) * 0.15f)
            val fall = radius * 0.5f * p * p
            val x = center.x + (cos(angle) * dist).toFloat()
            val y = center.y + (sin(angle) * dist).toFloat() + fall
            drawRect(palette[i % palette.size].copy(alpha = 1f - p), topLeft = Offset(x - piece.width / 2, y - piece.height / 2), size = piece)
        }
    }
}

private const val PIECES = 14

/** Corps de la vue étendue : événement épinglé puis liste des prochains (réutilisé par l'aperçu des réglages). */
@Composable
fun CountdownDetails(pinned: CountdownOccurrence, upcoming: List<CountdownOccurrence>, modifier: Modifier = Modifier, onDark: Boolean = true) {
    val primary = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = primary.copy(alpha = 0.65f)
    val accent = Color(pinned.event.color)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { EventEmoji(pinned.event, festive = pinned.status == CountdownStatus.Today, size = 28.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pinned.event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(occurrenceDate(pinned), style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1)
            }
            Text(
                countdownLabel(pinned.status),
                style = MaterialTheme.typography.titleLarge.merge(CompactDigits),
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
        val others = upcoming.filter { it.event.id != pinned.event.id }.take(MAX_ROWS)
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.countdown_upcoming), style = MaterialTheme.typography.labelMedium, color = secondary)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { OccurrenceRow(it, primary, secondary) }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            ProgressBar(pinned.progress, accent, primary.copy(alpha = 0.12f))
        }
    }
}

/** Une ligne de la liste : émoji, nom, temps restant et barre de progression depuis la création. */
@Composable
fun OccurrenceRow(occurrence: CountdownOccurrence, primary: Color, secondary: Color, modifier: Modifier = Modifier) {
    val accent = Color(occurrence.event.color)
    val remaining = countdownLongLabel(occurrence.status)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = occurrence.event.name + ", " + remaining }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(occurrence.event.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(occurrence.event.name, style = MaterialTheme.typography.bodyMedium, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(remaining, style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(occurrence.progress, accent, primary.copy(alpha = 0.12f))
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color, track: Color) {
    Canvas(Modifier.fillMaxWidth().height(4.dp).graphicsLayer()) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (progress > 0f) drawRoundRect(color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/** Date lisible de l'occurrence (format court localisé, avec l'heure si choisie). */
@Composable
fun occurrenceDate(occurrence: CountdownOccurrence): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return remember(occurrence.target, locale) {
        val date = occurrence.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val time = CountdownMath.parseTime(occurrence.event.time)
        if (time == null) date else date + " · " + time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }
}

private const val MAX_ROWS = 4
