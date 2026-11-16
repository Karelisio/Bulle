package com.karelisio.bulle.modules.standby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État partagé de Veille (lu par le Halo, l'île et la tuile). */
object StandbyState {
    private val _active = MutableStateFlow(false)
    /** Veille est affichée (activité de secours ou économiseur d'écran). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _dreaming = MutableStateFlow(false)
    /** L'économiseur d'écran du système (DreamService) affiche Veille. */
    val dreaming: StateFlow<Boolean> = _dreaming.asStateFlow()

    internal fun setActive(on: Boolean) {
        _active.value = on
    }

    internal fun setDreaming(on: Boolean) {
        _dreaming.value = on
    }
}
