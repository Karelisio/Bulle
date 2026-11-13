package com.karelisio.bulle.modules.capture

import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.modules.capture.memo.MemoStore
import com.karelisio.bulle.modules.capture.qr.QrParser
import com.karelisio.bulle.modules.capture.qr.QrPayload
import com.karelisio.bulle.modules.capture.qr.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure de la capture : analyse des QR, chrono, machines d'état, tailles vidéo, noms de fichiers. */
class CaptureLogicTest {

    // ---- QR ----

    @Test
    fun urls() {
        assertEquals(QrPayload.Url("https://exemple.fr/a?b=1", "https://exemple.fr/a?b=1"), QrParser.parse("  https://exemple.fr/a?b=1 "))
        assertEquals("https://www.exemple.fr", (QrParser.parse("www.exemple.fr") as QrPayload.Url).url)
        assertTrue(QrParser.parse("HTTP://EXEMPLE.FR") is QrPayload.Url)
        // Une phrase contenant un lien reste du texte.
        assertTrue(QrParser.parse("voir https://exemple.fr") is QrPayload.Text)
    }

    @Test
    fun wifi() {
        val w = QrParser.parse("WIFI:T:WPA;S:Ma Box;P:mot;de\\;passe;;") as QrPayload.Wifi
        assertEquals("Ma Box", w.ssid)
        assertEquals("mot", w.password) // « ; » non échappé : fin du champ.
        val w2 = QrParser.parse("WIFI:S:Café\\:5G;T:WPA;P:a\\;b\\\\c;H:true;;") as QrPayload.Wifi
        assertEquals("Café:5G", w2.ssid)
        assertEquals("a;b\\c", w2.password)
        assertEquals(WifiSecurity.WPA, w2.security)
        assertTrue(w2.hidden)
        val open = QrParser.parse("WIFI:T:nopass;S:Libre;;") as QrPayload.Wifi
        assertEquals(WifiSecurity.OPEN, open.security)
        assertNull(open.password)
        assertEquals(WifiSecurity.WPA3, (QrParser.parse("WIFI:T:SAE;S:X;P:y;;") as QrPayload.Wifi).security)
        assertEquals(WifiSecurity.WEP, (QrParser.parse("wifi:T:WEP;S:X;P:y;;") as QrPayload.Wifi).security)
        // Sans SSID : texte.
        assertTrue(QrParser.parse("WIFI:T:WPA;P:x;;") is QrPayload.Text)
    }

    @Test
    fun vcard() {
        val raw = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Dupont;Jean;;;\r\nFN:Jean Dupont\r\nORG:Acme\\, SA\r\n" +
            "TEL;TYPE=CELL:+33 6 00 00 00 00\r\nitem1.EMAIL;TYPE=INTERNET:jean@exemple.fr\r\nURL:https://exemple.fr\r\n" +
            "ADR:;;1 rue de la Paix;Paris;;75000;France\r\nEND:VCARD"
        val c = QrParser.parse(raw) as QrPayload.Contact
        assertEquals("Jean Dupont", c.name)
        assertEquals("Acme, SA", c.organization)
        assertEquals(listOf("+33 6 00 00 00 00"), c.phones)
        assertEquals(listOf("jean@exemple.fr"), c.emails)
        assertEquals(listOf("https://exemple.fr"), c.urls)
        assertEquals("1 rue de la Paix, Paris, 75000, France", c.address)
        // Sans FN : nom reconstruit depuis N.
        val c2 = QrParser.parse("BEGIN:VCARD\nN:Martin;Léa\nEND:VCARD") as QrPayload.Contact
        assertEquals("Léa Martin", c2.name)
        val me = QrParser.parse("MECARD:N:Durand,Paul;TEL:0102030405;EMAIL:p@d.fr;;") as QrPayload.Contact
        assertEquals("Paul Durand", me.name)
        assertEquals(listOf("0102030405"), me.phones)
    }

    @Test
    fun phoneSmsMailGeo() {
        assertEquals(QrPayload.Phone("tel:+33102030405", "+33102030405"), QrParser.parse("tel:+33102030405"))
        val sms = QrParser.parse("SMSTO:+33600000000:Bonjour : ça va ?") as QrPayload.Sms
        assertEquals("+33600000000", sms.number)
        assertEquals("Bonjour : ça va ?", sms.body)
        val sms2 = QrParser.parse("sms:0600000000?body=Salut%20toi") as QrPayload.Sms
        assertEquals("Salut toi", sms2.body)
        assertNull((QrParser.parse("SMSTO:0600") as QrPayload.Sms).body)

        val mail = QrParser.parse("mailto:a@b.fr?subject=Objet%20test&body=Corps") as QrPayload.Email
        assertEquals("a@b.fr", mail.address)
        assertEquals("Objet test", mail.subject)
        assertEquals("Corps", mail.body)
        val mat = QrParser.parse("MATMSG:TO:c@d.fr;SUB:Sujet;BODY:Texte;;") as QrPayload.Email
        assertEquals("c@d.fr", mat.address)
        assertEquals("Sujet", mat.subject)

        val geo = QrParser.parse("geo:48.8566,2.3522?q=Tour%20Eiffel") as QrPayload.Geo
        assertEquals(48.8566, geo.latitude, 1e-9)
        assertEquals(2.3522, geo.longitude, 1e-9)
        assertEquals("Tour Eiffel", geo.query)
        assertTrue(QrParser.parse("geo:200,0") is QrPayload.Text)
        assertTrue(QrParser.parse("Bonjour le monde") is QrPayload.Text)
        assertTrue(QrParser.parse("   ") is QrPayload.Text)
    }

    // ---- Chrono ----

