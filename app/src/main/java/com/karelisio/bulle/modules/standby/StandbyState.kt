package com.karelisio.bulle.modules.standby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dernier « pense à toi » reçu d'Écho (page Écho de Veille). */
data class EchoThought(val title: String?, val text: String?, val at: Long)

/** État partagé de Veille (lu par le Halo, les Bords, l'île, la tuile et l'écran Veille). */
object StandbyState {
    private val _active = MutableStateFlow(false)
    /** Veille est affichée (activité de secours ou économiseur d'écran). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _dreaming = MutableStateFlow(false)
    /** L'économiseur d'écran du système (DreamService) affiche Veille. */
    val dreaming: StateFlow<Boolean> = _dreaming.asStateFlow()

    private val _conditions = MutableStateFlow(false)
    /** En charge et à l'horizontale (calculé par le module qui tourne dans le service). */
    val conditions: StateFlow<Boolean> = _conditions.asStateFlow()

    private val _keep = MutableStateFlow(true)
    /**
     * Veille peut rester affichée : toujours branchée (bon chargeur) et pas revenue à la verticale.
     * Une notification, l'écran de verrouillage ou l'appli au premier plan n'y changent rien.
     */
    val keep: StateFlow<Boolean> = _keep.asStateFlow()

    private val _echo = MutableStateFlow<EchoThought?>(null)
    /** Dernier « pense à toi » d'Écho. */
    val echo: StateFlow<EchoThought?> = _echo.asStateFlow()

    internal fun setActive(on: Boolean) {
        _active.value = on
    }

    internal fun setDreaming(on: Boolean) {
        _dreaming.value = on
    }

    internal fun setConditions(on: Boolean) {
        _conditions.value = on
    }

    internal fun setKeep(on: Boolean) {
        _keep.value = on
    }

    internal fun setEcho(e: EchoThought) {
        _echo.value = e
    }
}
