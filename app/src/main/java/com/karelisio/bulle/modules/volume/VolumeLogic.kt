package com.karelisio.bulle.modules.volume

import android.view.KeyEvent

/** Ce qui décide si l'île prend la main sur une touche volume (réduit à des booléens : testable). */
data class KeyConditions(
    val moduleEnabled: Boolean,
    /** L'île tourne et est visible (pas masquée : plein écran, appli exclue, désactivée…). */
    val islandVisible: Boolean,
    /** Appel en cours ou sonnerie : les touches gardent leur rôle système (volume d'appel, silence). */
    val inCall: Boolean,
    val screenOn: Boolean,
    val locked: Boolean,
    val allowWhenLocked: Boolean,
)

/** Décisions pures du volume sur l'île. */
object VolumeLogic {

    fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    /** Intercepter la touche ? Dans le doute, non : le système garde son comportement habituel. */
    fun shouldIntercept(c: KeyConditions): Boolean =
        c.moduleEnabled && c.islandVisible && !c.inCall && c.screenOn && (!c.locked || c.allowWhenLocked)

    /** Flux à régler : celui imposé pour cet affichage, sinon le réglage, sinon média ou sonnerie selon la lecture. */
    fun pickStream(choice: VolumeStreamChoice, musicActive: Boolean, override: VolumeStream? = null): VolumeStream =
        override ?: when (choice) {
            VolumeStreamChoice.AUTO -> if (musicActive) VolumeStream.MEDIA else VolumeStream.RING
            VolumeStreamChoice.MEDIA -> VolumeStream.MEDIA
            VolumeStreamChoice.RING -> VolumeStream.RING
            VolumeStreamChoice.NOTIFICATION -> VolumeStream.NOTIFICATION
            VolumeStreamChoice.ALARM -> VolumeStream.ALARM
        }

    /** Position du curseur (0..1). */
    fun fraction(level: Int, min: Int, max: Int): Float =
        if (max <= min) 0f else ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f)
}
