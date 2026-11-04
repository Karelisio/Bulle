package com.karelisio.bulle.modules.volet.qs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QsLogicTest {

    @Test
    fun defaultOrderWhenEmpty() {
        val order = QsTileRegistry.resolveOrder(emptyList())
        assertEquals(QsTileRegistry.defaultOrder, order)
        // Toutes les tuiles du registre ont un identifiant unique et figurent dans l'ordre par défaut.
        assertEquals(QsTileRegistry.all.size, QsTileRegistry.all.map { it.id }.toSet().size)
        assertEquals(QsTileRegistry.all.map { it.id }.toSet(), QsTileRegistry.defaultOrder.toSet())
    }

    @Test
    fun savedOrderIsCleaned() {
        val order = QsTileRegistry.resolveOrder(listOf("torch", "inconnue", "wifi", "torch"))
        assertEquals(listOf("torch", "wifi"), order)
        // Uniquement des identifiants inconnus : retour à l'ordre par défaut.
        assertEquals(QsTileRegistry.defaultOrder, QsTileRegistry.resolveOrder(listOf("x", "y")))
    }

    @Test
    fun reorder() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), QsTileRegistry.move(l, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), QsTileRegistry.move(l, 3, 0))
        assertEquals(listOf("a", "b", "d", "c"), QsTileRegistry.move(l, 2, 3))
        assertEquals(l, QsTileRegistry.move(l, 1, 1))
        assertEquals(l, QsTileRegistry.move(l, 9, 0))
        assertEquals(listOf("b", "c", "d", "a"), QsTileRegistry.move(l, 0, 99))
    }

    @Test
    fun addAndRemove() {
        assertEquals(listOf("a", "b"), QsTileRegistry.add(listOf("a"), "b"))
        assertEquals(listOf("a"), QsTileRegistry.add(listOf("a"), "a"))
        assertEquals(listOf("b"), QsTileRegistry.remove(listOf("a", "b"), "a"))
        // La dernière tuile ne peut pas être retirée.
        assertEquals(listOf("a"), QsTileRegistry.remove(listOf("a"), "a"))
    }

    @Test
    fun brightnessGamma() {
        assertEquals(0f, BrightnessCurve.toPosition(BrightnessCurve.MIN), 1e-6f)
        assertEquals(1f, BrightnessCurve.toPosition(BrightnessCurve.MAX), 1e-6f)
        assertEquals(BrightnessCurve.MIN, BrightnessCurve.toValue(0f))
        assertEquals(BrightnessCurve.MAX, BrightnessCurve.toValue(1f))
        // Perceptuel : la moitié de la course correspond à bien moins de la moitié de la valeur.
        val mid = BrightnessCurve.toValue(0.5f)
        assertTrue("mid=$mid", mid in 50..64)
        // Aller-retour stable sur toute la plage.
        for (v in BrightnessCurve.MIN..BrightnessCurve.MAX) {
            assertEquals(v, BrightnessCurve.toValue(BrightnessCurve.toPosition(v)))
        }
        // Monotone et bornée.
        var prev = 0
        for (i in 0..100) {
            val v = BrightnessCurve.toValue(i / 100f)
            assertTrue(v >= prev)
            prev = v
        }
        assertEquals(BrightnessCurve.MAX, BrightnessCurve.toValue(4f))
        assertEquals(BrightnessCurve.STEPS, BrightnessCurve.step(1f))
    }

    @Test
    fun toggleMethodChoice() {
        fun choose(
            action: Boolean = false,
            direct: Boolean = false,
            permitted: Boolean = false,
            shell: Boolean = false,
            shizuku: ShizukuStatus = ShizukuStatus.UNAVAILABLE,
            panel: Boolean = false,
        ) = ToggleChooser.choose(action, direct, permitted, shell, shizuku, panel)

        assertEquals(ToggleMethod.ACTION, choose(action = true, direct = true, permitted = true))
        // Lampe, DND accordé, WRITE_SETTINGS accordé : bascule directe.
        assertEquals(ToggleMethod.DIRECT, choose(direct = true, permitted = true, shell = true, shizuku = ShizukuStatus.READY))
        // DND sans accès, avec Shizuku : commande shell.
        assertEquals(ToggleMethod.SHIZUKU, choose(direct = true, shell = true, shizuku = ShizukuStatus.READY))
        // Sans accès ni Shizuku : page qui accorde le droit.
        assertEquals(ToggleMethod.GRANT, choose(direct = true, shell = true))
        // Wi-Fi : Shizuku prêt ou à autoriser, sinon panneau.
        assertEquals(ToggleMethod.SHIZUKU, choose(shell = true, panel = true, shizuku = ShizukuStatus.READY))
        assertEquals(ToggleMethod.SHIZUKU, choose(shell = true, panel = true, shizuku = ShizukuStatus.NEEDS_PERMISSION))
        assertEquals(ToggleMethod.PANEL, choose(shell = true, panel = true))
        // Mode avion sans Shizuku : page de réglages.
        assertEquals(ToggleMethod.SETTINGS, choose(shell = true))
        // Point d'accès : toujours la page.
        assertEquals(ToggleMethod.SETTINGS, choose(shizuku = ShizukuStatus.READY))

        assertEquals(ToggleMethod.GRANT, ToggleChooser.fallback(hasDirect = true, hasPanel = true))
        assertEquals(ToggleMethod.PANEL, ToggleChooser.fallback(hasDirect = false, hasPanel = true))
        assertEquals(ToggleMethod.SETTINGS, ToggleChooser.fallback(hasDirect = false, hasPanel = false))
    }

    @Test
    fun tilesDeclareTheirMethods() {
        assertTrue(TorchTile.direct.permitted(android.app.Application()))
        assertEquals("svc wifi enable", WifiTile.shizukuCommand(true))
        assertEquals("svc data disable", MobileDataTile.shizukuCommand(false))
        assertEquals("cmd connectivity airplane-mode enable", AirplaneTile.shizukuCommand(true))
        assertEquals(null, HotspotTile.shizukuCommand(true))
        assertTrue(ScreenshotTile.action != null && LockTile.action != null)
    }
}
