package com.karelisio.bulle.modules.deck

import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.os.Bundle
import android.util.SizeF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.settings.icon
import com.karelisio.bulle.ui.settings.label
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.karelisio.bulle.modules.search.SearchPanelContent
import com.karelisio.bulle.ui.components.rememberReduceMotion

/** Le Deck : carte déroulante avec onglets (applis favorites, raccourcis, widgets). */
object DeckPanel : IslandPanel {
    override val id = "deck"

    /** Actions qui quittent l'île : on referme le Deck avant de les lancer. */
    private val leaving = setOf(
        ActionType.OPEN_APP, ActionType.LAUNCH_SHORTCUT, ActionType.HOME, ActionType.BACK, ActionType.RECENTS,
        ActionType.NOTIFICATIONS, ActionType.QUICK_SETTINGS, ActionType.LOCK_SCREEN, ActionType.SCREENSHOT,
        ActionType.WIFI, ActionType.BLUETOOTH, ActionType.BATTERY_SAVER,
    )

    @Composable
    override fun Content(host: IslandHost) {
        val context = host.context
        val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
        val deck = settings.deck
        val tabs = deck.tabs.ifEmpty { listOf(DeckTab(id = "main")) }
        val pager = rememberPagerState { tabs.size }

        DisposableEffect(Unit) {
            DeckWidgets.startListening(context)
            onDispose { DeckWidgets.stopListening(context) }
        }

        fun run(action: GestureAction) {
            if (action.type in leaving) {
                host.closePanel()
                host.collapse()
            }
            host.runAction(action)
        }

        // Recherche intégrée : la barre ouvre, dans le Deck même, le contenu du panneau de recherche
        // (applis, calculs, conversions, devises, contacts, réglages) sans le dupliquer.
        var searching by remember { mutableStateOf(false) }
        val reduceMotion = rememberReduceMotion()
        AnimatedContent(
            targetState = searching && deck.searchBar,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMedium))
                }
            },
            label = "deckSearch",
        ) { inSearch ->
            if (inSearch) {
                DeckSearch(host) { searching = false }
            } else {
                DeckHome(host, deck, tabs, pager, ::run, onSearch = { searching = true })
            }
        }
    }

    @Composable
    private fun DeckSearch(host: IslandHost, onClose: () -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            SearchPanelContent(host)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.deck_search_close), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }

    /** Barre de recherche (non éditable) : la toucher bascule le Deck en mode recherche. */
    @Composable
    private fun SearchBar(onClick: () -> Unit) {
        val label = stringResource(R.string.deck_search_hint)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun DeckHome(
        host: IslandHost,
        deck: DeckSettings,
        tabs: List<DeckTab>,
        pager: PagerState,
        run: (GestureAction) -> Unit,
        onSearch: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth()) {
            if (deck.searchBar) SearchBar(onSearch)
            if (tabs.size > 1) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEachIndexed { i, tab ->
                        val selected = pager.currentPage == i
                        val bg by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.7f), label = "tab")
                        Text(
                            tab.name.ifBlank { stringResource(R.string.deck_tab_default, i + 1) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.6f + 0.4f * bg),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * bg))
                                .clickable { scope.launch { pager.animateScrollToPage(i) } }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            HorizontalPager(state = pager, pageSpacing = 24.dp, verticalAlignment = Alignment.Top) { page ->
                DeckTabContent(tabs[page], deck.themedIcons, run) { host.closePanel(); host.collapse() }
            }
        }
    }

    @Composable
    private fun DeckTabContent(tab: DeckTab, themed: Boolean, run: (GestureAction) -> Unit, leave: () -> Unit) {
        val context = LocalContext.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Applis favorites.
            val favorites = remember(tab.favorites) {
                tab.favorites.mapNotNull { ComponentName.unflattenFromString(it) }
            }
            if (favorites.isEmpty()) {
                Text(
                    stringResource(R.string.deck_no_favorites),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    favorites.take(6).forEachIndexed { i, component ->
                        val entry = remember(component) {
                            val label = runCatching {
                                context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString()
                            }.getOrDefault(component.packageName)
                            AppEntry(label, component.packageName, component)
                        }
                        Pressable(onClick = {
                            leave()
                            AppCatalog.launch(context, entry)
                        }, modifier = Modifier.cascade(i)) {
                            AppIcon(entry, 48.dp, themed = themed)
                        }
                    }
                }
            }
            // Grille de raccourcis (4 colonnes).
            tab.shortcuts.chunked(4).forEachIndexed { row, items ->
                Row(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { col, action ->
                        Pressable(onClick = { run(action) }, modifier = Modifier.weight(1f).cascade(6 + row * 4 + col)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(action.type.icon(), contentDescription = null) }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    action.label(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                    repeat(4 - items.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Widgets hébergés.
            tab.widgets.forEachIndexed { i, id -> DeckWidget(id, Modifier.cascade(14 + i)) }
        }
    }

    @Composable
    private fun DeckWidget(id: Int, modifier: Modifier) {
        val context = LocalContext.current
        val info = remember(id) { DeckWidgets.info(context, id) } ?: return
        val density = LocalDensity.current
        val heightDp = with(density) { (info.minHeight.coerceAtLeast(1)).toDp() }.coerceIn(72.dp, 240.dp)
        AndroidView(
            factory = { ctx ->
                // Le widget peut avoir été supprimé (appli désinstallée) entre-temps : vue vide plutôt que NPE.
                val view = runCatching { DeckWidgets.createView(ctx, id) }.getOrNull()
                    ?: return@AndroidView android.view.View(ctx)
                view.apply {
                    setPadding(0, 0, 0, 0)
                    // La largeur n'est connue qu'après la mise en page (0 dans `update` au premier passage) :
                    // on transmet la taille au fournisseur à chaque changement de largeur.
                    addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ ->
                        val w = (r - l) / v.resources.displayMetrics.density
                        if (r - l > 0 && r - l != or - ol) {
                            runCatching {
                                (v as AppWidgetHostView).updateAppWidgetSize(Bundle(), listOf(SizeF(w, heightDp.value)))
                            }
                        }
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(heightDp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )
    }

    /** Élément qui s'enfonce au toucher (ressort). */
    @Composable
    private fun Pressable(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(16.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
