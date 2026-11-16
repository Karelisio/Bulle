package com.karelisio.bulle.modules.edges

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rendez-vous entre l'appli (réglages, Fiabilité) et le module Bords qui tourne dans le service. */
object EdgeBus {
    private val _preview = MutableStateFlow(false)
    /** Aperçu coloré des zones (écran de réglages ouvert). */
    val preview: StateFlow<Boolean> = _preview.asStateFlow()

    private val _flashUntil = MutableStateFlow(0L)
    /** Affichage temporaire des zones (test de l'écran Fiabilité), jusqu'à cet instant (uptime). */
    val flashUntil: StateFlow<Long> = _flashUntil.asStateFlow()

    private val _placed = MutableStateFlow<List<PlacedZone>>(emptyList())
    /** Zones réellement posées (décalées si elles touchaient le Volet ou la barre de navigation). */
    val placed: StateFlow<List<PlacedZone>> = _placed.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setPreview(on: Boolean) {
        _preview.value = on
    }

    /** Montre les zones en couleur pendant [ms] (même si le module est coupé). */
    fun flash(ms: Long = 5_000L) {
        _flashUntil.value = SystemClock.uptimeMillis() + ms
    }

    internal fun publish(zones: List<PlacedZone>) {
        _placed.value = zones
    }

    internal fun setRunning(on: Boolean) {
        _running.value = on
        if (!on) _placed.value = emptyList()
    }
}
