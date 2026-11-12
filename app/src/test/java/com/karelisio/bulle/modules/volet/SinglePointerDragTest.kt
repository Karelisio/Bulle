package com.karelisio.bulle.modules.volet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suivi d'un seul doigt dans les zones de déclenchement. */
class SinglePointerDragTest {

    private class Recorder : SinglePointerDrag.Callbacks {
        var starts = 0
        val drags = mutableListOf<Float>()
        val ends = mutableListOf<Boolean>()
        override fun onStart() { starts++ }
        override fun onDrag(dyPx: Float) { drags += dyPx }
        override fun onEnd(cancelled: Boolean) { ends += cancelled }
        val total get() = drags.sum()
    }

    @Test
    fun singleFingerAfterSlop() {
        val r = Recorder()
        val d = SinglePointerDrag(10f, r)
        d.down(0, 0f)
        d.move { 5f }
        assertEquals(0, r.starts)
        d.move { 30f }
        assertEquals(1, r.starts)
        assertEquals(20f, r.total, 1e-4f)
        d.up(cancelled = false)
        assertEquals(listOf(false), r.ends)
    }

    @Test
    fun secondFingerIgnored() {
        val r = Recorder()
        val d = SinglePointerDrag(10f, r)
        d.down(0, 0f)
        d.move { 50f }
        // Un second doigt se pose bien plus bas : aucun saut, seul le doigt 0 compte.
        val pos = mutableMapOf(0 to 60f, 1 to 400f)
        d.move { pos[it] }
        assertEquals(50f, r.total, 1e-4f)
        pos[1] = 700f
        d.move { pos[it] }
        assertEquals(50f, r.total, 1e-4f)
        // Le second doigt se lève : rien ne change.
        assertFalse(d.pointerUp(1, listOf(0 to 60f)))
        assertEquals(0, d.activeId)
        assertTrue(r.ends.isEmpty())
    }

    @Test
    fun activeFingerLiftsSwitchesWithoutJump() {
        val r = Recorder()
        val d = SinglePointerDrag(10f, r)
        d.down(0, 0f)
        d.move { mapOf(0 to 100f)[it] }
        val before = r.total
        // Le doigt actif se lève, le doigt 1 (posé à 500) reprend sans saut.
        assertTrue(d.pointerUp(0, listOf(1 to 500f)))
        assertEquals(1, d.activeId)
        d.move { mapOf(1 to 500f)[it] }
        assertEquals(before, r.total, 1e-4f)
        d.move { mapOf(1 to 520f)[it] }
        assertEquals(before + 20f, r.total, 1e-4f)
        assertTrue(r.ends.isEmpty())
        d.up(cancelled = false)
        assertEquals(listOf(false), r.ends)
        assertEquals(SinglePointerDrag.NONE, d.activeId)
    }

    @Test
    fun cancelAndLiftWithoutOthers() {
        val r = Recorder()
        val d = SinglePointerDrag(10f, r)
        d.down(3, 0f)
        d.move { 40f }
        d.pointerUp(3, emptyList())
        assertEquals(listOf(false), r.ends)
        // Après la fin, plus aucun mouvement n'est transmis.
        d.move { 90f }
        assertEquals(30f, r.total, 1e-4f)
        // Geste annulé sans glissement : pas de fin signalée.
        d.down(0, 0f)
        d.up(cancelled = true)
        assertEquals(1, r.ends.size)
    }
}
