package com.karelisio.bulle.ui.shell

import com.karelisio.bulle.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure de la coque à onglets. */
class ShellNavigationTest {

    @Test
    fun defaultTabIsHomeAndFirst() {
        assertEquals(ShellTab.HOME, ShellNavigation.DEFAULT)
        assertEquals(ShellTab.HOME, ShellTab.entries.first())
        assertEquals(4, ShellTab.entries.size)
    }

    @Test
    fun backFromOtherTabReturnsHome() {
        for (tab in ShellTab.entries - ShellTab.HOME) {
            assertEquals(ShellTab.HOME, ShellNavigation.onBack(tab))
            assertTrue(ShellNavigation.interceptsBack(tab))
        }
    }

    @Test
    fun backFromHomeLetsTheAppExit() {
        assertNull(ShellNavigation.onBack(ShellTab.HOME))
        assertFalse(ShellNavigation.interceptsBack(ShellTab.HOME))
    }

    @Test
    fun directionFollowsBarOrder() {
        assertEquals(1, ShellNavigation.direction(ShellTab.HOME, ShellTab.SETTINGS))
        assertEquals(-1, ShellNavigation.direction(ShellTab.CONTENTS, ShellTab.LOOK))
        assertEquals(0, ShellNavigation.direction(ShellTab.LOOK, ShellTab.LOOK))
    }

    @Test
    fun tabsListTheExpectedScreens() {
        fun routes(tab: ShellTab) = ShellCatalog.groups(tab).flatMap { g -> g.entries.map { it.route } }
        assertTrue(routes(ShellTab.HOME).isEmpty())
        assertEquals(listOf(Routes.STYLE, Routes.POSITION, Routes.VISIBILITY, Routes.GESTURES), routes(ShellTab.LOOK))
        assertEquals(
            setOf(Routes.MUSIC, Routes.LIVE, Routes.CALLS, Routes.ALERTS, Routes.CALENDAR, Routes.WEATHER, Routes.FOCUS, Routes.MESSAGES, Routes.NETWORK, Routes.BATTERY, Routes.COUNTDOWN, Routes.SCREENTIME, Routes.VOLUME, Routes.VOLET, Routes.DECK, Routes.FOCUS_MODE, Routes.RULES, Routes.CAPTURE, Routes.INTEGRATIONS),
            routes(ShellTab.CONTENTS).toSet(),
        )
        assertEquals(
            listOf(Routes.RELIABILITY, Routes.PERMISSIONS, Routes.BACKUP, Routes.ABOUT),
            routes(ShellTab.SETTINGS),
        )
        // Aucun écran n'apparaît deux fois, et aucun groupe n'est vide.
        val all = ShellTab.entries.flatMap(::routes)
        assertEquals(all.size, all.toSet().size)
        ShellTab.entries.flatMap(ShellCatalog::groups).forEach { assertTrue(it.entries.isNotEmpty()) }
    }
}
