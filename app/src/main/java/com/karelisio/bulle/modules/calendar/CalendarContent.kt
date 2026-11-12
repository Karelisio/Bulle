package com.karelisio.bulle.modules.calendar

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.LiveContent
import com.karelisio.bulle.modules.live.LiveFormat
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.modules.live.ui.rememberWallClock
import java.util.Date

/** Heure locale « 14:30 » (12 ou 24 h selon le téléphone). */
internal fun formatTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

internal fun timeRange(context: Context, event: CalendarEvent): String =
    formatTime(context, event.begin) + " – " + formatTime(context, event.end)

/** Rappel d'un événement imminent : alerte qui s'étend toute seule, compte à rebours qui défile. */
data class CalendarReminderContent(
    val event: CalendarEvent,
    override val postedAt: Long,
) : IslandContent() {
    override val key = keyOf(event)
    override val moduleId = CalendarModule.ID
    override val priority = Priority.ALERT
    override val autoExpandMs: Long = 8_000L
    override val accent: Color = LiveContent.accentOf(event.color)
    override val compactSlotWidth: Dp = 60.dp
    override val compactSubtitle: String? = event.title.takeIf { it.isNotEmpty() }

    @Composable
    override fun Leading() = Icon(Icons.Rounded.Event, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))

    @Composable
    override fun Trailing() {
        val now = rememberWallClock()
        RollingText(
            LiveFormat.clock((event.begin - now).coerceAtLeast(0)),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            color = accent,
            upward = false,
        )
    }

    @Composable
    override fun Minimal() = Icon(Icons.Rounded.Event, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))

    @Composable
    override fun Expanded(host: IslandHost) {
        val context = LocalContext.current
        val now = rememberWallClock()
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventBadge(accent, Icons.Rounded.Event)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val minutes = CalendarPlanner.minutesUntil(event.begin, now)
                    Text(
                        stringResource(R.string.calendar_starts_in, minutes) + " · " + timeRange(context, event),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        event.title.ifEmpty { stringResource(R.string.calendar_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.location?.let { place ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                place,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                RollingText(
                    LiveFormat.clock((event.begin - now).coerceAtLeast(0)),
                    style = MaterialTheme.typography.headlineSmall.merge(CompactDigits),
                    color = accent,
                    upward = false,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                event.location?.let { place ->
                    PillButton(stringResource(R.string.calendar_directions), Icons.Rounded.Directions, accent) {
                        if (CalendarIntents.openDirections(host.context, place)) host.collapse()
                    }
                }
                PillButton(stringResource(R.string.calendar_open), Icons.Rounded.OpenInNew, accent) {
                    if (CalendarIntents.openEvent(host.context, event.eventId)) host.collapse()
                }
            }
        }
    }

    /** Toucher la carte ouvre l'événement dans l'agenda. */
    override fun onExpandedTap(host: IslandHost): Boolean {
        if (!CalendarIntents.openEvent(host.context, event.eventId)) return false
        host.collapse()
        return true
    }

    companion object {
        fun keyOf(event: CalendarEvent) = "calendar:reminder:${event.instanceId}:${event.begin}"
    }
}

/** Événement en cours : contenu discret avec le temps restant. */
data class CalendarOngoingContent(
    val event: CalendarEvent,
    override val postedAt: Long,
) : IslandContent() {
    override val key = keyOf(event)
    override val moduleId = CalendarModule.ID
    override val priority = Priority.LIVE
    override val accent: Color = LiveContent.accentOf(event.color)
    override val compactSlotWidth: Dp = 56.dp
    override val compactSubtitle: String? = event.title.takeIf { it.isNotEmpty() }

    @Composable
    override fun Leading() = Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))

    @Composable
    override fun Trailing() {
        val now = rememberWallClock()
        Text(
            stringResource(R.string.calendar_minutes_short, CalendarPlanner.minutesUntil(event.end, now)),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            color = accent,
            maxLines = 1,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val context = LocalContext.current
        val now = rememberWallClock()
        val total = (event.end - event.begin).coerceAtLeast(1)
        val fraction = ((now - event.begin).toFloat() / total).coerceIn(0f, 1f)
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventBadge(accent, Icons.Rounded.EventAvailable)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.calendar_ends_at, formatTime(context, event.end)),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Text(
                        event.title.ifEmpty { stringResource(R.string.calendar_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.calendar_minutes_short, CalendarPlanner.minutesUntil(event.end, now)),
                    style = MaterialTheme.typography.titleMedium.merge(CompactDigits),
                    color = accent,
                )
            }
            Spacer(Modifier.height(14.dp))
            TimeBar(fraction, accent)
        }
    }

    override fun onExpandedTap(host: IslandHost): Boolean {
        if (!CalendarIntents.openEvent(host.context, event.eventId)) return false
        host.collapse()
        return true
    }

    companion object {
        fun keyOf(event: CalendarEvent) = "calendar:ongoing:${event.instanceId}:${event.begin}"
    }
}

@Composable
private fun EventBadge(accent: Color, icon: ImageVector) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp)) }
}

/** Barre de temps écoulé : la fraction animée au ressort n'est lue qu'au dessin. */
@Composable
private fun TimeBar(fraction: Float, accent: Color) {
    val animated = animateFloatAsState(fraction, spring(stiffness = 120f), label = "elapsed")
    Box(
        Modifier.fillMaxWidth().height(6.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(accent.copy(alpha = 0.2f), cornerRadius = r)
            drawRoundRect(accent, size = Size(size.width * animated.value, size.height), cornerRadius = r)
        },
    )
}

@Composable
private fun PillButton(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    FilledTonalButton(
        onClick = onClick,
        interactionSource = source,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.2f), contentColor = Color.White),
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
