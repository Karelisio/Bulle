package com.karelisio.bulle.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.island.IslandGeometry
import kotlinx.coroutines.flow.StateFlow

/** Racine Compose de l'île : pour l'instant une pilule noire au repos. */
@Composable
fun IslandRoot(geometry: StateFlow<IslandGeometry>) {
    val g by geometry.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        with(density) {
            Box(
                Modifier
                    .size(g.width.toDp(), g.height.toDp())
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black),
            )
        }
    }
}
