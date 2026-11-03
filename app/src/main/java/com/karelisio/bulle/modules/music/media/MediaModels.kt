package com.karelisio.bulle.modules.music.media

import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.core.graphics.get

/** Métadonnées du morceau en cours (copie figée de MediaMetadata). */
data class TrackInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Pochette fournie directement par le lecteur (référence, non copiée). */
    val artBitmap: Bitmap?,
    /** URI de pochette candidates, par ordre de préférence. */
    val artUris: List<String>,
    /** Paroles transmises par le lecteur dans ses métadonnées, s'il y en a. */
    val playerLyrics: String?,
    /** Empreinte de la pochette (dimensions + échantillon de pixels) : stable d'une copie à l'autre. */
    val artKey: String = "",
) {
    /** Identité du morceau (sans la pochette) : sert de clé pour les paroles. */
    val identity: String get() = "$packageName|$artist|$title|$album|${durationMs / 1000}"

    /** Identité de la pochette : change aussi quand le lecteur fournit l'image en retard. */
    val artIdentity: String get() = "$identity|$artKey|${artUris.firstOrNull()}"

    /** Même morceau et même pochette (les bitmaps reçus sont des copies : on compare leur empreinte). */
    fun sameAs(other: TrackInfo?): Boolean =
        other != null && copy(artBitmap = null) == other.copy(artBitmap = null)
}

/** Empreinte légère d'un bitmap : dimensions et 16 pixels répartis sur l'image. */
fun bitmapFingerprint(b: Bitmap?): String {
    if (b == null || b.isRecycled) return ""
    if (b.config == Bitmap.Config.HARDWARE) return "${b.width}x${b.height}#${b.generationId}"
    var h = 17
    for (i in 1..4) for (j in 1..4) {
        h = h * 31 + b[(b.width * i / 5).coerceIn(0, b.width - 1), (b.height * j / 5).coerceIn(0, b.height - 1)]
    }
    return "${b.width}x${b.height}#$h"
}

/** État de lecture figé (copie de PlaybackState). */
data class PlaybackSnapshot(
    val state: Int,
    val positionMs: Long,
    /** elapsedRealtime de la dernière mise à jour de [positionMs]. */
    val updatedAt: Long,
    val speed: Float,
    val actions: Long,
) {
    val isPlaying: Boolean get() = isPlayingState(state)

    fun can(action: Long) = actions and action != 0L

    /** Position extrapolée à l'instant [now] (elapsedRealtime). */
    fun positionAt(now: Long, durationMs: Long): Long =
        // Certains lecteurs annoncent une vitesse nulle en pleine lecture : on suppose 1×.
        extrapolatePosition(positionMs, updatedAt, if (state == PlaybackState.STATE_PLAYING && speed == 0f) 1f else speed, isPlaying, now, durationMs)

    companion object {
        val EMPTY = PlaybackSnapshot(PlaybackState.STATE_NONE, 0, 0, 0f, 0)
    }
}

/** États considérés comme « en lecture » (le son sort ou va sortir). */
fun isPlayingState(state: Int): Boolean = when (state) {
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    -> true
    else -> false
}

/** États à ignorer : session arrêtée, vide ou en erreur. */
fun isDeadState(state: Int): Boolean = when (state) {
    PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR -> true
    else -> false
}

/**
 * Position de lecture extrapolée : position + vitesse × temps écoulé depuis la dernière mise à jour,
 * bornée à [0, durée]. En pause, la position ne bouge pas.
 */
fun extrapolatePosition(
    positionMs: Long,
    updatedAt: Long,
    speed: Float,
    playing: Boolean,
    now: Long,
    durationMs: Long,
): Long {
    var p = positionMs
    if (playing && updatedAt > 0 && now > updatedAt) {
        p += ((now - updatedAt) * speed).toLong()
    }
    if (durationMs > 0) p = p.coerceAtMost(durationMs)
    return p.coerceAtLeast(0)
}

/** Candidat pour le choix de la session affichée (logique pure, testable). */
data class SessionCandidate(
    val id: String,
    val state: Int,
    val hasContent: Boolean,
    /** elapsedRealtime de la dernière activité connue (lecture, changement d'état). */
    val lastActive: Long,
)

/**
 * Choisit la session pertinente : une session en lecture (la plus récemment active) en priorité,
 * sinon la plus récemment active parmi les sessions en pause. Ignore les sessions arrêtées ou vides.
 */
fun pickSession(candidates: List<SessionCandidate>): SessionCandidate? {
    val alive = candidates.filter { it.hasContent && !isDeadState(it.state) }
    return alive.filter { isPlayingState(it.state) }.maxByOrNull { it.lastActive }
        ?: alive.maxByOrNull { it.lastActive }
}
