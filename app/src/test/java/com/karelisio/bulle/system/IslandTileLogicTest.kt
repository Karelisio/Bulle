package com.karelisio.bulle.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandTileLogicTest {
    @Test
    fun tapInvertsDisplayedState() {
        // Tuile active → on désactive l'île.
        assertFalse(IslandTileService.nextEnabled(running = true, enabled = true))
        // Tuile inactive (île désactivée) → on l'active.
        assertTrue(IslandTileService.nextEnabled(running = true, enabled = false))
        // Tuile inactive parce que le service était coupé : l'île doit rester / devenir activée.
        assertTrue(IslandTileService.nextEnabled(running = false, enabled = true))
        assertTrue(IslandTileService.nextEnabled(running = false, enabled = false))
    }
}
