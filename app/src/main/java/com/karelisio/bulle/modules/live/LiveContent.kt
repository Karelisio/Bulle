package com.karelisio.bulle.modules.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.LiveProgressBar
import com.karelisio.bulle.modules.live.ui.ProgressRing
import com.karelisio.bulle.modules.live.ui.PulseDot
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.modules.live.ui.rememberWallClock

/** Icônes déjà converties pour l'affichage (petite icône de l'appli, grande icône). */
data class LiveVisuals(
    val appLabel: String?,
    val smallIcon: ImageBitmap?,
    val largeIcon: ImageBitmap?,
)

/** Une activité en cours affichée par l'île. */
data class LiveContent(
    val activity: LiveActivity,
    val visuals: LiveVisuals,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "live:" + activity.key
    override val moduleId = LiveModule.ID
    override val priority = Priority.LIVE

    /** Couleur d'accent : celle de la notification, éclaircie si trop sombre sur fond noir. */
    override val accent: Color = accentOf(activity.color)

    override val compactSlotWidth: Dp = when {
        activity.chrono != null -> if (hoursLikely()) 72.dp else 60.dp
        activity.shortText != null -> 64.dp
        activity.progress?.indeterminate == false -> 60.dp
        else -> 40.dp
    }

    private fun hoursLikely(): Boolean {
        val c = activity.chrono ?: return false
        return kotlin.math.abs(c.valueAt(System.currentTimeMillis())) >= 3_600_000L - 60_000L
    }

    // ---------- Vue compacte ----------

    @Composable
    override fun Leading() = AppGlyph(22.dp)

    @Composable
    override fun Trailing() {
        val chrono = activity.chrono
        val progress = activity.progress
        when {
            chrono != null -> {
                val now = rememberWallClock()
                RollingText(
                    LiveFormat.clock(chrono.valueAt(now)),
                    style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
                    color = accent,
                    upward = !chrono.countDown,
                )
            }
            activity.shortText != null -> Text(
                activity.shortText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            progress != null && !progress.indeterminate -> Row(verticalAlignment = Alignment.CenterVertically) {
                RollingText(
                    "${LiveFormat.percent(progress.fraction)}",
                    style = MaterialTheme.typography.labelMedium.merge(CompactDigits),
                    color = accent,
                )
                Spacer(Modifier.width(4.dp))
                ProgressRing(progress, accent, size = 16.dp, stroke = 2.5.dp)
            }
            progress != null -> ProgressRing(progress, accent)
            else -> PulseDot(accent)
        }
    }

    @Composable
    override fun Minimal() {
        val progress = activity.progress
        if (progress != null && activity.chrono == null) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(progress, accent, size = 24.dp, stroke = 2.5.dp)
                AppGlyph(12.dp)
            }
        } else {
            AppGlyph(20.dp)
        }
    }

    // ---------- Vue étendue ----------

    @Composable
    override fun Expanded(host: IslandHost) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { AppGlyph(28.dp, preferLarge = true) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val header = listOfNotNull(visuals.appLabel, activity.subText).distinct().joinToString(" · ")
                    if (header.isNotEmpty()) {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    activity.title?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    activity.text?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                activity.chrono?.let { chrono ->
                    Spacer(Modifier.width(10.dp))
                    val now = rememberWallClock()
                    RollingText(
                        LiveFormat.clock(chrono.valueAt(now)),
                        style = MaterialTheme.typography.headlineSmall.merge(CompactDigits),
                        color = accent,
                        upward = !chrono.countDown,
                    )
                }
            }
            activity.progress?.let { progress ->
                Spacer(Modifier.height(14.dp))
                LiveProgressBar(progress, accent)
                if (!progress.indeterminate) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        RollingText(
                            "${LiveFormat.percent(progress.fraction)} %",
                            style = MaterialTheme.typography.labelMedium.merge(CompactDigits),
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = activity.actions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    activity.actions.forEach { action ->
                        ActionButton(action) {
                            NotificationIntents.send(host.context, action.intent)
                        }
                    }
                }
            }
        }
    }

    /** Toucher la carte ouvre l'appli (contentIntent de la notification). */
    override fun onExpandedTap(host: IslandHost): Boolean {
        if (!NotificationIntents.send(host.context, activity.contentIntent)) return false
        host.collapse()
        return true
    }

    // ---------- Éléments ----------

    @Composable
    private fun AppGlyph(size: Dp, preferLarge: Boolean = false) {
        val large = visuals.largeIcon
        val small = visuals.smallIcon
        when {
            large != null && (preferLarge || small == null || activity.kind == LiveKind.NAVIGATION) -> Image(
                large,
                contentDescription = visuals.appLabel,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)),
            )
            small != null -> Image(
                small,
                contentDescription = visuals.appLabel,
                colorFilter = ColorFilter.tint(accent),
                modifier = Modifier.size(size),
            )
            else -> Icon(Icons.Rounded.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(size))
        }
    }

    @Composable
    private fun ActionButton(action: LiveAction, onClick: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
        FilledTonalButton(
            onClick = onClick,
            interactionSource = source,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accent.copy(alpha = 0.2f),
                contentColor = Color.White,
            ),
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    companion object {
        private val DefaultAccent = Color(0xFF8FD3FF)

        fun accentOf(color: Int): Color {
            if (color == 0) return DefaultAccent
            val c = Color(color).copy(alpha = 1f)
            // Sur le fond noir de l'île, une couleur trop sombre est illisible : on l'éclaircit.
            return if (c.luminance() < 0.18f) androidx.compose.ui.graphics.lerp(c, Color.White, 0.45f) else c
        }
    }
}
