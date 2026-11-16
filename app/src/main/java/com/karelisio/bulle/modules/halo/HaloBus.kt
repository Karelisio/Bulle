package com.karelisio.bulle.modules.halo

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Origine d'un halo (couleur et forme dédiées pour Écho et la charge). */
enum class HaloKind { NOTIFICATION, MUSIC, ECHO, CHARGE, TEST }

/** Un halo à jouer. */
data class HaloRequest(
    val kind: HaloKind,
    /** Couleur ARGB. */
    val color: Int,
    val style: HaloStyle,
    /** Charge : niveau de la batterie (0..1) ; 1 sinon. */
    val level: Float = 1f,
    val packageName: String? = null,
    val id: Long = SystemClock.uptimeMillis(),
)

/**
 * Point de rendez-vous du Halo : l'appli de réglages (aperçu, test), l'écran Fiabilité, les gestes
 * et l'activité « écran éteint » parlent au module qui tourne dans le service d'accessibilité.
 */
object HaloBus {
    private val _tests = MutableSharedFlow<HaloStyle?>(extraBufferCapacity = 4)
    /** Demandes de halo de test (style imposé, ou null pour le style réglé). */
    val tests: SharedFlow<HaloStyle?> = _tests.asSharedFlow()

    private val _relock = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    /** L'activité « écran éteint » a fini : le service rééteint l'écran. */
    val relock: SharedFlow<Unit> = _relock.asSharedFlow()

    private val _running = MutableStateFlow(false)
    /** Le module tourne (service d'accessibilité relié). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Fait passer un halo de test (réglages, Fiabilité, geste « Halo test »). */
    fun test(style: HaloStyle? = null): Boolean = _tests.tryEmit(style)

    internal fun requestRelock() = _relock.tryEmit(Unit)

    internal fun setRunning(on: Boolean) {
        _running.value = on
    }
}
