package com.karelisio.bulle.modules.messages

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.volet.notifications.NotifInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Détection, extraction, réponse et confidentialité du module « Messages », avec de vraies Notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageLogicTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val me = Person.Builder().setName("Moi").build()
    private val alice = Person.Builder().setName("Alice")
        .setIcon(Icon.createWithBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))).build()
    private val bob = Person.Builder().setName("Bob").build()

    private fun pi(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("test.$id"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun builder() = Notification.Builder(context, "msg").setSmallIcon(android.R.drawable.ic_dialog_email)

    private fun chat(vararg messages: Pair<String, Person?>, onlyAlertOnce: Boolean = false, group: String? = null): Notification {
        val style = Notification.MessagingStyle(me)
        if (group != null) style.setConversationTitle(group).setGroupConversation(true)
        messages.forEachIndexed { i, (text, who) -> style.addMessage(text, 1_000L + i, who) }
        return builder().setStyle(style).setOnlyAlertOnce(onlyAlertOnce).setContentIntent(pi(9)).build()
    }

    private fun input(n: Notification, key: String = "k") = NotifInput(key, "com.chat", n, 1_000L, "app|com.chat")

    // --- Détection ---

    @Test
    fun recognisesMessagingNotifications() {
        assertTrue(MessageDetector.isMessaging(chat("Salut" to alice), isConversation = false))
        assertTrue(MessageDetector.isMessaging(builder().setCategory(Notification.CATEGORY_MESSAGE).build(), false))
        assertTrue(MessageDetector.isMessaging(builder().setContentTitle("x").build(), isConversation = true))
        assertFalse(MessageDetector.isMessaging(builder().setContentTitle("Mise à jour").setContentText("Prête").build(), false))
    }

    @Test
    fun newMessageVersusSilentUpdate() {
        val tracker = MessageTracker()
        // Déjà affichée au démarrage : jamais annoncée telle quelle.
        tracker.seed("old", chat("Ancien" to bob))
        assertFalse(tracker.onPosted("old", chat("Ancien" to bob)))

        // Première publication d'une conversation : nouveau message.
        assertTrue(tracker.onPosted("k", chat("Salut" to alice)))
        // Republication identique (simple mise à jour) : rien.
        assertFalse(tracker.onPosted("k", chat("Salut" to alice)))
        // Nouveau dernier message : annoncé.
        assertTrue(tracker.onPosted("k", chat("Salut" to alice, "Tu viens ?" to alice)))
        // Notre propre réponse ajoutée par l'appli : rien.
        assertFalse(tracker.onPosted("k", chat("Salut" to alice, "Tu viens ?" to alice, "Oui" to null)))
        // « Alerter une seule fois » sur une mise à jour : rien, même avec un nouveau message.
        assertFalse(tracker.onPosted("k", chat("Encore" to alice, onlyAlertOnce = true)))
        // Retirée puis republiée : de nouveau annoncée (même avec onlyAlertOnce, c'est la première fois).
        tracker.onRemoved("k")
        assertTrue(tracker.onPosted("k", chat("Encore" to alice, onlyAlertOnce = true)))
    }

    @Test
    fun groupSummaryAndEmptyAreIgnored() {
        val summary = builder().setCategory(Notification.CATEGORY_MESSAGE).setContentText("2 messages")
            .setGroup("g").setGroupSummary(true).build()
        assertFalse(MessageTracker().onPosted("s", summary))
        val empty = builder().setCategory(Notification.CATEGORY_MESSAGE).build()
        assertNull(MessageDetector.signature(empty))
        assertFalse(MessageTracker().onPosted("e", empty))
        // Sans MessagingStyle : titre + texte servent d'empreinte.
        val plain = builder().setCategory(Notification.CATEGORY_MESSAGE).setContentTitle("Alice").setContentText("Coucou").build()
        assertEquals(MessageSignature("Alice", "Coucou", 0L, false), MessageDetector.signature(plain))
    }

    // --- Extraction ---

    @Test
    fun extractsSenderAvatarAndLastMessages() {
        val n = chat("1" to alice, "2" to alice, "3" to null, "4" to alice)
        val info = MessageExtractor.extract(input(n), "Chat")
        assertEquals("Alice", info.sender)
        assertFalse(info.isGroup)
        assertEquals(listOf("2", "3", "4"), info.messages.map { it.text.toString() })
        assertTrue(info.messages[1].fromSelf)
        assertNotNull(info.avatar)
        assertEquals("4", info.preview)
        assertEquals("Chat", info.appName)
        assertNotNull(info.contentIntent)
        assertNull(info.reply)
        assertNull(info.markAsRead)
    }

    @Test
    fun extractsGroupTitleAndPlainText() {
        val group = MessageExtractor.extract(input(chat("Salut" to alice, "Yo" to bob, group = "Équipe")), "Chat")
        assertEquals("Équipe", group.sender)
        assertTrue(group.isGroup)

        val plain = builder().setCategory(Notification.CATEGORY_MESSAGE).setContentTitle("Bob").setContentText("Coucou").build()
        val info = MessageExtractor.extract(input(plain), "SMS")
        assertEquals("Bob", info.sender)
        assertEquals(listOf("Coucou"), info.messages.map { it.text.toString() })
    }

    @Test
    fun findsReplyAndMarkAsReadActions() {
        val remote = RemoteInput.Builder("reply_key").setLabel("Répondre").setChoices(arrayOf("Oui", "Non")).build()
        val other = RemoteInput.Builder("other").build()
        val n = Notification.Builder(context, "msg").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(Notification.MessagingStyle(me).addMessage("Salut", 1L, alice))
            .addAction(Notification.Action.Builder(null, "Autre", pi(1)).addRemoteInput(other).build())
            .addAction(
                Notification.Action.Builder(null, "Répondre", pi(2)).addRemoteInput(remote)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).build(),
            )
            .addAction(
                Notification.Action.Builder(null, "Lu", pi(3))
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).build(),
            )
            .build()
        val info = MessageExtractor.extract(input(n), "Chat")
        // L'action sémantique « répondre » passe devant la première action à saisie.
        assertEquals("reply_key", info.reply?.remoteInput?.resultKey)
        assertEquals(listOf("Oui", "Non"), info.reply?.choices?.map { it.toString() })
        assertSame(n.actions[2].actionIntent, info.markAsRead)
    }

    // --- Réponse ---

    @Test
    fun fillInCarriesReplyText() {
        val remote = RemoteInput.Builder("reply_key").build()
        val free = MessageReply.fillIn(remote, "J'arrive", fromChoice = false)
        assertEquals("J'arrive", RemoteInput.getResultsFromIntent(free)?.getCharSequence("reply_key").toString())
        assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, RemoteInput.getResultsSource(free))
        val chip = MessageReply.fillIn(remote, "Oui", fromChoice = true)
        assertEquals(RemoteInput.SOURCE_CHOICE, RemoteInput.getResultsSource(chip))
    }

    // --- Confidentialité et durée ---

    @Test
    fun lockedHidesEverything() {
        val locked = MessagePrivacy.visibility(locked = true, showContent = true)
        assertFalse(locked.showSender)
        assertFalse(locked.showText)
        assertFalse(locked.canReply)
        val noContent = MessagePrivacy.visibility(locked = false, showContent = false)
        assertTrue(noContent.showSender)
        assertFalse(noContent.showText)
        assertTrue(noContent.canReply)
        assertEquals(MessageVisibility(true, true, true), MessagePrivacy.visibility(false, true))
    }

    @Test
    fun buildMasksCompactLineWhenLocked() {
        val info = MessageExtractor.extract(input(chat("Code : 1234\nsuite" to alice)), "Chat")
        val entry = MessagesModule.Entry(info, postedAt = 10_000L, seq = 1)
        val open = MessagesModule.build(entry, MessagesSettings(), locked = false, MessageInputState(), "Nouveau message")
        assertEquals("Code : 1234", open.compactSubtitle)
        assertEquals("Alice", open.displayName)
        assertTrue(open.canReply.not()) // pas d'action de réponse dans cette notification
        val locked = MessagesModule.build(entry, MessagesSettings(), locked = true, MessageInputState(), "Nouveau message")
        assertEquals("Nouveau message", locked.compactSubtitle)
        assertEquals("Chat", locked.displayName)
        assertEquals("messages:k:1", locked.key)
    }

    @Test
    fun typingKeepsContentAlive() {
        val info = MessageExtractor.extract(input(chat("Salut" to alice)), "Chat")
        val entry = MessagesModule.Entry(info, postedAt = 10_000L, seq = 2)
        val s = MessagesSettings(timeoutMs = 6_000L, autoExpand = true)
        val idle = MessagesModule.build(entry, s, false, MessageInputState(), "")
        assertEquals(6_000L, idle.timeoutMs)
        assertEquals(6_000L, idle.autoExpandMs)
        assertEquals(Priority.ALERT, idle.priority)

        val typing = MessagesModule.build(entry, s, false, MessageInputState(active = setOf(entry.contentKey)), "")
        assertNull(typing.timeoutMs)
        assertTrue(typing.priority > Priority.ALERT)
        assertTrue(typing.stayExpandedOnOutsideTouch)

        // Saisie arrêtée 20 s après l'arrivée : la durée complète repart de là.
        val released = MessagesModule.build(entry, s, false, MessageInputState(releasedAt = mapOf(entry.contentKey to 30_000L)), "")
        assertEquals(26_000L, released.timeoutMs)
    }

    @Test
    fun inputStateTransitions() {
        MessageInput.clear("x")
        MessageInput.set("x", true, now = 5L)
        assertTrue("x" in MessageInput.state.value.active)
        MessageInput.set("x", false, now = 9L)
        assertFalse("x" in MessageInput.state.value.active)
        assertEquals(9L, MessageInput.state.value.releasedAt["x"])
        MessageInput.clear("x")
        assertNull(MessageInput.state.value.releasedAt["x"])
    }
}
