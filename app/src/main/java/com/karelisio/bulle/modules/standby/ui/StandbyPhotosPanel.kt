package com.karelisio.bulle.modules.standby.ui

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.standby.KenBurns
import com.karelisio.bulle.modules.standby.StandbyPhotos
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/** Durée d'une photo du diaporama (ms). */
private const val PHOTO_MS = 9_000

/** Diaporama d'un album, en fondu enchaîné, avec effet Ken Burns (figé si « réduire les animations »). */
@Composable
fun PhotosPanel(albumId: String?) {
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var image by remember { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
    val granted = remember { StandbyPhotos.granted(context) }
    LaunchedEffect(albumId) {
        photos = if (albumId != null) StandbyPhotos.photos(context, albumId).shuffled() else emptyList()
    }
    LaunchedEffect(photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        val maxPx = maxOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        while (true) {
            val i = index % photos.size
            StandbyPhotos.load(context, photos[i], maxPx)?.let { image = i to it.asImageBitmap() }
            delay(PHOTO_MS.toLong())
            index++
        }
    }
    StandbyCard {
        val current = image
        if (current == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (!granted) R.string.standby_photos_permission else R.string.standby_photos_none),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        } else {
            Crossfade(current, animationSpec = tween(if (reduce) 0 else 1200), label = "photo") { (i, bmp) ->
                val progress = remember(i) { Animatable(0f) }
                LaunchedEffect(i, reduce) {
                    if (!reduce) progress.animateTo(1f, tween(PHOTO_MS + 1200, easing = LinearEasing))
                }
                Image(
                    bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val (s, dx, dy) = if (reduce) Triple(1f, 0f, 0f) else KenBurns.frame(i, progress.value)
                        scaleX = s
                        scaleY = s
                        translationX = dx * size.width
                        translationY = dy * size.height
                    },
                )
            }
        }
    }
}
