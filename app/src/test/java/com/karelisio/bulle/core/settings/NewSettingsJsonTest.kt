package com.karelisio.bulle.core.settings

import com.karelisio.bulle.modules.music.MusicSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réglages ajoutés (exceptions plein écran, paroles) : défauts et aller-retour JSON. */
class NewSettingsJsonTest {
    @Test
    fun oldJsonGetsDefaults() {
        val s = BulleJson.decodeFromString(
            BulleSettings.serializer(),
            """{"visibility": {"hideInFullscreen": true, "hiddenApps": ["a"]}, "music": {"lyricsEnabled": true}}""",
        )
        assertEquals(emptySet<String>(), s.visibility.fullscreenIgnoredApps)
        assertTrue(s.visibility.ignoreFullscreenOnLauncher)
        assertTrue(s.music.keepLyricsOpen)
        assertTrue(s.music.stayVisibleWithLyrics)
    }

    @Test
    fun roundTrip() {
        val s = BulleSettings(
            visibility = VisibilitySettings(fullscreenIgnoredApps = setOf("com.video", "com.maps"), ignoreFullscreenOnLauncher = false),
            music = MusicSettings(keepLyricsOpen = false, stayVisibleWithLyrics = false),
        )
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), BulleJson.encodeToString(BulleSettings.serializer(), s))
        assertEquals(setOf("com.video", "com.maps"), back.visibility.fullscreenIgnoredApps)
        assertFalse(back.visibility.ignoreFullscreenOnLauncher)
        assertFalse(back.music.keepLyricsOpen)
        assertFalse(back.music.stayVisibleWithLyrics)
        assertEquals(s, back)
    }
}
