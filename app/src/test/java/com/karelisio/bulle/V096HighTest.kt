package com.karelisio.bulle

import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.IslandArbiter
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.profiles.Profiles
import com.karelisio.bulle.system.PerfSample
import com.karelisio.bulle.system.PerfSummary
import com.karelisio.bulle.ui.Routes
import com.karelisio.bulle.ui.shell.SearchItem
import com.karelisio.bulle.ui.shell.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class C(override val key: String, override val moduleId: String, override val priority: Priority, override val postedAt: Long) : IslandContent() {
    @Composable override fun Leading() = Unit
}

/** Points « high » : balayage entre contenus, profils, recherche, performance. */
class V096HighTest {

    private val music = C("music", "music", Priority.MEDIA, 1)
    private val timer = C("timer", "live", Priority.LIVE, 2)
    private val weather = C("weather", "weather", Priority.IDLE, 3)

    @Test
    fun cyclingWrapsInPriorityOrder() {
        val all = listOf(music, timer, weather)
        assertEquals("music", IslandArbiter.cycle(all, "timer", 1)?.key)
        assertEquals("weather", IslandArbiter.cycle(all, "music", 1)?.key)
        assertEquals("timer", IslandArbiter.cycle(all, "weather", 1)?.key)
        assertEquals("weather", IslandArbiter.cycle(all, "timer", -1)?.key)
        assertNull(IslandArbiter.cycle(listOf(music), "music", 1))
        assertEquals(1 to 3, IslandArbiter.position(all, "music"))
    }

    @Test
    fun preferredContentLeadsUntilSomethingMoreUrgentArrives() {
        val all = listOf(music, timer, weather)
        assertEquals("timer", IslandArbiter.arrange(all).primary?.key)
        assertEquals("music", IslandArbiter.arrange(all, preferred = "music" to 10L).primary?.key)
        // Un appel arrivé après le choix passe devant.
        val call = C("call", "call", Priority.CALL, 20)
        assertEquals("call", IslandArbiter.arrange(all + call, preferred = "music" to 10L).primary?.key)
        // Contenu choisi disparu : ordre normal.
        assertEquals("timer", IslandArbiter.arrange(all, preferred = "gone" to 10L).primary?.key)
    }

    @Test
    fun profilesChangeOnlyWhatTheySet() {
        val s = BulleSettings()
        val night = Profiles.defaults.first { it.id == "night" }
        val r = Profiles.applyTo(s, night)
        assertFalse(r.halo.enabled)
        assertFalse(r.alerts.enabled)
        assertFalse(r.motion.haptics)
        assertTrue(r.standby.enabled)
        assertEquals(s.islandEnabled, r.islandEnabled)
        assertEquals("night", r.profiles.activeId)
        val snap = Profiles.snapshot(r, "x", "X")
        assertEquals(false, snap.halo)
        assertEquals(true, snap.standby)
    }

    @Test
    fun settingsSearchFindsByTitleSummaryAndKeywords() {
        val items = listOf(
            SearchItem(Routes.VOLET, "Volet", "Notifications et réglages rapides", SettingsSearch.keywords[Routes.VOLET].orEmpty()),
            SearchItem(Routes.HALO, "Halo", "Bords lumineux", SettingsSearch.keywords[Routes.HALO].orEmpty()),
            SearchItem(Routes.BACKUP, "Sauvegarde", "Exporter ou restaurer", SettingsSearch.keywords[Routes.BACKUP].orEmpty()),
        )
        assertEquals(listOf(Routes.HALO), SettingsSearch.match(items, "halo").map { it.route })
        assertEquals(listOf(Routes.VOLET), SettingsSearch.match(items, "hauteur paysage").map { it.route })
        assertEquals(listOf(Routes.BACKUP), SettingsSearch.match(items, "RESTAURER").map { it.route })
        assertEquals(listOf(Routes.HALO), SettingsSearch.match(items, "lumiere").map { it.route })
        assertTrue(SettingsSearch.match(items, "x").isEmpty())
    }

    @Test
    fun perfSummaryOver24h() {
        val h = 3_600_000L
        val s = listOf(
            PerfSample(0, 50_000, 1_000, 0, 80, false),
            PerfSample(h, 70_000, 61_000, 0, 78, false),
            // Redémarrage du processus : le compteur repart, delta ignoré.
            PerfSample(2 * h, 60_000, 500, 99, 76, false),
        )
        val p = PerfSummary.of(s, 2 * h)!!
        assertEquals(3, p.samples)
        assertEquals(1f, p.cpuMinutes, 0.01f)
        assertEquals(2f, p.drainPerHour!!, 0.01f)
        assertEquals(70_000 / 1024f, p.memMaxMb, 0.01f)
        assertNull(PerfSummary.of(s.take(1), 0))
    }
}
