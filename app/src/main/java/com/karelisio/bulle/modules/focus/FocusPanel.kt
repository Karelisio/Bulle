package com.karelisio.bulle.modules.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.focus.ui.FocusColors
import com.karelisio.bulle.modules.focus.ui.FocusControls
import com.karelisio.bulle.modules.focus.ui.FocusRing
import com.karelisio.bulle.modules.focus.ui.phaseLabel
import com.karelisio.bulle.modules.focus.ui.rememberRemaining
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.ui.components.rememberReduceMotion

/**
 * Panneau « Minuteur » de l'île : choix rapides (5, 10, 15, 25 min, Pomodoro), puis commandes
 * du minuteur en cours. À ouvrir avec `host.openPanel(FocusPanel)` (geste, Deck…).
 */
object FocusPanel : IslandPanel {
    override val id = "focus"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content(host: IslandHost) {
        val context = host.context
        val data by remember(context) { Focus.loaded(context) }.collectAsStateWithLifecycle()
        val reduceMotion = rememberReduceMotion()
        val state = data.state

        Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = FocusColors.Timer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.focus_panel_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            AnimatedContent(
                targetState = state == null,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snapSpec()) togetherWith fadeOut(snapSpec())
                    } else {
                        (fadeIn() + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f)) togetherWith fadeOut()
                    }
                },
                label = "focusPanel",
            ) { idle ->
                val current = data.state
                if (idle || current == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusPreset.quick.forEach { preset ->
                            FilledTonalButton(
                                onClick = {
                                    Focus.start(context, preset)
                                    host.closePanel()
                                },
                                colors = if (preset is FocusPreset.Pomodoro) {
                                    ButtonDefaults.filledTonalButtonColors(containerColor = FocusColors.Work.copy(alpha = 0.25f), contentColor = Color.White)
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                            ) {
                                Text(presetLabel(preset, data.settings))
                            }
                        }
                    }
                } else {
                    RunningSummary(current, onStop = {})
                }
            }
        }
    }

    @Composable
    private fun RunningSummary(state: FocusState, onStop: () -> Unit) {
        val accent = FocusColors.of(state.phase)
        val remaining = rememberRemaining(state)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusRing(
                if (state.durationMs <= 0) 1f else 1f - remaining.toFloat() / state.durationMs,
                accent,
                size = 64.dp,
                stroke = 5.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(phaseLabel(state), style = MaterialTheme.typography.labelLarge, color = accent)
                RollingText(
                    FocusTimer.format(remaining),
                    style = MaterialTheme.typography.headlineMedium.merge(CompactDigits),
                    color = Color.White,
                    upward = false,
                )
            }
            FocusControls(state, accent, onStop = onStop)
        }
    }

    private fun <T> snapSpec() = androidx.compose.animation.core.snap<T>()
}

/** Libellé d'un choix rapide : « 5 min », « Pomodoro 25/5 ×4 ». */
@Composable
fun presetLabel(preset: FocusPreset, settings: FocusSettings): String = when (preset) {
    is FocusPreset.Minutes -> stringResource(R.string.focus_preset_minutes, preset.minutes)
    FocusPreset.Pomodoro -> {
        val s = settings.sanitized()
        stringResource(R.string.focus_preset_pomodoro, s.workMinutes, s.shortBreakMinutes, s.cycles)
    }
}
