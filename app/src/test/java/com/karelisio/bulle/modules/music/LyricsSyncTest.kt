package com.karelisio.bulle.modules.music

import android.media.AudioDeviceInfo
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.modules.music.lyrics.LyricsSource
import com.karelisio.bulle.modules.music.sync.AudioOutput
import com.karelisio.bulle.modules.music.sync.LyricsPrefs
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.sync.OutputCandidate
import com.karelisio.bulle.modules.music.sync.OutputKind
import com.karelisio.bulle.modules.music.sync.lyricsPrefs
import com.karelisio.bulle.modules.music.sync.outputKey
import com.karelisio.bulle.modules.music.sync.pickOutput
import com.karelisio.bulle.modules.music.sync.withOffset
import com.karelisio.bulle.modules.music.sync.withoutOffset
import com.karelisio.bulle.modules.music.ui.lineAlpha
import com.karelisio.bulle.modules.music.ui.lyricsWindowLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Décalage des paroles par sortie audio, choix de la sortie et réglages d'affichage. */
class LyricsSyncTest {
    private val lyrics = LrcParser.parse("[00:01.00]un\n[00:05.00]deux\n[00:09.00]trois", LyricsSource.LRCLIB)!!

    @Test
    fun offsetDelaysLyrics() {
        assertEquals(5_000L, LyricsSync.lyricsPosition(5_300, 300))
        assertEquals(5_300L, LyricsSync.lyricsPosition(5_000, -300))
        // Sans décalage : « deux » s'allume 250 ms avant 5 s (comportement historique).
        assertEquals(1, LyricsSync.currentLine(lyrics, 4_800, 0))
        assertEquals(0, LyricsSync.currentLine(lyrics, 4_700, 0))
        // Casque Bluetooth retardé de 300 ms : à 4,8 s on entend encore « un ».
        assertEquals(0, LyricsSync.currentLine(lyrics, 4_800, 300))
        assertEquals(1, LyricsSync.currentLine(lyrics, 5_050, 300))
        // Décalage négatif : les paroles arrivent plus tôt.
        assertEquals(1, LyricsSync.currentLine(lyrics, 4_500, -300))
        // Avant la première ligne.
        assertEquals(-1, LyricsSync.currentLine(lyrics, 900, 200))
    }

    @Test
    fun offsetIsClampedToStep() {
        assertEquals(250, LyricsSync.clampOffset(260))
        assertEquals(300, LyricsSync.clampOffset(276))
        assertEquals(LyricsSync.MAX_OFFSET_MS, LyricsSync.clampOffset(10_000))
        assertEquals(LyricsSync.MIN_OFFSET_MS, LyricsSync.clampOffset(-10_000))
        assertEquals("+250 ms", LyricsSync.formatOffset(250))
        assertEquals("0 ms", LyricsSync.formatOffset(0))
        assertEquals("−150 ms", LyricsSync.formatOffset(-150))
    }

    @Test
    fun outputKeys() {
        assertEquals("speaker", outputKey(OutputCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Magic7 Pro", "")))
        assertEquals("bt:sony wh-1000xm5", outputKey(OutputCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, " Sony WH-1000XM5 ", "AA:BB")))
        assertEquals("bt:aa:bb:cc:dd:ee:ff", outputKey(OutputCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "", "AA:BB:CC:DD:EE:FF")))
        assertEquals("ble:buds", outputKey(OutputCandidate(AudioDeviceInfo.TYPE_BLE_HEADSET, "Buds", null)))
        assertEquals("wired", outputKey(OutputCandidate(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, null, null)))
        assertEquals(OutputKind.BLUETOOTH, AudioOutput.kindOfKey("ble:buds"))
        assertEquals(OutputKind.WIRED, AudioOutput.kindOfKey("wired"))
        assertEquals(OutputKind.SPEAKER, AudioOutput.kindOfKey("speaker"))
    }

