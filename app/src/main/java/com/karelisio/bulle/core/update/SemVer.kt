package com.karelisio.bulle.core.update

/**
 * Comparaison sémantique de versions (« v1.2.3 », « 1.2.3-debug », « 1.10 »).
 * Fonctions pures : testées sans Android.
 */
object SemVer {

    /** Version découpée : numéros principaux + éventuel suffixe de pré-version. */
    data class Parsed(val numbers: List<Int>, val preRelease: String?)

    /** Retire le « v » initial et les espaces. */
    fun clean(raw: String): String = raw.trim().removePrefix("v").removePrefix("V").trim()

    fun parse(raw: String): Parsed? {
        val s = clean(raw).substringBefore('+') // métadonnées de build ignorées
        if (s.isEmpty()) return null
        val main = s.substringBefore('-')
        val pre = s.substringAfter('-', "").ifEmpty { null }
        val numbers = main.split('.').map { it.toIntOrNull() ?: return null }
        if (numbers.isEmpty()) return null
        return Parsed(numbers, pre)
    }

    /**
     * Compare deux versions : < 0 si [a] est plus ancienne, 0 si égales, > 0 si plus récente.
     * Les numéros manquants valent 0 (1.2 == 1.2.0). Une pré-version (1.2.0-beta)
     * est plus ancienne que la version finale. Versions illisibles : comparées comme du texte.
     */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        if (pa == null || pb == null) return clean(a).compareTo(clean(b))
        val size = maxOf(pa.numbers.size, pb.numbers.size)
        for (i in 0 until size) {
            val c = pa.numbers.getOrElse(i) { 0 }.compareTo(pb.numbers.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            pa.preRelease == pb.preRelease -> 0
            pa.preRelease == null -> 1
            pb.preRelease == null -> -1
            else -> comparePre(pa.preRelease, pb.preRelease)
        }
    }

    /** [remote] est-elle strictement plus récente que [current] ? */
    fun isNewer(remote: String, current: String): Boolean {
        // Le suffixe « -debug » de la variante debug n'est pas une pré-version.
        val cur = clean(current).removeSuffix("-debug")
        return compare(remote, cur) > 0
    }

    private fun comparePre(a: String, b: String): Int {
        val ia = a.split('.')
        val ib = b.split('.')
        for (i in 0 until maxOf(ia.size, ib.size)) {
            val x = ia.getOrNull(i) ?: return -1
            val y = ib.getOrNull(i) ?: return 1
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val c = when {
                nx != null && ny != null -> nx.compareTo(ny)
                nx != null -> -1
                ny != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return 0
    }
}
