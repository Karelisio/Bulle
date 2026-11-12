package com.karelisio.bulle.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveValueTest {

    @Test
    fun dragShowsLocalValueUntilStoredCatchesUp() {
        val live = LiveValue().apply { stored = 1f }
        live.drag(1.4f)
        assertEquals(1.4f, live.value)
        assertEquals(1.4f, live.pending.value)

        // Fin du glissement alors que l'écriture n'est pas terminée : on garde la valeur locale.
        live.finish()
        assertEquals(1.4f, live.value)

        // Écriture terminée et valeur relue.
        live.stored = 1.4f
        live.writing = false
        live.settle()
        assertNull(live.local)
        assertEquals(1.4f, live.value)
    }

    @Test
    fun localValueIsKeptWhileDragging() {
        val live = LiveValue().apply { stored = 0f }
        live.drag(2f)
        live.stored = 2f
        live.writing = false
        live.settle()
        assertEquals(2f, live.local)
    }
}
