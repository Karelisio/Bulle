package com.karelisio.bulle.modules.deck

import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import kotlinx.serialization.Serializable
import java.util.UUID

/** Un onglet du Deck : applis favorites, grille de raccourcis, widgets hébergés. */
@Serializable
data class DeckTab(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Composants d'activités (ComponentName.flattenToString). */
    val favorites: List<String> = emptyList(),
    val shortcuts: List<GestureAction> = DefaultShortcuts,
    /** Identifiants AppWidget alloués par l'hôte du Deck. */
    val widgets: List<Int> = emptyList(),
)

@Serializable
data class DeckSettings(
    val tabs: List<DeckTab> = listOf(DeckTab(id = "main")),
    /** Icônes thématiques (monochromes teintées) quand l'appli en fournit. */
    val themedIcons: Boolean = true,
    /** Barre de recherche intégrée en haut du Deck (réutilise la recherche de l'île). */
    val searchBar: Boolean = true,
)

/** Listes réordonnables d'un onglet (l'ordre des identifiants est l'ordre d'affichage). */
enum class DeckSection { FAVORITES, WIDGETS }

/**
 * Déplace l'élément en position [from] vers la position [to] (fonction pure).
 * Indices hors limites ou identiques : la liste est rendue telle quelle.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val m = toMutableList()
    m.add(to, m.removeAt(from))
    return m
}

/** Réordonne la section [section] de l'onglet [tabId] ; les autres onglets restent intacts. */
fun DeckSettings.reorder(tabId: String, section: DeckSection, from: Int, to: Int): DeckSettings =
    copy(
        tabs = tabs.map { tab ->
            if (tab.id != tabId) tab
            else when (section) {
                DeckSection.FAVORITES -> tab.copy(favorites = tab.favorites.moved(from, to))
                DeckSection.WIDGETS -> tab.copy(widgets = tab.widgets.moved(from, to))
            }
        },
    )

/**
 * Applique un ordre complet (fin de glisser-déposer). Ignoré si [order] ne contient pas exactement
 * les mêmes éléments que la liste enregistrée (elle a changé entre-temps).
 */
fun DeckSettings.withOrder(tabId: String, section: DeckSection, order: List<String>): DeckSettings =
    copy(
        tabs = tabs.map { tab ->
            if (tab.id != tabId) tab
            else when (section) {
                DeckSection.FAVORITES ->
                    if (order.sorted() == tab.favorites.sorted()) tab.copy(favorites = order) else tab
                DeckSection.WIDGETS -> {
                    val ids = order.mapNotNull { it.toIntOrNull() }
                    if (ids.size == order.size && ids.sorted() == tab.widgets.sorted()) tab.copy(widgets = ids) else tab
                }
            }
        },
    )

val DefaultShortcuts = listOf(
    GestureAction(ActionType.TORCH),
    GestureAction(ActionType.SCREENSHOT),
    GestureAction(ActionType.WIFI),
    GestureAction(ActionType.BLUETOOTH),
    GestureAction(ActionType.ROTATION),
    GestureAction(ActionType.LOCK_SCREEN),
    GestureAction(ActionType.APP_SEARCH),
    GestureAction(ActionType.QUICK_SETTINGS),
)
