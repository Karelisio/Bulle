package com.karelisio.bulle.modules.volet.notifications

import android.os.SystemClock
import android.service.notification.StatusBarNotification
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Ligne de la liste (en-tête média, titre de section, groupe, bouton « Tout effacer »). */
private sealed interface ListRow {
    val key: String

    data object Header : ListRow { override val key = "header" }
    data object Empty : ListRow { override val key = "empty" }
    data class Section(val section: NotifSection) : ListRow { override val key = "section|$section" }

    /** [clearIndex] : rang parmi les groupes effaçables (cascade de « Tout effacer »). */
    data class Group(val group: NotifGroup, val index: Int, val clearIndex: Int) : ListRow { override val key = group.id }
    data object ClearAll : ListRow { override val key = "clear" }
}

private fun rows(model: NotifListModel): List<ListRow> = buildList {
    add(ListRow.Header)
    if (model.isEmpty) add(ListRow.Empty)
    var index = 0
    var clearIndex = 0
    model.sections.forEach { s ->
        add(ListRow.Section(s.section))
        s.groups.forEach { add(ListRow.Group(it, index++, if (it.clearable) clearIndex++ else 0)) }
    }
    if (model.clearableKeys.isNotEmpty()) add(ListRow.ClearAll)
}

/** Dernier modèle construit, réutilisé à l'ouverture suivante (aucun parsing pendant l'animation). */
private object NotifModelCache {
    private class Entry(val active: Any, val ranking: Int, val locked: Boolean, val model: NotifListModel)

    @Volatile
    private var entry: Entry? = null

    fun get(active: Any, ranking: Int, locked: Boolean): NotifListModel? =
        entry?.takeIf { it.active === active && it.ranking == ranking && it.locked == locked }?.model

    fun put(active: Any, ranking: Int, locked: Boolean, model: NotifListModel) {
        entry = Entry(active, ranking, locked, model)
    }
}

private fun buildModel(host: VoletHost, active: Map<String, StatusBarNotification>, locked: Boolean): NotifListModel =
    runCatching {
        NotifModelFactory.build(
            host.context,
            active.values,
            ranking = { key -> NotificationRepository.ranking(key)?.let(RankInfo::from) },
            locked = locked,
            // Les applis exclues désactivent seulement l'ouverture du Volet, pas leurs notifications.
            excludedApps = emptySet(),
        )
    }.getOrDefault(NotifListModel.EMPTY)

/**
 * Modèle de la liste, recalculé à chaque publication, retrait ou changement de classement.
 * La reconstruction (lecture des extras, noms d'applis) se fait hors du fil principal ; seule
 * la toute première sans cache est synchrone, pour ne pas ouvrir un volet vide.
 */
