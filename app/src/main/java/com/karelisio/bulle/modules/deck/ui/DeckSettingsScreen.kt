package com.karelisio.bulle.modules.deck.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.deck.DeckSettings
import com.karelisio.bulle.modules.deck.DeckTab
import com.karelisio.bulle.modules.deck.DeckWidgets
import com.karelisio.bulle.modules.deck.moved
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.settings.ActionList
import com.karelisio.bulle.ui.settings.AppList
import com.karelisio.bulle.ui.settings.icon
import com.karelisio.bulle.ui.settings.label
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.karelisio.bulle.modules.deck.DeckSection
import com.karelisio.bulle.modules.deck.reorder
import com.karelisio.bulle.modules.deck.withOrder
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.flow.map

@Composable
private fun rememberDeck(): Pair<DeckSettings, (suspend (DeckSettings) -> DeckSettings) -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val update: (suspend (DeckSettings) -> DeckSettings) -> Unit = { transform ->
        scope.launch {
            val next = transform(SettingsRepository.current(context).deck)
            SettingsRepository.update(context) { it.copy(deck = next) }
        }
    }
    return settings.deck to update
}

@Composable
private fun BackTopBar(title: String, onBack: () -> Unit, scroll: androidx.compose.material3.TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        scrollBehavior = scroll,
    )
}

/** Liste des onglets du Deck : ajout, réorganisation, suppression par glissement. */
@Composable
fun DeckSettingsScreen(onBack: () -> Unit, onOpenTab: (String) -> Unit) {
    val (deck, update) = rememberDeck()
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackTopBar(stringResource(R.string.deck_title), onBack, scroll) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { update { it.copy(tabs = it.tabs + DeckTab()) } },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.deck_add_tab)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.deck_themed_icons)) },
                    supportingContent = { Text(stringResource(R.string.deck_themed_icons_desc)) },
                    trailingContent = {
                        Switch(checked = deck.themedIcons, onCheckedChange = { on -> update { it.copy(themedIcons = on) } })
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.deck_search_bar)) },
                    supportingContent = { Text(stringResource(R.string.deck_search_bar_desc)) },
                    trailingContent = {
                        Switch(checked = deck.searchBar, onCheckedChange = { on -> update { it.copy(searchBar = on) } })
                    },
                )
                Text(
                    stringResource(R.string.deck_tabs_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(deck.tabs, key = { it.id }) { tab ->
                val index = deck.tabs.indexOf(tab)
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = dismiss,
                    modifier = Modifier.animateItem().cascade(index),
                    enableDismissFromStartToEnd = false,
                    onDismiss = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            tab.widgets.forEach { id -> DeckWidgets.delete(context, id) }
                            update { d -> d.copy(tabs = d.tabs.filterNot { t -> t.id == tab.id }) }
                        }
                    },
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().clip28().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                    },
                ) {
                    ListItem(
                        headlineContent = { Text(tab.name.ifBlank { stringResource(R.string.deck_tab_default, index + 1) }) },
                        supportingContent = {
                            Text(stringResource(R.string.deck_tab_summary, tab.favorites.size, tab.shortcuts.size, tab.widgets.size))
                        },
                        trailingContent = {
                            Row {
                                IconButton(enabled = index > 0, onClick = { update { d -> d.copy(tabs = d.tabs.move(index, index - 1)) } }) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                                }
                                IconButton(enabled = index < deck.tabs.lastIndex, onClick = { update { d -> d.copy(tabs = d.tabs.move(index, index + 1)) } }) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clip28().clickable { onOpenTab(tab.id) },
                    )
                }
            }
        }
    }
}

private fun Modifier.clip28() = this.clip(RoundedCornerShape(28.dp))

private fun <T> List<T>.move(from: Int, to: Int): List<T> = moved(from, to)

private enum class TabSheet { NONE, FAVORITE, SHORTCUT, WIDGET }

/** Édition d'un onglet : nom, favoris, raccourcis, widgets. */
@Composable
fun DeckTabScreen(tabId: String, onBack: () -> Unit) {
    val (deck, update) = rememberDeck()
    val context = LocalContext.current
    val haptics by remember { SettingsRepository.flow(context).map { it.motion.haptics } }.collectAsStateWithLifecycle(true)
    DeckTabScreenContent(deck, update, haptics, tabId, onBack)
}

