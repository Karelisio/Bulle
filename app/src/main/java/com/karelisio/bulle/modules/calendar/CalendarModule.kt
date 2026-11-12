package com.karelisio.bulle.modules.calendar

import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/**
 * Module « Agenda » : rappel du prochain événement quelques minutes avant, puis
 * (option) temps restant pendant l'événement. Lecture locale de l'agenda uniquement.
 * Désactivé par défaut : il demande la permission READ_CALENDAR à l'activation.
 */
object CalendarModule : IslandModule {
    const val ID = "calendar"
    override val id = ID
    override val titleRes = R.string.calendar_module_title
    override val enabledByDefault = false

    /** Relecture au plus tard toutes les 15 min, même sans changement signalé. */
    private const val MAX_SLEEP_MS = 15 * 60_000L

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        val context = env.context.applicationContext
        // postedAt stable par contenu, pour ne pas « republier » à chaque relecture.
        val firstSeen = HashMap<String, Long>()
        return combine(
            env.settings.map { CalendarSettingsBinding.read(it) }.distinctUntilChanged(),
            CalendarSource.changes(context),
        ) { settings, _ -> settings }
            .transformLatest { settings ->
                while (true) {
                    val now = System.currentTimeMillis()
                    val events = withContext(Dispatchers.IO) {
                        CalendarSource.query(context, now - CalendarPlanner.HORIZON_MS, now + CalendarPlanner.HORIZON_MS)
                    }
                    val plan = CalendarPlanner.plan(events, now, settings)
                    val contents = buildList<IslandContent> {
                        plan.reminder?.let {
                            val key = CalendarReminderContent.keyOf(it)
                            add(CalendarReminderContent(it, firstSeen.getOrPut(key) { SystemClock.elapsedRealtime() }))
                        }
                        plan.ongoing?.let {
                            val key = CalendarOngoingContent.keyOf(it)
                            add(CalendarOngoingContent(it, firstSeen.getOrPut(key) { SystemClock.elapsedRealtime() }))
                        }
                    }
                    firstSeen.keys.retainAll(contents.mapTo(HashSet()) { it.key })
                    emit(contents)
                    // Réveil juste après le prochain changement (rappel, début, fin).
                    val wake = plan.nextChangeAt?.let { it - System.currentTimeMillis() + 500 } ?: MAX_SLEEP_MS
                    delay(wake.coerceIn(1_000L, MAX_SLEEP_MS))
                }
            }
            .flowOn(Dispatchers.Default)
    }
}
