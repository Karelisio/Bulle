package com.karelisio.bulle.modules.live

/**
 * Barres de progression figées (logique pure, testée) : une appli qui a fini son travail sans
 * retirer sa notification (ou sans envoyer 100 %) laissait l'île bloquée, par ex. « 88 % ».
 * Une progression qui n'avance plus pendant [staleMs] est retirée de l'île ; elle revient dès
 * qu'elle bouge à nouveau. Les minuteurs, chronos et guidages ne sont jamais concernés.
 */
class LiveStaleness(private val staleMs: Long = STALE_MS) {
    private data class Seen(val signature: String, val since: Long)

    private val seen = HashMap<String, Seen>()

    /** Vrai si [a] est une progression qui n'a pas changé depuis [staleMs] à l'instant [now]. */
    fun isStale(a: LiveActivity, now: Long): Boolean {
        val p = a.progress
        if (a.kind != LiveKind.PROGRESS || p == null || a.chrono != null) {
            seen.remove(a.key)
            return false
        }
        val signature = "${p.current}/${p.max}/${p.indeterminate}|${a.title}|${a.text}"
        val previous = seen[a.key]
        if (previous == null || previous.signature != signature) {
            seen[a.key] = Seen(signature, now)
            return false
        }
        return now - previous.since >= staleMs
    }

    /** Oublie les activités disparues. */
    fun retain(keys: Set<String>) {
        seen.keys.retainAll(keys)
    }

    companion object {
        /** 3 minutes sans avancer. */
        const val STALE_MS = 180_000L
        /** Fréquence de vérification. */
        const val CHECK_MS = 30_000L
    }
}
