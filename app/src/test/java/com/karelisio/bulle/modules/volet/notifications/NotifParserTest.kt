package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reconstruction des notifications et construction de la liste, avec de vraies Notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifParserTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Titre")
        .setContentText("Texte")

    private fun intent(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("test.$id"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun parse(
        n: Notification,
        key: String = "k",
        rank: RankInfo? = null,
        locked: Boolean = false,
        group: String = "app|com.example",
        postTime: Long = 1_000L,
    ) = NotifParser.parse(NotifInput(key, "com.example", n, postTime, group, rank), "Exemple", locked)

    @Test
    fun basicFields() {
        val item = parse(builder().setSubText("Sous").setColor(0xFF3366CC.toInt()).setContentIntent(intent(1)).setAutoCancel(true).build())
        assertEquals("Titre", item.title.toString())
        assertEquals("Texte", item.text.toString())
        assertEquals("Sous", item.subText.toString())
        assertEquals(0xFF3366CC.toInt(), item.color)
        assertNotNull(item.smallIcon)
        assertNotNull(item.contentIntent)
        assertTrue(item.autoCancel)
        assertTrue(item.clearable)
        assertFalse(item.persistent)
        assertEquals("Exemple", item.appName)
        assertNull(item.customView)
    }

    @Test
    fun bigText() {
        val item = parse(builder().setStyle(Notification.BigTextStyle().bigText("Un texte bien plus long")).build())
        assertEquals("Un texte bien plus long", item.bigText.toString())
        assertTrue(item.expandable)
    }

    @Test
    fun inboxLines() {
        val item = parse(builder().setStyle(Notification.InboxStyle().addLine("Un").addLine("Deux").addLine("Trois")).build())
        assertEquals(listOf("Un", "Deux", "Trois"), item.lines.map { it.toString() })
    }

    @Test
    fun bigPicture() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val item = parse(builder().setStyle(Notification.BigPictureStyle().bigPicture(bmp)).build())
        assertNotNull(item.picture)
    }

    @Test
    fun progress() {
        val item = parse(builder().setProgress(200, 50, false).build())
        assertEquals(0.25f, item.progress!!.fraction, 0.001f)
        assertFalse(item.progress!!.indeterminate)
        assertTrue(parse(builder().setProgress(0, 0, true).build()).progress!!.indeterminate)
        assertNull(parse(builder().build()).progress)
    }

    @Test
    fun messagingStyle() {
        val me = Person.Builder().setName("Moi").build()
        val alice = Person.Builder().setName("Alice Martin").build()
        val style = Notification.MessagingStyle(me)
            .setConversationTitle("Famille")
            .setGroupConversation(true)
            .addMessage("Salut", 1_000L, alice)
            .addMessage("Coucou", 2_000L, null as Person?)
        val item = parse(builder().setStyle(style).setShortcutId("famille").build())
        assertEquals(2, item.messages.size)
        assertEquals("Alice Martin", item.messages[0].sender.toString())
        assertEquals("Salut", item.messages[0].text.toString())
        assertFalse(item.messages[0].fromSelf)
        assertTrue(item.messages[1].fromSelf)
        assertEquals("Famille", item.title.toString())
        assertTrue(item.isGroupConversation)
        assertEquals("AM", initials(item.messages[0].sender))
        // Sans classement : MessagingStyle + raccourci = conversation.
        assertEquals(NotifSection.CONVERSATIONS, item.section)
        // Avec classement : le système décide.
        assertEquals(NotifSection.ALERTING, parse(builder().setStyle(style).build(), rank = RankInfo(isConversation = false)).section)
        assertEquals(NotifSection.CONVERSATIONS, parse(builder().setStyle(style).build(), rank = RankInfo(isConversation = true)).section)
    }

    @Test
    fun sections() {
        assertEquals(NotifSection.ALERTING, parse(builder().build(), rank = RankInfo(importance = NotificationManager.IMPORTANCE_HIGH)).section)
        assertEquals(NotifSection.SILENT, parse(builder().build(), rank = RankInfo(importance = NotificationManager.IMPORTANCE_LOW)).section)
        assertEquals(NotifSection.SILENT, parse(builder().build(), rank = RankInfo(importance = NotificationManager.IMPORTANCE_MIN)).section)
        @Suppress("DEPRECATION")
        assertEquals(NotifSection.SILENT, parse(builder().setPriority(Notification.PRIORITY_MIN).build()).section)
        assertEquals(NotifSection.ALERTING, parse(builder().build()).section)
    }

    @Test
    fun persistentNotClearable() {
        val ongoing = parse(builder().setOngoing(true).build())
        assertTrue(ongoing.persistent)
        assertFalse(ongoing.clearable)
        val noClear = builder().build().apply { flags = flags or Notification.FLAG_NO_CLEAR }
        assertFalse(parse(noClear).clearable)
        val fgs = builder().build().apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
        assertTrue(parse(fgs).persistent)
    }

    @Test
    fun redactedWhenLocked() {
        val private = builder().setVisibility(Notification.VISIBILITY_PRIVATE).build()
        val masked = parse(private, locked = true)
        assertTrue(masked.redacted)
        assertNull(masked.title)
        assertNull(masked.text)
        assertTrue(masked.actions.isEmpty())
        assertFalse(parse(private, locked = false).redacted)
        // Version publique fournie : affichée à la place.
        val pub = builder().setContentTitle("Public").setContentText("2 messages").build()
        val withPublic = builder().setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(pub).build()
        val p = parse(withPublic, locked = true)
        assertFalse(p.redacted)
        assertEquals("Public", p.title.toString())
        // Publique : rien à masquer. Secrète : toujours masquée.
        assertFalse(parse(builder().setVisibility(Notification.VISIBILITY_PUBLIC).build(), locked = true).redacted)
        assertTrue(
            NotifParser.shouldRedact(builder().setVisibility(Notification.VISIBILITY_SECRET).build(), null, locked = true, allowPrivate = true),
        )
        assertFalse(NotifParser.shouldRedact(private, null, locked = true, allowPrivate = true))
    }

    @Test
    fun actionsAndReplies() {
        val input = RemoteInput.Builder("reply").setLabel("Répondre").setChoices(arrayOf("Oui", "Non")).build()
        val reply = Notification.Action.Builder(null, "Répondre", intent(2)).addRemoteInput(input).setAllowGeneratedReplies(true).build()
        val read = Notification.Action.Builder(null, "Lu", intent(3)).build()
        val item = parse(
            builder().addAction(reply).addAction(read).build(),
            rank = RankInfo(smartReplies = listOf("D'accord", "Oui")),
        )
        assertEquals(2, item.actions.size)
        val a = item.actions[0]
        assertNotNull(a.remoteInput)
        assertEquals(listOf("Oui", "Non", "D'accord"), a.choices.map { it.toString() })
        assertNull(item.actions[1].remoteInput)

        val fillIn = NotifCommands.replyFillIn(a.remoteInput!!, "Bonjour", fromChoice = false)
        assertEquals("Bonjour", RemoteInput.getResultsFromIntent(fillIn)!!.getCharSequence("reply").toString())
        assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, RemoteInput.getResultsSource(fillIn))
    }

    @Test
    fun customViewFallback() {
        val rv = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        val n = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCustomContentView(rv).setStyle(Notification.DecoratedCustomViewStyle()).build()
        assertNotNull(parse(n).customView)
        // Avec du texte : rendu natif.
        assertNull(parse(builder().setCustomContentView(rv).build()).customView)
    }

    @Test
    fun settingsIntent() {
        val item = parse(builder().build(), rank = RankInfo(channelId = "chan"))
        val i = NotifCommands.settingsIntent(item)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, i.action)
        assertEquals("chan", i.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals("com.example", i.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun groupingSortingAndClearAll() {
        val a = parse(builder().setWhen(100).build(), key = "a", group = "g1")
        val b = parse(builder().setWhen(300).build(), key = "b", group = "g1")
        val summary = parse(builder().setGroup("x").setGroupSummary(true).build(), key = "s", group = "g1")
        val c = parse(builder().setWhen(200).setOngoing(true).build(), key = "c", group = "g2")
        val d = parse(builder().setWhen(50).build(), key = "d", group = "g3", rank = RankInfo(rank = 0, importance = NotificationManager.IMPORTANCE_HIGH))
        val silent = parse(builder().build(), key = "e", group = "g4", rank = RankInfo(rank = 1, importance = NotificationManager.IMPORTANCE_LOW))
        val model = NotifListBuilder.build(listOf(a, b, summary, c, d, silent))

        assertEquals(listOf(NotifSection.ALERTING, NotifSection.SILENT), model.sections.map { it.section })
        val alerting = model.sections[0].groups
        // Rang système d'abord, puis heure décroissante.
        assertEquals(listOf("d", "b", "c"), alerting.map { it.lead.key })
        val g1 = alerting[1]
        assertEquals(listOf("b", "a"), g1.items.map { it.key })
        assertEquals("s", g1.summaryKey)
        // Persistante exclue de « Tout effacer », résumé inclus.
        val keys = model.clearableKeys.toSet()
        assertTrue("c" !in keys)
        assertTrue(keys.containsAll(listOf("a", "b", "d", "e", "s")))
        assertEquals("g1".let { model.groupOf("a")?.id }, g1.id)

        // Résumé seul : affiché comme une notification normale.
        val lone = NotifListBuilder.build(listOf(summary))
        assertEquals("s", lone.sections.single().groups.single().lead.key)
        assertNull(lone.sections.single().groups.single().summaryKey)
    }

    @Test
    fun summaryJoinsChildrenInAnotherSection() {
        // Enfants « conversation », résumé classé dans les alertes : pas de carte de résumé en double.
        val style = Notification.MessagingStyle(Person.Builder().setName("Moi").build())
            .addMessage("Salut", 1_000L, Person.Builder().setName("Alice").build())
        val conv = RankInfo(isConversation = true)
        val a = parse(builder().setStyle(style).build(), key = "a", group = "g", rank = conv)
        val b = parse(builder().setStyle(style).build(), key = "b", group = "g", rank = conv)
        val summary = parse(builder().setGroup("x").setGroupSummary(true).build(), key = "s", group = "g")
        val model = NotifListBuilder.build(listOf(a, b, summary))
        assertEquals(listOf(NotifSection.CONVERSATIONS), model.sections.map { it.section })
        val group = model.sections.single().groups.single()
        assertEquals(listOf("a", "b").toSet(), group.items.map { it.key }.toSet())
        assertEquals("s", group.summaryKey)
        assertTrue("s" in model.clearableKeys)
        // Résumé gardé quand un enfant persistant reste.
        val ongoing = parse(builder().setOngoing(true).build(), key = "o", group = "h")
        val other = parse(builder().build(), key = "p", group = "h")
        val sum2 = parse(builder().setGroup("y").setGroupSummary(true).build(), key = "s2", group = "h")
        assertFalse("s2" in NotifListBuilder.build(listOf(ongoing, other, sum2)).clearableKeys)
    }

    @Test
    fun mediaSessionExcluded() {
        val media = builder().setStyle(Notification.MediaStyle()).build()
        assertFalse(NotifParser.isMediaSession(media))
        media.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession(context, "t").sessionToken)
        assertTrue(NotifParser.isMediaSession(media))
    }

    @Test
    fun relativeTime() {
        val now = 10_000_000_000L
        assertEquals(RelativeTime.Unit.NOW, RelativeTime.of(now, now - 10_000).unit)
        assertEquals(RelativeTime(RelativeTime.Unit.MINUTES, 5), RelativeTime.of(now, now - 5 * 60_000))
        assertEquals(RelativeTime(RelativeTime.Unit.HOURS, 2), RelativeTime.of(now, now - 2 * 3_600_000))
        assertEquals(RelativeTime(RelativeTime.Unit.DAYS, 3), RelativeTime.of(now, now - 3 * 86_400_000L))
        assertEquals("?", initials(null))
        assertEquals("B", initials("bob"))
    }
}
