package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Notifications effaçables et clés ciblées par le glisser et « Tout effacer », silencieuses comprises. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifDismissTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val silentRank = RankInfo(rank = 1, importance = NotificationManager.IMPORTANCE_LOW)

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Titre")
        .setContentText("Texte")

    private fun parse(n: Notification, key: String, group: String, rank: RankInfo? = silentRank) =
        NotifParser.parse(NotifInput(key, "com.example", n, 1_000L, group, rank), "Exemple")

    @Test
    fun clearableFlagsMatchTheSystem() {
        assertTrue(NotifDismiss.isClearable(0))
        assertTrue(NotifDismiss.isClearable(Notification.FLAG_AUTO_CANCEL or Notification.FLAG_GROUP_SUMMARY))
        // Service de premier plan sans « en cours » ni NO_CLEAR : effaçable (c'était le cas bloqué).
        assertTrue(NotifDismiss.isClearable(Notification.FLAG_FOREGROUND_SERVICE))
        assertFalse(NotifDismiss.isClearable(Notification.FLAG_ONGOING_EVENT))
        assertFalse(NotifDismiss.isClearable(Notification.FLAG_NO_CLEAR))
        assertFalse(NotifDismiss.isClearable(Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR))
    }

    @Test
    fun silentNotificationsAreClearedLikeOthers() {
        val silent = parse(builder().build(), "s1", "g1")
        val fgs = parse(builder().build().apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }, "s2", "g2")
        val ongoing = parse(builder().setOngoing(true).build(), "s3", "g3")
        val alerting = parse(builder().build(), "a1", "g4", RankInfo(rank = 0, importance = NotificationManager.IMPORTANCE_HIGH))
        assertEquals(NotifSection.SILENT, silent.section)
        assertTrue(silent.clearable)
        assertTrue(fgs.clearable)
        // Style discret conservé pour le service de premier plan.
        assertTrue(fgs.persistent)
        assertFalse(ongoing.clearable)

        val model = NotifListBuilder.build(listOf(silent, fgs, ongoing, alerting))
        assertEquals(listOf(NotifSection.ALERTING, NotifSection.SILENT), model.sections.map { it.section })
        // « Tout effacer » : alertes et silencieuses effaçables ; jamais les « en cours ».
        assertEquals(setOf("a1", "s1", "s2"), model.clearableKeys.toSet())
    }

    @Test
    fun groupSummaryLeavesWithItsChildren() {
        val a = parse(builder().build(), "a", "g")
        val b = parse(builder().build(), "b", "g")
        val summary = parse(builder().setGroup("x").setGroupSummary(true).build(), "sum", "g")
        val model = NotifListBuilder.build(listOf(a, b, summary))
        val group = model.sections.single().groups.single()
        assertEquals(NotifSection.SILENT, model.sections.single().section)
        // Glisser le groupe : enfants puis résumé.
        assertEquals(listOf("a", "b", "sum").toSet(), NotifDismiss.groupKeys(group).toSet())
        assertEquals("sum", NotifDismiss.groupKeys(group).last())
        // Glisser un enfant quand il en reste un autre : le résumé reste.
        assertEquals(listOf("a"), NotifDismiss.itemKeys(group, group.items.first { it.key == "a" }))
        // Dernier enfant : le résumé part avec lui.
        val lone = NotifListBuilder.build(listOf(a, summary)).sections.single().groups.single()
        assertEquals(listOf("a", "sum"), NotifDismiss.itemKeys(lone, lone.items.single()))
        assertTrue(model.clearableKeys.containsAll(listOf("a", "b", "sum")))
    }

    @Test
    fun ongoingChildKeepsTheSummary() {
        val ongoing = parse(builder().setOngoing(true).build(), "o", "h")
        val other = parse(builder().build(), "p", "h")
        val summary = parse(builder().setGroup("y").setGroupSummary(true).build(), "sum", "h")
        val group = NotifListBuilder.build(listOf(ongoing, other, summary)).sections.single().groups.single()
        assertEquals(listOf("p"), NotifDismiss.groupKeys(group))
        assertTrue(NotifDismiss.itemKeys(group, group.items.first { it.key == "o" }).isEmpty())
    }
}
