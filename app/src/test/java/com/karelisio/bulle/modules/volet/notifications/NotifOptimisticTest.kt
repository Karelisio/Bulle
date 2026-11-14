package com.karelisio.bulle.modules.volet.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suppression optimiste : retrait immédiat du modèle affiché, oubli si confirmé, restauration si refusé. */
class NotifOptimisticTest {

    private fun item(key: String, group: String = "g", section: NotifSection = NotifSection.ALERTING) =
        sampleNotifItem(key, "App", key, "texte", null, section = section).copy(groupKey = group)

    private fun model(): NotifListModel = NotifListBuilder.build(
        listOf(
            item("a1", "ga"), item("a2", "ga"),
            item("b", "gb"),
            item("s", "gs", NotifSection.SILENT),
        ),
    )

    @Test
    fun hideRemovesItemsImmediatelyAndKeepsGroupIds() {
        val m = model()
        val hidden = NotifOptimistic.hide(emptyMap(), listOf("a1"), now = 100L)
        val shown = NotifOptimistic.apply(m, hidden.keys)
        val group = shown.groupOf("a2")!!
        assertEquals(listOf("a2"), group.items.map { it.key })
        // Même identifiant de groupe : la clé de la LazyColumn reste stable.
        assertEquals(m.groupOf("a2")!!.id, group.id)
        assertNull(shown.groupOf("a1"))
    }

    @Test
    fun emptyGroupsAndSectionsDisappear() {
        val m = model()
        val shown = NotifOptimistic.apply(m, setOf("s", "b"))
        assertEquals(listOf(NotifSection.ALERTING), shown.sections.map { it.section })
        assertEquals(1, shown.sections.single().groups.size)
        assertTrue(NotifOptimistic.apply(m, setOf("a1", "a2", "b", "s")).isEmpty)
    }

    @Test
    fun nothingHiddenReturnsSameInstance() {
        val m = model()
        assertSame(m, NotifOptimistic.apply(m, emptySet()))
        assertSame(m, NotifOptimistic.apply(m, setOf("inconnue")))
    }

    @Test
    fun hideKeepsFirstTimestamp() {
        val h = NotifOptimistic.hide(mapOf("a" to 10L), listOf("a", "b"), now = 50L)
        assertEquals(mapOf("a" to 10L, "b" to 50L), h)
    }

    @Test
    fun confirmedRemovalIsForgotten() {
        val hidden = mapOf("a1" to 0L)
        val r = NotifOptimistic.reconcile(hidden, activeKeys = setOf("a2", "b"), now = 10L)
        assertTrue(r.hidden.isEmpty())
        assertTrue(r.restored.isEmpty())
        assertNull(r.nextCheckAt)
    }

    @Test
    fun pendingRemovalSchedulesCheck() {
        val hidden = mapOf("a1" to 100L)
        val r = NotifOptimistic.reconcile(hidden, setOf("a1"), now = 500L, timeoutMs = 2_000L)
        assertSame(hidden, r.hidden)
        assertEquals(2_100L, r.nextCheckAt)
    }

    @Test
    fun refusedRemovalIsRestoredAfterTimeout() {
        val hidden = mapOf("a1" to 0L, "b" to 1_500L)
        val r = NotifOptimistic.reconcile(hidden, setOf("a1", "b"), now = 2_000L, timeoutMs = 2_000L)
        assertEquals(setOf("a1"), r.restored)
        assertEquals(mapOf("b" to 1_500L), r.hidden)
        assertEquals(3_500L, r.nextCheckAt)
        // La carte restaurée réapparaît dans le modèle affiché.
        assertTrue(NotifOptimistic.apply(model(), r.hidden.keys).groupOf("a1") != null)
    }

    @Test
    fun clearCascadeIsFastAndBounded() {
        assertEquals(0L, ClearCascade.delayMs(0))
        assertEquals(ClearCascade.STEP_MS * 3, ClearCascade.delayMs(3))
        assertEquals(ClearCascade.MAX_STAGGER_MS, ClearCascade.delayMs(500))
        assertTrue(ClearCascade.totalMs(100) <= ClearCascade.MAX_STAGGER_MS + ClearCascade.EXIT_MS)
        assertEquals(ClearCascade.EXIT_MS, ClearCascade.totalMs(0))
    }
}
