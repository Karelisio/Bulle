package com.karelisio.bulle.modules.external.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.external.BreathPattern
import com.karelisio.bulle.modules.external.BreathPhase
import com.karelisio.bulle.modules.external.Breathing
import com.karelisio.bulle.modules.external.ExternalContent
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * Panneau « Respiration » de l'île : cercle qui grossit à l'inspiration et rétrécit à l'expiration
 * (4-7-8 ou respiration carrée 4-4-4-4), consigne « Inspire / Bloque / Expire », 1 à 2 minutes.
 * Ouvert par le modèle `breathing` ou par l'action « Envie de fumer » (Exit).
 */
data class BreathingPanel(
    val pattern: BreathPattern = BreathPattern.RELAX_478,
    val cycles: Int = pattern.defaultCycles,
) : IslandPanel {
    override val id = "breathing"

    @Composable
    override fun Content(host: IslandHost) = BreathingExercise(pattern, cycles, onClose = { host.closePanel() })
}

@Composable
fun BreathingExercise(initial: BreathPattern, initialCycles: Int, onClose: () -> Unit) {
    val reduce = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val accent = ExternalContent.BreathTeal
    var pattern by remember { mutableStateOf(initial) }
    var cycles by remember { mutableIntStateOf(initialCycles) }
    var run by remember { mutableIntStateOf(0) }
    val elapsed = remember(pattern, run) { mutableLongStateOf(0L) }

    // Horloge de l'exercice : images d'animation, ou 4 rafraîchissements par seconde si les animations sont coupées.
    LaunchedEffect(pattern, run, reduce) {
        val total = Breathing.totalMs(pattern, cycles)
        val start = withFrameMillis { it } - elapsed.longValue
        while (elapsed.longValue < total) {
            if (reduce) delay(250)
            withFrameMillis { t -> elapsed.longValue = (t - start).coerceAtMost(total) }
        }
    }

    // Lectures dérivées : la composition ne se refait qu'au changement de phase / de seconde.
    val phase by remember(pattern, run) { derivedStateOf { Breathing.stateAt(pattern, elapsed.longValue, cycles).phase } }
    val secondsLeft by remember(pattern, run) { derivedStateOf { Breathing.stateAt(pattern, elapsed.longValue, cycles).secondsLeft } }
    val cycle by remember(pattern, run) { derivedStateOf { Breathing.stateAt(pattern, elapsed.longValue, cycles).cycle } }
    val finished by remember(pattern, run) { derivedStateOf { Breathing.stateAt(pattern, elapsed.longValue, cycles).finished } }

    // Petit tic haptique à chaque changement de consigne, confirmation à la fin.
    LaunchedEffect(phase, finished) {
        if (elapsed.longValue > 0) haptic.performHapticFeedback(if (finished) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick)
    }

    Column(
        Modifier.fillMaxWidth().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Air, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.ext_breath_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            BreathPattern.entries.forEach { p ->
                FilterChip(
                    selected = p == pattern,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        pattern = p
                        cycles = p.defaultCycles
                        run++
                    },
                    label = { Text(stringResource(if (p == BreathPattern.BOX) R.string.ext_breath_box else R.string.ext_breath_478)) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White.copy(alpha = 0.7f),
                        selectedContainerColor = accent.copy(alpha = 0.25f),
                        selectedLabelColor = Color.White,
                    ),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            val brush = remember { Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.3f))) }
            Canvas(Modifier.size(150.dp)) {
                // Valeurs lues au dessin : pas de recomposition à chaque image.
                val st = Breathing.stateAt(pattern, elapsed.longValue, cycles)
                val r = size.minDimension / 2
                val stroke = 4.dp.toPx()
                val scale = if (reduce) 0.8f else Breathing.scaleOf(st)
                drawCircle(accent.copy(alpha = 0.10f), radius = r - stroke)
                drawCircle(accent.copy(alpha = 0.22f), radius = (r - stroke) * Breathing.MIN_SCALE, style = Stroke(1.dp.toPx()))
                drawCircle(brush, radius = (r - stroke * 2) * scale)
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = 360f * st.totalProgress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = if (finished) null else phase,
                    transitionSpec = {
                        if (reduce) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f)) togetherWith fadeOut(tween(150))
                        }
                    },
                    label = "phase",
                ) { p ->
                    Text(
                        stringResource(phaseLabel(p)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                if (!finished) {
                    Text(
                        secondsLeft.toString(),
                        style = MaterialTheme.typography.titleMedium.merge(CompactDigits),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Text(
            if (finished) stringResource(R.string.ext_breath_done_text) else stringResource(R.string.ext_breath_cycle, cycle, cycles),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (finished) {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); run++ }) {
                    Text(stringResource(R.string.ext_breath_again), color = accent)
                }
            }
            FilledTonalButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onClose() },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White),
            ) {
                Text(stringResource(if (finished) R.string.ext_breath_close else R.string.ext_breath_stop))
            }
        }
    }
}

private fun phaseLabel(phase: BreathPhase?): Int = when (phase) {
    BreathPhase.INHALE -> R.string.ext_breath_inhale
    BreathPhase.HOLD, BreathPhase.HOLD_OUT -> R.string.ext_breath_hold
    BreathPhase.EXHALE -> R.string.ext_breath_exhale
    null -> R.string.ext_breath_done
}
