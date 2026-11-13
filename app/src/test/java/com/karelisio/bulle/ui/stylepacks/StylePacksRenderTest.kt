package com.karelisio.bulle.ui.stylepacks

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.style.BuiltInPacks
import com.karelisio.bulle.core.style.EntryAnimation
import com.karelisio.bulle.core.style.MyStylesStore
import com.karelisio.bulle.core.style.SavedStyles
import com.karelisio.bulle.core.style.SavedStylesOps
import com.karelisio.bulle.core.style.StyleShare
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.settings.StyleScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private data class EntryTestContent(override val key: String, override val moduleId: String) : IslandContent() {
    override val priority = Priority.ALERT
    override val postedAt: Long = 1

    @Composable
    override fun Leading() = Text("L")

    @Composable
    override fun Trailing() = Text("T")
}

/** Rendu des écrans Packs de styles, Partage et Animations d'entrée, et du crochet dans l'île. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StylePacksRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        return activity
    }

    @Test
    fun carouselAndMyStylesRender() {
        runBlocking {
            MyStylesStore.update(context) { SavedStylesOps.add(SavedStyles(), BuiltInPacks.byId("neon")!!.pack, id = "n") }
        }
        var consumed = 0
        val qr = StyleShare.encodeQr(BuiltInPacks.byId("retro")!!.pack)
        render {
            StylePacksScreen(onBack = {}, onScanQr = {}, importRequest = qr, onImportRequestConsumed = { consumed++ })
        }
        idle()
        // Le texte scanné a été pris en compte une seule fois.
        assertEquals(1, consumed)
        runBlocking { assertTrue(MyStylesStore.flow(context).first().any { it.id == "n" }) }
    }

    @Test
    fun shareScreenRenders() {
        render { StyleShareScreen(BuiltInPacks.byId("liquid")!!.pack, onBack = {}) }
        render { StyleShareScreen(BuiltInPacks.byId("bulle")!!.pack.copy(name = ""), onBack = {}) }
    }

    @Test
    fun entryAnimationsScreenRenders() {
        runBlocking {
            SettingsRepository.update(context) {
                it.copy(motion = it.motion.copy(entryAnimations = mapOf("default" to "DROP", "music" to "RIPPLE")))
            }
        }
        render { EntryAnimationsScreen(onBack = {}) }
        // Aperçus avec animations système actives (boucle d'aperçu non lancée en test : une seule passe).
        render {
            EntryAnimation.entries.forEach { a -> EntryPreviewCard(a, selected = a == EntryAnimation.DROP, tick = 0, speed = 1f) {} }
        }
    }

    @Test
    fun styleScreenStillRendersWithEntryPoints() {
        render { StyleScreen(onBack = {}) }
        render { StyleScreen(onBack = {}, onOpenStylePacks = {}, onOpenEntryAnimations = {}) }
    }

    @Test
    fun islandPlaysEntryAnimationsWithoutBreaking() {
        // Animations système actives : les vraies courbes (goutte, éclat) sont jouées.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        runBlocking {
            SettingsRepository.update(context) {
                it.copy(motion = it.motion.copy(entryAnimations = mapOf("default" to "DROP", "other" to "RIPPLE")))
            }
        }
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()
        val a = EntryTestContent("entry:a", PushModule.id)
        val b = EntryTestContent("entry:b", "other")
        PushModule.push(a)
        idle()
        assertEquals("entry:a", controller.state.value.primary?.key)
        PushModule.push(b)
        idle()
        PushModule.remove(b.key)
        PushModule.remove(a.key)
        idle()
        scope.cancel()
    }
}
