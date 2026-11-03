package com.karelisio.bulle.modules.live.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.live.LiveProgress
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * Heure murale qui avance à chaque seconde pile (pour les chronos et durées d'appel).
 * Simple attente, pas une animation : s'arrête quand le composable quitte l'écran.
 */
@Composable
fun rememberWallClock(enabled: Boolean = true): Long {
    val now by produceState(System.currentTimeMillis(), enabled) {
        if (!enabled) return@produceState
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            delay(1000 - t % 1000 + 5)
        }
    }
    return now
}

/**
 * Texte dont chaque caractère « roule » verticalement quand il change (chiffres d'un compteur).
 * Les positions sont indexées depuis la droite : passer de 9:59 à 10:00 ne décale rien.
 * @param upward sens du défilement (vers le haut pour un compteur qui monte).
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    upward: Boolean = true,
) {
    val reduceMotion = rememberReduceMotion()
    val tabular = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(modifier.clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            key(n - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (reduceMotion || !initialState.isDigit() || !targetState.isDigit()) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val dir = if (upward) 1 else -1
                            (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { dir * it } + fadeIn(tween(160)))
                                .togetherWith(slideOutVertically(tween(220)) { -dir * it } + fadeOut(tween(140)))
                                .using(SizeTransform(clip = false))
                        }
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = tabular, color = color, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/**
 * Anneau de progression compact. La valeur animée (ressort) n'est lue qu'au dessin ;
 * en mode indéterminé, un arc tourne (coupé si les animations sont supprimées).
 */
@Composable
fun ProgressRing(
    progress: LiveProgress,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    stroke: Dp = 3.dp,
) {
    val reduceMotion = rememberReduceMotion()
    val sweep = remember { Animatable(progress.fraction) }
    LaunchedEffect(progress.fraction, reduceMotion) {
        sweep.animateTo(progress.fraction, if (reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
    }
    val spinning = progress.indeterminate && !reduceMotion
    val rotation = if (spinning) {
        val t = rememberInfiniteTransition(label = "ring")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "rot")
    } else {
        null
    }
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        if (progress.indeterminate) {
            val start = (rotation?.value ?: 0f) - 90f
            drawArc(color, start, 100f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        } else {
            drawArc(color, -90f, 360f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/**
 * Barre de progression de la vue étendue : piste arrondie, remplissage au ressort,
 * segments colorés et points d'étape (ProgressStyle) quand l'appli en fournit.
 */
@Composable
fun LiveProgressBar(progress: LiveProgress, color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val fill = remember { Animatable(0f) }
    LaunchedEffect(progress.fraction, reduceMotion) {
        fill.animateTo(progress.fraction, if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    val shimmer = if (progress.indeterminate && !reduceMotion) {
        val t = rememberInfiniteTransition(label = "bar")
        t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    } else {
        null
    }
    Box(modifier.fillMaxWidth().height(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val h = 6.dp.toPx()
            val top = (size.height - h) / 2
            val r = CornerRadius(h / 2)
            val total = progress.max.coerceAtLeast(1).toFloat()
            val gap = 3.dp.toPx()
            // Piste : segments colorés atténués, ou piste unique.
            if (progress.segments.isNotEmpty()) {
                var x = 0f
                val sum = progress.segments.sumOf { it.length }.coerceAtLeast(1).toFloat()
                progress.segments.forEachIndexed { i, seg ->
                    val w = size.width * seg.length / sum
                    val segColor = if (seg.color != 0) Color(seg.color) else color
                    val last = i == progress.segments.lastIndex
                    val drawW = (w - if (last) 0f else gap).coerceAtLeast(1f)
                    drawRoundRect(segColor.copy(alpha = 0.28f), Offset(x, top), Size(drawW, h), r)
                    // Partie remplie du segment.
                    val filledEnd = size.width * fill.value
                    val segFill = (filledEnd - x).coerceIn(0f, drawW)
                    if (segFill > 0f) drawRoundRect(segColor, Offset(x, top), Size(segFill, h), r)
                    x += w
                }
            } else {
                drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), r)
                if (progress.indeterminate) {
                    val p = shimmer?.value ?: 0.3f
                    val bw = size.width * 0.35f
                    val start = (size.width * p - bw / 2).coerceIn(0f, size.width)
                    val end = (size.width * p + bw / 2).coerceIn(0f, size.width)
                    if (end > start) drawRoundRect(color, Offset(start, top), Size(end - start, h), r)
                } else {
                    val fw = size.width * fill.value
                    if (fw > 0f) drawRoundRect(color, Offset(0f, top), Size(fw.coerceAtLeast(h), h), r)
                }
            }
            // Points d'étape.
            progress.points.forEach { pt ->
                val cx = size.width * (pt.position / total).coerceIn(0f, 1f)
                val reached = pt.position / total <= fill.value
                val c = if (pt.color != 0) Color(pt.color) else color
                drawCircle(Color.Black, radius = 5.dp.toPx(), center = Offset(cx, size.height / 2))
                drawCircle(if (reached) c else c.copy(alpha = 0.5f), radius = 3.5.dp.toPx(), center = Offset(cx, size.height / 2))
            }
        }
    }
}

/** Petit point qui « respire » : signale une activité en cours sans chiffre. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val pulse = if (!reduceMotion) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    } else {
        null
    }
    Canvas(modifier.size(14.dp)) {
        val p = pulse?.value ?: 0.5f
        drawCircle(color.copy(alpha = 0.25f * (1f - p) + 0.08f), radius = size.minDimension / 2 * (0.6f + 0.4f * p))
        drawCircle(color, radius = size.minDimension / 2 * 0.42f)
    }
}

/** Style des chiffres compacts. */
val CompactDigits = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