/** Contenu de [DeckTabScreen], indépendant du stockage (testable). */
@Composable
internal fun DeckTabScreenContent(
    deck: DeckSettings,
    update: (suspend (DeckSettings) -> DeckSettings) -> Unit,
    haptics: Boolean,
    tabId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val tab = deck.tabs.firstOrNull { it.id == tabId } ?: return
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sheet by remember { mutableStateOf(TabSheet.NONE) }
    // rememberSaveable : l'activité peut être recréée (rotation, mort du processus) pendant
    // l'écran système de liaison/configuration ; sans cela le résultat était ignoré et l'ID fuyait.
    var pendingWidget by rememberSaveable { mutableStateOf<Int?>(null) }

    fun edit(transform: (DeckTab) -> DeckTab) = update { d -> d.copy(tabs = d.tabs.map { if (it.id == tabId) transform(it) else it }) }

    fun addWidget(id: Int) = edit { it.copy(widgets = it.widgets + id) }

    // Réorganisation des favoris et des widgets (glisser-déposer ou actions TalkBack).
    val listState = rememberLazyListState()
    val reorder = rememberDeckReorderState(listState)
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(tab.favorites, tab.widgets) { reorder.clearPending() }
    fun drop(section: DeckSection, order: List<String>) = update { it.withOrder(tabId, section, order) }

    // Configuration du widget si nécessaire (via l'hôte), sinon ajout direct.
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val id = pendingWidget ?: return@rememberLauncherForActivityResult
        if (r.resultCode == Activity.RESULT_OK) addWidget(id) else DeckWidgets.delete(context, id)
        pendingWidget = null
    }
    // Repli quand l'API cachée est bloquée : intention explicite vers l'activité de configuration.
    val configureDirect = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget ?: return@rememberLauncherForActivityResult
        if (r.resultCode == Activity.RESULT_OK) addWidget(id) else DeckWidgets.delete(context, id)
        pendingWidget = null
    }
    fun configureOrAdd(id: Int) {
        val info = DeckWidgets.info(context, id)
        val configureActivity = info?.configure
        val sender = if (configureActivity != null) {
            runCatching {
                DeckWidgets.host(context).let { host ->
                    // IntentSender de configuration fourni par l'hôte (activités non exportées comprises).
                    val m = host.javaClass.getMethod("getIntentSenderForConfigureActivity", Int::class.java, Int::class.java)
                    m.invoke(host, id, 0) as android.content.IntentSender
                }
            }.getOrNull()
        } else null
        try {
            when {
                sender != null -> {
                    pendingWidget = id
                    configure.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                }
                configureActivity != null -> {
                    // Sans ce repli, un widget exigeant une configuration était ajouté non configuré.
                    pendingWidget = id
                    configureDirect.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .setComponent(configureActivity)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    )
                }
                else -> addWidget(id)
            }
        } catch (e: Exception) {
            // Activité absente ou non exportée : on libère l'ID alloué.
            pendingWidget = null
            DeckWidgets.delete(context, id)
        }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget ?: return@rememberLauncherForActivityResult
        pendingWidget = null
        if (r.resultCode == Activity.RESULT_OK) configureOrAdd(id) else DeckWidgets.delete(context, id)
    }
    fun pickWidget(provider: AppWidgetProviderInfo) {
        val id = DeckWidgets.allocate(context)
        val bound = runCatching { DeckWidgets.manager(context).bindAppWidgetIdIfAllowed(id, provider.provider) }
            .getOrDefault(false)
        if (bound) {
            configureOrAdd(id)
        } else {
            pendingWidget = id
            try {
                bind.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider),
                )
            } catch (e: Exception) {
                // Écran de liaison absent (ROM) : l'ID ne doit pas rester alloué.
                pendingWidget = null
                DeckWidgets.delete(context, id)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackTopBar(tab.name.ifBlank { stringResource(R.string.deck_tab_default, deck.tabs.indexOf(tab) + 1) }, onBack, scroll) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                OutlinedTextField(
                    value = tab.name,
                    onValueChange = { name -> edit { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.deck_tab_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            // Favoris
            item { SectionHeader(stringResource(R.string.deck_favorites)) { sheet = TabSheet.FAVORITE } }
            val favorites = reorder.displayed(DeckSection.FAVORITES, tab.favorites)
            itemsIndexed(favorites, key = { _, flat -> "f$flat" }) { i, flat ->
                val component = ComponentName.unflattenFromString(flat) ?: return@itemsIndexed
                val label = remember(flat) {
                    runCatching { context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString() }
                        .getOrDefault(component.packageName)
                }
                ReorderRow(
                    reorder, DeckSection.FAVORITES, "f", favorites, flat, i, haptics, reduceMotion,
                    onMove = { from, to -> update { it.reorder(tabId, DeckSection.FAVORITES, from, to) } },
                    onDrop = ::drop,
                    onDelete = { edit { it.copy(favorites = it.favorites - flat) } },
                    headline = label,
                    leading = { AppIcon(AppEntry(label, component.packageName, component), 40.dp, themed = deck.themedIcons) },
                )
            }
            // Raccourcis
            item { SectionHeader(stringResource(R.string.deck_shortcuts)) { sheet = TabSheet.SHORTCUT } }
            items(tab.shortcuts.withIndex().toList(), key = { "s${it.index}${it.value}" }) { (i, action) ->
                SwipeRow(Modifier.animateItem(), onDelete = { edit { t -> t.copy(shortcuts = t.shortcuts.filterIndexed { j, _ -> j != i }) } }) {
                    ListItem(
                        headlineContent = { Text(action.label()) },
                        leadingContent = { Icon(action.type.icon(), contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            // Widgets
            item { SectionHeader(stringResource(R.string.deck_widgets)) { sheet = TabSheet.WIDGET } }
            val widgets = reorder.displayed(DeckSection.WIDGETS, tab.widgets.map { it.toString() })
            itemsIndexed(widgets, key = { _, id -> "w$id" }) { i, key ->
                val id = key.toInt()
                val info = remember(id) { DeckWidgets.info(context, id) }
                ReorderRow(
                    reorder, DeckSection.WIDGETS, "w", widgets, key, i, haptics, reduceMotion,
                    onMove = { from, to -> update { it.reorder(tabId, DeckSection.WIDGETS, from, to) } },
                    onDrop = ::drop,
                    onDelete = {
                        DeckWidgets.delete(context, id)
                        edit { it.copy(widgets = it.widgets - id) }
                    },
                    headline = info?.loadLabel(context.packageManager) ?: "Widget #$id",
                    leading = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                )
            }
            item {
                Text(
                    stringResource(R.string.deck_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (sheet != TabSheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = TabSheet.NONE }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            when (sheet) {
                TabSheet.FAVORITE -> AppList { app ->
                    edit { it.copy(favorites = (it.favorites + app.component.flattenToString()).distinct()) }
                    sheet = TabSheet.NONE
                }
                TabSheet.SHORTCUT -> ActionList(current = null) { type ->
                    if (type != ActionType.OPEN_APP && type != ActionType.LAUNCH_SHORTCUT) {
                        edit { it.copy(shortcuts = it.shortcuts + GestureAction(type)) }
                    }
                    sheet = TabSheet.NONE
                }
                TabSheet.WIDGET -> WidgetPicker { provider ->
                    sheet = TabSheet.NONE
                    pickWidget(provider)
                }
                TabSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.deck_add))
        }
    }
}

/**
 * Ligne réordonnable : poignée (appui long + glisser) et actions TalkBack « Monter / Descendre ».
 * L'élément saisi suit le doigt (graphicsLayer), ses voisins glissent au ressort (animateItem).
 */
@Composable
private fun LazyItemScope.ReorderRow(
    state: DeckReorderState,
    section: DeckSection,
    keyPrefix: String,
    ids: List<String>,
    id: String,
    index: Int,
    haptics: Boolean,
    reduceMotion: Boolean,
    onMove: (Int, Int) -> Unit,
    onDrop: (DeckSection, List<String>) -> Unit,
    onDelete: () -> Unit,
    headline: String,
    leading: @Composable () -> Unit,
) {
    val dragging = state.isDragging(section, id)
    val lift by animateFloatAsState(
        if (dragging) 1f else 0f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lift",
    )
    val up = stringResource(R.string.deck_move_up)
    val down = stringResource(R.string.deck_move_down)
    val placement = if (dragging || reduceMotion) null else spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    SwipeRow(
        Modifier
            .animateItem(placementSpec = placement)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (dragging) state.offset else 0f
                val scale = 1f + 0.03f * lift
                scaleX = scale
                scaleY = scale
                shadowElevation = 12.dp.toPx() * lift
                shape = RoundedCornerShape(20.dp)
            }
            .semantics {
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(up) { onMove(index, index - 1); true })
                    if (index < ids.lastIndex) add(CustomAccessibilityAction(down) { onMove(index, index + 1); true })
                }
            },
        onDelete = onDelete,
    ) {
        ListItem(
            headlineContent = { Text(headline) },
            leadingContent = leading,
            trailingContent = {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.deck_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp)
                        .deckDragHandle(state, section, keyPrefix, ids, id, haptics, onDrop),
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
private fun SwipeRow(modifier: Modifier, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) { Box(Modifier.animateContentSize()) { content() } }
}

@Composable
private fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val providers = remember { DeckWidgets.providers(context) }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                stringResource(R.string.deck_pick_widget),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        items(providers, key = { it.provider.flattenToShortString() }) { p ->
            val appLabel = remember(p) {
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }
                    .getOrDefault(p.provider.packageName)
            }
            ListItem(
                headlineContent = { Text(p.loadLabel(context.packageManager)) },
                supportingContent = { Text(appLabel) },
                leadingContent = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { onPick(p) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}
