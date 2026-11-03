package com.karelisio.bulle.core.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** Icône d'appli chargée en arrière-plan, qui apparaît en fondu. */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx().roundToInt() }
    var bitmap by remember(app.component) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.component, px) { bitmap = AppCatalog.icon(context, app.component, px) }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, label = "icon")
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha }) {
        bitmap?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(size)) }
    }
}
