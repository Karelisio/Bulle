package com.karelisio.bulle.modules

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.call.CallContent
import com.karelisio.bulle.modules.call.CallParser
import com.karelisio.bulle.modules.live.LiveContent
import com.karelisio.bulle.modules.live.LiveParser
import com.karelisio.bulle.modules.live.LiveVisuals
import com.karelisio.bulle.modules.push.PushModule
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Affiche une activité en cours et un appel dans l'île (compact puis étendu) : aucun ne doit planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveAndCallRenderTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun rendersCompactAndExpanded() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Animations coupées : les animations infinies ne bouclent pas dans Robolectric.
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        fun pi(id: Int) = PendingIntent.getBroadcast(activity, id, Intent("t.$id"), PendingIntent.FLAG_IMMUTABLE)
        val b = { Notification.Builder(activity, "t").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Minuteur") }

        val timer = LiveParser.parse(
            "timer", "com.clock",
            b().setOngoing(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + 90_000)
                .addAction(Notification.Action.Builder(null, "Pause", pi(1)).build()).build(),
        )!!
        val download = LiveParser.parse("dl", "com.browser", b().setOngoing(true).setProgress(100, 30, false).build())!!
        val incoming = CallParser.parse(
            "call", "com.dialer",
            b().setStyle(Notification.CallStyle.forIncomingCall(Person.Builder().setName("Alice").build(), pi(2), pi(3))).build(),
        )!!
        val visuals = LiveVisuals(appLabel = "Horloge", smallIcon = null, largeIcon = null)
        val contents: List<IslandContent> = listOf(
            LiveContent(timer, visuals, postedAt = 1),
            LiveContent(download, visuals, postedAt = 2),
            CallContent(incoming, avatar = null, appLabel = "Téléphone", autoExpand = false, startWallMillis = 0, postedAt = 3),
            CallContent(
                incoming.copy(state = com.karelisio.bulle.modules.call.CallState.ONGOING),
                avatar = null, appLabel = null, autoExpand = false,
                startWallMillis = System.currentTimeMillis() - 65_000, postedAt = 4,
            ),
        )
        for (c in contents) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            controller.onGesture(IslandGesture.TAP)
            idle()
            assertTrue(controller.state.value.expanded)
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }
        scope.cancel()
    }
}
