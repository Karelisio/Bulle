package com.karelisio.bulle.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appli au premier plan, déduite des seuls événements TYPE_WINDOW_STATE_CHANGED
 * (nom de paquet). Le contenu de l'écran n'est jamais lu.
 */
object ForegroundApp {
    private val _packageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = _packageName.asStateFlow()

    // Fenêtres qui ne sont pas « une appli ouverte » (clavier, volet système…).
    private val ignored = setOf("com.android.systemui")

    fun onWindowChanged(pkg: String?) {
        if (pkg.isNullOrBlank() || pkg in ignored) return
        _packageName.value = pkg
    }
}
