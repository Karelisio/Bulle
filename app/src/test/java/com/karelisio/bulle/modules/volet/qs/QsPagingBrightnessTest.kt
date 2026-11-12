package com.karelisio.bulle.modules.volet.qs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pagination de la grille et plage de luminosité réelle (logique pure). */
class QsPagingBrightnessTest {

    @Test
    fun pagingByEight() {
        assertEquals(1, QsPaging.pageCount(0))
        assertEquals(1, QsPaging.pageCount(8))
        assertEquals(2, QsPaging.pageCount(9))
        assertEquals(2, QsPaging.pageCount(16))
        assertEquals(3, QsPaging.pageCount(17))
        assertEquals(listOf(0 until 8), QsPaging.pages(8))
        assertEquals(listOf(0 until 8, 8 until 13), QsPaging.pages(13))
        // Toutes les tuiles sont réparties, sans doublon ni trou.
        assertEquals((0 until 21).toList(), QsPaging.pages(21).flatten())
    }

    @Test
    fun rangeSanitize() {
        assertEquals(BrightnessRange.DEFAULT, BrightnessRange.sanitize(null, null))
        assertEquals(BrightnessRange(1, 4095), BrightnessRange.sanitize(1, 4095))
        assertEquals(BrightnessRange(1, 4095), BrightnessRange.sanitize(null, 4095))
        assertEquals(BrightnessRange(0, 10000), BrightnessRange.sanitize(0, 10000))
        // Valeurs incohérentes : repli.
        assertEquals(BrightnessRange.DEFAULT, BrightnessRange.sanitize(1, 0))
        assertEquals(BrightnessRange(1, 100), BrightnessRange.sanitize(300, 100))
    }

    @Test
    fun systemRangeFallsBack() {
        // Hors appareil, les ressources du framework sont absentes ou AOSP : plage valide.
        val r = BrightnessRange.system()
        assertTrue(r.max > r.min)
    }

    @Test
    fun curveOnWideRange() {
        val r = BrightnessRange(1, 4095)
        assertEquals(0f, BrightnessCurve.toPosition(1, r), 1e-6f)
        assertEquals(1f, BrightnessCurve.toPosition(4095, r), 1e-6f)
        assertEquals(1, BrightnessCurve.toValue(0f, r))
        assertEquals(4095, BrightnessCurve.toValue(1f, r))
        // Même courbe perceptuelle qu'en 1 – 255 : mi-course ≈ 22 % de la plage.
        val mid = BrightnessCurve.toValue(0.5f, r)
        assertTrue("mid=$mid", mid in 800..1000)
        // Valeur hors plage (ancienne écriture 1 – 255) bornée, sans exception.
        assertEquals(1f, BrightnessCurve.toPosition(9999, r), 1e-6f)
        // Aller-retour stable.
        for (v in 1..4095 step 7) assertEquals(v, BrightnessCurve.toValue(BrightnessCurve.toPosition(v, r), r))
        // Plage commençant à 0.
        val z = BrightnessRange(0, 10000)
        assertEquals(0, BrightnessCurve.toValue(0f, z))
        assertEquals(10000, BrightnessCurve.toValue(1f, z))
    }
}
