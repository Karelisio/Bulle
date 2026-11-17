package com.karelisio.bulle.core.island

/** Ce que l'île affiche : une bulle principale et, éventuellement, une bulle secondaire. */
data class Arrangement(val primary: IslandContent?, val secondary: IslandContent?) {
    companion object {
        val Empty = Arrangement(null, null)
    }
}

/** File de priorité : choisit la bulle principale et la secondaire. */
object IslandArbiter {

    private val order = compareByDescending<IslandContent> { it.priority.value }.thenByDescending { it.postedAt }

    /**
     * @param swappedPair paire (principale, secondaire) que l'utilisateur a échangée ;
     * l'échange ne s'applique que tant que ces deux mêmes contenus sont en tête.
     * @param filter contenus admis (ex. mode focus : l'essentiel seulement) ; tous par défaut.
     */
    fun arrange(
        contents: List<IslandContent>,
        swappedPair: Pair<String, String>? = null,
        filter: (IslandContent) -> Boolean = { true },
        /**
         * Contenu choisi en balayant l'île (clé, instant du choix, elapsedRealtime) : il passe devant,
         * sauf si un contenu plus prioritaire est arrivé depuis (un appel l'emporte toujours).
         */
        preferred: Pair<String, Long>? = null,
    ): Arrangement {
        val admitted = contents.filter(filter)
        if (admitted.isEmpty()) return Arrangement.Empty
        val sorted = admitted.sortedWith(order)
        val chosen = preferred?.let { (key, at) ->
            val p = sorted.firstOrNull { it.key == key } ?: return@let null
            p.takeIf { sorted.none { o -> o.priority > p.priority && o.postedAt > at } }
        }
        val primary = chosen ?: sorted.first()
        val secondary = sorted.filter { it !== primary }.firstOrNull { it.canBeSecondary && it.moduleId != primary.moduleId }
        if (secondary != null && swappedPair == primary.key to secondary.key && primary.canBeSecondary) {
            return Arrangement(secondary, primary)
        }
        return Arrangement(primary, secondary)
    }

    /**
     * Contenu suivant ([direction] = +1) ou précédent (-1) dans l'ordre de priorité, en boucle,
     * à partir de [currentKey]. null s'il n'y a pas d'autre contenu.
     */
    fun cycle(contents: List<IslandContent>, currentKey: String?, direction: Int): IslandContent? {
        val sorted = contents.sortedWith(order)
        if (sorted.size < 2) return null
        val i = sorted.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        val n = sorted.size
        return sorted[((i + direction) % n + n) % n]
    }

    /** Position (0..n-1) et nombre de contenus, pour les points de pagination. */
    fun position(contents: List<IslandContent>, currentKey: String?): Pair<Int, Int> {
        val sorted = contents.sortedWith(order)
        return sorted.indexOfFirst { it.key == currentKey }.coerceAtLeast(0) to sorted.size
    }
}
