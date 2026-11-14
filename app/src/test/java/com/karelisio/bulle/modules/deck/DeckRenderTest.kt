package com.karelisio.bulle.modules.deck

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.deck.ui.DeckTabScreenContent
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Rendu Robolectric du Deck (barre de recherche) et de l'écran d'onglet (réorganisation). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1800dp")
class DeckRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class FakeHost(override val context: Context) : IslandHost {
        val inputRequests = mutableListOf<Boolean>()
        override fun expand(key: String) = Unit
        override fun collapse() = Unit
        override fun dismiss(key: String) = Unit
        override fun openPanel(panel: IslandPanel) = Unit
        override fun closePanel() = Unit
        override fun requestInput(enabled: Boolean) { inputRequests += enabled }
        override fun runAction(action: GestureAction) = Unit
    }

    private fun settle() {
        repeat(20) {
            Thread.sleep(50)
            // Les écritures d'état hors composition (clic, flux) doivent être signalées au Recomposer.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        }
    }

    /** Comme [settle], jusqu'à ce que [done] soit vrai (10 s de temps réel au plus : machine chargée). */
    private fun settleUntil(activity: Activity, done: (List<String>) -> Boolean): List<String> {
        val end = System.currentTimeMillis() + 10_000
        var all = texts(activity)
        while (!done(all) && System.currentTimeMillis() < end) {
            Thread.sleep(50)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            all = texts(activity)
        }
        return all
    }

    private fun root(activity: Activity): SemanticsNode {
        fun find(v: View): ViewRootForTest? = (v as? ViewRootForTest)
            ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(activity.window.decorView)!!.semanticsOwner.unmergedRootSemanticsNode
    }

    private fun nodes(activity: Activity): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(root(activity))
        return out
    }

    private fun texts(activity: Activity): List<String> = nodes(activity).flatMap { n ->
        (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
            (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) +
            listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text)
    }


    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    /** Activité suivie : détruite en fin de test pour libérer les effets (hôte de widgets, saisie…). */
    private fun launch(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().also { controllers += it }.get()

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        shadowOf(Looper.getMainLooper()).idle()
        // La recherche fait enregistrer à AppCatalog (objet global) un récepteur sur l'Application de CE test.
        runCatching {
            com.karelisio.bulle.core.apps.AppCatalog::class.java.getDeclaredField("watching").apply {
                isAccessible = true
                setBoolean(null, false)
            }
        }
        com.karelisio.bulle.core.apps.AppCatalog.invalidate()
    }

    @Test
    fun deckShowsSearchBarWithoutKeyboard() {
        val activity = launch()
        val host = FakeHost(activity)
        activity.setContent { BulleTheme { DeckPanel.DeckContent(host, DeckSettings()) } }
        settle()
        val all = texts(activity)
        assertTrue(all.toString(), all.contains("Search apps, maths, contacts…"))
        // La barre est un bouton (TalkBack) et le clavier ne s'ouvre pas tant qu'on ne la touche pas.
        val bar = nodes(activity).first { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Search apps, maths, contacts…" } == true
        }.let { generateSequence(it) { n -> n.parent }.firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null } }
        assertTrue(bar != null)
        assertTrue(host.inputRequests.isEmpty())
    }

    @Test
    fun deckHidesSearchBarWhenDisabled() {
        val activity = launch()
        activity.setContent {
            BulleTheme { DeckPanel.DeckContent(FakeHost(activity), DeckSettings(searchBar = false), initialSearch = true) }
        }
        settle()
        val all = texts(activity)
        assertFalse(all.contains("Search apps, maths, contacts…"))
        assertFalse(all.contains("Back to the Deck"))
        assertTrue(all.toString(), all.contains("Flashlight"))
    }

    @Test
    fun tabScreenReordersWithAccessibilityActions() {
        var deck = DeckSettings(
            tabs = listOf(DeckTab(id = "main", favorites = listOf("com.a/.Main", "com.b/.Main", "com.c/.Main"), shortcuts = emptyList())),
        )
        val activity = launch()
        activity.setContent {
            BulleTheme {
                DeckTabScreenContent(deck, { t -> deck = runBlocking { t(deck) } }, haptics = false, tabId = "main", onBack = {})
            }
        }
        settle()
        val all = texts(activity)
        // Une poignée par favori (réorganisation toujours disponible).
        assertEquals(all.toString(), 3, all.count { it == "Move (long press, then drag)" })
        // Actions TalkBack : le premier ne peut que descendre, le dernier que monter.
        val actions = nodes(activity).mapNotNull { n -> n.config.getOrNull(SemanticsActions.CustomActions) }
        assertEquals(
            listOf(listOf("Move down"), listOf("Move up", "Move down"), listOf("Move up")),
            actions.map { a -> a.map { it.label } },
        )
        actions.first().first().action()
        assertEquals(listOf("com.b/.Main", "com.a/.Main", "com.c/.Main"), deck.tabs.first().favorites)
        actions.last().first().action()
        assertEquals(listOf("com.b/.Main", "com.c/.Main", "com.a/.Main"), deck.tabs.first().favorites)
    }

    @Test
    fun deckSearchReusesIslandSearchInPlace() {
        val activity = launch()
        val host = FakeHost(activity)
        activity.setContent { BulleTheme { DeckPanel.DeckContent(host, DeckSettings(), initialSearch = true) } }
        val all = settleUntil(activity) { it.contains("Back to the Deck") }
        // Le contenu de la recherche de l'île est affiché dans le Deck et demande la saisie.
        assertTrue(all.toString(), all.contains("Back to the Deck"))
        assertEquals(true, host.inputRequests.firstOrNull())
        assertFalse(all.contains("Flashlight"))
    }

}
