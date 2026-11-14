package com.karelisio.bulle.modules.volet.notifications

/**
 * Suppression optimiste (logique pure, testée) : une notification balayée disparaît du modèle
 * affiché tout de suite, sans attendre le NotificationListener. Si elle est toujours active
 * après [TIMEOUT_MS] (refus du système, listener absent), elle est restaurée.
 */
object NotifOptimistic {
    const val TIMEOUT_MS = 2_000L

    /** Résultat d'un rapprochement : clés encore masquées, clés restaurées, prochaine échéance. */
    data class Reconciled(val hidden: Map<String, Long>, val restored: Set<String>, val nextCheckAt: Long?)

    /** Masque [keys] à l'instant [now] (les clés déjà masquées gardent leur heure). */
    fun hide(hidden: Map<String, Long>, keys: Collection<String>, now: Long): Map<String, Long> =
        if (keys.isEmpty()) hidden else hidden + keys.filter { it !in hidden }.associateWith { now }

    /** Modèle affiché : sans les éléments masqués (groupes et sections vides retirés). */
    fun apply(model: NotifListModel, hidden: Set<String>): NotifListModel {
        if (hidden.isEmpty()) return model
        val sections = model.sections.mapNotNull { s ->
            val groups = s.groups.mapNotNull { g ->
                if (g.items.none { it.key in hidden }) return@mapNotNull g
                val items = g.items.filter { it.key !in hidden }
                if (items.isEmpty()) null else g.copy(items = items, appName = items.first().appName)
            }
            if (groups.isEmpty()) null else if (groups == s.groups) s else s.copy(groups = groups)
        }
        return if (sections == model.sections) model else NotifListModel(sections)
    }

    /**
     * Rapproche les clés masquées des notifications réellement actives ([activeKeys]) :
     * disparues = effacement confirmé (oubliées) ; encore là après le délai = refus (restaurées).
     */
    fun reconcile(hidden: Map<String, Long>, activeKeys: Set<String>, now: Long, timeoutMs: Long = TIMEOUT_MS): Reconciled {
        if (hidden.isEmpty()) return Reconciled(hidden, emptySet(), null)
        val kept = HashMap<String, Long>()
        val restored = HashSet<String>()
        hidden.forEach { (key, at) ->
            when {
                key !in activeKeys -> Unit
                now - at >= timeoutMs -> restored += key
                else -> kept[key] = at
            }
        }
        val next = kept.values.minOrNull()?.plus(timeoutMs)
        return Reconciled(if (kept.size == hidden.size) hidden else kept, restored, next)
    }
}
