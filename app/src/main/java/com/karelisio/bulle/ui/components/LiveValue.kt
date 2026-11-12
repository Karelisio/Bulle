package com.karelisio.bulle.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Valeur de curseur affichée localement pendant le glissement, et écrite dans DataStore
 * de façon conflatée (seule la dernière valeur en attente est écrite).
 *
 * Avant : chaque onValueChange lançait une écriture DataStore complète (JSON + fsync) ; un
 * glissement rapide empilait des dizaines d'écritures et le curseur, lié à la valeur relue
 * de façon asynchrone, sautait en arrière.
 */
@Stable
class LiveValue internal constructor() {
    internal var local by mutableStateOf<Float?>(null)
    internal var dragging by mutableStateOf(false)
    internal var writing = false
    internal val pending = MutableStateFlow<Float?>(null)
    internal var stored = 0f

    /** Valeur à afficher : la valeur en cours de glissement, sinon la valeur enregistrée. */
    val value: Float get() = local ?: stored

    /** Nouvelle valeur (glissement ou bouton −/+). */
    fun set(v: Float) {
        local = v
        writing = true
        pending.value = v
    }

    /** onValueChange d'un curseur. */
    fun drag(v: Float) {
        dragging = true
        set(v)
    }

    /** onValueChangeFinished d'un curseur. */
    fun finish() {
        dragging = false
        settle()
    }

    internal fun settle() {
        if (!dragging && !writing && local == stored) local = null
    }
}

@Composable
fun rememberLiveValue(stored: Float, persist: suspend (Float) -> Unit): LiveValue {
    val live = remember { LiveValue() }
    live.stored = stored
    val write by rememberUpdatedState(persist)
    LaunchedEffect(live) {
        live.pending.filterNotNull().collect { v ->
            try {
                write(v)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Écriture impossible (disque) : on garde la valeur affichée, sans planter l'écran.
            }
            // Remise à null : une même valeur reposée plus tard doit être réémise (StateFlow déduplique).
            if (live.pending.compareAndSet(v, null)) {
                live.writing = false
                live.settle()
            }
        }
    }
    // Changement de la valeur enregistrée (écriture terminée, réinitialisation externe…).
    LaunchedEffect(stored) {
        if (!live.dragging && !live.writing) live.local = null
    }
    return live
}
