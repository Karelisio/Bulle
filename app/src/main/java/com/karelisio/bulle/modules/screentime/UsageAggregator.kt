package com.karelisio.bulle.modules.screentime

/** Types d'événements d'utilisation retenus (indépendants des constantes Android, pour les tests). */
enum class UsageKind { RESUMED, PAUSED, STOPPED, SCREEN_ON, SCREEN_OFF, KEYGUARD_SHOWN, KEYGUARD_HIDDEN }

/** Un événement d'utilisation (heure murale en ms, paquet pour les événements d'activité). */
data class UsageEvt(val time: Long, val kind: UsageKind, val pkg: String = "")

/** Utilisation agrégée d'une fenêtre (un jour, depuis minuit). */
data class DayUsage(
    /** Temps d'écran total : au moins une appli au premier plan (sans double compte en multi-fenêtre). */
    val totalMs: Long = 0,
    /** Temps au premier plan par appli. */
    val perApp: Map<String, Long> = emptyMap(),
    /** Déverrouillages (KEYGUARD_HIDDEN, sinon allumages de l'écran). */
    val unlocks: Int = 0,
) {
    /** Les [n] applis les plus utilisées (durée décroissante). */
    fun top(n: Int = 5): List<Pair<String, Long>> =
        perApp.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(n).map { it.key to it.value }
}

/**
 * Agrégation pure des événements UsageEvents d'une fenêtre [start, end[ :
 *  - une appli est au premier plan de ACTIVITY_RESUMED à ACTIVITY_PAUSED / STOPPED ;
 *  - SCREEN_NON_INTERACTIVE ferme toutes les applis ouvertes ;
 *  - une appli déjà ouverte au début de la fenêtre (premier événement = PAUSED) compte depuis
 *    [start], sauf si l'écran s'est éteint entre-temps ;
 *  - une appli encore ouverte à la fin compte jusqu'à [end].
 * Les paquets [excluded] (lanceur, interface système) sont ignorés.
 */
object UsageAggregator {

    fun aggregate(events: List<UsageEvt>, start: Long, end: Long, excluded: Set<String> = emptySet()): DayUsage {
        if (end <= start) return DayUsage()
        val perApp = HashMap<String, Long>()
        val open = LinkedHashMap<String, Long>()
        val seen = HashSet<String>()
        var screenOffSeen = false
        var total = 0L
        var anySince = -1L // début de l'intervalle courant où au moins une appli est ouverte
        var keyguardHidden = 0
        var keyguardSeen = false
        var screenOns = 0

        fun openApp(pkg: String, t: Long) {
            if (pkg in open) return
            if (open.isEmpty()) anySince = t
            open[pkg] = t
        }

        fun closeApp(pkg: String, t: Long) {
            val since = open.remove(pkg) ?: return
            perApp[pkg] = (perApp[pkg] ?: 0L) + (t - since).coerceAtLeast(0)
            if (open.isEmpty() && anySince >= 0) {
                total += (t - anySince).coerceAtLeast(0)
                anySince = -1
            }
        }

        fun closeAll(t: Long) = open.keys.toList().forEach { closeApp(it, t) }

        for (e in events.sortedBy { it.time }) {
            val t = e.time.coerceIn(start, end)
            if (e.time < start || e.time > end) continue
            when (e.kind) {
                UsageKind.RESUMED -> if (e.pkg !in excluded) {
                    seen += e.pkg
                    openApp(e.pkg, t)
                }
                UsageKind.PAUSED, UsageKind.STOPPED -> if (e.pkg !in excluded) {
                    if (e.pkg !in open && e.pkg !in seen && !screenOffSeen) openApp(e.pkg, start)
                    seen += e.pkg
                    closeApp(e.pkg, t)
                }
                UsageKind.SCREEN_OFF -> {
                    screenOffSeen = true
                    closeAll(t)
                }
                UsageKind.SCREEN_ON -> screenOns++
                UsageKind.KEYGUARD_HIDDEN -> {
                    keyguardSeen = true
                    keyguardHidden++
                }
                UsageKind.KEYGUARD_SHOWN -> keyguardSeen = true
            }
        }
        closeAll(end)
        return DayUsage(totalMs = total, perApp = perApp, unlocks = if (keyguardSeen) keyguardHidden else screenOns)
    }
}
