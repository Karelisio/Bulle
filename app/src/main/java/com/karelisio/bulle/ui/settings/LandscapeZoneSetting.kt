package com.karelisio.bulle.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.island.CutoutLocator
import kotlin.math.roundToInt

/**
 * Paysage : aperçu de la zone tactile invisible autour de la caméra (téléphone tourné, caméra à
 * gauche) et curseur de marge. La caméra est celle de l'appareil quand elle est connue.
 */
@Composable
internal fun LandscapeZoneSetting(marginDp: Float, onChange: (Float) -> Unit) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val info = remember(context) { runCatching { CutoutLocator.screenInfo(context) }.getOrNull() }
    // Écran en paysage : largeur = côté long, hauteur = côté court.
    val portraitW = info?.let { minOf(it.width, it.height) } ?: 1280
    val portraitH = info?.let { maxOf(it.width, it.height) } ?: 2800
    val density = info?.density ?: 3f
    val cam = info?.cutout?.let { CutoutLocator.portraitTopToLandscapeLeft(it.left, it.top, it.right, it.bottom, portraitW) }
        ?: intArrayOf(24, portraitW / 2 - 165, 134, portraitW / 2 + 165)
    var live by remember(marginDp) { mutableFloatStateOf(marginDp) }
    val g = CutoutLocator.sideGeometry(cam[0], cam[1], cam[2], cam[3], portraitH, portraitW, density, marginDp = live)
    val frame = MaterialTheme.colorScheme.outline
    val zone = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.visibility_landscape_zone), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.visibility_landscape_zone_desc, live.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .aspectRatio(portraitH.toFloat() / portraitW)
                .clearAndSetSemantics { },
        ) {
            val k = size.width / portraitH
            val r = 18.dp.toPx()
            drawRoundRect(frame, cornerRadius = CornerRadius(r), style = Stroke(2.dp.toPx()))
            // Caméra.
            drawRoundRect(
                Color.Black,
                topLeft = Offset(cam[0] * k, cam[1] * k),
                size = Size((cam[2] - cam[0]) * k, (cam[3] - cam[1]) * k),
                cornerRadius = CornerRadius((cam[2] - cam[0]) * k / 2),
            )
            // Zone tactile de la bulle invisible.
            val left = (g.centerX - g.width / 2) * k
            val top = g.top * k
            val w = g.width * k
            val h = g.height * k
            drawRoundRect(zone.copy(alpha = 0.25f), Offset(left, top), Size(w, h), CornerRadius(w / 2))
            drawRoundRect(zone, Offset(left, top), Size(w, h), CornerRadius(w / 2), style = Stroke(1.5.dp.toPx()))
        }
        Slider(
            value = live,
            onValueChange = {
                // Petit cran haptique à chaque palier franchi.
                if (it.roundToInt() != live.roundToInt()) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                live = it
            },
            onValueChangeFinished = { onChange(live.roundToInt().toFloat()) },
            valueRange = 0f..CutoutLocator.MAX_LANDSCAPE_MARGIN_DP,
            steps = CutoutLocator.MAX_LANDSCAPE_MARGIN_DP.toInt() / 2 - 1,
        )
    }
}
