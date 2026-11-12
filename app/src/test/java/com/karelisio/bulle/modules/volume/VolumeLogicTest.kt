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

    private val idle = MixerContext(mediaPlaying = false, communication = false, notificationLinked = false, accessibilityActive = false)

    @Test
    fun sourcesListedByContext() {
        // Rien de spécial : sonnerie, notifications, média, alarme.
        assertEquals(
            listOf(VolumeStream.RING, VolumeStream.NOTIFICATION, VolumeStream.MEDIA, VolumeStream.ALARM),
            VolumeLogic.streamsToShow(idle),
        )
        // Lecture en cours : média en tête.
        assertEquals(VolumeStream.MEDIA, VolumeLogic.streamsToShow(idle.copy(mediaPlaying = true)).first())
        // Appel : la ligne appel passe avant tout, et seulement pendant l'appel.
        val call = VolumeLogic.streamsToShow(idle.copy(communication = true, mediaPlaying = true))
        assertEquals(listOf(VolumeStream.CALL, VolumeStream.MEDIA), call.take(2))
        assertFalse(VolumeStream.CALL in VolumeLogic.streamsToShow(idle))
        // Notifications liées à la sonnerie : un seul curseur.
        val linked = VolumeLogic.streamsToShow(idle.copy(notificationLinked = true))
        assertFalse(VolumeStream.NOTIFICATION in linked)
        assertTrue(VolumeStream.RING in linked)
        // Accessibilité seulement avec un lecteur d'écran actif.
        assertFalse(VolumeStream.ACCESSIBILITY in VolumeLogic.streamsToShow(idle))
        assertEquals(VolumeStream.ACCESSIBILITY, VolumeLogic.streamsToShow(idle.copy(accessibilityActive = true)).last())
        // Chaque source une seule fois.
        val all = VolumeLogic.streamsToShow(MixerContext(true, true, false, true))
        assertEquals(all.size, all.toSet().size)
        assertEquals(VolumeStream.entries.toSet(), all.toSet())
    }

    @Test
    fun keyStreamHighlightAndDnd() {
        val shown = VolumeLogic.streamsToShow(idle.copy(notificationLinked = true))
        assertEquals(VolumeStream.MEDIA, VolumeLogic.highlighted(VolumeStream.MEDIA, shown))
        assertEquals(VolumeStream.RING, VolumeLogic.highlighted(VolumeStream.NOTIFICATION, shown))
        assertEquals(null, VolumeLogic.highlighted(VolumeStream.CALL, shown))

        assertTrue(VolumeLogic.blockedByDnd(VolumeStream.RING, dndActive = true, policyAccess = false))
        assertTrue(VolumeLogic.blockedByDnd(VolumeStream.NOTIFICATION, dndActive = true, policyAccess = false))
        assertFalse(VolumeLogic.blockedByDnd(VolumeStream.RING, dndActive = true, policyAccess = true))
        assertFalse(VolumeLogic.blockedByDnd(VolumeStream.RING, dndActive = false, policyAccess = false))
        assertFalse(VolumeLogic.blockedByDnd(VolumeStream.MEDIA, dndActive = true, policyAccess = false))
        assertFalse(VolumeLogic.blockedByDnd(VolumeStream.ALARM, dndActive = true, policyAccess = false))

        assertTrue(VolumeLogic.canMute(VolumeStream.MEDIA))
        assertFalse(VolumeLogic.canMute(VolumeStream.ALARM))
        assertFalse(VolumeLogic.canMute(VolumeStream.CALL))
        assertTrue(VolumeLogic.isCommunicationMode(android.media.AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(VolumeLogic.isCommunicationMode(android.media.AudioManager.MODE_NORMAL))
        assertFalse(VolumeLogic.isCommunicationMode(android.media.AudioManager.MODE_RINGTONE))
    }
}
