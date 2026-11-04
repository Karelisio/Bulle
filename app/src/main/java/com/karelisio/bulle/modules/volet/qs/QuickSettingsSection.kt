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

/**
 * Grille à deux niveaux pilotée par qsExpansion : à 0, une rangée de tuiles rondes ;
 * à 1, deux colonnes de grandes tuiles. Positions et tailles sont interpolées à la mise en page
 * (lecture de l'état dans le bloc de mesure : seule la mise en page suit le doigt).
 */
@Composable
private fun TileGrid(host: VoletHost) {
    val context = host.context
    val tiles = remember(context, host.settings.tiles) { QsTileRegistry.current(context, host.settings.tiles) }
    val toggler = remember(host) { QsToggler(host) }
    val metrics = QsMetrics.of(host.settings.density)
    val collapsed = host.settings.collapsedTileCount.coerceIn(4, 6).coerceAtMost(tiles.size)
    val q = { host.qsExpansion }

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            tiles.forEachIndexed { index, tile ->
                key(tile.id) {
                    QsTileView(
                        tile = tile,
                        context = context,
                        inRow = index < collapsed,
                        expansion = q,
                        metrics = metrics,
                        cornerRadius = host.settings.cornerRadiusDp.dp,
                        toggler = toggler,
                        modifier = Modifier.cascade(index),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val e = q().coerceIn(0f, 1f)
        val w = constraints.maxWidth
        val gap = metrics.gap.roundToPx()
        val n = max(collapsed, 1)
        // Rangée : n emplacements égaux, tuiles rondes centrées dans chacun.
        val slot = (w - gap * (n - 1)).toFloat() / n
        val d = min(slot, metrics.rowMax.toPx())
        // Grille : deux colonnes.
        val colW = (w - gap) / 2f
        val tileH = metrics.tileHeight.toPx()
        val rows = (measurables.size + 1) / 2
        val gridH = rows * tileH + (rows - 1).coerceAtLeast(0) * gap
        val height = lerp(d, gridH, e)

        val placeables = measurables.mapIndexed { i, m ->
            val inRow = i < collapsed
            val tw = if (inRow) lerp(d, colW, e) else colW
            val th = if (inRow) lerp(d, tileH, e) else tileH
            m.measure(Constraints.fixed(tw.roundToInt().coerceAtLeast(1), th.roundToInt().coerceAtLeast(1)))
        }
        layout(w, height.roundToInt()) {
            placeables.forEachIndexed { i, p ->
                val gx = (i % 2) * (colW + gap)
                val gy = (i / 2) * (tileH + gap)
                if (i < collapsed) {
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
