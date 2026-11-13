package com.karelisio.bulle.modules.volume

import android.media.AudioManager
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

    /** Flux qu'on peut couper (l'alarme et l'appel ont un minimum imposé par le système). */
    fun canMute(stream: VolumeStream): Boolean =
        stream == VolumeStream.MEDIA || stream == VolumeStream.RING || stream == VolumeStream.NOTIFICATION

    /**
     * Sources affichées dans la vue étendue, dans l'ordre : appel (pendant une communication),
     * média en tête si une lecture est en cours, sonnerie, notifications (si séparées de la
     * sonnerie), média sinon, alarme, accessibilité (lecteur d'écran actif).
     */
    fun streamsToShow(c: MixerContext): List<VolumeStream> = buildList {
        if (c.communication) add(VolumeStream.CALL)
        if (c.mediaPlaying) add(VolumeStream.MEDIA)
        add(VolumeStream.RING)
        if (!c.notificationLinked) add(VolumeStream.NOTIFICATION)
        if (!c.mediaPlaying) add(VolumeStream.MEDIA)
        add(VolumeStream.ALARM)
        if (c.accessibilityActive) add(VolumeStream.ACCESSIBILITY)
    }

    /** Ligne mise en évidence : celle du flux des touches (notifications liées → ligne sonnerie). */
    fun highlighted(keyStream: VolumeStream, shown: List<VolumeStream>): VolumeStream? = when {
        keyStream in shown -> keyStream
        keyStream == VolumeStream.NOTIFICATION && VolumeStream.RING in shown -> VolumeStream.RING
        else -> null
    }

    /**
     * Ne pas déranger actif sans accès à la politique de notifications : le système refuse
     * (SecurityException) de changer sonnerie et notifications.
     */
    fun blockedByDnd(stream: VolumeStream, dndActive: Boolean, policyAccess: Boolean): Boolean =
        dndActive && !policyAccess && (stream == VolumeStream.RING || stream == VolumeStream.NOTIFICATION)

    /** Mode audio de communication (appel téléphonique, VoIP, filtrage d'appel). */
    fun isCommunicationMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_CALL_SCREENING

    // --- Délai d'affichage (« keep alive ») ---

    /** Regroupement des prolongations pendant un glissement : au plus une toutes les 400 ms. */
    const val KEEP_ALIVE_THROTTLE_MS = 400L

    /** Délai minimal quand la vue étendue est ouverte. */
    const val EXPANDED_MIN_TIMEOUT_MS = 6_000L

    /**
     * Délai d'affichage après la dernière interaction : aucun (jamais d'expiration) tant qu'un
     * doigt est posé sur un curseur ; plus long vue étendue ouverte (durée × 2,5, au moins 6 s).
     */
    fun timeoutFor(baseMs: Long, holding: Boolean, expanded: Boolean): Long? = when {
        holding -> null
        expanded -> maxOf((baseMs * 5) / 2, EXPANDED_MIN_TIMEOUT_MS)
        else -> baseMs
    }

    /** Instant d'expiration (null = n'expire pas) à partir du dernier événement. */
    fun expiresAt(lastEventAt: Long, baseMs: Long, holding: Boolean, expanded: Boolean): Long? =
        timeoutFor(baseMs, holding, expanded)?.let { lastEventAt + it }

    /** Prolonger maintenant ? Toujours si [force] (début / fin de geste), sinon au plus toutes les 400 ms. */
    fun shouldKeepAlive(now: Long, lastBumpAt: Long, force: Boolean): Boolean =
        force || now - lastBumpAt >= KEEP_ALIVE_THROTTLE_MS
}

/** Contexte qui décide des sources de volume listées (réduit à des booléens : testable). */
data class MixerContext(
    val mediaPlaying: Boolean,
    val communication: Boolean,
    /** Sonnerie et notifications partagent un seul volume sur cet appareil. */
    val notificationLinked: Boolean,
    val accessibilityActive: Boolean,
)
