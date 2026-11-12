package com.karelisio.bulle.modules.call

/**
 * Mémoire du début d'appel, par clé d'appel (état + clé de notification).
 *
 * Certaines applis reconstruisent leur notification en cours d'appel avec un nouveau `when` :
 * la durée repartait alors de zéro. On garde le PLUS ANCIEN début connu tant que l'appel
 * continue, et on oublie la clé dès que l'appel disparaît ([retain]). Logique pure, testée.
 */
class CallStartTracker {
    private val earliest = HashMap<String, Long>()

    /**
     * Début à afficher pour [key] : le plus ancien [candidate] valide vu jusqu'ici,
     * sinon [fallback] (instant où l'appel a été vu en cours). Un candidat dans le futur
     * (au-delà d'1 s de [nowWall]) est ignoré.
     */
    fun resolve(key: String, candidate: Long?, nowWall: Long, fallback: Long): Long {
        val valid = candidate?.takeIf { it > 0 && it <= nowWall + 1_000 }
        val best = when (val known = earliest[key]) {
            null -> valid
            else -> if (valid != null) minOf(known, valid) else known
        }
        if (best != null) earliest[key] = best
        return best ?: fallback
    }

    /** Oublie les appels terminés. */
    fun retain(keys: Set<String>) {
        earliest.keys.retainAll(keys)
    }

    internal val size: Int get() = earliest.size
}
