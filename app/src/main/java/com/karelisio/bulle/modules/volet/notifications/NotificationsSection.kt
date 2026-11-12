package com.karelisio.bulle.modules.volet.notifications

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

/** Ligne de la liste (en-tête média, titre de section, groupe, bouton « Tout effacer »). */
private sealed interface ListRow {
    val key: String

    data object Header : ListRow { override val key = "header" }
    data object Empty : ListRow { override val key = "empty" }
    data class Section(val section: NotifSection) : ListRow { override val key = "section|$section" }
    data class Group(val group: NotifGroup, val index: Int) : ListRow { override val key = group.id }
    data object ClearAll : ListRow { override val key = "clear" }
}

private fun rows(model: NotifListModel): List<ListRow> = buildList {
    add(ListRow.Header)
    if (model.isEmpty) add(ListRow.Empty)
    var index = 0
    model.sections.forEach { s ->
        add(ListRow.Section(s.section))
        s.groups.forEach { add(ListRow.Group(it, index++)) }
    }
    if (model.clearableKeys.isNotEmpty()) add(ListRow.ClearAll)
}

/** Modèle de la liste, recalculé à chaque publication, retrait ou changement de classement. */
@Composable
internal fun rememberNotifListModel(host: VoletHost): NotifListModel {
    val active by NotificationRepository.active.collectAsState()
    val rankingVersion by NotificationRepository.rankingVersion.collectAsState()
    val locked = host.locked
    val context = host.context
    return remember(active, rankingVersion, locked) {
        runCatching {
            NotifModelFactory.build(
                context,
                active.values,
                ranking = { key -> NotificationRepository.ranking(key)?.let(RankInfo::from) },
                locked = locked,
                // Les applis exclues désactivent seulement l'ouverture du Volet, pas leurs notifications.
                excludedApps = emptySet(),
            )
        }.getOrDefault(NotifListModel.EMPTY)
    }
}

/**
 * Liste des notifications du volet. [header] (carte média) est placé en tête de liste
 * et défile avec elle. La LazyColumn relaie le défilement imbriqué au volet.
 */
@Composable
fun NotificationsSection(host: VoletHost, modifier: Modifier, header: @Composable () -> Unit) {
    // Contenu recréé à chaque ouverture : la cascade rejoue.
    val open by remember(host) { derivedStateOf { host.expansion > 0.01f } }
    if (!open) {
        Box(modifier)
        return
    }
    NotificationList(host, rememberNotifListModel(host), modifier, header)
}

@Composable
internal fun NotificationList(host: VoletHost, model: NotifListModel, modifier: Modifier, header: @Composable () -> Unit) {
    val compact = host.settings.density == VoletDensity.COMPACT
    val corner = host.settings.cornerRadiusDp.dp
    val dims = if (compact) NotifDims(pad = 12.dp, gap = 6.dp, badge = 32.dp, corner = corner * 0.85f, compact = true)
    else NotifDims(pad = 16.dp, gap = 10.dp, badge = 38.dp, corner = corner, compact = false)
    val reduce = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val rows = remember(model) { rows(model) }
    // Clés visibles à l'ouverture : les suivantes arrivent par le haut.
    val initialKeys = remember { model.allItems.map { it.key }.toSet() }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    var clearing by remember { mutableStateOf(false) }
    val focusKey = host.focusKey

    // Notification ouverte depuis l'île : groupe déplié et défilement jusqu'à elle, une seule fois
    // (relancé à chaque mise à jour de la liste, une notification de progression ramènerait sans cesse
    // la liste sur elle et redéplierait le groupe replié par l'utilisateur).
    var handledFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusKey, rows) {
        val key = focusKey ?: return@LaunchedEffect
        if (key == handledFocus) return@LaunchedEffect
        val group = model.groupOf(key) ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is ListRow.Group && it.group.id == group.id }
        if (index < 0) return@LaunchedEffect
        handledFocus = key
        if (group.items.size > 1) expandedGroups = expandedGroups + group.id
        runCatching { if (reduce) listState.scrollToItem(index) else listState.animateScrollToItem(index) }
    }
    LaunchedEffect(clearing) {
        if (!clearing) return@LaunchedEffect
        if (!reduce) delay(320)
        model.clearableKeys.forEach(NotifCommands::cancel)
        clearing = false
    }

    val placement = if (reduce) null else spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
    val fade = if (reduce) null else spring<Float>(stiffness = Spring.StiffnessMediumLow)

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(dims.gap),
    ) {
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                ListRow.Header -> header()
                ListRow.Empty -> EmptyState(Modifier.animateItem(fade, placement, fade).cascade(0))
                is ListRow.Section -> SectionTitle(row.section, Modifier.animateItem(fade, placement, fade).cascade(row.section.ordinal))
                is ListRow.Group -> {
                    val isNew = row.group.items.none { it.key in initialKeys }
                    NotifGroupCard(
                        group = row.group,
                        host = host,
                        dims = dims,
                        expanded = row.group.id in expandedGroups,
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            expandedGroups = if (row.group.id in expandedGroups) expandedGroups - row.group.id else expandedGroups + row.group.id
                        },
                        focusKey = focusKey,
                        clearing = clearing,
                        modifier = Modifier
                            .animateItem(null, placement, fade)
                            .then(if (isNew) Modifier.dropIn() else Modifier.cascade(row.index + 1)),
                    )
                }
                ListRow.ClearAll -> Box(Modifier.fillMaxWidth().animateItem(fade, placement, fade), contentAlignment = Alignment.CenterEnd) {
                    FilledTonalButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        clearing = true
                    }) {
                        Icon(Icons.Rounded.ClearAll, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.notif_clear_all), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(section: NotifSection, modifier: Modifier) {
    val label = when (section) {
        NotifSection.CONVERSATIONS -> R.string.notif_section_conversations
        NotifSection.ALERTING -> R.string.notif_section_alerting
        NotifSection.SILENT -> R.string.notif_section_silent
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 6.dp),
    )
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Row {
            Text(stringResource(R.string.notif_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Nouvelle notification : descend du haut au ressort. */
private fun Modifier.dropIn(): Modifier = composed {
    val reduce = rememberReduceMotion()
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val v = p.value
        translationY = (1f - v) * -36.dp.toPx()
        alpha = v.coerceIn(0f, 1f)
        val s = 0.94f + 0.06f * v
        scaleX = s
        scaleY = s
    }
}
