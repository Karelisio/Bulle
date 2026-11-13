package com.karelisio.bulle.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Écrans de Bulle qui recouvrent l'île (ex. paroles plein écran) : tant qu'au moins un est
 * affiché, l'île est masquée, même pour un contenu prioritaire.
 */
object IslandOcclusion {
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun set(tag: String, shown: Boolean) {
        _active.update { if (shown) it + tag else it - tag }
    }
}
