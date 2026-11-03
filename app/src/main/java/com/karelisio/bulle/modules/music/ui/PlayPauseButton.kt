package com.karelisio.bulle.modules.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Bouton lecture/pause M3 Expressive : le fond passe d'un carré arrondi (pause) à un « cookie »
 * à 9 lobes qui tourne lentement pendant la lecture, et l'icône se déforme du triangle aux deux barres.
 */
@Composable
internal fun PlayPauseButton(
    playing: Boolean,
    accent: Color,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 64.dp,
) {
    val reduceMotion = rememberReduceMotion()
    val morph = remember { Morph(MaterialShapes.Square.normalized(), MaterialShapes.Cookie9Sided.normalized()) }
    val progress = remember { Animatable(if (playing) 1f else 0f) }
    val rotation = remember { mutableFloatStateOf(0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val bg by animateColorAsState(accent, tween(600), label = "bg")

    LaunchedEffect(playing) {
        progress.animateTo(if (playing) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    LaunchedEffect(playing, reduceMotion) {
        if (playing && !reduceMotion) {
            var last = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    rotation.floatValue = (rotation.floatValue + (now - last) / 1e9f * 24f) % 360f
                    last = now
                }
            }
        } else {
            // Se repose sur un angle où le carré est droit.
            val start = rotation.floatValue
            val rest = (start / 90f).roundToInt() * 90f
            animate(start, rest, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { v, _ ->
                rotation.floatValue = v
            }
        }
    }

    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Fond : forme morphée et tournante.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation.floatValue
                    shape = MorphShape(morph, progress.value)
                    clip = true
                }
                .background(bg),
        )
        // Icône : triangle ↔ barres, dessinée à la main pour pouvoir la déformer.
        Box(
            Modifier
                .size(size * 0.5f)
                .drawBehind { drawPlayPause(progress.value.coerceIn(0f, 1f), Color.Black) },
        )
    }
}

/** Quadrilatères (repère 24×24) : lecture = triangle coupé en deux, pause = deux barres. */
private val PlayQuads = arrayOf(
    floatArrayOf(8f, 5f, 13.5f, 8.5f, 13.5f, 15.5f, 8f, 19f),
    floatArrayOf(13.5f, 8.5f, 19f, 12f, 19f, 12f, 13.5f, 15.5f),
)
private val PauseQuads = arrayOf(
    floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f),
    floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f),
)

/** Icône lecture (t = 0) → pause (t = 1) par interpolation des sommets. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlayPause(t: Float, color: Color) {
    val path = Path()
    for (q in 0..1) {
        val a = PlayQuads[q]
        val b = PauseQuads[q]
        for (p in 0 until 4) {
            val x = a[p * 2] + (b[p * 2] - a[p * 2]) * t
            val y = a[p * 2 + 1] + (b[p * 2 + 1] - a[p * 2 + 1]) * t
            if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    val s = size.minDimension / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawPath(path, color)
        // Contour arrondi : adoucit les angles de l'icône.
        drawPath(path, color, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
    }
}
