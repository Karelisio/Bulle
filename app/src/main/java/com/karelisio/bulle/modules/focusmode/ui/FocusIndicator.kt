package com.karelisio.bulle.modules.focusmode.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.focusmode.FocusState
import com.karelisio.bulle.ui.components.rememberReduceMotion

/**
 * Petite lune discrète affichée dans l'île pendant le mode focus (si l'option est active).
 * Apparition au ressort, lue dans graphicsLayer ; aucune animation infinie.
 */
@Composable
fun FocusIndicator(state: FocusState, modifier: Modifier = Modifier, size: Dp = 10.dp, tint: Color = Color.White) {
    val shown = state.active && state.settings.showIndicator
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "focusIndicator",
    )
    Icon(
        Icons.Rounded.Bedtime,
        contentDescription = null,
        tint = tint.copy(alpha = 0.75f),
        modifier = modifier.size(size).graphicsLayer {
            alpha = progress.coerceIn(0f, 1f)
            scaleX = 0.5f + 0.5f * progress
            scaleY = 0.5f + 0.5f * progress
        },
    )
}
