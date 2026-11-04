package com.karelisio.bulle.modules.volet.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletSettings

/** Échelle de l'aperçu par rapport au vrai volet. */
private const val SCALE = 0.5f

/**
 * Petit aperçu stylisé du volet à demi ouvert (faux contenu) : flou, voile, coins, densité
 * et nombre de tuiles reflètent [settings] en direct.
 */
@Composable
fun VoletMiniPreview(settings: VoletSettings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val compact = settings.density == VoletDensity.COMPACT
    val gap = if (compact) 4.dp else 6.dp
    val corner = (settings.cornerRadiusDp * SCALE).dp
    Box(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        // Faux fond d'écran (formes colorées), flouté comme derrière le vrai volet.
        Box(
            Modifier
                .fillMaxSize()
                .blur((settings.blurRadiusDp * SCALE).dp)
                .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer))),
        ) {
            Box(Modifier.offset(24.dp, 40.dp).size(96.dp).clip(CircleShape).background(scheme.primary))
            Box(Modifier.align(Alignment.BottomEnd).offset((-20).dp, (-10).dp).size(120.dp).clip(CircleShape).background(scheme.tertiary))
            Box(Modifier.align(Alignment.Center).size(64.dp, 28.dp).clip(CircleShape).background(scheme.secondary))
        }
        // Voile.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.scrimOpacity)))
        // Contenu du volet.
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("12:30", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(width = 36.dp, height = 8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                repeat(settings.collapsedTileCount.coerceIn(4, 6)) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (compact) 30.dp else 36.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(if (i < 2) scheme.primary else scheme.surfaceContainerHighest),
                    )
                }
            }
            repeat(2) { i ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corner))
                        .background(scheme.surfaceContainer)
                        .padding(if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (i == 0) scheme.tertiary else scheme.secondary))
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(width = 72.dp, height = 7.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.7f)))
                        Box(Modifier.size(width = 120.dp, height = 6.dp).clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.45f)))
                    }
                }
            }
        }
    }
}
