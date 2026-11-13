package com.karelisio.bulle.modules.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regroupement des téléchargements : règle, progression globale, stabilité de la clé. */
class LiveDownloadsTest {

    private val label: (Int) -> String = { "$it téléchargements" }

    private fun activity(
        key: String,
        cur: Int = 0,
        max: Int = 100,
        indeterminate: Boolean = false,
        pkg: String = "com.android.chrome",
        kind: LiveKind = LiveKind.PROGRESS,
        generic: Boolean = true,
        chrono: LiveChrono? = null,
    ) = LiveActivity(
        key = key, packageName = pkg, kind = kind, title = "$key.zip", text = null, subText = null, shortText = null,
        progress = if (kind == LiveKind.PROGRESS) LiveProgress(cur, max, indeterminate) else null,
        chrono = chrono, color = 0, promoted = !generic, genericProgress = generic,
        actions = emptyList(), contentIntent = null,
    )

    private fun content(a: LiveActivity, postedAt: Long) = LiveContent(a, LiveVisuals(null, null, null), postedAt)

    private fun timer() = content(activity("t", kind = LiveKind.TIMER, generic = false, chrono = LiveChrono(10, true)), 5)

    @Test
    fun ruleKeepsOnlyGenericTransferProgress() {
        assertTrue(LiveDownloads.isDownload(activity("a")))
        // Livraison promue / ProgressStyle, minuteur, VTC : jamais regroupés.
        assertFalse(LiveDownloads.isDownload(activity("b", generic = false)))
        assertFalse(LiveDownloads.isDownload(activity("c", kind = LiveKind.TIMER, generic = false, chrono = LiveChrono(0, true))))
        assertFalse(LiveDownloads.isDownload(activity("d", kind = LiveKind.GENERIC, generic = false)))
    }

    @Test
    fun groupingReplacesDownloadsByOneStableContent() {
        val t = timer()
        val d1 = content(activity("d1", 10), 20)
        val d2 = content(activity("d2", 50), 10)
        val out = LiveDownloads.group(listOf(d1, t, d2), label)
        assertEquals(2, out.size)
        val agg = out[0] as LiveDownloadsContent
        assertSame(t, out[1])
        assertEquals(LiveDownloads.KEY, agg.key)
        // Tri stable par heure de début.
        assertEquals(listOf("d2", "d1"), agg.items.map { it.activity.key })
        assertEquals("2 téléchargements", agg.compactSubtitle)
        assertEquals(10L, agg.postedAt)
        assertNull(agg.notificationKey)
    }

    @Test
    fun keyIsStableWithOneTwoAndReorderedDownloads() {
        val d1 = content(activity("d1", 10), 1)
        val d2 = content(activity("d2", 10), 2)
        val one = LiveDownloads.group(listOf(d1), label).single()
        val two = LiveDownloads.group(listOf(d2, d1), label).single()
        val reordered = LiveDownloads.group(listOf(d1, d2), label).single()
        assertEquals(LiveDownloads.KEY, one.key)
        assertEquals(one.key, two.key)
        // Même contenu quel que soit l'ordre d'arrivée : rien n'est recréé inutilement.
        assertEquals(two, reordered)
        // Un seul téléchargement : affichage habituel (seconde ligne = nom du fichier).
        assertEquals("d1.zip", one.compactSubtitle)
        assertEquals("d1", one.notificationKey)
        // La progression qui avance change le contenu, pas sa clé.
        val advanced = LiveDownloads.group(listOf(content(activity("d1", 60), 1), d2), label).single()
        assertNotEquals(two, advanced)
        assertEquals(two.key, advanced.key)
    }

    @Test
    fun noDownloadLeavesListUntouched() {
        val list = listOf(timer())
        assertSame(list, LiveDownloads.group(list, label))
    }

    @Test
    fun overallWeightedBySizeWhenComparable() {
        // Même appli, tailles en octets : Σcourant / Σmax = (100 + 900) / (1000 + 9000) = 10 %.
        val p = LiveDownloads.overall(listOf(activity("a", 100, 1_000), activity("b", 900, 9_000)))!!
        assertEquals(0.10f, p.fraction, 0.001f)
        // Petit fichier presque fini, gros à peine commencé : le gros pèse davantage.
        val w = LiveDownloads.overall(listOf(activity("a", 990, 1_000), activity("b", 0, 99_000)))!!
        assertEquals(0.0099f, w.fraction, 0.001f)
    }

    @Test
    fun overallSimpleMeanWhenNotComparable() {
        // Applis différentes (unités possiblement différentes) : moyenne simple.
        val p = LiveDownloads.overall(listOf(activity("a", 990, 1_000, pkg = "x"), activity("b", 0, 99_000, pkg = "y")))!!
        assertEquals(0.495f, p.fraction, 0.001f)
        // Pourcentages (max = 100) : moyenne simple aussi.
        assertFalse(LiveDownloads.sizesComparable(listOf(activity("a", 10), activity("b", 30))))
        assertEquals(0.2f, LiveDownloads.overall(listOf(activity("a", 10), activity("b", 30)))!!.fraction, 0.001f)
    }

    @Test
    fun overallIgnoresIndeterminate() {
        val p = LiveDownloads.overall(listOf(activity("a", 50), activity("b", indeterminate = true, max = 0)))!!
        assertFalse(p.indeterminate)
        assertEquals(0.5f, p.fraction, 0.001f)
        assertTrue(LiveDownloads.overall(listOf(activity("a", indeterminate = true, max = 0)))!!.indeterminate)
    }
}
