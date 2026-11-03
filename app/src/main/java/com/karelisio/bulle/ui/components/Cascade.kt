package com.karelisio.bulle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Apparition en cascade : chaque élément monte et apparaît avec un léger décalage. */
fun Modifier.cascade(index: Int, stepMs: Long = 35L): Modifier = composed {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index.coerceAtMost(12) * stepMs)
        progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 380f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 14.dp.toPx()
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
    }
}
