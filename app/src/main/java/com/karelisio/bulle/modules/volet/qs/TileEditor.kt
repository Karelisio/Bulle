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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    // Tuiles des applis : découvertes hors du fil principal (null tant que la recherche dure).
    val apps by produceState<List<AppTile>?>(null, context) {
        value = withContext(Dispatchers.IO) { AppTiles.discover(context) }
    }
    val availableApps = remember(order, apps) { QsTileRegistry.availableApps(apps.orEmpty(), order) }
    // Recherche d'une tuile de la liste (système, ou appli déjà ajoutée / découverte).
    val savedApps = remember { QsTileRegistry.current(context, host.settings.tiles).filterIsInstance<AppTile>() }
    val lookup: (String) -> QsTile? = remember(apps) {
        val byId: Map<String, QsTile> = (savedApps + apps.orEmpty()).associateBy { it.id };
        { id: String -> QsTileRegistry.get(id) ?: byId[id] }
    }
    val corner = host.settings.cornerRadiusDp.dp

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.volet_qs_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Ordre par défaut limité aux tuiles prises en charge (pas de NFC sans puce NFC…).
            TextButton(onClick = { save(QsTileRegistry.current(context, emptyList()).map { it.id }) }) {
                Text(stringResource(R.string.volet_qs_edit_reset))
            }
            TextButton(onClick = { host.editingTiles = false }) {
                Text(stringResource(R.string.volet_qs_edit_done))
            }
        }
        Spacer(Modifier.height(6.dp))
        // Deux onglets : « Mes tuiles » (réorganiser) et « Ajouter » (système + applis), pour ne pas
        // avoir à faire défiler toute la grille avant d'atteindre les tuiles à ajouter.
        var adding by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(
                selected = !adding, onClick = { adding = false },
                label = { Text(stringResource(R.string.volet_qs_tab_mine)) },
            )
            androidx.compose.material3.FilterChip(
                selected = adding, onClick = { adding = true },
                label = { Text(stringResource(R.string.volet_qs_tab_add, available.size + availableApps.size)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!adding) {
        ReorderableTileGrid(
            order = order,
            corner = corner,
            onOrderChange = { order = it },
            onDrop = { save(order) },
            onRemove = { id -> save(QsTileRegistry.remove(order, id)) },
            lookup = lookup,
        )
        } else {
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
        AppTilesSection(apps, availableApps, corner) { save(QsTileRegistry.add(order, it.id)) }
        }
    }
}

/** Section « Applis » : tuiles Réglages rapides des applis tierces, à ajouter à la liste. */
@Composable
private fun AppTilesSection(apps: List<AppTile>?, available: List<AppTile>, corner: Dp, onAdd: (AppTile) -> Unit) {
    val context = LocalContext.current
    val shizuku = remember { ShizukuShell.status() }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.volet_qs_apps_section),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(
            if (shizuku == ShizukuStatus.UNAVAILABLE) R.string.volet_qs_apps_hint_no_shizuku
            else R.string.volet_qs_apps_hint_shizuku,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (apps == null) {
        Text(
            stringResource(R.string.volet_qs_apps_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (apps != null && available.isEmpty()) {
        val scan = AppTiles.lastScan
        Text(
            if (apps.isEmpty()) {
                stringResource(R.string.volet_qs_apps_none) +
                    (scan?.let { " " + stringResource(R.string.volet_qs_apps_scan, it.first, it.second) } ?: "")
            } else stringResource(R.string.volet_qs_available_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    available.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            pair.forEach { tile ->
                key(tile.id) {
                    val bitmap = remember(tile) { tile.loadIcon(context, iconPx) }
                    EditTile(
                        icon = tile.icon,
                        label = tile.title,
                        corner = corner,
                        added = false,
                        onButton = { onAdd(tile) },
                        modifier = Modifier.weight(1f),
                        bitmap = bitmap,
                        tintBitmap = tile.hasOwnIcon,
                        subtitle = tile.appName.takeIf { it != tile.title },
                    )
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
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
    lookup: (String) -> QsTile?,
) {
    val context = LocalContext.current
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
                val tile = lookup(id) ?: return@forEachIndexed
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
                    val bitmap = remember(tile) { tile.loadIcon(context, with(density) { 40.dp.roundToPx() }) }
                    EditTile(
                        icon = tile.icon,
                        label = tile.labelText ?: stringResource(tile.label),
                        bitmap = bitmap,
                        tintBitmap = (tile as? AppTile)?.hasOwnIcon != false,
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
    bitmap: ImageBitmap? = null,
    tintBitmap: Boolean = true,
    subtitle: String? = null,
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
            if (bitmap != null) {
                Icon(
                    bitmap,
                    contentDescription = null,
                    tint = if (tintBitmap) scheme.onSurfaceVariant else Color.Unspecified,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
