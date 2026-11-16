package com.karelisio.bulle.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Notifications masquées côté Bulle (en mémoire) : celles que le système refuse d'effacer
 * (service de premier plan, « en cours »…) disparaissent du Volet sans être annulées.
 * Une clé est oubliée dès que la notification disparaît vraiment.
 */
object NotifLocalHide {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun hide(keys: Collection<String>) {
        if (keys.isNotEmpty()) _keys.update { it + keys }
    }

    fun show(keys: Collection<String>) {
        if (keys.isNotEmpty()) _keys.update { it - keys.toSet() }
    }

    /** La notification [key] a disparu : inutile de la masquer encore. */
    fun forget(key: String) {
        if (key in _keys.value) _keys.update { it - key }
    }

    /** Ne garde que les clés encore actives (reconnexion du listener). */
    fun retain(active: Set<String>) {
        _keys.update { pruned(it, active) }
    }

    fun pruned(hidden: Set<String>, active: Set<String>): Set<String> =
        if (hidden.all { it in active }) hidden else hidden.filterTo(HashSet()) { it in active }

    fun resetForTests() {
        _keys.value = emptySet()
    }
}
