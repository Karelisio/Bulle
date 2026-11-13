package com.karelisio.bulle.modules.countdown

import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.broadcasts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Module « Compte à rebours » : prochain événement choisi au repos (« 🎂 J-12 ») et rappel la
 * veille. Activé par défaut mais sans effet (ni diffusion écoutée) tant qu'aucun événement
 * n'existe.
 *
 * Économie : aucun minuteur à la seconde ; un réveil au prochain changement d'affichage
 * (minuit, passage aux heures, minute suivante dans la dernière heure), plafonné à 30 min, et un
 * recalcul à l'allumage de l'écran ou au changement d'heure / de fuseau.
 */
object CountdownModule : IslandModule {
    const val ID = "countdown"
    override val id = ID
    override val titleRes = R.string.countdown_module_title
    override val enabledByDefault = true

    private val MAX_SLEEP: Duration = Duration.ofMinutes(30)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = channelFlow {
        val ctx = env.context.applicationContext
        val prefs = CountdownPrefs.store.loaded(ctx)
        // Le rappel survit aux republications des réglages (marquage « déjà montré »).
        var reminder: CountdownReminderContent? = null
        val hasEvents = prefs.map { it.events.isNotEmpty() }.distinctUntilChanged()
        hasEvents.collectLatest { any ->
            if (!any) {
                send(emptyList())
                return@collectLatest
            }
            val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            val ticks = broadcasts(ctx, filter).map { }.onStart { emit(Unit) }
            combine(prefs, ticks) { s, _ -> s }.collectLatest { s ->
                while (true) {
                    val now = ZonedDateTime.now()
                    val due = CountdownMath.dueReminders(s, now)
                    if (due.isNotEmpty()) reminder = CountdownReminderContent(due.first(), SystemClock.elapsedRealtime())
                    reminder?.let { r -> if (SystemClock.elapsedRealtime() > r.postedAt + r.timeoutMs!! + 1_000) reminder = null }
                    send(build(s, now) + listOfNotNull(reminder))
                    if (due.isNotEmpty()) {
                        val today = now.toLocalDate()
                        // Marque les rappels et oublie ceux des jours passés (republie les réglages).
                        CountdownPrefs.store.update(ctx) { cur ->
                            cur.copy(
                                reminded = (cur.reminded + due.map { it.reminderKey })
                                    .filter { key -> key.substringAfter('@').let { CountdownMath.parseDate(it)?.isBefore(today) != true } }
                                    .toSet(),
                            )
                        }
                    }
                    val wake = CountdownMath.nextWake(s, now)
                    val sleep = Duration.between(now, wake).coerceAtMost(MAX_SLEEP).toMillis().coerceAtLeast(1_000L)
                    delay(if (reminder != null) minOf(sleep, CountdownReminderContent.TIMEOUT_MS + 1_500) else sleep)
                }
            }
        }
    }

    /** Contenu ambiant, ou rien si aucun événement n'est à montrer au repos. */
    internal fun build(settings: CountdownSettings, now: ZonedDateTime): List<IslandContent> {
        val idle = settings.idleEvent(now) ?: return emptyList()
        val upcoming = CountdownMath.upcoming(settings.events, now)
        val pinned = upcoming.firstOrNull { it.event.id == idle.id } ?: return emptyList()
        return listOf(CountdownContent(pinned, upcoming))
    }
}
