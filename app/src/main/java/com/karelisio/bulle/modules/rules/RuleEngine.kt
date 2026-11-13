package com.karelisio.bulle.modules.rules

import android.content.Context
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.island.ForegroundApp
import com.karelisio.bulle.modules.focusmode.FocusMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Moteur de règles : observe l'environnement ([RuleWorldSources]), publie les effets des règles
 * actives et lance les actions au moment où une règle s'active. L'action « Mode focus » est
 * transmise directement à [FocusMode].
 */
class RuleEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val rules: StateFlow<List<Rule>>,
    private val onEffects: (RuleEffects) -> Unit,
    private val onRun: (GestureAction) -> Unit,
) {
    private val sources = RuleWorldSources(context, scope, rules)
    private var job: Job? = null
    private var activeIds = emptySet<String>()

    fun start() {
        if (job != null) return
        sources.start()
        job = scope.launch {
            combine(rules, sources.world, ForegroundApp.packageName) { r, w, fg -> r to w.copy(foregroundPackage = fg) }
                .collect { (r, w) ->
                    val effects = RuleLogic.effects(r, w)
                    onEffects(effects)
                    FocusMode.setRuleRequest(effects.focus)
                    // Front montant : lancer les actions des règles qui viennent de s'activer.
                    val (nowActive, runs) = RuleLogic.risingRuns(r, activeIds, w)
                    runs.forEach(onRun)
                    activeIds = nowActive
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        sources.stop()
        FocusMode.setRuleRequest(false)
    }
}
