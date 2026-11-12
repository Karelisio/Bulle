package com.karelisio.bulle.modules.volume

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choix du flux et décision d'interception des touches (fonctions pures). */
class VolumeLogicTest {

    private val ok = KeyConditions(
        moduleEnabled = true, islandVisible = true, inCall = false,
        screenOn = true, locked = false, allowWhenLocked = false,
    )

    @Test
    fun interceptsOnlyWhenEverythingAllowsIt() {
        assertTrue(VolumeLogic.shouldIntercept(ok))
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(moduleEnabled = false)))
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(islandVisible = false)))
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(inCall = true)))
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(screenOn = false)))
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(locked = true)))
        assertTrue(VolumeLogic.shouldIntercept(ok.copy(locked = true, allowWhenLocked = true)))
        // Écran éteint : jamais, même si le verrouillage est autorisé.
        assertFalse(VolumeLogic.shouldIntercept(ok.copy(screenOn = false, locked = true, allowWhenLocked = true)))
    }

    @Test
    fun onlyVolumeKeys() {
        assertTrue(VolumeLogic.isVolumeKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(VolumeLogic.isVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(VolumeLogic.isVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(VolumeLogic.isVolumeKey(KeyEvent.KEYCODE_POWER))
    }

    @Test
    fun pickStream() {
        assertEquals(VolumeStream.MEDIA, VolumeLogic.pickStream(VolumeStreamChoice.AUTO, musicActive = true))
        assertEquals(VolumeStream.RING, VolumeLogic.pickStream(VolumeStreamChoice.AUTO, musicActive = false))
        assertEquals(VolumeStream.ALARM, VolumeLogic.pickStream(VolumeStreamChoice.ALARM, musicActive = true))
        assertEquals(VolumeStream.NOTIFICATION, VolumeLogic.pickStream(VolumeStreamChoice.NOTIFICATION, musicActive = false))
        assertEquals(VolumeStream.MEDIA, VolumeLogic.pickStream(VolumeStreamChoice.MEDIA, musicActive = false))
        // Flux choisi à la main dans la vue étendue : prioritaire.
        assertEquals(VolumeStream.ALARM, VolumeLogic.pickStream(VolumeStreamChoice.AUTO, musicActive = true, override = VolumeStream.ALARM))
    }

    @Test
    fun fraction() {
        assertEquals(0f, VolumeLogic.fraction(0, 0, 15), 0f)
        assertEquals(1f, VolumeLogic.fraction(15, 0, 15), 0f)
        assertEquals(0.5f, VolumeLogic.fraction(4, 1, 7), 0.0001f)
        assertEquals(0f, VolumeLogic.fraction(3, 5, 5), 0f)
    }
}
