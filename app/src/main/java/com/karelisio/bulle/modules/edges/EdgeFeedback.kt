package com.karelisio.bulle.modules.edges

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.island.ui.LIQUID_AGSL
import com.karelisio.bulle.island.ui.LiquidSupported
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.roundToInt

/** État du doigt sur un bord, lu par le retour visuel (fenêtre non touchable le long du bord). */
class EdgeTouchState {
    /** Un doigt est posé (la goutte se forme). */
    var touching by mutableStateOf(false)
    /** Position du doigt dans la fenêtre de retour (pixels). */
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    /** Étirement vers l'intérieur (pixels, ≥ 0). */
    var stretch by mutableFloatStateOf(0f)
    /** Curseur en cours (null : pas de jauge). */
    var slider by mutableStateOf<EdgeSlider?>(null)
    var value by mutableFloatStateOf(0f)
    /** Incrémenté à chaque geste validé (petit éclat de la goutte). */
    var confirms by mutableIntStateOf(0)
}

/**
 * Retour visuel d'un bord : goutte liquide sous le doigt (même shader AGSL que la scission de
 * l'île) qui s'étire avec le geste, et jauge Material You du curseur qui suit le doigt.
 * Sans animations : pas de goutte, jauge seule (sans ressort).
 */
@Composable
fun EdgeFeedback(state: EdgeTouchState, side: EdgeSide, liquid: Boolean) {
    val reduce = rememberReduceMotion()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    val appear by animateFloatAsState(
        if (state.touching) 1f else 0f,
        if (reduce) tween(0) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "drop",
    )
    val pop = remember { Animatable(0f) }
    LaunchedEffect(state.confirms) {
        if (state.confirms == 0 || reduce) return@LaunchedEffect
        pop.snapTo(1f)
        pop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        if (liquid && !reduce && appear > 0.01f) {
            val edgeX = if (side == EdgeSide.LEFT) 0f else w
            val dir = if (side == EdgeSide.LEFT) 1f else -1f
            val color = scheme.primary.copy(alpha = 0.92f)
            if (Build.VERSION.SDK_INT >= 33 && LiquidSupported) {
                LiquidDrop(edgeX, dir, state.y, state.stretch, appear, pop.value, color, density)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val r = (12f + 6f * pop.value) * density * appear
                    val cx = edgeX + dir * (10f * density + state.stretch * 0.6f)
                    drawCircle(color, r, Offset(cx, state.y))
                }
            }
        }
        state.slider?.let { kind -> Gauge(state, kind, side, w, constraints.maxHeight.toFloat(), reduce) }
    }
}

@RequiresApi(33)
@Composable
private fun LiquidDrop(edgeX: Float, dir: Float, y: Float, stretch: Float, appear: Float, pop: Float, color: Color, density: Float) {
    val shader = remember { RuntimeShader(LIQUID_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        val pull = stretch.coerceAtMost(72f * density)
        // Le bord : un mince ruban collé au bord de l'écran, qui s'allonge sous le doigt.
        shader.setFloatUniform("mainCenter", edgeX, y)
        shader.setFloatUniform("mainHalf", 3f * density * appear, (26f * density + pull * 0.25f) * appear)
        shader.setFloatUniform("mainCorner", 3f * density)
        shader.setFloatUniform("topCorner", 3f * density)
        // La goutte : se détache vers l'intérieur et grossit avec l'étirement.
        val r = (11f + 4f * pop) * density * appear + pull * 0.08f
        shader.setFloatUniform("secCenter", edgeX + dir * (12f * density + pull * 0.65f), y)
        shader.setFloatUniform("secRadius", r)
        shader.setFloatUniform("smoothK", 16f * density)
        shader.setFloatUniform("alpha", appear.coerceIn(0f, 1f))
        shader.setFloatUniform("cornerExp", 2f)
        shader.setFloatUniform("fillColor", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("outlineColor", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("outlineWidth", 0f)
        drawRect(brush)
    }
}

/** Jauge du curseur : pilule verticale près du doigt, remplie depuis le bas, valeur animée. */
@Composable
private fun Gauge(state: EdgeTouchState, kind: EdgeSlider, side: EdgeSide, w: Float, h: Float, reduce: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val shown = if (reduce) state.value else animateFloatAsState(state.value, spring(dampingRatio = 0.9f, stiffness = 900f), label = "gauge").value
    val gaugeH = with(density) { 168.dp.toPx() }
    val gaugeW = with(density) { 44.dp.toPx() }
    val inset = with(density) { 30.dp.toPx() }
    val top = (state.y - gaugeH / 2).coerceIn(0f, (h - gaugeH).coerceAtLeast(0f))
    val left = if (side == EdgeSide.LEFT) inset else w - inset - gaugeW
    Column(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(44.dp, 168.dp + 28.dp)
            .graphicsLayer { alpha = 0.98f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp, 168.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.92f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val fill = size.height * shown.coerceIn(0f, 1f)
                drawRoundRect(
                    scheme.primary,
                    topLeft = Offset(0f, size.height - fill),
                    size = Size(size.width, fill),
                    cornerRadius = CornerRadius(size.width / 2, size.width / 2),
                )
            }
            Icon(
                if (kind == EdgeSlider.BRIGHTNESS) Icons.Rounded.LightMode else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = if (shown > 0.12f) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp).size(20.dp),
            )
        }
        Text(
            "${(shown * 100).roundToInt()}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.92f))
                .padding(horizontal = 6.dp),
        )
    }
}

/** Étirement vers l'intérieur à partir du déplacement horizontal (logique pure). */
internal fun inwardStretch(side: EdgeSide, dx: Float): Float =
    (if (side == EdgeSide.LEFT) dx else -dx).coerceAtLeast(0f).let { if (it.isNaN()) 0f else it }
