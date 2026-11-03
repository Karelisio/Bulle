package com.karelisio.bulle.modules.music.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.karelisio.bulle.modules.music.lyrics.LrcParser
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

/** Session média retenue pour l'île. */
data class MediaSnapshot(
    val controller: MediaController,
    val track: TrackInfo,
    val playback: PlaybackSnapshot,
    /** elapsedRealtime de la dernière activité (début de lecture, mise en pause…). */
    val lastActive: Long,
)

/**
 * Observe les sessions média actives (via l'accès aux notifications de Bulle) et émet
 * la plus pertinente. Les callbacks sont libérés dès que le flux n'est plus collecté.
 */
object MediaSessionSource {

    private const val TAG = "BulleMusic"

    /** Se réabonne à chaque (dé)connexion du listener : l'accès a pu être accordé entre-temps. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(context: Context): Flow<MediaSnapshot?> =
        NotificationRepository.connected
            .flatMapLatest { sessions(context.applicationContext) }
            .distinctUntilChanged()

    private fun sessions(context: Context): Flow<MediaSnapshot?> = callbackFlow {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val component = NotificationRepository.component(context)
        val handler = Handler(Looper.getMainLooper())
        val tracked = LinkedHashMap<MediaSession.Token, TrackedSession>()

        fun publish() {
            val candidates = tracked.values.map { it.candidate() }
            val picked = pickSession(candidates)
            trySend(picked?.let { c -> tracked.values.firstOrNull { it.id == c.id }?.snapshot() })
        }

        fun sync(controllers: List<MediaController>?) {
            val list = controllers.orEmpty()
            val tokens = list.mapTo(HashSet()) { it.sessionToken }
            tracked.entries.removeAll { (token, t) ->
                (token !in tokens).also { gone -> if (gone) t.release() }
            }
            val now = SystemClock.elapsedRealtime()
            // La liste système est triée par priorité : on en déduit une activité initiale décroissante.
            list.forEachIndexed { index, controller ->
                if (controller.sessionToken !in tracked && controller.packageName != context.packageName) {
                    val t = TrackedSession(controller, now - 1_000L * (index + 1), ::publish)
                    tracked[controller.sessionToken] = t
                    controller.registerCallback(t, handler)
                }
            }
            publish()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sync(it) }
        try {
            msm.addOnActiveSessionsChangedListener(listener, component, handler)
            sync(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Accès aux notifications non accordé : on attendra la prochaine connexion du listener.
            Log.i(TAG, "Sessions média inaccessibles : ${e.message}")
            trySend(null)
            close()
            return@callbackFlow
        }

        awaitClose {
            runCatching { msm.removeOnActiveSessionsChangedListener(listener) }
            tracked.values.forEach { it.release() }
            tracked.clear()
        }
    }
        .flowOn(Dispatchers.Main)
        .conflate()
}

/**
 * Suivi d'une session : métadonnées et état mémorisés depuis les callbacks (évite un aller-retour
 * binder — et la copie de la pochette — à chaque publication), dernière activité, callback à libérer.
 */
private class TrackedSession(
    val controller: MediaController,
    initialActive: Long,
    private val onChange: () -> Unit,
) : MediaController.Callback() {

    val id: String = "${controller.packageName}#${System.identityHashCode(controller.sessionToken)}"
    private var track: TrackInfo? = controller.metadata?.let { readTrack(it, controller.packageName) }
    private var state: PlaybackState? = controller.playbackState
    private var lastState = state?.state ?: PlaybackState.STATE_NONE
    private var lastActive = maxOf(initialActive, state?.lastPositionUpdateTime ?: 0L)
    private var released = false

    fun candidate() = SessionCandidate(
        id = id,
        state = state?.state ?: PlaybackState.STATE_NONE,
        hasContent = track != null,
        lastActive = lastActive,
    )

    fun snapshot(): MediaSnapshot? {
        val t = track ?: return null
        val playback = state?.let {
            PlaybackSnapshot(it.state, it.position, it.lastPositionUpdateTime, it.playbackSpeed, it.actions)
        } ?: PlaybackSnapshot.EMPTY
        return MediaSnapshot(controller, t, playback, lastActive)
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        this.state = state
        val s = state?.state ?: PlaybackState.STATE_NONE
        if (s != lastState || isPlayingState(s)) lastActive = SystemClock.elapsedRealtime()
        lastState = s
        onChange()
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        val next = metadata?.let { readTrack(it, controller.packageName) }
        // Mêmes informations re-publiées : on garde l'instance (aucune recomposition inutile).
        if (next == null || !next.sameAs(track)) track = next
        onChange()
    }

    override fun onSessionDestroyed() {
        track = null
        onChange()
    }

    fun release() {
        if (released) return
        released = true
        runCatching { controller.unregisterCallback(this) }
    }
}

private val STANDARD_PREFIX = "android.media.metadata."

/** Copie les métadonnées utiles. Null si la session n'a pas de titre (session vide). */
internal fun readTrack(m: MediaMetadata, packageName: String): TrackInfo? {
    val title = m.text(MediaMetadata.METADATA_KEY_TITLE) ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
    val artist = m.text(MediaMetadata.METADATA_KEY_ARTIST)
        ?: m.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: m.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        ?: ""
    val bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val uris = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    ).mapNotNull { m.text(it) }
    return TrackInfo(
        packageName = packageName,
        title = title,
        artist = artist,
        album = m.text(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
        durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
        artBitmap = bitmap?.takeUnless { it.isRecycled },
        artUris = uris,
        playerLyrics = findPlayerLyrics(m),
        artKey = runCatching { bitmapFingerprint(bitmap) }.getOrDefault(""),
    )
}

/**
 * Cherche des paroles transmises par le lecteur : d'abord une clé dont le nom évoque des paroles
 * (ex. « android.media.metadata.LYRICS »), sinon toute valeur texte non standard qui ressemble à du LRC.
 */
private fun findPlayerLyrics(m: MediaMetadata): String? {
    val keys = runCatching { m.keySet() }.getOrNull() ?: return null
    keys.firstOrNull { it.contains("lyric", ignoreCase = true) }
        ?.let { k -> m.text(k)?.takeIf { it.length > 20 } }
        ?.let { return it }
    for (k in keys) {
        if (k.startsWith(STANDARD_PREFIX)) continue
        val v = m.text(k) ?: continue
        if (v.length > 40 && LrcParser.looksLikeLrc(v)) return v
    }
    return null
}

private fun MediaMetadata.text(key: String): String? =
    runCatching { getText(key)?.toString() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
