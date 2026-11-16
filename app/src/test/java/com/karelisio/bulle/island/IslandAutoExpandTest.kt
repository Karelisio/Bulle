package com.karelisio.bulle.island

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paysage : seuls les appels ouvrent la carte d'eux-mêmes ; portrait inchangé. */
class IslandAutoExpandTest {
    @Test
    fun onlyCallsAutoExpandInLandscape() {
        assertTrue(IslandAutoExpand.allowed(vertical = true, moduleId = "call"))
        assertFalse(IslandAutoExpand.allowed(vertical = true, moduleId = "alerts"))
        assertFalse(IslandAutoExpand.allowed(vertical = true, moduleId = "push"))
        assertTrue(IslandAutoExpand.allowed(vertical = false, moduleId = "alerts"))
    }
}
