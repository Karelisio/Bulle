package com.karelisio.bulle.modules.volet.qs

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Dimensions des tuiles selon la densité choisie. */
internal data class QsMetrics(val rowMax: Dp, val tileHeight: Dp, val gap: Dp, val chip: Dp) {
    companion object {
        fun of(density: VoletDensity) = when (density) {
            VoletDensity.COMFORTABLE -> QsMetrics(rowMax = 58.dp, tileHeight = 72.dp, gap = 8.dp, chip = 42.dp)
            VoletDensity.COMPACT -> QsMetrics(rowMax = 50.dp, tileHeight = 62.dp, gap = 6.dp, chip = 36.dp)
        }
    }
}

/**
 * Réglages rapides : rangée de tuiles volet à demi ouvert, grille complète quand
 * host.qsExpansion tend vers 1, curseur de luminosité, édition (host.editingTiles).
 */
@Composable
fun QuickSettingsSection(host: VoletHost, modifier: Modifier) {
    val reduce = rememberReduceMotion()
    AnimatedContent(
        targetState = host.editingTiles,
        modifier = modifier,
        transitionSpec = {
            val d = if (reduce) 0 else 220
            fadeIn(tween(d)) togetherWith fadeOut(tween(d))
        },
        label = "qsEdit",
    ) { editing ->
        if (editing) {
            TileEditor(host, Modifier.fillMaxWidth())
        } else {
            Column(Modifier.fillMaxWidth()) {
                TileGrid(host)
                Spacer(Modifier.height(10.dp))
                BrightnessSlider(host, Modifier.fillMaxWidth().cascade(1))
            }
        }
    }
}

/** Découpage de la grille complète en pages (logique pure, testable). */
internal object QsPaging {
    /** Tuiles par page : 2 colonnes × 4 rangées. */
    const val PER_PAGE = 8
    const val ROWS_PER_PAGE = PER_PAGE / 2

    fun pageCount(tiles: Int): Int = if (tiles <= PER_PAGE) 1 else (tiles + PER_PAGE - 1) / PER_PAGE

    /** Indices des tuiles de chaque page (une seule page jusqu'à 8 tuiles). */
    fun pages(tiles: Int): List<IntRange> =
        (0 until pageCount(tiles)).map { p -> (p * PER_PAGE) until min(tiles, (p + 1) * PER_PAGE) }
}

/**
 * Grille à deux niveaux pilotée par qsExpansion : à 0, une rangée de tuiles rondes ;
 * à 1, deux colonnes de grandes tuiles. Au-delà de 8 tuiles, la grille se répartit en pages
 * horizontales ; la rangée repliée vit sur la première page, qui revient d'elle-même au repli.
 */
@Composable
private fun TileGrid(host: VoletHost) {
    val context = host.context
    val tiles = remember(context, host.settings.tiles) { QsTileRegistry.current(context, host.settings.tiles) }
    val toggler = remember(host) { QsToggler(host) }
    val metrics = QsMetrics.of(host.settings.density)
    val collapsed = host.settings.collapsedTileCount.coerceIn(4, 6).coerceAtMost(tiles.size)
    val q = { host.qsExpansion }
    val pages = remember(tiles.size) { QsPaging.pages(tiles.size) }

    if (pages.size <= 1) {
        TilePage(host, tiles, 0, collapsed, collapsed, minRows = 0, metrics, toggler, q)
        return
    }

    val reduce = rememberReduceMotion()
    val pager = rememberPagerState { pages.size }
    // Défilement horizontal seulement grille dépliée (lu hors composition à chaque image).
    val expanded by remember { derivedStateOf { host.qsExpansion > 0.98f } }
    val folding by remember { derivedStateOf { host.qsExpansion < 0.98f } }
    // Au repli, retour à la première page : c'est elle qui porte la rangée.
    LaunchedEffect(folding) {
        if (folding && pager.currentPage != 0) {
            if (reduce) pager.scrollToPage(0) else pager.animateScrollToPage(0)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = expanded,
            pageSpacing = metrics.gap,
            verticalAlignment = Alignment.Top,
            key = { it },
        ) { page ->
            val range = pages[page]
            TilePage(
                host = host,
                tiles = tiles.subList(range.first, range.last + 1),
                firstIndex = range.first,
                inRow = if (page == 0) collapsed else 0,
                rowSlots = collapsed,
                minRows = QsPaging.ROWS_PER_PAGE,
                metrics = metrics,
                toggler = toggler,
                expansion = q,
            )
        }
        PageIndicator(pager, pages.size, q)
    }
}

