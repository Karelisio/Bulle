package com.karelisio.bulle.modules.focus

import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Module « Minuteur » : minuteur simple ou Pomodoro lancé depuis l'île (panneau [FocusPanel]).
 * Invisible tant qu'aucun minuteur ne tourne.
 */
object FocusModule : IslandModule {
    const val ID = "focus"
    override val id = ID
    override val titleRes = R.string.module_focus

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = channelFlow {
        val context = env.context.applicationContext
        Focus.ensureLoaded(context)
        val states = Focus.data.map { it.state }.distinctUntilChanged()

        // Fin de phase : on attend l'heure prévue (horloge murale) puis on enchaîne.
        // Une fin passée pendant l'arrêt du service est traitée dès la reprise.
        launch {
            states.collectLatest { st ->
                if (st?.running != true) return@collectLatest
                delay(FocusTimer.remaining(st, System.currentTimeMillis()) + 20)
                while (Focus.data.value.state == st && !Focus.completeIfDue(context, env.settings.value.motion.haptics)) {
                    delay(250)
                }
            }
        }

        // postedAt stable tant que le module tourne : pas de réordonnancement à chaque seconde.
        val since = SystemClock.elapsedRealtime()
        combine(states, Focus.alert) { st, alert ->
            listOfNotNull<IslandContent>(
                st?.let { FocusContent(it, postedAt = since) },
                alert?.let { FocusAlertContent(it) },
            )
        }.collect { send(it) }
    }
}
