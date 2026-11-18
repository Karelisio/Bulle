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

/** Fine jauge verticale le long de la caméra (paysage) ; apparaît et disparaît en fondu. */
@Composable
internal fun LandscapeGauge(level: Float?, g: com.karelisio.bulle.island.IslandGeometry, accent: Color) {
    var last by remember { mutableStateOf(0f) }
    if (level != null) last = level
    val alpha by animateFloatAsState(if (level != null) 1f else 0f, tween(if (level != null) 120 else 400), label = "gauge")
    val fill by animateFloatAsState(last.coerceIn(0f, 1f), spring(dampingRatio = 0.8f), label = "gaugeFill")
    if (alpha <= 0.01f) return
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val geo = IslandLayout.landscapeGauge(
            cameraCenterX = g.centerX.toFloat(), cameraHalfThickness = g.width / 2f, cameraCenterY = g.centerY.toFloat(),
            cameraLeft = g.cameraLeft, density = density.density, screenHeight = size.height,
        )
        val r = androidx.compose.ui.geometry.CornerRadius(geo.width / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.55f * alpha), Offset(geo.left - 2f, geo.top - 2f), androidx.compose.ui.geometry.Size(geo.width + 4f, geo.height + 4f), androidx.compose.ui.geometry.CornerRadius(geo.width / 2f + 2f))
        drawRoundRect(accent.copy(alpha = 0.3f * alpha), Offset(geo.left, geo.top), androidx.compose.ui.geometry.Size(geo.width, geo.height), r)
        val h = geo.height * fill
        if (h > 0f) {
            drawRoundRect(accent.copy(alpha = alpha), Offset(geo.left, geo.top + geo.height - h), androidx.compose.ui.geometry.Size(geo.width, h.coerceAtLeast(geo.width)), r)
        }
    }
}

@Composable
internal fun SecondaryBubble(
    content: IslandContent?,
    fill: Color,
    offsetX: () -> Float,
    radius: () -> Float,
    fullRadius: Float,
    onTap: () -> Unit,
) {
    if (content == null) return
    val density = LocalDensity.current
    val d = with(density) { (fullRadius * 2).toDp() }
    Box(
        Modifier
            .size(d)
            .graphicsLayer {
                translationX = offsetX()
                val p = (radius() / fullRadius).coerceIn(0f, 1.2f)
                scaleX = p
                scaleY = p
                // Le contenu n'apparaît qu'une fois la goutte détachée.
                alpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                shape = CircleShape
                clip = true
            }
            .then(if (LiquidSupported) Modifier else Modifier.background(fill))
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .secondarySemantics(content, onTap),
        contentAlignment = Alignment.Center,
    ) {
        content.Minimal()
    }
}
