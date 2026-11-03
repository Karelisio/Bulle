package com.karelisio.bulle.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/** Ressort « bulle » : souple, avec un léger dépassement. */
private fun <T> bubbleSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/**
 * Fait défiler [count] étapes en boucle tant que la page est visible ;
 * figé sur [still] quand les animations sont coupées.
 */
@Composable
private fun rememberCycle(active: Boolean, count: Int, periodMs: Long, still: Int): Int {
    val reduce = rememberReduceMotion()
    var stage by remember { mutableIntStateOf(if (reduce) still else 0) }
    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        while (true) {
            delay(periodMs)
            stage = (stage + 1) % count
        }
    }
    return stage
}

/** Contour de téléphone, l'île posée en haut. */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width = 230.dp, height = 280.dp)
            .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(scheme.surfaceContainerHigh)
            .border(3.dp, scheme.outlineVariant, RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp, bottomStart = 12.dp, bottomEnd = 12.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Faux contenu d'écran, en filigrane.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(1f, 0.7f, 0.85f).forEach { w ->
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = 0.08f)),
                )
            }
        }
        Box(Modifier.padding(top = 12.dp)) { content() }
    }
}

/** Petites barres d'égaliseur, dessinées en boîtes. */
@Composable
private fun Bars(color: Color, height: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.5f, 1f, 0.7f, 0.9f).forEach { f ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height * f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Page 1 : la bulle passe de pilule à compacte puis à carte, au ressort. */
@Composable
fun MorphingIslandIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val stage = rememberCycle(active, count = 3, periodMs = 1700, still = 1)
    val accent = MaterialTheme.colorScheme.primary
    val width by animateDpAsState(listOf(72.dp, 150.dp, 206.dp)[stage], bubbleSpring(), label = "w")
    val height by animateDpAsState(listOf(24.dp, 28.dp, 104.dp)[stage], bubbleSpring(), label = "h")
    val corner by animateDpAsState(listOf(12.dp, 14.dp, 30.dp)[stage], bubbleSpring(), label = "r")
    val compactAlpha by animateFloatAsState(if (stage == 1) 1f else 0f, tween(220), label = "ca")
    val cardAlpha by animateFloatAsState(if (stage == 2) 1f else 0f, tween(260, delayMillis = 120), label = "ka")
    Box(modifier, contentAlignment = Alignment.Center) {
        PhoneFrame {
            Box(
                Modifier
                    .size(width, height)
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black),
            ) {
                // Vue compacte : pochette à gauche, égaliseur à droite.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 6.dp)
                        .graphicsLayer { alpha = compactAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(accent))
                    Bars(accent, 14.dp)
                }
                // Carte : pochette, titres, barre de progression.
                Column(
                    Modifier
                        .padding(14.dp)
                        .graphicsLayer { alpha = cardAlpha },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent))
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(96.dp).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                            Box(Modifier.width(64.dp).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(0.42f).fillMaxHeight().clip(CircleShape).background(accent))
                    }
                }
            }
        }
    }
}

/** Page 2 : la bulle se scinde en deux puis se referme. */
@Composable
fun SplitIslandIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val stage = rememberCycle(active, count = 2, periodMs = 1900, still = 1)
    val split = stage == 1
    val scheme = MaterialTheme.colorScheme
    val mainWidth by animateDpAsState(if (split) 118.dp else 150.dp, bubbleSpring(), label = "mw")
    val satelliteX by animateDpAsState(
        if (split) 92.dp else 50.dp,
        spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessLow),
        label = "sx",
    )
    val satelliteScale by animateFloatAsState(if (split) 1f else 0.4f, bubbleSpring(), label = "ss")
    Box(modifier, contentAlignment = Alignment.Center) {
        PhoneFrame {
            Box(contentAlignment = Alignment.Center) {
                // Bulle secondaire : glisse hors de la principale.
                Box(
                    Modifier
                        .offset(x = satelliteX)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = satelliteScale
                            scaleY = satelliteScale
                        }
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.tertiary))
                }
                // Bulle principale : un minuteur.
                Row(
                    Modifier
                        .offset(x = if (split) (-14).dp else 0.dp)
                        .size(mainWidth, 28.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.primary))
                    Box(Modifier.width(34.dp).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
                }
            }
        }
    }
}

/** Page 3 : une forme expressive qui se métamorphose au ressort, un doigt au centre. */
@Composable
fun ShapeShiftIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val shapes = remember {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Pill,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.SoftBurst,
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        while (true) {
            delay(900)
            if (rotation.value >= 360f) rotation.snapTo(rotation.value - 360f)
            rotation.animateTo(rotation.value + 60f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        while (true) {
            delay(700)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow, visibilityThreshold = 0.002f))
            index = (index + 1) % shapes.size
            progress.snapTo(0f)
        }
    }
    val morph = remember(index) { Morph(shapes[index], shapes[(index + 1) % shapes.size]) }
    val scheme = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(190.dp)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(MorphShape(morph, progress.value.coerceIn(0f, 1f)))
                .background(scheme.primaryContainer),
        )
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie6Sided.toShape())
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
        }
    }
}
