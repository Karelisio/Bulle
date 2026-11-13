package com.karelisio.bulle.modules.external

/**
 * Tableau immuable des contenus reçus (logique pure, testée en JUnit).
 * Clé = « paquet / id » ; au plus [maxPerApp] contenus par appli et [maxTotal] au total :
 * au-delà, le plus ancien cède sa place.
 */
data class ExternalBoard(
    val items: List<ExternalSpec> = emptyList(),
    val maxPerApp: Int = ExternalApi.MAX_PER_APP,
    val maxTotal: Int = ExternalApi.MAX_TOTAL,
) {
    /** Ajoute ou remplace un contenu (même clé = mise à jour, place conservée en tête des récents). */
    fun put(spec: ExternalSpec, nowElapsed: Long): ExternalBoard {
        val alive = prune(nowElapsed).items.filter { it.key != spec.key }
        val sameApp = alive.filter { it.packageName == spec.packageName }.sortedBy { it.receivedAt }
        val evictApp = sameApp.take((sameApp.size - (maxPerApp - 1)).coerceAtLeast(0)).map { it.key }.toSet()
        val kept = alive.filter { it.key !in evictApp }
        val evictTotal = kept.sortedBy { it.receivedAt }
            .take((kept.size - (maxTotal - 1)).coerceAtLeast(0)).map { it.key }.toSet()
        return copy(items = kept.filter { it.key !in evictTotal } + spec)
    }

    fun dismiss(key: String): ExternalBoard = copy(items = items.filter { it.key != key })

    /** Retire tous les contenus d'une appli (appli bloquée, désinstallée…). */
    fun removeApp(packageName: String): ExternalBoard = copy(items = items.filter { it.packageName != packageName })

    /** Retire les contenus expirés. */
    fun prune(nowElapsed: Long): ExternalBoard {
        val alive = items.filter { (it.expiresAt ?: Long.MAX_VALUE) > nowElapsed }
        return if (alive.size == items.size) this else copy(items = alive)
    }

    fun forApp(packageName: String): List<ExternalSpec> = items.filter { it.packageName == packageName }
}
