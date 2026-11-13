package com.karelisio.bulle.modules.live

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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

/** Rendu de l'agrégat de téléchargements (compact / étendu, 1, 2 et 6 téléchargements). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveDownloadsRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun intent(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("test.$id"), PendingIntent.FLAG_IMMUTABLE)

    private fun download(i: Int, cur: Int, indeterminate: Boolean = false, withActions: Boolean = false): LiveContent {
        val a = LiveActivity(
            key = "0|com.android.chrome|$i|null|1", packageName = if (i % 2 == 0) "com.android.chrome" else "com.android.vending",
            kind = LiveKind.PROGRESS, title = "fichier-$i.zip", text = "$cur Mo / 100 Mo", subText = null, shortText = null,
            progress = LiveProgress(cur, if (indeterminate) 0 else 100, indeterminate), chrono = null,
            color = if (i % 2 == 0) 0xFF4285F4.toInt() else 0, promoted = false, genericProgress = true,
            actions = if (withActions) listOf(LiveAction("Pause", intent(i * 10)), LiveAction("Annuler", intent(i * 10 + 1))) else emptyList(),
            contentIntent = intent(i),
        )
        val icon = if (i % 3 == 0) android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap() else null
        return LiveContent(a, LiveVisuals("Appli $i", icon, null), postedAt = 1_000L + i)
    }

    private fun aggregate(vararg items: LiveContent): LiveDownloadsContent =
        LiveDownloads.group(items.toList()) { n -> context.resources.getQuantityString(R.plurals.live_downloads_count, n, n) }
            .single() as LiveDownloadsContent

    private class TestHost(override val context: Context) : IslandHost {
        var collapsed = 0
        override fun expand(key: String) = Unit
        override fun collapse() { collapsed++ }
        override fun dismiss(key: String) = Unit
        override fun openPanel(panel: IslandPanel) = Unit
        override fun closePanel() = Unit
        override fun requestInput(enabled: Boolean) = Unit
        override fun runAction(action: GestureAction) = Unit
    }

    @Test
    fun rendersCompactAndExpandedWithOneTwoAndSixDownloads() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = TestHost(activity)
        val cases = listOf(
            aggregate(download(1, 40)),
            aggregate(download(1, 40), download(2, 0, indeterminate = true, withActions = true)),
            aggregate(*(1..6).map { download(it, it * 15, indeterminate = it == 4, withActions = it == 2) }.toTypedArray()),
            aggregate(download(1, 0, indeterminate = true), download(2, 0, indeterminate = true)),
        )
        assertEquals(listOf(1, 2, 6, 2), cases.map { it.items.size })
        for (c in cases) {
            activity.setContent {
                BulleTheme {
                    Column {
                        c.Leading()
                        c.Trailing()
                        c.Minimal()
                        c.Expanded(host)
                    }
                }
            }
            idle()
        }
        assertEquals("fichier-1.zip", cases[0].compactSubtitle)
        assertEquals(context.resources.getQuantityString(R.plurals.live_downloads_count, 6, 6), cases[2].compactSubtitle)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun expandedIslandStaysOpenWhenDownloadsChange() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        PushModule.push(aggregate(download(1, 10)))
        idle()
        assertEquals(LiveDownloads.KEY, controller.state.value.primary?.key)
        controller.onGesture(IslandGesture.TAP)
        idle()
        assertTrue(controller.state.value.expanded)

        // Un 2e téléchargement arrive, puis passe devant, puis 6 en parallèle : l'île reste ouverte.
        val steps = listOf(
            aggregate(download(1, 20), download(2, 5)),
            aggregate(download(2, 60), download(1, 30)),
            aggregate(*(1..6).map { download(it, it * 10) }.toTypedArray()),
            aggregate(download(3, 90)),
        )
        for (s in steps) {
            PushModule.push(s)
            idle()
            assertEquals(LiveDownloads.KEY, controller.state.value.primary?.key)
            assertTrue(controller.state.value.expanded)
        }
        controller.onOutsideTouch()
        idle()
        PushModule.remove(LiveDownloads.KEY)
        idle()
        scope.cancel()
    }
}
