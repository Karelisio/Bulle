package com.karelisio.bulle.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.island.ui.IslandLayout
import com.karelisio.bulle.island.ui.addSmoothRoundRect
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.island.ui.IslandMotion
import com.karelisio.bulle.island.ui.islandFill
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.ui.theme.bulleColorScheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aperçu vivant de l'île sur un faux fond d'écran : elle passe en boucle du repos
 * au compact puis à la carte étendue, avec le style, les ressorts et le sens d'ouverture choisis.
 */
@Composable
fun IslandPreview(
    style: StyleSettings,
    motion: MotionSettings,
    modifier: Modifier = Modifier,
    height: Dp = 210.dp,
    openDirection: OpenDirection = OpenDirection.CENTER,
) {
    val reduce = rememberReduceMotion()
    val m = remember(motion, reduce) { IslandMotion(motion, reduce) }
    val dark = bulleColorScheme(dark = true)
    val scheme = MaterialTheme.colorScheme
    val fill = islandFill(style, dark)
    val outline = dark.primary
    val accent = scheme.tertiary

    // Phases : 0 repos, 1 compact, 2 étendu.
    var phase by remember { mutableIntStateOf(1) }
    LaunchedEffect(reduce) {
        if (reduce) { phase = 1; return@LaunchedEffect }
        while (true) {
            phase = 1; delay(1700)
            phase = 2; delay(2200)
            phase = 1; delay(1200)
            phase = 0; delay(900)
        }
    }
    val s = style.size.coerceIn(0.4f, 2f)
    val baseW = 46f * s
    val baseH = 26f * s
    val targetW = when (phase) { 0 -> baseW; 1 -> baseW + 2 * 40f; else -> 280f }
    val targetH = when (phase) { 2 -> 112f; else -> baseH }
    val targetC = when (phase) { 2 -> 30f; else -> baseH / 2 }
    // Sens d'ouverture : même calcul que l'île réelle, sur la largeur du canevas (290 dp).
    val targetDx = IslandLayout.openingOffsetX(
        openDirection, baseW, targetW, cameraX = PreviewWidth / 2, screenWidth = PreviewWidth,
        margin = 0f, clampToScreen = openDirection != OpenDirection.CENTER || phase == 2,
    )
    val w = remember { Animatable(targetW) }
    val h = remember { Animatable(targetH) }
    val c = remember { Animatable(targetC) }
    val dx = remember { Animatable(targetDx) }
    LaunchedEffect(targetW, targetH, targetC, targetDx, m) {
        val spec = if (targetW * targetH >= w.value * h.value) m.grow() else m.shrink()
        coroutineScope {
            launch { w.animateTo(targetW, spec) }
            launch { h.animateTo(targetH, spec) }
            launch { c.animateTo(targetC, spec) }
            launch { dx.animateTo(targetDx, spec) }
        }
    }
    val smoothing by animateFloatAsState(style.cornerSmoothing.coerceIn(0f, 1f), spring(dampingRatio = 0.8f), label = "smoothing")
    val contentAlpha by animateFloatAsState(if (phase == 0) 0f else 1f, label = "content")
    val topFrac by animateFloatAsState(style.shape.topRadius, spring(dampingRatio = 0.7f), label = "shape")
    val bottomFrac by animateFloatAsState(style.shape.bottomRadius, spring(dampingRatio = 0.7f), label = "shape")

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondaryContainer)),
            ),
    ) {
        // Faux fond d'écran : quelques formes douces.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.12f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.15f, size.height * 1.05f))
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.4f, center = Offset(size.width * 0.9f, size.height * 0.2f))
        }
        // Barre d'état factice.
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("12:30", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.SignalCellularAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Icon(Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Icon(Icons.Rounded.Battery5Bar, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        // L'île.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .then(if (style.shadow) Modifier.shadow(10.dp, RoundedCornerShape(50)) else Modifier),
        ) {
            Canvas(Modifier.width(PreviewWidth.dp).height(130.dp)) {
                val ww = w.value.dp.toPx()
                val hh = h.value.dp.toPx()
                val cc = c.value.dp.toPx().coerceAtMost(hh / 2)
                val left = (size.width - ww) / 2 + dx.value.dp.toPx()
                val cam = size.width / 2
                val path = Path().apply {
                    addSmoothRoundRect(left, 0f, left + ww, hh, cc * topFrac, cc * bottomFrac, smoothing)
                }
                drawPath(path, fill)
                if (style.outline) drawPath(path, outline, style = Stroke(style.outlineWidthDp.dp.toPx()))
                // Caméra.
                drawCircle(Color(0xFF0A0A0A), radius = 5.5.dp.toPx(), center = Offset(size.width / 2, baseH.dp.toPx() / 2))
                drawCircle(Color(0xFF1C2333), radius = 2.dp.toPx(), center = Offset(size.width / 2 + 1.dp.toPx(), baseH.dp.toPx() / 2 - 1.dp.toPx()))
                // Contenu factice.
                val a = contentAlpha
                if (a > 0f) {
                    val cy = baseH.dp.toPx() / 2
                    // Pochette (gauche) et égaliseur (droite), placés de part et d'autre de la caméra
                    // ou ensemble du côté où l'île s'ouvre.
                    val halfCam = baseW.dp.toPx() / 2
                    val coverX = if (openDirection == OpenDirection.RIGHT) cam + halfCam + 9.dp.toPx() else left + 9.dp.toPx()
                    val barsX = if (openDirection == OpenDirection.LEFT) cam - halfCam - 28.dp.toPx() else left + ww - 28.dp.toPx()
                    drawRoundRect(
                        Brush.linearGradient(listOf(accent, Color(0xFFFFB86B))),
                        topLeft = Offset(coverX, cy - 8.dp.toPx()),
                        size = Size(16.dp.toPx(), 16.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        alpha = a,
                    )
                    val bars = listOf(0.5f, 0.9f, 0.65f)
                    bars.forEachIndexed { i, f ->
                        val bh = 14.dp.toPx() * f
                        drawRoundRect(
                            accent,
                            topLeft = Offset(barsX + i * 6.dp.toPx(), cy - bh / 2),
                            size = Size(3.dp.toPx(), bh),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                            alpha = a,
                        )
                    }
                    // Carte étendue : lignes de texte factices.
                    val ea = ((hh - baseH.dp.toPx() * 1.5f) / (70.dp.toPx())).coerceIn(0f, 1f) * a
                    if (ea > 0f) translate(left + 18.dp.toPx(), baseH.dp.toPx() + 14.dp.toPx()) {
                        drawRoundRect(Brush.linearGradient(listOf(accent, Color(0xFFFFB86B))), size = Size(46.dp.toPx(), 46.dp.toPx()), cornerRadius = CornerRadius(10.dp.toPx()), alpha = ea)
                        drawRoundRect(Color.White, topLeft = Offset(58.dp.toPx(), 6.dp.toPx()), size = Size(120.dp.toPx(), 9.dp.toPx()), cornerRadius = CornerRadius(5.dp.toPx()), alpha = ea * 0.9f)
                        drawRoundRect(Color.White, topLeft = Offset(58.dp.toPx(), 24.dp.toPx()), size = Size(80.dp.toPx(), 7.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()), alpha = ea * 0.5f)
                        drawRoundRect(accent, topLeft = Offset(0f, 58.dp.toPx()), size = Size((ww - 36.dp.toPx()) * 0.4f, 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), alpha = ea)
                        drawRoundRect(Color.White, topLeft = Offset((ww - 36.dp.toPx()) * 0.4f, 58.dp.toPx()), size = Size((ww - 36.dp.toPx()) * 0.6f, 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), alpha = ea * 0.25f)
                    }
                }
            }
        }
    }
}

/** Largeur du canevas de l'aperçu (dp). */
private const val PreviewWidth = 290f
