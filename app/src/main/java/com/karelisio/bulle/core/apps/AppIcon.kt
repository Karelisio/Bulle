package com.karelisio.bulle.core.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Icône d'appli chargée en arrière-plan, qui apparaît en fondu.
 * [themed] : icône thématique (monochrome teintée aux couleurs du thème) si l'appli en fournit.
 */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier, themed: Boolean = false) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx().roundToInt() }
    var bitmap by remember(app.component, themed) { mutableStateOf<ImageBitmap?>(null) }
    var isMono by remember(app.component, themed) { mutableStateOf(false) }
    LaunchedEffect(app.component, px, themed) {
        val mono = if (themed) AppCatalog.themedIcon(context, app.component, px) else null
        isMono = mono != null
        bitmap = mono ?: AppCatalog.icon(context, app.component, px)
    }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, label = "icon")
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .then(if (isMono) Modifier.clip(CircleShape).background(scheme.primaryContainer) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it,
                contentDescription = app.label,
                modifier = Modifier.size(size),
                colorFilter = if (isMono) ColorFilter.tint(scheme.onPrimaryContainer) else null,
            )
        }
    }
}
