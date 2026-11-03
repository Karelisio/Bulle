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
