package com.karelisio.bulle.modules.music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Pochette (ou note de musique) à la taille du conteneur. */
@Composable
internal fun ArtworkImage(image: ImageBitmap?, accent: Color, modifier: Modifier = Modifier) {
    Crossfade(targetState = image, modifier = modifier, animationSpec = tween(420), label = "art") { img ->
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.62f))
            }
        }
    }
}

/**
 * Pochette compacte (à gauche de la caméra) qui « respire » doucement pendant la lecture :
 * une pulsation d'échelle lente, qui se pose au repos quand la musique s'arrête.
 */
@Composable
internal fun CompactArtwork(image: ImageBitmap?, playing: Boolean, accent: Color) {
    val reduceMotion = rememberReduceMotion()
    val breath = remember { Animatable(1f) }
    val dim = remember { Animatable(if (playing) 1f else 0.7f) }
    LaunchedEffect(playing, reduceMotion) {
        dim.animateTo(if (playing) 1f else 0.7f, tween(380))
    }
    LaunchedEffect(playing, reduceMotion) {
        if (playing && !reduceMotion) {
            while (isActive) {
                breath.animateTo(1.08f, tween(820, easing = FastOutSlowInEasing))
                breath.animateTo(0.96f, tween(980, easing = FastOutSlowInEasing))
            }
        } else {
            breath.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    ArtworkImage(
        image,
        accent,
        Modifier
            .fillMaxHeight(0.7f)
            .heightIn(max = 26.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                scaleX = breath.value
                scaleY = breath.value
                alpha = dim.value
                shape = RoundedCornerShape(30)
                clip = true
            },
    )
}

/** Pochette ronde de la bulle secondaire, qui tourne lentement comme un vinyle pendant la lecture. */
@Composable
internal fun MinimalArtwork(image: ImageBitmap?, playing: Boolean, accent: Color) {
    val reduceMotion = rememberReduceMotion()
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, reduceMotion) {
        if (!playing || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                angle.floatValue = (angle.floatValue + (now - last) / 1e9f * 30f) % 360f // un tour en 12 s
                last = now
            }
        }
    }
    ArtworkImage(
        image,
        accent,
        Modifier
            .fillMaxHeight(0.78f)
            .heightIn(max = 30.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                rotationZ = angle.floatValue
                shape = CircleShape
                clip = true
            },
    )
}

/** Hauteurs de repos des barres (égaliseur figé en pause). */
private val RestHeights = floatArrayOf(0.38f, 0.66f, 0.5f, 0.3f)

/** Fréquences (rad/s) et phases des deux sinusoïdes de chaque barre : un mouvement organique, jamais périodique à l'œil. */
private val Freq1 = floatArrayOf(7.1f, 9.3f, 6.2f, 8.4f)
private val Freq2 = floatArrayOf(3.3f, 2.1f, 4.4f, 2.9f)
private val Phase = floatArrayOf(0f, 1.7f, 3.1f, 4.6f)

/** Hauteur relative (0..1) de la barre [i] au temps [t] (secondes). */
internal fun barHeight(i: Int, t: Float, level: Float): Float {
    val a = 0.5f + 0.5f * sin(t * Freq1[i] + Phase[i])
    val b = 0.55f + 0.45f * sin(t * Freq2[i] + Phase[i] * 0.7f)
    val dynamic = 0.18f + 0.82f * a * b
    return RestHeights[i] + (dynamic - RestHeights[i]) * level
}

/**
 * Égaliseur compact (à droite de la caméra) : 4 barres arrondies à la couleur de la pochette.
 * Le temps est lu dans la lambda de dessin : seule la phase de dessin est invalidée à chaque image.
 */
@Composable
internal fun CompactEqualizer(playing: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val color by animateColorAsState(accent, tween(600), label = "eqColor")
    val time = remember { mutableFloatStateOf(0f) }
    val level = remember { Animatable(if (playing && !reduceMotion) 1f else 0f) }
    LaunchedEffect(playing, reduceMotion) {
        level.animateTo(if (playing && !reduceMotion) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(playing, reduceMotion) {
        if (!playing || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                time.floatValue += (now - last) / 1e9f
                last = now
            }
        }
    }
    Box(
        modifier
            .fillMaxHeight(0.46f)
            .heightIn(max = 16.dp)
            .aspectRatio(1.15f, matchHeightConstraintsFirst = true)
            .drawBehind {
                val n = RestHeights.size
                val gap = size.width * 0.12f
                val barW = (size.width - gap * (n - 1)) / n
                val top = lerp(color, Color.White, 0.35f)
                for (i in 0 until n) {
                    val h = (barHeight(i, time.floatValue, level.value) * size.height).coerceAtLeast(barW)
                    val x = i * (barW + gap)
                    val y = (size.height - h) / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(top, color), startY = y, endY = y + h),
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f),
                    )
                }
            },
    )
}
