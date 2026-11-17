package com.karelisio.bulle.system

import android.content.Context
import androidx.core.content.edit

/** Modules suivis pour la proposition « jamais utilisé depuis 7 jours ». */
enum class TrackedModule { HALO, EDGES, STANDBY, VOLET }

/**
 * Dernière utilisation de chaque module (halo joué, geste de bord, Veille affichée, Volet ouvert),
 * gardée sur le téléphone. Un module activé mais jamais utilisé depuis 7 jours est proposé à la
 * désactivation sur l'accueil (« Garder » repousse la question de 30 jours).
 */
object ModuleUsage {
    private const val PREFS = "bulle_module_usage"
    const val IDLE_MS = 7 * 24 * 3_600_000L
    const val SNOOZE_MS = 30 * 24 * 3_600_000L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, m: TrackedModule, now: Long = System.currentTimeMillis()) {
        runCatching { prefs(context).edit { putLong("used_${m.name}", now) } }
    }

    /** Premier instant où le module a été vu activé (sert de départ quand il n'a jamais servi). */
    fun noteEnabled(context: Context, m: TrackedModule, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val key = "enabled_${m.name}"
        if (enabled && !p.contains(key)) p.edit { putLong(key, now) }
        if (!enabled && p.contains(key)) p.edit { remove(key) }
    }

    fun snooze(context: Context, m: TrackedModule, now: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong("snooze_${m.name}", now + SNOOZE_MS) }
    }

    /** Modules à proposer de couper. */
    fun idle(context: Context, enabled: Map<TrackedModule, Boolean>, now: Long = System.currentTimeMillis()): List<TrackedModule> {
        val p = prefs(context)
        return enabled.filter { (m, on) ->
            noteEnabled(context, m, on, now)
            isIdle(on, p.getLong("used_${m.name}", 0L), p.getLong("enabled_${m.name}", now), p.getLong("snooze_${m.name}", 0L), now)
        }.keys.toList()
    }

    /** Logique pure (testée). */
    fun isIdle(enabled: Boolean, lastUsed: Long, enabledSince: Long, snoozedUntil: Long, now: Long): Boolean {
        if (!enabled || now < snoozedUntil) return false
        val since = maxOf(lastUsed, enabledSince)
        return now - since >= IDLE_MS
    }
}