    @Test
    fun bluetoothWinsOverWiredUsbAndSpeaker() {
        val speaker = OutputCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone", "")
        val earpiece = OutputCandidate(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "Phone", "")
        val usb = OutputCandidate(AudioDeviceInfo.TYPE_USB_HEADSET, "USB-C", "card=1")
        val wired = OutputCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Jack", "")
        val sco = OutputCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Casque", "AA")
        val a2dp = OutputCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Casque", "AA")

        assertEquals(AudioOutput.Speaker, pickOutput(emptyList()))
        assertEquals(AudioOutput.Speaker, pickOutput(listOf(earpiece, speaker)))
        assertEquals("usb:usb-c", pickOutput(listOf(speaker, usb)).key)
        assertEquals("wired:jack", pickOutput(listOf(speaker, usb, wired)).key)
        assertEquals("wired:jack", pickOutput(listOf(speaker, sco, wired)).key)
        val bt = pickOutput(listOf(speaker, usb, wired, sco, a2dp))
        assertEquals("bt:casque", bt.key)
        assertEquals("Casque", bt.name)
        assertEquals(OutputKind.BLUETOOTH, bt.kind)
    }

    @Test
    fun offsetsPerOutputWithDefaults() {
        val headset = AudioOutput("bt:casque", OutputKind.BLUETOOTH, "Casque")
        var s = MusicSettings()
        assertEquals(0, s.lyricsPrefs().offsetFor(headset.key))
        assertEquals(0, s.lyricsPrefs().offsetFor(AudioOutput.SPEAKER_KEY))

        // Défaut global : appliqué aux sorties non réglées, pas au haut-parleur.
        s = s.copy(lyricsDefaultOffsetMs = 150)
        assertEquals(150, s.lyricsPrefs().offsetFor(headset.key))
        assertEquals(0, s.lyricsPrefs().offsetFor(AudioOutput.SPEAKER_KEY))

        s = s.withOffset(headset, 263)
        assertEquals(250, s.lyricsOffsets[headset.key])
        assertEquals("Casque", s.lyricsOutputNames[headset.key])
        assertEquals(250, s.lyricsPrefs().offsetFor(headset.key))

        s = s.withOffset(AudioOutput.Speaker, -100)
        assertEquals(-100, s.lyricsPrefs().offsetFor(AudioOutput.SPEAKER_KEY))
        assertFalse(AudioOutput.SPEAKER_KEY in s.lyricsOutputNames)

        s = s.withoutOffset(headset.key)
        assertEquals(150, s.lyricsPrefs().offsetFor(headset.key))
        assertFalse(headset.key in s.lyricsOutputNames)
    }

    @Test
    fun displayPrefsAreBounded() {
        val p = MusicSettings(lyricsTextScale = 5f, lyricsVisibleLines = 42, lyricsDefaultOffsetMs = 99_999).lyricsPrefs()
        assertEquals(LyricsSync.MAX_TEXT_SCALE, p.textScale, 0.001f)
        assertEquals(LyricsSync.MAX_LINES, p.visibleLines)
        assertEquals(LyricsSync.MAX_OFFSET_MS, p.defaultOffsetMs)
        val q = MusicSettings(lyricsTextScale = 0.1f, lyricsVisibleLines = 0).lyricsPrefs()
        assertEquals(LyricsSync.MIN_TEXT_SCALE, q.textScale, 0.001f)
        assertEquals(LyricsSync.MIN_LINES, q.visibleLines)
        assertEquals(LyricsPrefs(), MusicSettings().lyricsPrefs())
        assertEquals(7, lyricsWindowLines(5, focused = true))
        assertEquals(5, lyricsWindowLines(5, focused = false))
        // Les lignes voisines restent plus lisibles que les lointaines.
        assertTrue(lineAlpha(1) > lineAlpha(2) && lineAlpha(2) > lineAlpha(5) && lineAlpha(0) == 1f)
    }

    @Test
    fun newSettingsSerializeAndOldJsonGetsDefaults() {
        val music = MusicSettings()
            .withOffset(AudioOutput("bt:casque", OutputKind.BLUETOOTH, "Casque"), 300)
            .copy(lyricsDefaultOffsetMs = -50, lyricsTextScale = 1.3f, lyricsVisibleLines = 7, lyricsFocusMode = true)
        val json = BulleJson.encodeToString(BulleSettings.serializer(), BulleSettings(music = music))
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), json).music
        assertEquals(music, back)

        // Réglages d'une version précédente : les nouveaux champs prennent leur valeur par défaut.
        val old = BulleJson.decodeFromString(MusicSettings.serializer(), """{"enabled":true,"keepLyricsOpen":false}""")
        assertEquals(MusicSettings(keepLyricsOpen = false), old)
        assertTrue(old.lyricsOffsets.isEmpty())
        assertEquals(5, old.lyricsVisibleLines)
        assertEquals(1f, old.lyricsTextScale, 0f)
        assertFalse(old.lyricsFocusMode)
    }
}
