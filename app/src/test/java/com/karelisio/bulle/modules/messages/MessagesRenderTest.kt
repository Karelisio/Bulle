package com.karelisio.bulle.modules.messages

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.messages.ui.MessagesSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.volet.notifications.NotifInput
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
import java.io.File

/** Rendu Robolectric : message compact et étendu (déverrouillé, verrouillé), écran de réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessagesRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        MessagesPrefs.resetForTests()
    }

    private fun pi(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("t.$id"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun info(): MessageInfo {
        val me = Person.Builder().setName("Moi").build()
        val alice = Person.Builder().setName("Alice").build()
        val n = Notification.Builder(context, "msg").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(Notification.MessagingStyle(me).addMessage("Salut", 1L, alice).addMessage("Tu viens ce soir ?", 2L, alice))
            .setContentIntent(pi(1))
            .addAction(
                Notification.Action.Builder(null, "Répondre", pi(2))
                    .addRemoteInput(RemoteInput.Builder("r").setChoices(arrayOf("Oui", "Non")).build())
                    .setAllowGeneratedReplies(true)
                    .build(),
            )
            .addAction(
                Notification.Action.Builder(null, "Lu", pi(3))
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).build(),
            )
            .build()
        return MessageExtractor.extract(NotifInput("chat", "com.chat", n, 1L, "app|com.chat"), "Chat")
    }

    @Test
    fun rendersCompactAndExpanded() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        val info = info()
        assertTrue(info.reply != null && info.markAsRead != null)
        val settings = MessagesSettings(timeoutMs = 60_000L)
        val contents = listOf(
            MessagesModule.build(MessagesModule.Entry(info, android.os.SystemClock.elapsedRealtime(), 1), settings, false, MessageInputState(), "Nouveau message"),
            MessagesModule.build(MessagesModule.Entry(info, android.os.SystemClock.elapsedRealtime(), 2), settings, true, MessageInputState(), "Nouveau message"),
            MessagesModule.build(
                MessagesModule.Entry(info, android.os.SystemClock.elapsedRealtime(), 3),
                settings.copy(showContent = false), false, MessageInputState(), "Nouveau message",
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
        MessageInput.clear("messages:chat:1")
        scope.cancel()
    }

    @Test
    fun settingsScreenRendersAndPersists() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { MessagesSettingsScreen(onBack = {}) } }
        idle()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
        idle()

        MessagesPrefs.update(context) { it.copy(excludedApps = setOf("com.chat"), showContent = false) }
        val file = File(context.filesDir, "messages-test.json")
        MessagesPrefs.write(file, MessagesPrefs.data.value)
        val read = MessagesPrefs.read(file)
        assertEquals(setOf("com.chat"), read.excludedApps)
        assertFalse(read.showContent)
        assertEquals(MessagesSettings.DEFAULT_TIMEOUT_MS, read.timeoutMs)
        assertFalse(MessagesSettings().autoExpand)
        assertTrue(MessagesModule.enabledByDefault)
    }
}
