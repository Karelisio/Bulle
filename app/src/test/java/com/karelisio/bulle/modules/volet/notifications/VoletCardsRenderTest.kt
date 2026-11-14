package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.modules.volet.VoletCardDensity
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

/** Rendu des cartes : densités, aperçu des réglages, un seul avatar en conversation 1-à-1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoletCardsRenderTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Le modèle est reconstruit hors du fil principal : attend que [cond] devienne vrai. */
    private fun waitUntil(cond: () -> Boolean) {
        repeat(100) {
            idle()
            if (cond()) return
            Thread.sleep(20)
        }
        idle()
    }

    private class TestHost(override val context: Context, initial: VoletSettings) : VoletHost {
        override var expansion by mutableFloatStateOf(1f)
        override val zone = VoletZone.NOTIFICATIONS
        override var settings by mutableStateOf(initial)
        override val locked = false
        override val focusKey: String? = null
        override var editingTiles = false
        override fun close() = Unit
        override fun expandFull() = Unit
        override fun collapseToHalf() = Unit
        override fun dragBy(dyPx: Float) = Unit
        override fun settle(velocityPxPerSec: Float) = Unit
        override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = true
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

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        return activity
    }

    private fun root(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) root(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun count(node: SemanticsNode, tag: String): Int =
        (if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) 1 else 0) + node.children.sumOf { count(it, tag) }

    private fun avatars(activity: ComponentActivity): Int {
        val owner = root(activity.window.decorView)!!.semanticsOwner
        return count(owner.unmergedRootSemanticsNode, AVATAR_TAG)
    }

    private fun text(activity: ComponentActivity): String {
        val owner = root(activity.window.decorView)!!.semanticsOwner
        val out = StringBuilder()
        fun walk(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { out.append(it.text).append('\n') }
            n.children.forEach(::walk)
        }
        walk(owner.unmergedRootSemanticsNode)
        return out.toString()
    }

    private fun msg(sender: String) = NotifMessage(sender, "Bonjour de $sender", System.currentTimeMillis(), null, fromSelf = false)

    @Test
    fun oneToOneConversationShowsASingleAvatar() {
        val item = sampleNotifItem("c", "Messages", "Alice", "Salut", null, messages = listOf(msg("Alice"), msg("Alice")), section = NotifSection.CONVERSATIONS)
        val host = TestHost(ctx, VoletSettings(enabled = true))
        // Dépliée : les deux messages sont affichés, sans avatar à côté des bulles.
        val activity = render { NotifCard(item, host, NotifDims.of(host.settings), focused = false, initiallyExpanded = true) }
        assertEquals(1, avatars(activity))
    }

    @Test
    fun groupConversationShowsAvatarPerSenderOnlyWhenSendersDiffer() {
        val item = sampleNotifItem("g", "Messages", "Famille", "", null, messages = listOf(msg("Alice"), msg("Bob")), section = NotifSection.CONVERSATIONS)
            .copy(isGroupConversation = true)
        val host = TestHost(ctx, VoletSettings(enabled = true))
        val activity = render { NotifCard(item, host, NotifDims.of(host.settings), focused = false, initiallyExpanded = true) }
        // Avatar du groupe en tête + un petit avatar par message.
        assertEquals(3, avatars(activity))
    }

    @Test
    fun compactConversationUsesPlainText() {
        val item = sampleNotifItem("c", "Messages", "Alice", "Salut", null, messages = listOf(msg("Alice")), section = NotifSection.CONVERSATIONS)
        val host = TestHost(ctx, VoletSettings(enabled = true, cardDensity = VoletCardDensity.COMPACT))
        val activity = render { NotifCard(item, host, NotifDims.of(host.settings), focused = false, initiallyExpanded = false) }
        assertEquals(1, avatars(activity))
        val t = text(activity)
        assertTrue(t, "Alice" in t && "Bonjour de Alice" in t && "Messages" in t)
    }

    @Test
    fun settingsPreviewFollowsDensity() {
        var settings by mutableStateOf(VoletSettings(enabled = true))
        val activity = render { NotifCardPreview(settings) }
        assertEquals(1, avatars(activity))
        VoletCardDensity.entries.forEach { d ->
            settings = settings.copy(cardDensity = d, cardTextScale = 1.15f, cardCollapsedLines = 1)
            idle()
            assertEquals(1, avatars(activity))
        }
        assertTrue(text(activity).contains(ctx.getString(com.karelisio.bulle.R.string.vset_cards_sample_title)))
    }

    @Suppress("DEPRECATION")
    private fun post(id: Int, pkg: String, n: Notification) {
        NotificationRepository.onPosted(StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, n, Process.myUserHandle(), System.currentTimeMillis()))
    }

    @Test
    fun compactListRendersAndUpdates() {
        val alice = Person.Builder().setName("Alice").build()
        post(
            1, "com.chat",
            Notification.Builder(ctx, "c").setSmallIcon(android.R.drawable.ic_dialog_info).setShortcutId("alice")
                .setStyle(Notification.MessagingStyle(Person.Builder().setName("Moi").build()).addMessage("Salut", 1L, alice)).build(),
        )
        repeat(4) { i ->
            post(10 + i, "com.app$i", Notification.Builder(ctx, "c").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Titre $i").setContentText("Texte $i").build())
        }
        val host = TestHost(ctx, VoletSettings(enabled = true, cardDensity = VoletCardDensity.COMPACT, cardTextScale = 0.85f, cardCollapsedLines = 1))
        val activity = render { NotificationsSection(host, Modifier.fillMaxSize()) { Text("Média") } }
        assertTrue(text(activity).contains("Titre 0"))
        // Retrait réel : la carte disparaît de la liste.
        NotificationRepository.onRemoved(NotificationRepository.active.value.values.first { it.id == 10 }, 0)
        host.settings = host.settings.copy(cardDensity = VoletCardDensity.AIRY)
        waitUntil { !text(activity).contains("Titre 0") }
        assertTrue(!text(activity).contains("Titre 0"))
        val after = text(activity)
        assertTrue(after, after.contains("Titre 1"))
    }
}
