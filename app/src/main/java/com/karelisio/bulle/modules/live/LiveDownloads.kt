package com.karelisio.bulle.modules.live

import com.karelisio.bulle.core.island.IslandContent

/**
 * Regroupement des téléchargements (fonctions pures).
 *
 * Règle : une activité est un « téléchargement » si le [LiveParser] l'a classée comme
 * progression générique de transfert ([LiveActivity.genericProgress]) — barre de progression
 * ordinaire (setProgress), sans chrono, non promue, sans ProgressStyle ni catégorie « live »
 * (navigation, VTC…). C'est le cas des téléchargements de Chrome, du Play Store, de Drive,
 * des gestionnaires de fichiers, des envois…
 *
 * Dès qu'il y en a au moins un, tous les téléchargements sont réunis dans UN contenu
 * [LiveDownloadsContent] de clé fixe [KEY] : l'île étendue ne se replie plus quand un
 * deuxième téléchargement arrive, qu'un autre termine ou que l'ordre change.
 */
object LiveDownloads {
    /** Clé stable du contenu agrégé, quel que soit le nombre de téléchargements. */
    const val KEY = "live:downloads"

    /** Échelle de la progression globale (LiveProgress entière). */
    const val OVERALL_MAX = 1000

    /** Seuil au-delà duquel un max ressemble à une taille (octets, Ko…) plutôt qu'à un pourcentage. */
    private const val SIZE_LIKE_MAX = 100

    fun isDownload(a: LiveActivity): Boolean =
        a.genericProgress && a.kind == LiveKind.PROGRESS && a.chrono == null && a.progress != null

    /** Tri stable : par heure de première apparition, puis par clé de notification. */
    fun sorted(items: List<LiveContent>): List<LiveContent> =
        items.sortedWith(compareBy<LiveContent> { it.postedAt }.thenBy { it.activity.key })

    /**
     * Progression globale des téléchargements.
     *  - les barres indéterminées sont ignorées ; si toutes le sont, le résultat est indéterminé ;
     *  - moyenne pondérée par la taille (Σ courant / Σ max) si les max sont comparables :
     *    même appli (donc même unité) et tous supérieurs à 100 (des tailles, pas des pourcentages) ;
     *  - sinon moyenne simple des fractions.
     */
    fun overall(activities: List<LiveActivity>): LiveProgress? {
        val determinate = activities.filter { val p = it.progress; p != null && !p.indeterminate && p.max > 0 }
        if (determinate.isEmpty()) {
            return if (activities.any { it.progress != null }) LiveProgress(0, OVERALL_MAX, indeterminate = true) else null
        }
        val fraction = if (sizesComparable(determinate)) {
            val cur = determinate.sumOf { it.progress!!.current.toLong() }
            val max = determinate.sumOf { it.progress!!.max.toLong() }
            (cur.toDouble() / max).toFloat()
        } else {
            determinate.map { it.progress!!.fraction }.average().toFloat()
        }.coerceIn(0f, 1f)
        return LiveProgress(current = Math.round(fraction * OVERALL_MAX), max = OVERALL_MAX, indeterminate = false)
    }

    /** Les max sont-ils dans la même unité (même appli) et des tailles plutôt que des pourcentages ? */
    fun sizesComparable(determinate: List<LiveActivity>): Boolean =
        determinate.map { it.packageName }.distinct().size == 1 &&
            determinate.all { (it.progress?.max ?: 0) > SIZE_LIKE_MAX }

    /**
     * Remplace tous les téléchargements de [contents] par un seul contenu agrégé (placé à la
     * position du premier) ; les autres activités sont laissées telles quelles.
     * @param countLabel texte « N téléchargements » (pluriel localisé).
     */
    fun group(contents: List<LiveContent>, countLabel: (Int) -> String): List<IslandContent> {
        val downloads = contents.filter { isDownload(it.activity) }
        if (downloads.isEmpty()) return contents
        val aggregate = LiveDownloadsContent(sorted(downloads), countLabel(downloads.size))
        val out = ArrayList<IslandContent>(contents.size - downloads.size + 1)
        var placed = false
        for (c in contents) {
            if (isDownload(c.activity)) {
                if (!placed) {
                    out += aggregate
                    placed = true
                }
            } else {
                out += c
            }
        }
        return out
    }
}
