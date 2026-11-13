package com.karelisio.bulle.modules.focusmode

import android.content.Context
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.island.ForegroundApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import java.util.Calendar

/** Ce qui a activé le mode focus. */
enum class FocusSource { MANUAL, RULE, APP, SCHEDULE }

/** État du mode focus. [automatic] = source automatique en cours (même si l'utilisateur l'a coupée). */
data class FocusState(
    val active: Boolean = false,
    val source: FocusSource? = null,
    val automatic: FocusSource? = null,
    val settings: FocusModeSettings = FocusModeSettings(),
) {
    companion object {
        val Off = FocusState()
    }
}

/**
 * Choix manuel de l'utilisateur, valable tant que la source automatique ne change pas :
 * couper un focus planifié le coupe jusqu'au prochain changement, pas pour toujours.
 */
data class ManualOverride(val on: Boolean, val automaticWhenSet: Boolean)

/** Logique pure du mode focus (testée en JUnit). */
object FocusLogic {

    fun previousDay(isoDay: Int): Int = if (isoDay == 1) 7 else isoDay - 1

    fun inSlot(slot: FocusSlot, isoDay: Int, minute: Int): Boolean {
        if (!slot.enabled) return false
        val s = slot.startMinute
        val e = slot.endMinute
        return when {
            s == e -> isoDay in slot.days
            s < e -> isoDay in slot.days && minute in s until e
            // Passe minuit : soir du jour de début, ou matin du lendemain.
            else -> (isoDay in slot.days && minute >= s) || (previousDay(isoDay) in slot.days && minute < e)
        }
    }

    /** Source automatique en cours, par ordre : règle, appli, créneau. */
    fun automatic(settings: FocusModeSettings, rule: Boolean, foreground: String?, isoDay: Int, minute: Int): FocusSource? = when {
        rule -> FocusSource.RULE
        foreground != null && foreground in settings.apps -> FocusSource.APP
        settings.slots.any { inSlot(it, isoDay, minute) } -> FocusSource.SCHEDULE
        else -> null
    }

    fun resolve(settings: FocusModeSettings, automatic: FocusSource?, override: ManualOverride?): FocusState {
        val o = override?.takeIf { it.automaticWhenSet == (automatic != null) }
        return when {
            o != null && o.on -> FocusState(true, automatic ?: FocusSource.MANUAL, automatic, settings)
            o != null -> FocusState(false, null, automatic, settings)
            automatic != null -> FocusState(true, automatic, automatic, settings)
            else -> FocusState(false, null, null, settings)
        }
    }

    /** Contenu essentiel : montré même pendant le focus. */
    fun isEssential(c: IslandContent, s: FocusModeSettings): Boolean {
        // Appel entrant & co. : toujours montrés.
        if (c.showWhenHidden) return true
        return when (s.modeOf(c.moduleId)) {
            FocusModuleMode.ALWAYS -> true
            FocusModuleMode.NEVER -> false
            FocusModuleMode.AUTO -> c.priority.value >= s.minPriority
        }
    }

    /**
     * Contenus visibles : tout hors focus ; pendant le focus, seulement l'essentiel. Les autres
     * sont retenus (pas supprimés) : ils réapparaissent dès que le focus se termine.
     */
    fun visible(contents: List<IslandContent>, state: FocusState): List<IslandContent> =
        if (!state.active) contents else contents.filter { isEssential(it, state.settings) }

    /** Contenus retenus en ce moment. */
    fun held(contents: List<IslandContent>, state: FocusState): List<IslandContent> =
        if (!state.active) emptyList() else contents.filterNot { isEssential(it, state.settings) }
}

/**
 * Mode focus : l'île ne montre que l'essentiel pendant un créneau, dans certaines applis,
 * quand une règle le demande ou à la demande (action de geste).
 */
object FocusMode {
    private val override = MutableStateFlow<ManualOverride?>(null)
    private val ruleRequest = MutableStateFlow(false)
    private val _state = MutableStateFlow(FocusState.Off)

    /** Dernier état calculé (publié tant que l'île tourne). */
    val state: StateFlow<FocusState> = _state.asStateFlow()

    /** Bascule manuelle (action de geste FOCUS_MODE, écran de réglages). */
    fun toggle() {
        val cur = _state.value
        val o = ManualOverride(on = !cur.active, automaticWhenSet = cur.automatic != null)
        override.value = o
        // Retour immédiat (écran de réglages) ; recalculé ensuite par [observe].
        _state.value = FocusLogic.resolve(cur.settings, cur.automatic, o)
    }

    /** Demande du moteur de règles (action « Mode focus »). */
    fun setRuleRequest(on: Boolean) {
        ruleRequest.value = on
    }

    /** Flux de l'état du mode focus ; publie aussi [state]. */
    fun observe(context: Context): Flow<FocusState> = combine(
        FocusModePrefs.loaded(context), override, ruleRequest, ForegroundApp.packageName, minuteTicks(),
    ) { s, o, rule, fg, (day, minute) ->
        FocusLogic.resolve(s, FocusLogic.automatic(s, rule, fg, day, minute), o)
    }.distinctUntilChanged().onEach { _state.value = it }

    internal fun resetForTests() {
        override.value = null
        ruleRequest.value = false
        _state.value = FocusState.Off
    }

    /** (jour ISO, minute du jour), émis à chaque minute pile. */
    private fun minuteTicks(): Flow<Pair<Int, Int>> = flow {
        while (true) {
            val c = Calendar.getInstance()
            val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            emit(day to c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
            delay(60_000L - (c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND)) + 50)
        }
    }.distinctUntilChanged()
}
