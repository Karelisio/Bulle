package com.karelisio.bulle.modules.search

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.search.ui.SearchSettingsScreen
import com.karelisio.bulle.modules.translate.MissingModelException
import com.karelisio.bulle.modules.translate.TranslatePanel
import com.karelisio.bulle.modules.translate.TranslatePanelContent
import com.karelisio.bulle.modules.translate.TranslationEngine
import com.karelisio.bulle.modules.translate.ui.TranslateSettingsContent
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Rendu Robolectric du panneau de recherche (résultats) et du panneau de traduction (traducteur factice). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchTranslateRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Les delay() des effets tournent en temps réel (DefaultExecutor) : on laisse passer le temps réel aussi. */
    private fun settle() {
        repeat(25) {
            Thread.sleep(60)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        }
    }

    /** Comme [settle], mais jusqu'à ce que [done] soit vrai (10 s de temps réel au plus : machine chargée). */
    private fun settleUntil(activity: Activity, done: (List<String>) -> Boolean): List<String> {
        val end = System.currentTimeMillis() + 10_000
        var all = texts(activity)
        while (!done(all) && System.currentTimeMillis() < end) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            all = texts(activity)
        }
        return all
    }

    private class FakeHost(override val context: Context) : IslandHost {
        val inputRequests = mutableListOf<Boolean>()
        var closed = 0
        override fun expand(key: String) = Unit
        override fun collapse() = Unit
        override fun dismiss(key: String) = Unit
        override fun openPanel(panel: IslandPanel) = Unit
        override fun closePanel() { closed++ }
        override fun requestInput(enabled: Boolean) { inputRequests += enabled }
        override fun runAction(action: GestureAction) = Unit
    }

    /** Engine factice : « Hello » → « Bonjour », modèles manquants simulables. */
    private class FakeEngine(var missing: List<String> = emptyList()) : TranslationEngine {
        val downloads = mutableListOf<String>()
        override val languages = listOf("de", "en", "es", "fr")
        override suspend fun identify(text: String) = if (text.startsWith("Hello")) "en" else "fr"
        override suspend fun downloaded() = languages.toSet() - missing.toSet()
        override suspend fun download(language: String, wifiOnly: Boolean) {
            downloads += language
            missing = missing - language
        }
        override suspend fun delete(language: String) { missing = missing + language }
        override suspend fun translate(text: String, source: String, target: String): String {
            if (missing.isNotEmpty()) throw MissingModelException(missing)
            return if (text == "Hello") "Bonjour" else "[$source→$target] $text"
        }
    }

    private fun texts(activity: Activity): List<String> {
        fun find(v: View): ViewRootForTest? = (v as? ViewRootForTest)
            ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        val root = find(activity.window.decorView) ?: return emptyList()
        val out = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { out += it.text }
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { out += it }
            n.children.forEach(::walk)
        }
        walk(root.semanticsOwner.unmergedRootSemanticsNode)
        return out
    }

    private fun renderSearch(query: String): Pair<ComponentActivity, FakeHost> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FakeHost(activity)
        activity.setContent { BulleTheme { SearchPanelContent(host, initialQuery = query, debounceMs = 0) } }
        settle()
        return activity to host
    }

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun tearDown() {
        CurrencyRates.fetcher = { null }
        // Le panneau de recherche fait enregistrer à AppCatalog (objet global) son récepteur de paquets
        // sur l'Application de CE test : on le réarme pour les tests suivants (nouvelle Application).
        runCatching {
            com.karelisio.bulle.core.apps.AppCatalog::class.java.getDeclaredField("watching").apply {
                isAccessible = true
                setBoolean(null, false)
            }
        }
        com.karelisio.bulle.core.apps.AppCatalog.invalidate()
    }

    @Test
    fun searchShowsCalculatorResult() {
        val (activity, host) = renderSearch("2+3×4")
        val all = texts(activity)
        assertTrue(all.toString(), all.any { it == "= 14" })
        assertEquals(true, host.inputRequests.first())
    }

    @Test
    fun searchShowsUnitConversion() {
        val (activity, _) = renderSearch("5 km en miles")
        val all = texts(activity)
        assertTrue(all.toString(), all.any { it.startsWith("3") && it.endsWith(" mi") })
    }

    @Test
    fun searchShowsCurrencyFromFetchedRates() {
        var calls = 0
        CurrencyRates.fetcher = {
            calls++
            """{"amount":1.0,"base":"EUR","date":"2026-11-12","rates":{"USD":1.1}}"""
        }
        val (activity, _) = renderSearch("20 € en $")
        val all = settleUntil(activity) { t -> t.any { it.contains("22") && it.endsWith("USD") } }
        assertTrue(all.toString(), all.any { it.contains("22") && it.endsWith("USD") })
        assertEquals(1, calls)
    }

    @Test
    fun searchShowsSettingsShortcutAndContactsChip() {
        val (activity, _) = renderSearch("wifi")
        val all = texts(activity)
        assertTrue(all.toString(), all.contains("Wi-Fi"))
        // READ_CONTACTS non accordée : une puce propose de l'autoriser, la recherche continue.
        assertTrue(all.toString(), all.contains("Allow contacts"))
    }

    @Test
    fun searchSettingsScreenRenders() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { SearchSettingsScreen(onBack = {}) } }
        settle()
        assertTrue(texts(activity).contains("Calculator"))
    }

    @Test
    fun translatePanelReadsClipboardAndTranslates() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FakeHost(activity)
        val engine = FakeEngine()
        activity.setContent { BulleTheme { TranslatePanelContent(host, engine, debounceMs = 0, clipboardFallbackMs = 0) { "Hello" } } }
        val all = settleUntil(activity) { it.contains("Bonjour") }
        assertTrue(all.toString(), all.contains("Hello"))
        assertTrue(all.toString(), all.contains("Bonjour"))
        assertEquals(true, host.inputRequests.first())
    }

    @Test
    fun translatePanelExplainsMissingModel() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FakeHost(activity)
        val engine = FakeEngine(missing = listOf("fr"))
        activity.setContent { BulleTheme { TranslatePanelContent(host, engine, debounceMs = 0, clipboardFallbackMs = 0) { "Hello" } } }
        val all = settleUntil(activity) { it.contains("Download") }
        assertTrue(all.toString(), all.any { it.startsWith("Offline model missing") && it.contains("French") })
        assertTrue(all.toString(), all.contains("Download"))
    }

    @Test
    fun translatePanelEmptyClipboardOffersPaste() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FakeHost(activity)
        activity.setContent { BulleTheme { TranslatePanelContent(host, FakeEngine(), debounceMs = 0, clipboardFallbackMs = 0) { null } } }
        settle()
        assertTrue(texts(activity).contains("Paste or type some text"))
    }

    @Test
    fun clipboardIsReadFromSystem() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("t", "  Hola  "))
        assertEquals("Hola", TranslatePanel.readClipboard(context))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2400dp")
    fun translateSettingsListsModels() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { TranslateSettingsContent(onBack = {}, engine = FakeEngine(missing = listOf("de"))) } }
        settle()
        val all = texts(activity)
        assertTrue(all.toString(), all.contains("German"))
        assertTrue(all.toString(), all.contains("Not downloaded"))
        assertTrue(all.toString(), all.contains("Built in"))
    }
}
