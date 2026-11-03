package com.karelisio.bulle.core.island

import android.content.Context

/** Ce que l'île met à disposition des contenus et des panneaux. */
interface IslandHost {
    val context: Context
    fun expand(key: String)
    fun collapse()
    fun dismiss(key: String)
    fun openPanel(panel: IslandPanel)
    fun closePanel()
    /** Rend l'île focusable pour une saisie clavier (recherche). */
    fun requestInput(enabled: Boolean)
}

/**
 * Panneau plein format affiché dans l'île étendue, indépendamment des contenus
 * (Deck, recherche d'applis…).
 */
interface IslandPanel {
    val id: String

    @androidx.compose.runtime.Composable
    fun Content(host: IslandHost)
}

/** Gestes reconnus sur l'île. */
enum class IslandGesture { TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_DOWN, SWIPE_UP }

/** Panneaux fournis par les modules (le Deck est enregistré par son module). */
object Panels {
    @Volatile
    var deck: IslandPanel? = null
}
