package com.karelisio.bulle.modules.deck

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
        val scope = rememberCoroutineScope()

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

        Column(Modifier.fillMaxWidth()) {
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
                DeckTabContent(tabs[page], deck.themedIcons, ::run) { host.closePanel(); host.collapse() }
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
                DeckWidgets.createView(ctx, id)!!.apply { setPadding(0, 0, 0, 0) }
            },
            update = { view ->
                val w = view.width / density.density
                if (w > 0) view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, heightDp.value)))
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
