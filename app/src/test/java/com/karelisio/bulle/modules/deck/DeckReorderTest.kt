package com.karelisio.bulle.modules.deck

import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure de réorganisation du Deck et persistance de l'ordre. */
class DeckReorderTest {

    @Test
    fun moveDownAndUp() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), l.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), l.moved(3, 0))
        assertEquals(listOf("a", "c", "b", "d"), l.moved(1, 2))
    }

    @Test
    fun moveOutOfBoundsOrSameIsNoOp() {
        val l = listOf(1, 2, 3)
        assertSame(l, l.moved(1, 1))
        assertSame(l, l.moved(-1, 0))
        assertSame(l, l.moved(0, 3))
        assertSame(emptyList<Int>(), emptyList<Int>().moved(0, 0))
    }

    private val deck = DeckSettings(
        tabs = listOf(
            DeckTab(id = "main", favorites = listOf("p/.A", "p/.B", "p/.C"), widgets = listOf(10, 20, 30)),
            DeckTab(id = "other", favorites = listOf("p/.A", "p/.B"), widgets = listOf(1, 2)),
        ),
    )

    @Test
    fun reorderTouchesOnlyTargetTabAndSection() {
        val next = deck.reorder("main", DeckSection.WIDGETS, 2, 0)
        assertEquals(listOf(30, 10, 20), next.tabs[0].widgets)
        assertEquals(deck.tabs[0].favorites, next.tabs[0].favorites)
        assertEquals(deck.tabs[1], next.tabs[1])
        val fav = deck.reorder("main", DeckSection.FAVORITES, 0, 1)
        assertEquals(listOf("p/.B", "p/.A", "p/.C"), fav.tabs[0].favorites)
    }

    @Test
    fun withOrderAppliesOnlyMatchingSets() {
        val next = deck.withOrder("main", DeckSection.WIDGETS, listOf("20", "30", "10"))
        assertEquals(listOf(20, 30, 10), next.tabs[0].widgets)
        // Liste modifiée entre-temps (élément manquant ou étranger) : ordre ignoré.
        assertEquals(deck, deck.withOrder("main", DeckSection.WIDGETS, listOf("20", "10")))
        assertEquals(deck, deck.withOrder("main", DeckSection.FAVORITES, listOf("p/.A", "p/.B", "p/.Z")))
        val fav = deck.withOrder("main", DeckSection.FAVORITES, listOf("p/.C", "p/.A", "p/.B"))
        assertEquals(listOf("p/.C", "p/.A", "p/.B"), fav.tabs[0].favorites)
    }

    @Test
    fun orderSurvivesJsonRoundTrip() {
        val settings = BulleSettings(deck = deck.reorder("main", DeckSection.WIDGETS, 0, 2).copy(searchBar = false))
        val json = BulleJson.encodeToString(BulleSettings.serializer(), settings)
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), json)
        assertEquals(listOf(20, 30, 10), back.deck.tabs[0].widgets)
        assertEquals(false, back.deck.searchBar)
    }

    @Test
    fun oldSettingsEnableSearchBarByDefault() {
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), """{"deck":{"tabs":[{"id":"main"}]}}""")
        assertTrue(back.deck.searchBar)
    }

    @Test
    fun shortcutsReorderByPosition() {
        val a = com.karelisio.bulle.core.actions.GestureAction(com.karelisio.bulle.core.actions.ActionType.TORCH)
        val b = com.karelisio.bulle.core.actions.GestureAction(com.karelisio.bulle.core.actions.ActionType.WIFI)
        val deck = DeckSettings(tabs = listOf(DeckTab(id = "main", shortcuts = listOf(a, b, a))))
        assertEquals(listOf(b, a, a), deck.reorder("main", DeckSection.SHORTCUTS, 1, 0).tabs[0].shortcuts)
        assertEquals(listOf(a, a, b), deck.withOrder("main", DeckSection.SHORTCUTS, listOf("0", "2", "1")).tabs[0].shortcuts)
        // Ordre incomplet : ignoré.
        assertEquals(deck, deck.withOrder("main", DeckSection.SHORTCUTS, listOf("0", "1")))
    }

}
