package com.karelisio.bulle.modules.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.focus.ui.FocusColors
import com.karelisio.bulle.modules.focus.ui.FocusControls
import com.karelisio.bulle.modules.focus.ui.FocusRing
import com.karelisio.bulle.modules.focus.ui.focusIcon
import com.karelisio.bulle.modules.focus.ui.phaseLabel
import com.karelisio.bulle.modules.focus.ui.rememberRemaining
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.RollingText

/** Minuteur en cours : compte à rebours compact entouré d'un anneau de progression. */
data class FocusContent(
    val state: FocusState,
    override val postedAt: Long,
) : IslandContent() {
    override val key = KEY
    override val moduleId = FocusModule.ID
    override val priority = Priority.LIVE
    override val accent: Color = FocusColors.of(state.phase)
    override val compactSlotWidth: Dp = if (state.durationMs >= 3_600_000L) 76.dp else 58.dp

    @Composable
    override fun Leading() = RingGlyph(26.dp, 12.dp)

    @Composable
    override fun Minimal() = RingGlyph(24.dp, 12.dp)

    @Composable
    private fun RingGlyph(size: Dp, icon: Dp) {
        val remaining = rememberRemaining(state)
        FocusRing(progress(remaining), accent, size = size, stroke = 2.5.dp) {
            Icon(focusIcon(state.phase), contentDescription = null, tint = accent, modifier = Modifier.size(icon))
        }
    }

    private fun progress(remaining: Long): Float =
        if (state.durationMs <= 0) 1f else (1f - remaining.toFloat() / state.durationMs).coerceIn(0f, 1f)

    @Composable
    override fun Trailing() {
        val remaining = rememberRemaining(state)
        RollingText(
            FocusTimer.format(remaining),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            // En pause, les chiffres pâlissent.
            color = if (state.running) accent else accent.copy(alpha = 0.55f),
            upward = false,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val remaining = rememberRemaining(state)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusRing(progress(remaining), accent, size = 96.dp, stroke = 6.dp) {
                Icon(focusIcon(state.phase), contentDescription = null, tint = accent.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    phaseLabel(state) + if (state.running) "" else " · " + stringResource(R.string.focus_paused),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RollingText(
                    FocusTimer.format(remaining),
                    style = MaterialTheme.typography.displaySmall.merge(CompactDigits),
                    color = Color.White,
                    upward = false,
                )
                FocusControls(state, accent, onStop = host::collapse)
            }
        }
    }

    companion object {
        const val KEY = "focus:timer"
    }
}

/** Fin de phase : alerte ponctuelle (« Pause ! », « Au travail ! », « Terminé »). */
data class FocusAlertContent(val alert: FocusAlert) : IslandContent() {
    override val key = "focus:alert:" + alert.at
    override val moduleId = FocusModule.ID
    override val priority = Priority.ALERT
    override val postedAt: Long = alert.at
    override val timeoutMs: Long = 5_000L
    override val expandable: Boolean = false
    override val canBeSecondary: Boolean = false
    override val accent: Color = alert.next?.let { FocusColors.of(it.phase) } ?: FocusColors.of(alert.finished)
    override val compactSlotWidth: Dp = 84.dp

    @Composable
    override fun Leading() {
        val next = alert.next
        Icon(
            if (next == null) Icons.Rounded.CheckCircle else focusIcon(next.phase),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
    }

    @Composable
    override fun Trailing() {
        val next = alert.next
        val text = when {
            next == null && alert.finished == FocusPhase.TIMER -> stringResource(R.string.focus_alert_timer_done)
            next == null -> stringResource(R.string.focus_alert_cycle_done)
            next.isBreak -> stringResource(R.string.focus_alert_break)
            else -> stringResource(R.string.focus_alert_work)
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
