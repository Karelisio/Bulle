package com.karelisio.bulle.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Hauteur visible de la pilule (hors marge) : sert aussi à réserver la place sous les listes. */
val FloatingTabBarHeight = 64.dp

/**
 * Barre de navigation flottante en pilule.
 *
 * L'onglet actif s'élargit pour révéler son libellé ; l'indicateur, dessiné derrière les onglets,
 * glisse de l'un à l'autre au ressort en s'étirant : le bord qui mène part plus vite que celui
 * qui suit, puis la pastille se resserre. Les valeurs animées ne sont lues qu'au dessin.
 */
@Composable
fun FloatingTabBar(
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val current by rememberUpdatedState(selected)

    // Bornes horizontales (gauche, droite) de chaque onglet dans la rangée.
    val bounds = remember { mutableStateMapOf<ShellTab, Pair<Float, Float>>() }
    val left = remember { Animatable(Float.NaN) }
    val right = remember { Animatable(Float.NaN) }

    LaunchedEffect(reduce) {
        snapshotFlow { bounds[current] }.filterNotNull().collect { (l, r) ->
            if (reduce || left.value.isNaN()) {
                left.snapTo(l)
                right.snapTo(r)
                return@collect
            }
            // Le bord dans le sens du déplacement est plus raide : effet d'étirement.
            val towardsRight = l > left.value
            val lead = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.78f, stiffness = 360f)
            // Sans attendre : une nouvelle cible (onglet qui s'élargit) reprend l'élan en cours.
            launch { left.animateTo(l, if (towardsRight) trail else lead) }
            launch { right.animateTo(r, if (towardsRight) lead else trail) }
        }
    }

    val barDescription = stringResource(R.string.nav_bar_description)
    Surface(
        modifier = modifier.semantics { contentDescription = barDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .height(FloatingTabBarHeight)
                .padding(8.dp)
                .selectableGroup()
                .drawBehind {
                    val l = left.value
                    val r = right.value
                    if (l.isNaN() || r.isNaN() || r <= l) return@drawBehind
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(l, 0f),
                        size = Size(r - l, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    reduce = reduce,
                    onClick = {
                        if (tab != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(tab)
                        }
                    },
                    modifier = Modifier.onPlaced {
                        val x = it.positionInParent().x
                        bounds[tab] = x to x + it.size.width
                    },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: ShellTab,
    selected: Boolean,
    reduce: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tab.labelRes)
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabTint",
    )
    // Petit rebond de l'icône à la sélection (lu dans graphicsLayer).
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduce) {
            pop.snapTo(0.82f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    val sizeSpring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tab.icon,
            contentDescription = if (selected) null else label,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        )
        AnimatedVisibility(
            visible = selected,
            enter = if (reduce) EnterTransition.None else expandHorizontally(sizeSpring) + fadeIn(),
            exit = if (reduce) ExitTransition.None else shrinkHorizontally(sizeSpring) + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
