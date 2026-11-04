package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone
import com.karelisio.bulle.notifications.NotificationRepository
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

/** La liste du volet se compose avec de vraies notifications, dans plusieurs états. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsSectionRenderTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private class TestHost(override val context: Context) : VoletHost {
        override var expansion by mutableFloatStateOf(1f)
        override val zone = VoletZone.NOTIFICATIONS
        override var settings by mutableStateOf(VoletSettings(enabled = true))
        override var locked by mutableStateOf(false)
        override var focusKey by mutableStateOf<String?>(null)
        override var editingTiles = false
        val sent = mutableListOf<PendingIntent?>()
        override fun close() = Unit
        override fun expandFull() = Unit
        override fun collapseToHalf() = Unit
        override fun dragBy(dyPx: Float) = Unit
        override fun settle(velocityPxPerSec: Float) = Unit
        override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean): Boolean {
            sent += intent
            return true
        }
        override fun startActivity(intent: Intent) = Unit
        override fun runAction(action: GestureAction) = Unit
        override fun requestKeyboard(show: Boolean) = Unit
        override fun openSystemShade() = Unit
    }

    @Before
    fun setUp() {
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        NotificationRepository.onDisconnected()
    }

    @After
    fun tearDown() = NotificationRepository.onDisconnected()

    private fun intent(id: Int) = PendingIntent.getBroadcast(ctx, id, Intent("t.$id"), PendingIntent.FLAG_IMMUTABLE)

    @Suppress("DEPRECATION")
    private fun post(id: Int, pkg: String, n: Notification): StatusBarNotification {
        val sbn = StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        NotificationRepository.onPosted(sbn)
        return sbn
    }

    private fun builder(title: String) = Notification.Builder(ctx, "c")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText("Texte de $title")
        .setContentIntent(intent(title.hashCode()))

    private fun postSamples() {
        val input = RemoteInput.Builder("r").setChoices(arrayOf("Oui")).build()
        val alice = Person.Builder().setName("Alice").build()
        post(
            1, "com.chat",
            builder("Alice").setShortcutId("alice")
                .setStyle(Notification.MessagingStyle(Person.Builder().setName("Moi").build()).addMessage("Salut", 1L, alice))
                .addAction(Notification.Action.Builder(null, "Répondre", intent(9)).addRemoteInput(input).build())
                .build(),
        )
        post(2, "com.mail", builder("Courrier 1").setGroup("mails").setStyle(Notification.InboxStyle().addLine("a").addLine("b")).build())
        post(3, "com.mail", builder("Courrier 2").setGroup("mails").build())
        post(4, "com.dl", builder("Téléchargement").setOngoing(true).setProgress(100, 40, false).build())
        post(5, "com.news", builder("Article").setStyle(Notification.BigTextStyle().bigText("Long texte")).build())
        val rv = RemoteViews(ctx.packageName, android.R.layout.simple_list_item_1)
        post(6, "com.custom", Notification.Builder(ctx, "c").setSmallIcon(android.R.drawable.ic_dialog_info).setCustomContentView(rv).build())
    }

    private fun compose(host: TestHost) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            BulleTheme {
                NotificationsSection(host, Modifier.fillMaxSize()) { Text("Média") }
            }
        }
        idle()
    }

    @Test
    fun rendersAllKinds() {
        postSamples()
        val host = TestHost(ctx)
        compose(host)
        // Nouvelle notification pendant que le volet est ouvert, puis retrait.
        post(7, "com.late", builder("Tardive").build())
        idle()
        NotificationRepository.onRemoved(NotificationRepository.active.value.values.first { it.id == 7 }, 0)
        idle()
        assertEquals(6, NotificationRepository.active.value.size)
    }

    @Test
    fun rendersLockedCompactAndFocused() {
        postSamples()
        val host = TestHost(ctx).apply {
            locked = true
            settings = VoletSettings(enabled = true, density = VoletDensity.COMPACT)
            focusKey = NotificationRepository.active.value.values.first { it.id == 3 }.key
        }
        compose(host)
        host.expansion = 0f
        idle()
        host.expansion = 1f
        idle()
    }

    @Test
    fun emptyAndModelFromRepository() {
        val host = TestHost(ctx)
        compose(host)
        postSamples()
        idle()
        val model = NotifModelFactory.build(ctx, NotificationRepository.active.value.values, { null }, locked = false, excludedApps = setOf("com.news"))
        val keys = model.allItems.map { it.packageName }
        assertTrue("com.news" !in keys)
        // Les deux courriers du même groupe forment un seul groupe.
        assertEquals(2, model.groupOf(model.allItems.first { it.packageName == "com.mail" }.key)!!.items.size)
        // Toucher une notification déclenche son contentIntent.
        assertTrue(NotifCommands.open(host, model.allItems.first { it.packageName == "com.mail" }))
        assertEquals(1, host.sent.size)
    }
}
