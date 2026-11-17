package com.karelisio.bulle.island.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.karelisio.bulle.core.settings.OpenDirection
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawWithContent
import com.karelisio.bulle.core.island.Priority
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandUiState
import com.karelisio.bulle.island.WindowRequest
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.core.style.EntryAnimation
import com.karelisio.bulle.core.style.EntryAnimations
import com.karelisio.bulle.ui.stylepacks.EntryAnimated
import com.karelisio.bulle.ui.stylepacks.EntryTracker
import com.karelisio.bulle.ui.theme.bulleColorScheme
import com.karelisio.bulle.core.a11y.SystemAccessibility
import com.karelisio.bulle.core.a11y.rememberSystemHighContrast
import com.karelisio.bulle.core.settings.AccessibilitySettings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ligne compacte. Centrée : [Leading] | caméra | [Trailing]. Vers la gauche : [Leading][Trailing] | caméra.
 * Vers la droite : caméra | [Leading][Trailing]. [subtitle] : petite seconde ligne sous [Trailing].
 */
@Composable
internal fun CompactRow(
    content: IslandContent,
    baseWidthPx: Int,
    heightPx: Int,
    density: androidx.compose.ui.unit.Density,
    scale: Float,
    direction: OpenDirection = OpenDirection.CENTER,
    marquee: Boolean = false,
    subtitle: String? = null,
) {
    with(density) {
        // Défilement des textes trop longs : l'enfant est mesuré sans limite puis défile dans l'emplacement.
        val scroll = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200) else Modifier
        val camera = @Composable { Spacer(Modifier.width(baseWidthPx.toDp())) }
        val leading = @Composable {
            // Les emplacements suivent la taille choisie : le contenu est dessiné à l'échelle.
            Box(Modifier.width(content.compactSlotWidth * scale), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .requiredWidth(content.compactSlotWidth)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { MarqueeSlot(scroll, marquee) { content.Leading() } }
            }
        }
        val trailing = @Composable {
            Box(Modifier.width(content.compactSlotWidth * scale), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .requiredWidth(content.compactSlotWidth)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (subtitle == null) {
                        MarqueeSlot(scroll, marquee) { content.Trailing() }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            MarqueeSlot(scroll, marquee) { content.Trailing() }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                                modifier = scroll,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.height(heightPx.toDp()), verticalAlignment = Alignment.CenterVertically) {
            when (direction) {
                OpenDirection.CENTER -> { leading(); camera(); trailing() }
                OpenDirection.LEFT -> { leading(); trailing(); camera() }
                OpenDirection.RIGHT -> { camera(); leading(); trailing() }
            }
        }
    }
}

/**
 * Échelle de police appliquée au contenu compact : celle du système, plafonnée pour qu'une ligne
 * tienne dans la hauteur de l'île (au-delà, défilement ou points de suspension sur la largeur).
 * Échelle ≤ 1,3 sur une île de hauteur courante : inchangée.
 */
internal fun compactFontScale(heightPx: Int, density: androidx.compose.ui.unit.Density): Float {
    val heightDp = heightPx / density.density.coerceAtLeast(0.1f)
    val max = (heightDp / 26f).coerceAtLeast(1f)
    return density.fontScale.coerceIn(0.5f, max)
}

@Composable
internal fun CompactFontScale(heightPx: Int, density: androidx.compose.ui.unit.Density, content: @Composable () -> Unit) {
    val scale = compactFontScale(heightPx, density)
    if (scale == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, scale), content = content)
    }
}

/** Enveloppe de défilement : sans défilement, le contenu est émis tel quel (mise en page inchangée). */
@Composable
internal fun MarqueeSlot(scroll: Modifier, enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) Box(scroll) { content() } else content()
}
