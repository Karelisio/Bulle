package com.karelisio.bulle.modules.music

import kotlinx.serialization.Serializable

/** Réglages du module « music ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class MusicSettings(
    val enabled: Boolean = true,
    /** Afficher les paroles dans la vue étendue. */
    val lyricsEnabled: Boolean = true,
    /**
     * Autoriser la recherche des paroles sur lrclib.net (titre, artiste, album et durée envoyés).
     * Désactivé par défaut : rien ne sort de l'appareil sans accord explicite.
     */
    val onlineLyrics: Boolean = false,
    /** Masquer le lecteur après une pause prolongée (sinon il reste tant que la session existe). */
    val hideWhenPaused: Boolean = true,
    /** Délai avant de masquer un lecteur en pause, en minutes. */
    val pausedHideMinutes: Int = 10,
    /** La carte étendue reste ouverte (toucher extérieur ignoré) pendant des paroles synchronisées. */
    val keepLyricsOpen: Boolean = true,
    /**
     * Pendant la lecture d'un morceau aux paroles synchronisées, l'île reste visible même si le
     * masquage automatique (plein écran, écran d'accueil, appli exclue) la cacherait.
     */
    val stayVisibleWithLyrics: Boolean = true,
)
