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
    ): Arrangement {
        val admitted = contents.filter(filter)
        if (admitted.isEmpty()) return Arrangement.Empty
        val sorted = admitted.sortedWith(order)
        val primary = sorted.first()
        val secondary = sorted.drop(1).firstOrNull { it.canBeSecondary && it.moduleId != primary.moduleId }
        if (secondary != null && swappedPair == primary.key to secondary.key && primary.canBeSecondary) {
            return Arrangement(secondary, primary)
        }
        return Arrangement(primary, secondary)
    }
}