@Composable
internal fun rememberNotifListModel(host: VoletHost): NotifListModel {
    val active by NotificationRepository.active.collectAsState()
    val rankingVersion by NotificationRepository.rankingVersion.collectAsState()
    val locked = host.locked
    val initial = remember {
        NotifModelCache.get(active, rankingVersion, locked)
            ?: buildModel(host, active, locked).also { NotifModelCache.put(active, rankingVersion, locked, it) }
    }
    val state = produceState(initial, active, rankingVersion, locked) {
        val cached = NotifModelCache.get(active, rankingVersion, locked)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val built = withContext(Dispatchers.Default) { buildModel(host, active, locked) }
        NotifModelCache.put(active, rankingVersion, locked, built)
        value = built
    }
    return state.value
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
    val settings = host.settings
    val dims = remember(settings.effectiveCardDensity, settings.cornerRadiusDp, settings.cardTextScale, settings.cardCollapsedLines) {
        NotifDims.of(settings)
    }
    val reduce = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Suppression optimiste : clés balayées (avec leur heure) retirées du modèle affiché tout de suite.
    var hidden by remember { mutableStateOf(emptyMap<String, Long>()) }
    val shown = remember(model, hidden) { NotifOptimistic.apply(model, hidden.keys) }
    val activeKeys = remember(model) {
        buildSet {
            model.sections.forEach { s ->
                s.groups.forEach { g ->
                    g.items.forEach { add(it.key) }
                    g.summaryKey?.let(::add)
                }
            }
        }
    }
    LaunchedEffect(hidden, activeKeys) {
        val r = NotifOptimistic.reconcile(hidden, activeKeys, SystemClock.uptimeMillis())
        if (r.hidden != hidden) {
            hidden = r.hidden
            return@LaunchedEffect
        }
        val next = r.nextCheckAt ?: return@LaunchedEffect
        delay((next - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        // Toujours active après le délai : le système a refusé, la carte revient.
        hidden = NotifOptimistic.reconcile(hidden, activeKeys, SystemClock.uptimeMillis()).hidden
    }
    val dismiss: (List<String>) -> Unit = remember {
        { keys ->
            hidden = NotifOptimistic.hide(hidden, keys, SystemClock.uptimeMillis())
            keys.forEach(NotifCommands::cancel)
        }
    }

    val rows = remember(shown) { rows(shown) }
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
        val group = shown.groupOf(key) ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is ListRow.Group && it.group.id == group.id }
        if (index < 0) return@LaunchedEffect
        handledFocus = key
        if (group.items.size > 1) expandedGroups = expandedGroups + group.id
        runCatching { if (reduce) listState.scrollToItem(index) else listState.animateScrollToItem(index) }
    }
    // « Tout effacer » : cascade de sortie rapide (décalage par carte), puis retrait optimiste de tout.
    LaunchedEffect(clearing) {
        if (!clearing) return@LaunchedEffect
        val count = rows.count { it is ListRow.Group && it.group.clearable }
        if (!reduce) delay(ClearCascade.totalMs(count))
        dismiss(shown.clearableKeys)
        clearing = false
    }

    val placement = if (reduce) null else spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
    val fade = if (reduce) null else spring<Float>(stiffness = Spring.StiffnessMediumLow)
    // Sortie : fondu court ; l'espace se referme au ressort (placement des cartes du dessous).
    val fadeOut = if (reduce) null else spring<Float>(stiffness = Spring.StiffnessMedium)

    // Taille du texte des cartes : échelle de police locale au volet (rien ne change à 100 %).
    val baseDensity = LocalDensity.current
    val listDensity = remember(baseDensity, dims.textScale) {
        if (dims.textScale == 1f) baseDensity else Density(baseDensity.density, baseDensity.fontScale * dims.textScale)
    }
    CompositionLocalProvider(LocalDensity provides listDensity) {
        LazyColumn(
            modifier = modifier,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(dims.gap),
        ) {
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    ListRow.Header -> header()
                    ListRow.Empty -> EmptyState(Modifier.animateItem(fade, placement, fadeOut).cascade(0))
                    is ListRow.Section -> SectionTitle(row.section, dims, Modifier.animateItem(fade, placement, fadeOut).cascade(row.section.ordinal))
                    is ListRow.Group -> {
                        val group = row.group
                        val isNew = group.items.none { it.key in initialKeys }
                        NotifGroupCard(
                            group = group,
                            host = host,
                            dims = dims,
                            expanded = group.id in expandedGroups,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                expandedGroups = if (group.id in expandedGroups) expandedGroups - group.id else expandedGroups + group.id
                            },
                            focusKey = focusKey,
                            clearing = clearing,
                            clearDelayMs = ClearCascade.delayMs(row.clearIndex),
                            onDismissKeys = dismiss,
                            modifier = Modifier
                                .animateItem(null, placement, fadeOut)
                                .then(if (isNew) Modifier.dropIn() else Modifier.cascade(row.index + 1)),
                        )
                    }
                    ListRow.ClearAll -> Box(Modifier.fillMaxWidth().animateItem(fade, placement, fadeOut), contentAlignment = Alignment.CenterEnd) {
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
}

/** Cascade de « Tout effacer » : chaque carte part un peu après la précédente (durée bornée). */
internal object ClearCascade {
    const val STEP_MS = 30L
    const val MAX_STAGGER_MS = 240L
    const val EXIT_MS = 220L

    fun delayMs(index: Int): Long = (index.coerceAtLeast(0) * STEP_MS).coerceAtMost(MAX_STAGGER_MS)
    fun totalMs(count: Int): Long = delayMs(count - 1) + EXIT_MS
}

@Composable
private fun SectionTitle(section: NotifSection, dims: NotifDims, modifier: Modifier) {
    val label = when (section) {
        NotifSection.CONVERSATIONS -> R.string.notif_section_conversations
        NotifSection.ALERTING -> R.string.notif_section_alerting
        NotifSection.SILENT -> R.string.notif_section_silent
    }
    Text(
        stringResource(label),
        style = if (dims.compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = dims.sectionTop),
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
