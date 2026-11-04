package com.karelisio.bulle.modules.volet.qs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Édition des tuiles : réorganisation par glisser-déposer (appui long puis glisser),
 * retrait, ajout depuis « Tuiles disponibles ». Chaque modification est sauvegardée.
 */
@Composable
internal fun TileEditor(host: VoletHost, modifier: Modifier = Modifier) {
    val context = host.context
    val scope = rememberCoroutineScope()
    var order by remember {
        mutableStateOf(QsTileRegistry.current(context, host.settings.tiles).map { it.id })
    }
    fun save(new: List<String>) {
        order = new
        scope.launch {
            SettingsRepository.update(context) { it.copy(volet = it.volet.copy(tiles = new)) }
        }
    }
    val available = remember(order) { QsTileRegistry.available(context, order) }
    val corner = host.settings.cornerRadiusDp.dp

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.volet_qs_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { save(QsTileRegistry.resolveOrder(emptyList())) }) {
                Text(stringResource(R.string.volet_qs_edit_reset))
            }
            TextButton(onClick = { host.editingTiles = false }) {
                Text(stringResource(R.string.volet_qs_edit_done))
            }
        }
        Spacer(Modifier.height(6.dp))
        ReorderableTileGrid(
            order = order,
            corner = corner,
            onOrderChange = { order = it },
            onDrop = { save(order) },
            onRemove = { id -> save(QsTileRegistry.remove(order, id)) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.volet_qs_available),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        if (available.isEmpty()) {
            Text(
                stringResource(R.string.volet_qs_available_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        available.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                pair.forEach { tile ->
                    EditTile(
                        icon = tile.icon,
                        label = stringResource(tile.label),
                        corner = corner,
                        added = false,
                        onButton = { save(QsTileRegistry.add(order, tile.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Grille réorganisable : la tuile tenue suit le doigt, les autres glissent au ressort vers leur place. */
@Composable
private fun ReorderableTileGrid(
    order: List<String>,
    corner: Dp,
    onOrderChange: (List<String>) -> Unit,
    onDrop: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val currentOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val tileH = 60.dp
    val gap = 8.dp
    val scope = rememberCoroutineScope()

    /** Lâcher : la tuile repart de la position du doigt vers sa place, au ressort. */
    fun release(anim: Animatable<Offset, *>) {
        scope.launch {
            anim.snapTo(dragOffset)
            dragging = null
            onDrop()
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val gapPx = with(density) { gap.toPx() }
        val colW = (constraints.maxWidth - gapPx) / 2f
        val tileHPx = with(density) { tileH.toPx() }
        val rows = (order.size + 1) / 2
        fun slot(index: Int) = Offset((index % 2) * (colW + gapPx), (index / 2) * (tileHPx + gapPx))
        fun indexAt(center: Offset, count: Int): Int {
            val col = if (center.x < colW + gapPx / 2f) 0 else 1
            val row = (center.y / (tileHPx + gapPx)).toInt().coerceIn(0, ((count + 1) / 2 - 1).coerceAtLeast(0))
            return (row * 2 + col).coerceIn(0, count - 1)
        }

        Box(Modifier.fillMaxWidth().height(tileH * rows + gap * (rows - 1).coerceAtLeast(0))) {
            order.forEachIndexed { index, id ->
                val tile = QsTileRegistry.get(id) ?: return@forEachIndexed
                key(id) {
                    val target = slot(index)
                    val anim = remember { Animatable(target, Offset.VectorConverter) }
                    val isDragged = dragging == id
                    LaunchedEffect(target, isDragged) {
                        if (isDragged) return@LaunchedEffect
                        if (reduce) anim.snapTo(target)
                        else anim.animateTo(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                    }
                    val lift by animateFloatAsState(
                        if (isDragged) 1f else 0f,
                        if (reduce) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "lift",
                    )
                    EditTile(
                        icon = tile.icon,
                        label = stringResource(tile.label),
                        corner = corner,
                        added = true,
                        onButton = { onRemove(id) },
                        modifier = Modifier
                            .offset {
                                val o = if (isDragged) dragOffset else anim.value
                                IntOffset(o.x.roundToInt(), o.y.roundToInt())
                            }
                            .zIndex(if (isDragged) 1f else 0f)
                            .width(with(density) { colW.toDp() })
                            .height(tileH)
                            .graphicsLayer {
                                val s = 1f + 0.05f * lift
                                scaleX = s
                                scaleY = s
                            }
                            .shadow((8 * lift).dp, RoundedCornerShape(corner))
                            .pointerInput(id, colW) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = id
                                        dragOffset = slot(currentOrder.indexOf(id))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val list = currentOrder
                                        val from = list.indexOf(id)
                                        val to = indexAt(dragOffset + Offset(colW / 2f, tileHPx / 2f), list.size)
                                        if (from >= 0 && to != from) {
                                            onOrderChange(QsTileRegistry.move(list, from, to))
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    },
                                    onDragEnd = { release(anim) },
                                    onDragCancel = { release(anim) },
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditTile(
    icon: ImageVector,
    label: String,
    corner: Dp,
    added: Boolean,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(corner))
            .background(scheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onButton) {
            Icon(
                if (added) Icons.Rounded.RemoveCircle else Icons.Rounded.AddCircle,
                contentDescription = stringResource(
                    if (added) R.string.volet_qs_remove_tile else R.string.volet_qs_add_tile,
                    label,
                ),
                tint = if (added) scheme.error else scheme.primary,
            )
        }
    }
}
