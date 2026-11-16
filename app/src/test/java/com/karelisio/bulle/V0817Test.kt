package com.karelisio.bulle

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.ui.IslandLayout
import com.karelisio.bulle.modules.music.ui.MusicQueueLogic
import com.karelisio.bulle.modules.music.ui.QueueEntry
import com.karelisio.bulle.modules.volet.notifications.NotifDismiss
import com.karelisio.bulle.modules.volet.notifications.NotifInput
import com.karelisio.bulle.modules.volet.notifications.NotifListBuilder
import com.karelisio.bulle.modules.volet.notifications.NotifParser
import com.karelisio.bulle.modules.volet.notifications.NotifSection
import com.karelisio.bulle.modules.volet.notifications.RankInfo
import com.karelisio.bulle.modules.volet.qs.TetherShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 0.8.17 : journal conservé, jauge de paysage, mini-panneau du point d'accès, file d'attente, silencieuses par appli. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V0817Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun journalLinesSurviveARestart() {
        val entries = (1..80).map { IslandLog.Entry(1_000L + it, it.toLong(), "Ligne $it") } +
            IslandLog.Entry(5L, 0L, IslandLog.PREVIOUS + "ancienne")
        val text = IslandLog.serialize(entries)
        val back = IslandLog.parse(text + "\nligne invalide")
        // 50 dernières lignes de la session, marquées comme précédentes ; les anciennes ne sont pas recopiées deux fois.
        assertEquals(IslandLog.PERSISTED, back.size)
        assertEquals(IslandLog.PREVIOUS + "Ligne 80", back.last().message)
        assertEquals(1_080L, back.last().wallTime)
        assertTrue(back.none { it.message.contains("ancienne") })
        assertEquals(IslandLog.Category.AUDIO, IslandLog.category(IslandLog.PREVIOUS + "Sortie audio : bouton"))
    }

    @Test
    fun landscapeGaugeSitsBesideTheCamera() {
        // Caméra à gauche : centre x 79, demi-épaisseur 67, centre y 640 ; densité 3.
        val g = IslandLayout.landscapeGauge(79f, 67f, 640f, cameraLeft = true, density = 3f, screenHeight = 1280f)
        assertEquals(79f + 67f + 24f, g.left, 0.01f)
        assertEquals(18f, g.width, 0.01f)
        assertEquals(640f, g.top + g.height / 2f, 0.01f)
        val right = IslandLayout.landscapeGauge(2721f, 67f, 640f, cameraLeft = false, density = 3f, screenHeight = 1280f)
        assertEquals(2721f - 67f - 24f, right.left + right.width, 0.01f)
        // Toujours dans l'écran.
        val low = IslandLayout.landscapeGauge(79f, 67f, 1250f, cameraLeft = true, density = 3f, screenHeight = 1280f)
        assertTrue(low.top + low.height <= 1280f - 24f + 0.01f)
    }

    @Test
    fun hotspotInfoIsParsed() {
        val neigh = """
            192.168.43.12 dev wlan1 lladdr aa:bb:cc:dd:ee:01 REACHABLE
            192.168.43.13 dev wlan1 lladdr aa:bb:cc:dd:ee:02 STALE
            fe80::1 dev wlan1 lladdr AA:BB:CC:DD:EE:01 REACHABLE
            192.168.43.14 dev wlan1  FAILED
            192.168.1.1 dev wlan0 lladdr 11:22:33:44:55:66 REACHABLE
            192.168.44.2 dev ap0 lladdr aa:bb:cc:dd:ee:03 DELAY
        """.trimIndent()
        // Point d'accès seulement (pas wlan0), sans doublon d'adresse MAC ni entrée en échec.
        assertEquals(3, TetherShell.countClients(neigh))
        val info = TetherShell.parseInfo("state=13\nssid=Mon réseau\nclients=2")!!
        assertEquals("Mon réseau", info.ssid)
        assertEquals(2, info.clients)
        assertEquals(true, info.active)
        assertNull(TetherShell.parseInfo(""))
        assertEquals("bulle-tether wifi info", TetherShell.INFO_COMMAND)
    }

    @Test
    fun queueShowsWhatComesNext() {
        val q = (1L..10L).map { QueueEntry(it, "Titre $it", null) }
        assertEquals(listOf(4L, 5L, 6L, 7L, 8L), MusicQueueLogic.upNext(q, activeId = 3L).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), MusicQueueLogic.upNext(q, activeId = null).map { it.id })
        assertTrue(MusicQueueLogic.upNext(q, activeId = 10L).isEmpty())
        assertEquals(5, MusicQueueLogic.upNext(q, activeId = 99L).size)
    }

    private fun silent(key: String, group: String, pkg: String = "com.example") = NotifParser.parse(
        NotifInput(
            key, pkg,
            Notification.Builder(context, "t").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(key).setContentText("x").build(),
            1_000L, group, RankInfo(rank = 1, importance = NotificationManager.IMPORTANCE_LOW),
        ),
        "Exemple",
    )

    private fun summary(key: String, group: String) = NotifParser.parse(
        NotifInput(
            key, "com.example",
            Notification.Builder(context, "t").setSmallIcon(android.R.drawable.ic_dialog_info).setGroup(group).setGroupSummary(true).build(),
            1_000L, group, RankInfo(rank = 1, importance = NotificationManager.IMPORTANCE_LOW),
        ),
        "Exemple",
    )

    @Test
    fun silentNotificationsAreStackedPerApp() {
        val items = listOf(
            silent("a1", "ga"), silent("a2", "ga"), summary("sa", "ga"),
            silent("b1", "gb"),
            silent("o1", "go", pkg = "com.other"),
        )
        val model = NotifListBuilder.build(items)
        val section = model.sections.single { it.section == NotifSection.SILENT }
        // Une pile pour com.example (deux groupes réunis), une pour com.other.
        assertEquals(2, section.groups.size)
        val example = section.groups.single { it.packageName == "com.example" }
        assertEquals(setOf("a1", "a2", "b1"), example.items.map { it.key }.toSet())
        assertEquals(listOf("sa"), example.summaryKeys)
        // Effacer toute la pile emporte le résumé ; le dernier enfant de « ga » emporte le sien, pas avant.
        assertEquals(setOf("a1", "a2", "b1", "sa"), NotifDismiss.groupKeys(example).toSet())
        val a1 = example.items.first { it.key == "a1" }
        assertEquals(listOf("a1"), NotifDismiss.itemKeys(example, a1))
        val onlyA2 = example.copy(items = example.items.filter { it.key != "a1" })
        assertEquals(listOf("a2", "sa"), NotifDismiss.itemKeys(onlyA2, onlyA2.items.first { it.key == "a2" }))
        assertEquals(listOf("b1"), NotifDismiss.itemKeys(onlyA2, onlyA2.items.first { it.key == "b1" }))
    }
}