/**
 * Une page de tuiles. Positions et tailles sont interpolées à la mise en page (lecture de
 * l'état dans le bloc de mesure : seule la mise en page suit le doigt).
 * [inRow] : nombre de tuiles de cette page présentes dans la rangée repliée ;
 * [rowSlots] : emplacements de la rangée (pour une hauteur repliée identique sur toutes les pages) ;
 * [minRows] : hauteur minimale en rangées (pages de même hauteur).
 */
@Composable
private fun TilePage(
    host: VoletHost,
    tiles: List<QsTile>,
    firstIndex: Int,
    inRow: Int,
    rowSlots: Int,
    minRows: Int,
    metrics: QsMetrics,
    toggler: QsToggler,
    expansion: () -> Float,
) {
    val context = host.context
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            tiles.forEachIndexed { index, tile ->
                key(tile.id) {
                    QsTileView(
                        tile = tile,
                        context = context,
                        inRow = index < inRow,
                        expansion = expansion,
                        metrics = metrics,
                        cornerRadius = host.settings.cornerRadiusDp.dp,
                        toggler = toggler,
                        modifier = Modifier.cascade(firstIndex + index),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val e = expansion().coerceIn(0f, 1f)
        val w = constraints.maxWidth
        val gap = metrics.gap.roundToPx()
        val n = max(rowSlots, 1)
        // Rangée : n emplacements égaux, tuiles rondes centrées dans chacun.
        val slot = (w - gap * (n - 1)).toFloat() / n
        val d = min(slot, metrics.rowMax.toPx())
        // Grille : deux colonnes.
        val colW = (w - gap) / 2f
        val tileH = metrics.tileHeight.toPx()
        val rows = max((measurables.size + 1) / 2, minRows)
        val gridH = rows * tileH + (rows - 1).coerceAtLeast(0) * gap
        val height = lerp(d, gridH, e)

        val placeables = measurables.mapIndexed { i, m ->
            val row = i < inRow
            val tw = if (row) lerp(d, colW, e) else colW
            val th = if (row) lerp(d, tileH, e) else tileH
            m.measure(Constraints.fixed(tw.roundToInt().coerceAtLeast(1), th.roundToInt().coerceAtLeast(1)))
        }
        layout(w, height.roundToInt()) {
            placeables.forEachIndexed { i, p ->
                val gx = (i % 2) * (colW + gap)
                val gy = (i / 2) * (tileH + gap)
                if (i < inRow) {
                    val rx = i * (slot + gap) + (slot - d) / 2f
                    p.place(lerp(rx, gx, e).roundToInt(), lerp(0f, gy, e).roundToInt())
                } else if (e > 0.02f) {
                    // Tuiles de la grille seule : elles descendent avec le volet.
                    p.place(gx.roundToInt(), lerp(gy - tileH * 0.6f, gy, e).roundToInt())
                }
            }
        }
    }
}

/**
 * Indicateur de pages expressif : la page courante s'étire en pilule qui glisse avec le doigt.
 * Sa hauteur et son opacité suivent qsExpansion (invisible et sans place dans la rangée).
 */
@Composable
private fun PageIndicator(pager: PagerState, count: Int, expansion: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.volet_qs_page, pager.currentPage + 1, count)
    Box(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .layout { measurable, constraints ->
                val e = expansion().coerceIn(0f, 1f)
                val full = 18.dp.roundToPx()
                val p = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                layout(p.width, (full * e).roundToInt()) { p.place(0, 0) }
            }
            .graphicsLayer { alpha = ((expansion() - 0.5f) / 0.5f).coerceIn(0f, 1f) }
            .drawBehind {
                val dot = 6.dp.toPx()
                val wide = 18.dp.toPx()
                val gap = 6.dp.toPx()
                val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                // Largeur de chaque point : interpolée selon la distance à la position courante.
                val widths = FloatArray(count) { i ->
                    val t = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
                    lerp(dot, wide, t)
                }
                val total = widths.sum() + gap * (count - 1)
                var x = (size.width - total) / 2f
                val y = (size.height - dot) / 2f
                for (i in 0 until count) {
                    val t = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
                    val c = lerp(scheme.outlineVariant, scheme.primary, t)
                    drawRoundRect(c, topLeft = Offset(x, y), size = Size(widths[i], dot), cornerRadius = CornerRadius(dot / 2f))
                    x += widths[i] + gap
                }
            },
    )
}

/** Une tuile : ronde dans la rangée, grande (icône + libellé + état) dans la grille. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsTileView(
    tile: QsTile,
    context: Context,
    inRow: Boolean,
    expansion: () -> Float,
    metrics: QsMetrics,
    cornerRadius: Dp,
    toggler: QsToggler,
    modifier: Modifier = Modifier,
) {
    val state by remember(tile, context) { tile.observe(context) }.collectAsState(TileState())
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scheme = MaterialTheme.colorScheme
    val active = state.active && state.known && tile.action == null

    // Morphing M3 : cercle → cookie quand la tuile s'active (au ressort).
    val morph = remember { Morph(MaterialShapes.Circle.normalized(), MaterialShapes.Cookie9Sided.normalized()) }
    val morphProgress = remember { Animatable(if (active) 1f else 0f) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active) {
        val target = if (active) 1f else 0f
        if (reduce) morphProgress.snapTo(target)
        else morphProgress.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        val target = if (pressed) 1f else 0f
        if (reduce) pulse.snapTo(target) else pulse.animateTo(target, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }

    val label = stringResource(tile.label)
    val stateText = when {
        tile.action != null -> null
        !state.known -> stringResource(R.string.volet_qs_state_unknown)
        state.active -> stringResource(R.string.volet_qs_state_on)
        else -> stringResource(R.string.volet_qs_state_off)
    }
    val containerOff = scheme.surfaceContainerHigh
    val chipOff = scheme.surfaceContainerHighest
    val chipOnExpanded = scheme.onPrimary.copy(alpha = 0.18f).compositeOver(scheme.primary)
    val iconTint = if (active) scheme.onPrimary else scheme.onSurfaceVariant
    val textColor = if (active) scheme.onPrimary else scheme.onSurface

    Layout(
        modifier = modifier
            .graphicsLayer {
                val e = expansion()
                // Tuiles de la grille seule : apparition en fondu avec l'ouverture.
                if (!inRow) alpha = ((e - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val s = 1f - 0.08f * pulse.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val e = if (inRow) expansion() else 1f
                if (e <= 0.01f) return@drawBehind
                val r = lerp(size.height / 2f, min(cornerRadius.toPx(), size.height / 2f), e)
                val c = if (active) scheme.primary else containerOff
                drawRoundRect(c.copy(alpha = c.alpha * e), cornerRadius = CornerRadius(r))
            }
            .semantics {
                if (stateText != null) stateDescription = stateText
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = if (tile.action == null) Role.Switch else Role.Button,
                onClickLabel = label,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    toggler.longPress(tile)
                },
                onClick = {
                    haptic.performHapticFeedback(if (active) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    toggler.tap(tile, state)
                },
            ),
        content = {
            // Pastille d'icône (forme morphée).
            Box(
                Modifier
                    .graphicsLayer {
                        val p = morphProgress.value
                        shape = MorphShape(morph, p.coerceIn(0f, 1f))
                        clip = true
                        rotationZ = 30f * p
                    }
                    .drawBehind {
                        val e = if (inRow) expansion() else 1f
                        val chipColor = if (active) lerp(scheme.primary, chipOnExpanded, e) else chipOff
                        drawRect(chipColor)
                    },
            )
            Icon(tile.icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(
                Modifier.graphicsLayer {
                    val e = if (inRow) expansion() else 1f
                    alpha = ((e - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    translationX = (1f - e) * -12.dp.toPx()
                },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stateText != null) {
                    Text(
                        stateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val e = if (inRow) expansion().coerceIn(0f, 1f) else 1f
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val inset = 12.dp.toPx() * e
        // La pastille occupe toute la tuile ronde, puis se réduit à gauche de la grande tuile.
        val chip = lerp(min(w, h).toFloat(), metrics.chip.toPx(), e).roundToInt().coerceAtLeast(1)
        val chipX = lerp((w - chip) / 2f, inset + 0f, e).roundToInt()
        val chipY = (h - chip) / 2
        val chipP = measurables[0].measure(Constraints.fixed(chip, chip))
        val iconP = measurables[1].measure(Constraints())
        val textX = (inset + chip + 12.dp.toPx()).roundToInt()
        val textW = (w - textX - 12.dp.roundToPx()).coerceAtLeast(0)
        val textP = measurables[2].measure(Constraints(maxWidth = textW, maxHeight = h))
        layout(w, h) {
            chipP.place(chipX, chipY)
            iconP.place(chipX + (chip - iconP.width) / 2, chipY + (chip - iconP.height) / 2)
            if (e > 0.05f && textW > 0) textP.place(textX, (h - textP.height) / 2)
        }
    }
}