    @Test
    fun chrono() {
        assertEquals("0:00", CaptureFormat.chrono(0))
        assertEquals("0:00", CaptureFormat.chrono(-5))
        assertEquals("0:07", CaptureFormat.chrono(7_999))
        assertEquals("12:34", CaptureFormat.chrono((12 * 60 + 34) * 1000L))
        assertEquals("59:59", CaptureFormat.chrono(3_599_000))
        assertEquals("1:02:03", CaptureFormat.chrono((3600 + 2 * 60 + 3) * 1000L))
    }

    // ---- Machines d'état ----

    @Test
    fun memoMachine() {
        var s = MemoSession()
        s = MemoMachine.reduce(s, MemoEvent.Pause(5)) // sans objet
        assertEquals(MemoPhase.IDLE, s.phase)
        s = MemoMachine.reduce(s, MemoEvent.Start(1_000))
        assertEquals(MemoPhase.RECORDING, s.phase)
        assertEquals(4_000, s.elapsed(5_000))
        s = MemoMachine.reduce(s, MemoEvent.Start(2_000)) // déjà en cours : ignoré
        assertEquals(1_000, s.startedAt)
        s = MemoMachine.reduce(s, MemoEvent.Pause(6_000))
        assertEquals(MemoPhase.PAUSED, s.phase)
        assertEquals(5_000, s.elapsed(60_000)) // figé en pause
        s = MemoMachine.reduce(s, MemoEvent.Resume(10_000))
        assertEquals(7_000, s.elapsed(12_000))
        s = MemoMachine.reduce(s, MemoEvent.Stop(13_000))
        assertEquals(MemoSession(), s)
        assertEquals(0, s.elapsed(99_999))
    }

    @Test
    fun memoLevel() {
        assertEquals(0f, MemoMachine.level(0), 0f)
        assertEquals(1f, MemoMachine.level(32_767), 1e-4f)
        assertTrue(MemoMachine.level(3_000) in 0.1f..0.9f)
        assertTrue(MemoMachine.level(1_000) < MemoMachine.level(10_000))
    }

    @Test
    fun recorderMachine() {
        var s = RecorderSession()
        assertEquals(RecorderToggle.REQUEST_CONSENT, RecorderMachine.toggle(s))
        s = RecorderMachine.reduce(s, RecorderEvent.Request)
        assertEquals(RecorderPhase.REQUESTING, s.phase)
        assertEquals(RecorderToggle.NOTHING, RecorderMachine.toggle(s))
        // Refus : retour au repos.
        assertEquals(RecorderSession(), RecorderMachine.reduce(s, RecorderEvent.Denied))
        s = RecorderMachine.reduce(s, RecorderEvent.Started(1_000, withAudio = true))
        assertTrue(s.active)
        assertTrue(s.withAudio)
        assertEquals(2_000, s.elapsed(3_000))
        assertEquals(RecorderToggle.STOP, RecorderMachine.toggle(s))
        // Un refus tardif (activité détruite après le démarrage) ne coupe rien.
        assertEquals(s, RecorderMachine.reduce(s, RecorderEvent.Denied))
        assertEquals(s, RecorderMachine.reduce(s, RecorderEvent.Request))
        // Arrêt système (onStop) ou utilisateur.
        assertEquals(RecorderSession(), RecorderMachine.reduce(s, RecorderEvent.Stopped))
    }

    // ---- Vidéo ----

    @Test
    fun videoSizing() {
        assertEquals(VideoSize(1280, 2800), VideoSizing.fit(1280, 2800, 0))
        assertEquals(VideoSize(720, 1568), VideoSizing.fit(1280, 2800, 720))
        assertEquals(VideoSize(480, 1040), VideoSizing.fit(1280, 2800, 480))
        // Paysage, et taille déjà plus petite que la cible : inchangée.
        assertEquals(VideoSize(1568, 720), VideoSizing.fit(2800, 1280, 720))
        assertEquals(VideoSize(480, 640), VideoSizing.fit(480, 640, 720))
        val s = VideoSizing.shrink(VideoSize(1280, 2800))
        assertTrue(s.width % 16 == 0 && s.height % 16 == 0 && s.width < 1280)
        assertEquals(10_752_000, VideoSizing.bitrate(VideoSize(1280, 2800), 30))
        assertEquals(2_000_000, VideoSizing.bitrate(VideoSize(160, 160), 30))
        assertEquals(20_000_000, VideoSizing.bitrate(VideoSize(4000, 4000), 60))
    }

    // ---- Fichiers et réglages ----

    @Test
    fun memoNames() {
        assertEquals("Réunion_1", MemoStore.sanitizeName("  Réunion/1 "))
        assertEquals("a_b_c", MemoStore.sanitizeName("a:b*c"))
        assertNull(MemoStore.sanitizeName("  ..  "))
        assertEquals(80, MemoStore.sanitizeName("x".repeat(200))!!.length)
        assertTrue(MemoStore.defaultName("Mémo", 0L).startsWith("Mémo 19"))
    }

    @Test
    fun settingsJsonDefaults() {
        assertEquals(CaptureSettings(), BulleJson.decodeFromString(CaptureSettings.serializer(), "{}"))
        // Valeur d'énumération inconnue : valeur par défaut, sans exception.
        val s = BulleJson.decodeFromString(CaptureSettings.serializer(), """{"videoQuality":"ULTRA","memoToMusic":true}""")
        assertEquals(VideoQuality.NATIVE, s.videoQuality)
        assertTrue(s.memoToMusic)
        val round = BulleJson.decodeFromString(CaptureSettings.serializer(), BulleJson.encodeToString(CaptureSettings.serializer(), s))
        assertEquals(s, round)
    }
}
