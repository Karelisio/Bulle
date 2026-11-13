package com.karelisio.bulle.modules.screentime

import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Module « Temps d'écran » (désactivé par défaut) :
 *  - rappel de pause après N minutes d'utilisation continue (suivi par les diffusions
 *    SCREEN_ON / SCREEN_OFF / USER_PRESENT, sans permission ni scrutation) ;
 *  - limites quotidiennes par appli et contenu ambiant optionnel, qui exigent l'« Accès aux
 *    données d'utilisation » : recalcul toutes les 5 min au plus, écran allumé seulement.
 */
object ScreenTimeModule : IslandModule {
    const val ID = "screentime"
    override val id = ID
    override val titleRes = R.string.screentime_module_title
    override val enabledByDefault = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        val ctx = env.context.applicationContext
        // Les marques « limite signalée » ne relancent pas le module.
        return ScreenTimePrefs.store.loaded(ctx)
            .map { it.copy(limitAlerted = emptySet()) }
            .distinctUntilChanged()
            .transformLatest { emitAll(run(env, it)) }
    }

    private fun run(env: ModuleEnv, settings: ScreenTimeSettings): Flow<List<IslandContent>> = channelFlow {
        val ctx = env.context.applicationContext
        val breakAlert = MutableStateFlow<IslandContent?>(null)
        val limitAlert = MutableStateFlow<IslandContent?>(null)
        val ambient = MutableStateFlow<IslandContent?>(null)

        if (settings.breakReminder) {
            launch {
                var state = BreakState()
                UsageAccess.activeScreen(ctx).collectLatest { active ->
                    val now = SystemClock.elapsedRealtime()
                    state = if (active) state.active(now) else state.screenOff(now)
                    while (state.isActive) {
                        delay(state.dueInMs(SystemClock.elapsedRealtime(), settings.breakMs) ?: return@collectLatest)
                        val at = SystemClock.elapsedRealtime()
                        if ((state.dueInMs(at, settings.breakMs) ?: 1L) > 0L) continue
                        state = state.reminded()
                        breakAlert.value = BreakAlertContent(settings.breakMinutes, settings.breakBreathing, postedAt = at)
                    }
                }
            }
        }

        val needsStats = settings.showAmbient || settings.appLimits.values.any { it > 0 }
        if (needsStats) {
            launch {
                UsageAccess.screenOn(ctx).collectLatest { on ->
                    if (!on) {
                        ambient.value = null
                        return@collectLatest
                    }
                    while (true) {
                        val usage = withContext(Dispatchers.IO) { UsageAccess.today(ctx) }
                        ambient.value = if (settings.showAmbient && usage != null) ScreenTimeContent(usage.totalMs) else null
                        if (usage != null) checkLimits(env, usage)?.let { limitAlert.value = it }
                        delay(SCREEN_TIME_REFRESH_MS)
                    }
                }
            }
        }

        combine(breakAlert, limitAlert, ambient) { b, l, a -> listOfNotNull(b, l, a) }.collect { send(it) }
    }

    /** Signale la première limite atteinte et pas encore signalée aujourd'hui. */
    private suspend fun checkLimits(env: ModuleEnv, usage: DayUsage): IslandContent? {
        val ctx = env.context.applicationContext
        val current = ScreenTimePrefs.store.data.value
        val day = LocalDate.now().toEpochDay()
        val reached = reachedLimits(current, usage, day)
        if (reached.isEmpty()) return null
        ScreenTimePrefs.store.update(ctx) { s ->
            s.copy(limitAlerted = (s.limitAlerted.filter { it.substringAfter('@').toLongOrNull() == day } + reached.map { "${it.first}@$day" }).toSet())
        }
        val (pkg, minutes) = reached.first()
        val label = withContext(Dispatchers.IO) { AppInfoCache.load(ctx, pkg).label }
        return LimitAlertContent(pkg, label, minutes, SystemClock.elapsedRealtime())
    }
}
