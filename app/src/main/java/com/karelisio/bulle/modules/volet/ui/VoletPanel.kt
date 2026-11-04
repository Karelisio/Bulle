package com.karelisio.bulle.modules.volet.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.modules.volet.VoletController
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.media.MediaSection
import com.karelisio.bulle.modules.volet.notifications.NotificationsSection
import com.karelisio.bulle.modules.volet.qs.QuickSettingsSection
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Le volet : voile flouté, en-tête, réglages rapides, média et notifications.
 * Il descend avec le doigt (expansion 0 → 1), puis les réglages rapides se déplient (1 → 2).
 */
@Composable
fun VoletPanel(host: VoletController) {
    BulleTheme {
        val context = LocalContext.current
        val density = LocalDensity.current
        val s = host.settings
        val scheme = MaterialTheme.colorScheme
        val blur = remember {
            Build.VERSION.SDK_INT >= 31 && context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        }
        // Sans flou, le voile est plus opaque pour rester lisible.
        val scrimAlpha = if (blur) s.scrimOpacity else max(s.scrimOpacity, 0.88f)
        val topInset = remember { with(density) { CutoutLocator.screenInfo(context).statusBarHeight.toDp() } }
        val connection = remember(host) { VoletScrollConnection(host) }

        Box(Modifier.fillMaxSize()) {
            // Voile : toucher dehors referme le volet.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = min(host.expansion, 1f) }
                    .background(scheme.surfaceContainerLowest.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) { detectTapGestures { host.close() } },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val e = min(host.expansion, 1f)
                        translationY = (e - 1f) * 160.dp.toPx()
                        alpha = e.coerceIn(0f, 1f)
                        // Retour prédictif : le volet recule légèrement en suivant le geste.
                        val sc = 1f - 0.08f * host.backProgress
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .padding(top = topInset + 6.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .nestedScroll(connection),
            ) {
                // En-tête et tuiles : glisser déplie ou replie.
                Column(
                    Modifier.draggable(
                        state = rememberDraggableState { host.dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> host.settle(v) },
                    ),
                ) {
                    VoletHeader(host)
                    QuickSettingsSection(host, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                NotificationsSection(
                    host = host,
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    header = { MediaSection(host, Modifier.fillMaxWidth()) },
                )
                Handle(host)
            }
        }
    }
}

/** Poignée : glisser vers le haut pour fermer, toucher pour déplier ou replier. */
@Composable
private fun Handle(host: VoletHost) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .draggable(
                state = rememberDraggableState { host.dragBy(it) },
                orientation = Orientation.Vertical,
                onDragStopped = { v -> host.settle(v) },
            )
            .pointerInput(Unit) { detectTapGestures { if (host.expansion > 1.05f) host.collapseToHalf() else host.expandFull() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}

/**
 * Relie le défilement des notifications au volet : tirer en haut de liste déplie,
 * pousser en bout de liste referme, et les réglages rapides se replient avant que la liste défile.
 */
private class VoletScrollConnection(private val host: VoletHost) : NestedScrollConnection {
    private var dragging = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f && host.expansion > 1f) {
            host.dragBy(available.y)
            dragging = true
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            host.dragBy(available.y)
            dragging = true
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!dragging) return Velocity.Zero
        dragging = false
        host.settle(available.y)
        return available
    }
}
