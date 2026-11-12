package com.karelisio.bulle.modules.focus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.focus.Focus
import com.karelisio.bulle.modules.focus.FocusPhase
import com.karelisio.bulle.modules.focus.FocusState
import com.karelisio.bulle.modules.focus.FocusTimer
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Couleurs des phases : tomate pour le travail, vert pour les pauses, ambre pour un minuteur simple. */
object FocusColors {
    val Work = Color(0xFFFF7A5C)
    val Break = Color(0xFF63D69A)
    val Timer = Color(0xFFFFB648)

    fun of(phase: FocusPhase): Color = when (phase) {
        FocusPhase.TIMER -> Timer
        FocusPhase.WORK -> Work
        FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> Break
    }
}

fun focusIcon(phase: FocusPhase): ImageVector = when (phase) {
    FocusPhase.TIMER -> Icons.Rounded.Timer
    FocusPhase.WORK -> Icons.Rounded.SelfImprovement
    FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> Icons.Rounded.Coffee
}

/**
 * Temps restant, rafraîchi au passage de chaque seconde affichée (calé sur la fin prévue,
 * pas sur l'horloge). Simple attente, pas une animation : s'arrête hors écran ou en pause.
 */
@Composable
fun rememberRemaining(state: FocusState): Long {
    val remaining by produceState(FocusTimer.remaining(state, System.currentTimeMillis()), state) {
        while (true) {
            val r = FocusTimer.remaining(state, System.currentTimeMillis())
            value = r
            if (!state.running || r <= 0) break
            // Prochaine bascule de l'affichage (arrondi à la seconde supérieure).
            delay((r - 1) % 1000 + 1 + 5)
        }
    }
    return remaining
}

/**
 * Anneau de progression autour de [content]. La valeur animée n'est lue qu'au dessin :
 * l'anneau avance d'un mouvement continu (interpolation linéaire d'une seconde à l'autre).
 */
@Composable
fun FocusRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    stroke: Dp = 2.5.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    val sweep = remember { Animatable(progress) }
    LaunchedEffect(progress, reduceMotion) {
        val spec: androidx.compose.animation.core.AnimationSpec<Float> = when {
            reduceMotion -> snap<Float>()
            // Grand saut (nouvelle phase, reprise) : ressort ; sinon avance linéaire continue.
            abs(progress - sweep.value) > 0.1f -> spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            else -> tween<Float>(1000, easing = LinearEasing)
        }
        sweep.animateTo(progress, spec)
    }
    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = stroke.toPx()
                val inset = w / 2
                val arc = Size(this.size.width - w, this.size.height - w)
                drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                // L'anneau se vide au fil du temps (temps restant), comme un sablier.
                val left = 1f - sweep.value
                if (left > 0f) {
                    drawArc(color, -90f, 360f * left, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Libellé de la phase : « Minuteur », « Travail · 2/4 », « Pause · 2/4 », « Pause longue ». */
@Composable
fun phaseLabel(state: FocusState): String = when (state.phase) {
    FocusPhase.TIMER -> stringResource(R.string.focus_phase_timer)
    FocusPhase.WORK -> stringResource(R.string.focus_phase_work, state.cycle, state.cycles)
    FocusPhase.SHORT_BREAK -> stringResource(R.string.focus_phase_break, state.cycle, state.cycles)
    FocusPhase.LONG_BREAK -> stringResource(R.string.focus_phase_long_break)
}

/** Boutons pause / reprise, phase suivante (Pomodoro) et arrêt. */
@Composable
fun FocusControls(state: FocusState, accent: Color, modifier: Modifier = Modifier, onStop: () -> Unit = {}) {
    val context = LocalContext.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = { if (state.running) Focus.pause(context) else Focus.resume(context) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.Black),
        ) {
            Icon(
                if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.running) R.string.focus_pause else R.string.focus_resume),
            )
        }
        if (state.pomodoro) {
            FilledTonalIconButton(onClick = { Focus.skip(context) }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.focus_skip))
            }
        }
        FilledTonalIconButton(onClick = { Focus.stop(context); onStop() }) {
            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.focus_stop))
        }
    }
}
