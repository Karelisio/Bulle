package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.notifications.NotifLocalHide
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

/** Notifications que le système refuse d'effacer : masquées dans Bulle, diagnostic, sortie audio reconnue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifRefusalTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val silentRank = RankInfo(rank = 1, importance = NotificationManager.IMPORTANCE_LOW)

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Titre")
        .setContentText("Texte")

    private fun parse(n: Notification, key: String, group: String) =
        NotifParser.parse(NotifInput(key, "com.example", n, 1_000L, group, silentRank), "Exemple")

    @Before
    fun setUp() = NotifLocalHide.resetForTests()

    @After
    fun tearDown() = NotifLocalHide.resetForTests()

    @Test
    fun swipeCancelsClearableAndHidesTheRestLocally() {
        val silent = parse(builder().build(), "s1", "g1")
        val ongoing = parse(builder().setOngoing(true).build(), "s2", "g2")
        val model = NotifListBuilder.build(listOf(silent, ongoing))
        val g1 = model.groupOf("s1")!!
        val g2 = model.groupOf("s2")!!
        assertEquals(NotifDismiss.Swipe(listOf("s1"), emptyList()), NotifDismiss.swipeItem(g1, silent))
        assertEquals(NotifDismiss.Swipe(emptyList(), listOf("s2")), NotifDismiss.swipeItem(g2, ongoing))
        // Silencieuses d'une même appli réunies en une pile : balayer la pile annule s1 et masque s2.
        assertEquals(NotifDismiss.Swipe(listOf("s1"), listOf("s2")), NotifDismiss.swipeGroup(g2))
    }

    @Test
    fun flagsAreDescribedForTheLog() {
        assertTrue(NotifDismiss.describeFlags(null).contains("inconnue"))
        val fgs = NotifDismiss.describeFlags(Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR)
        assertTrue(fgs.contains("FOREGROUND_SERVICE"))
        assertTrue(fgs.contains("NO_CLEAR"))
        assertTrue(fgs.endsWith("effaçable : non"))
        assertTrue(NotifDismiss.describeFlags(0).endsWith("effaçable : oui"))
    }

    @Test
    fun refusedNotificationsStayHiddenUntilTheyReallyLeave() {
        val a = parse(builder().build(), "a", "ga")
        val b = parse(builder().build(), "b", "gb")
        val model = NotifListBuilder.build(listOf(a, b))
        // Le système n'a pas effacé « a » après le délai : masquée dans Bulle (un résumé seul est ignoré).
        val refused = NotifOptimistic.refusedItems(setOf("a", "summary"), model)
        assertEquals(listOf("a"), refused)
        NotifLocalHide.hide(refused)
        val shown = NotifOptimistic.apply(model, NotifLocalHide.keys.value)
        assertEquals(listOf("b"), shown.allItems.map { it.key })
        // Retirée pour de bon : la clé est oubliée.
        NotifLocalHide.forget("a")
        assertTrue(NotifLocalHide.keys.value.isEmpty())
        // Réafficher, et élagage à la reconnexion du listener.
        NotifLocalHide.hide(listOf("a", "b"))
        NotifLocalHide.show(listOf("a"))
        assertEquals(setOf("b"), NotifLocalHide.keys.value)
        NotifLocalHide.retain(setOf("x"))
        assertTrue(NotifLocalHide.keys.value.isEmpty())
        assertEquals(setOf("k"), NotifLocalHide.pruned(setOf("k", "gone"), setOf("k", "other")))
    }

    @Test
    fun hiddenKeysSurviveARestart() {
        NotifLocalHide.init(context)
        NotifLocalHide.hide(listOf("k1", "k2"))
        NotifLocalHide.forget("k2")
        // Redémarrage : mémoire vidée, clés relues sur le téléphone.
        NotifLocalHide.resetForTests()
        NotifLocalHide.init(context)
        assertEquals(setOf("k1"), NotifLocalHide.keys.value)
        NotifLocalHide.show(listOf("k1"))
        NotifLocalHide.resetForTests()
        NotifLocalHide.init(context)
        assertTrue(NotifLocalHide.keys.value.isEmpty())
    }

    @Test
    fun audioOutputNoticeIsRecognized() {
        assertTrue(NotifCommands.isAudioOutputText("Changement de périphérique audio"))
        assertTrue(NotifCommands.isAudioOutputText("Changement de peripherique audio"))
        assertTrue(NotifCommands.isAudioOutputText("Périphériques audio disponibles"))
        assertTrue(NotifCommands.isAudioOutputText("Sortie audio : casque"))
        assertTrue(NotifCommands.isAudioOutputText("Audio output switched"))
        assertTrue(NotifCommands.isAudioOutputText("Output device changed"))
        assertFalse(NotifCommands.isAudioOutputText("Nouveau message de Léa"))
        val notice = parse(builder().setContentTitle("Système").setContentText("Changement de périphérique audio").build(), "n", "gn")
        assertTrue(NotifCommands.isAudioOutputNotice(notice))
        // Honor : publiée par le service audio de la surcouche, texte illisible (vue personnalisée).
        assertTrue(NotifCommands.isAudioOutputPackage("com.hihonor.imedia.sws"))
        assertFalse(NotifCommands.isAudioOutputPackage("com.example"))
        val honor = NotifParser.parse(
            NotifInput("h", "com.hihonor.imedia.sws", builder().setContentTitle("Audio").setContentText("").build(), 1_000L, "gh", silentRank),
            "Audio",
        )
        assertTrue(NotifCommands.isAudioOutputNotice(honor))
    }

    @Test
    fun outputSwitchRelayClosesItself() {
        val intent = com.karelisio.bulle.modules.volet.media.OutputSwitchActivity.intent(context, "com.example.player")
        assertEquals("com.example.player", intent.getStringExtra(com.karelisio.bulle.modules.volet.media.OutputSwitchActivity.EXTRA_PACKAGE))
        val controller = Robolectric.buildActivity(com.karelisio.bulle.modules.volet.media.OutputSwitchActivity::class.java, intent).setup()
        // Sélecteur ouvert (ou repli lancé) puis fermeture immédiate : rien ne reste à l'écran.
        assertTrue(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun refusalBannerRenders() {
        val item = parse(builder().build(), "a", "ga")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var shown = false
        activity.setContent {
            RefusalBanner(RefusalNotice(listOf("a"), item), onDisable = {}, onShow = { shown = true })
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shown)
        controller.pause().stop().destroy()
    }
}
