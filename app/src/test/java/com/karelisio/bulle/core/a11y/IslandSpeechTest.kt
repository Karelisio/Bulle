package com.karelisio.bulle.core.a11y

import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.IslandAnnouncements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table de chaînes françaises minimale : la génération des résumés reste une fonction pure. */
private val Fr = IslandSpeech.Res { id, args ->
    val template = when (id) {
        R.string.a11y_island_music -> "Musique"
        R.string.a11y_state_playing -> "en lecture"
        R.string.a11y_state_paused -> "en pause"
        R.string.a11y_island_call -> "Appel"
        R.string.a11y_island_video_call -> "Appel vidéo"
        R.string.a11y_unknown_caller -> "Numéro inconnu"
        R.string.a11y_state_ringing -> "sonne"
        R.string.a11y_state_ongoing_for -> "en cours depuis %1\$s"
        R.string.a11y_island_live -> "Activité en cours"
        R.string.a11y_percent -> "%1\$d pour cent"
        R.string.a11y_island_downloads -> "Téléchargements"
        R.string.a11y_downloads_count -> "%1\$d en cours"
        R.string.a11y_island_message -> "Message"
        R.string.a11y_state_typing -> "en train d'écrire"
        R.string.a11y_island_timer -> "Minuteur"
        R.string.a11y_remaining -> "reste %1\$s"
        R.string.a11y_island_volume -> "Volume"
        R.string.a11y_state_muted -> "coupé"
        R.string.a11y_state_recording -> "enregistrement en cours"
        R.string.a11y_island_countdown -> "Compte à rebours"
        R.string.a11y_island_alert -> "Alerte"
        R.string.a11y_island_generic -> "Bulle"
        R.string.a11y_duration_hms -> "%1\$d h %2\$d min %3\$d s"
        R.string.a11y_duration_ms -> "%1\$d min %2\$d s"
        R.string.a11y_duration_s -> "%1\$d s"
        else -> error("chaîne inattendue $id")
    }
    String.format(java.util.Locale.FRENCH, template, *args)
}

class IslandSpeechTest {

    @Test
    fun music() {
        assertEquals("Musique : Titre, Artiste, en lecture", IslandSpeech.music(Fr, "Titre", "Artiste", playing = true))
        assertEquals("Musique : Titre, en pause", IslandSpeech.music(Fr, "Titre", " ", playing = false))
    }

    @Test
    fun call() {
        assertEquals("Appel : Alice, sonne", IslandSpeech.call(Fr, "Alice", ringing = true, video = false, elapsed = null))
        assertEquals(
            "Appel vidéo : Numéro inconnu, en cours depuis 1 min 5 s",
            IslandSpeech.call(Fr, null, ringing = false, video = true, elapsed = IslandSpeech.duration(Fr, 65_000)),
        )
    }

    @Test
    fun liveAndDownloads() {
        assertEquals("Uber : Arrivée, 3 min, 42 pour cent", IslandSpeech.live(Fr, "Uber", "Arrivée", "3 min", 42))
        assertEquals("Activité en cours : Trajet", IslandSpeech.live(Fr, null, "Trajet", null, null))
        assertEquals("Téléchargements : 3 en cours, 100 pour cent", IslandSpeech.downloads(Fr, 3, 140))
    }

    @Test
    fun messageTimerVolumeRecordingCountdownAlert() {
        assertEquals("Message : Bob, Salut, WhatsApp", IslandSpeech.message(Fr, "Bob", "WhatsApp", "Salut", typing = false))
        assertEquals("Message : WhatsApp, en train d'écrire", IslandSpeech.message(Fr, null, "WhatsApp", "x", typing = true))
        assertEquals("Minuteur : reste 4 min 0 s", IslandSpeech.timer(Fr, null, IslandSpeech.duration(Fr, 240_000), running = true))
        assertEquals("Minuteur : Pause, reste 5 s, en pause", IslandSpeech.timer(Fr, "Pause", "5 s", running = false))
        assertEquals("Volume : 70 pour cent", IslandSpeech.volume(Fr, null, 70, muted = false))
        assertEquals("Volume : coupé", IslandSpeech.volume(Fr, null, 70, muted = true))
        assertEquals("Mémo : 12 s, enregistrement en cours", IslandSpeech.recording(Fr, "Mémo", "12 s", paused = false))
        assertEquals("Compte à rebours : Vacances", IslandSpeech.countdown(Fr, "Vacances", null))
        assertEquals("Alerte : Batterie faible", IslandSpeech.alert(Fr, "Batterie faible"))
        assertEquals("Bulle", IslandSpeech.fallback(Fr, null))
        assertEquals("Bulle : 2 h 0 min 1 s", IslandSpeech.summary("Bulle", IslandSpeech.duration(Fr, 7_201_000)))
    }

    @Test
    fun summaryIgnoresBlanksAndDuplicates() {
        assertEquals("A : b, c", IslandSpeech.summary("A", "b", null, "", "b", "c"))
        assertEquals("b", IslandSpeech.summary(null, "b"))
        assertEquals("A", IslandSpeech.summary("A"))
    }
}

class AnnouncementThrottleTest {
    private var now = 0L
    private val throttle = AnnouncementThrottle(clock = { now })

    @Test
    fun noneNeverAnnounces() {
        assertFalse(throttle.offer("a", "Alerte", important = true, level = IslandAnnouncements.NONE))
    }

    @Test
    fun importantOnlyNewImportantContent() {
        assertTrue(throttle.offer("alert:1", "Alerte : Wi-Fi", important = true, level = IslandAnnouncements.IMPORTANT))
        now += 20_000
        // Mise à jour du même contenu : jamais en mode « importantes ».
        assertFalse(throttle.offer("alert:1", "Alerte : Wi-Fi 2", important = true, level = IslandAnnouncements.IMPORTANT))
        // Contenu non important (musique) : silencieux.
        assertFalse(throttle.offer("music", "Musique : T", important = false, level = IslandAnnouncements.IMPORTANT))
        assertTrue(throttle.offer("msg:1", "Message : Bob", important = true, level = IslandAnnouncements.IMPORTANT))
    }

    @Test
    fun allThrottlesProgressUpdatesToTenSeconds() {
        assertTrue(throttle.offer("live", "Téléchargement 1 %", important = false, level = IslandAnnouncements.ALL))
        for (s in 1..9) {
            now = s * 1_000L
            assertFalse(throttle.offer("live", "Téléchargement ${s + 1} %", important = false, level = IslandAnnouncements.ALL))
        }
        now = 10_000
        assertTrue(throttle.offer("live", "Téléchargement 11 %", important = false, level = IslandAnnouncements.ALL))
        // Même texte : rien.
        now = 30_000
        assertFalse(throttle.offer("live", "Téléchargement 11 %", important = false, level = IslandAnnouncements.ALL))
    }

    @Test
    fun allCollapsesBurstsOfNewContent() {
        assertTrue(throttle.offer("a", "A", important = false, level = IslandAnnouncements.ALL))
        now = 500
        assertFalse(throttle.offer("b", "B", important = false, level = IslandAnnouncements.ALL))
        now = 600
        // Important : annoncé même dans la rafale.
        assertTrue(throttle.offer("c", "C", important = true, level = IslandAnnouncements.ALL))
        now = 3_000
        assertTrue(throttle.offer("d", "D", important = false, level = IslandAnnouncements.ALL))
    }
}
