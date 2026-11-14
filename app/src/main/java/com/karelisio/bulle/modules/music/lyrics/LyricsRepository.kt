package com.karelisio.bulle.modules.music.lyrics

import android.content.Context
import android.util.Log
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.modules.music.media.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Paroles du morceau en cours : d'abord celles du lecteur, puis le cache local,
 * puis LRCLIB si l'utilisateur l'a autorisé. Les recherches infructueuses sont mémorisées.
 */
object LyricsRepository {

    private const val TAG = "BulleMusic"

    /** Un « introuvable » est retenté après ce délai (la base LRCLIB s'enrichit). */
    private const val NOT_FOUND_TTL_MS = 7L * 24 * 3600 * 1000

    suspend fun load(context: Context, track: TrackInfo, online: Boolean): LyricsState = withContext(Dispatchers.IO) {
        track.playerLyrics?.let { text ->
            LrcParser.parse(text, LyricsSource.PLAYER)?.let { return@withContext LyricsState.Found(it) }
        }
        if (track.artist.isBlank()) return@withContext LyricsState.NotFound

        val dao = BulleDatabase.get(context).lyricsDao()
        val key = LyricsKey.of(track.artist, track.title, track.durationMs)
        val cached = runCatching { dao.get(key) }.getOrNull()
        val now = System.currentTimeMillis()
        if (cached != null) {
            if (!cached.notFound) {
                val source = runCatching { LyricsSource.valueOf(cached.source) }.getOrDefault(LyricsSource.LRCLIB)
                LrcParser.parse(cached.content, source)?.let { return@withContext LyricsState.Found(it) }
            } else if (now - cached.fetchedAt < NOT_FOUND_TTL_MS) {
                return@withContext LyricsState.NotFound
            }
        }
        if (!online) return@withContext LyricsState.NotFound

        val text = try {
            LrclibClient.find(track.title, track.artist, track.album, track.durationMs)
        } catch (e: IOException) {
            // Erreur réseau : pas de mise en cache, on réessaiera au prochain passage du morceau.
            Log.d(TAG, "LRCLIB indisponible", e)
            return@withContext LyricsState.NotFound
        } catch (e: RuntimeException) {
            Log.d(TAG, "Réponse LRCLIB illisible", e)
            return@withContext LyricsState.NotFound
        }
        val lyrics = text?.let { LrcParser.parse(it, LyricsSource.LRCLIB) }
        runCatching {
            dao.put(
                LyricsEntity(
                    key = key,
                    content = if (lyrics != null) text else "",
                    source = LyricsSource.LRCLIB.name,
                    notFound = lyrics == null,
                    fetchedAt = now,
                ),
            )
        }
        if (lyrics != null) LyricsState.Found(lyrics) else LyricsState.NotFound
    }

    /** Morceaux déjà essayés pendant cette session (une seule requête par morceau). */
    private val wordSyncTried = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Après affichage : cherche sur LRCLIB une version « mot à mot » (LRC enrichi) du morceau.
     * Ne retarde jamais l'affichage ; null si rien de mieux. Le résultat remplace le cache.
     */
    suspend fun upgradeToWordSync(context: Context, track: TrackInfo, current: Lyrics): Lyrics? = withContext(Dispatchers.IO) {
        if (current.source != LyricsSource.LRCLIB || current.lines.any { it.words.isNotEmpty() }) return@withContext null
        val key = LyricsKey.of(track.artist, track.title, track.durationMs)
        if (!wordSyncTried.add(key)) return@withContext null
        val text = runCatching { LrclibClient.findWordSynced(track.title, track.artist, track.durationMs) }.getOrNull()
            ?: return@withContext null
        val lyrics = LrcParser.parse(text, LyricsSource.LRCLIB)?.takeIf { l -> l.lines.any { it.words.isNotEmpty() } }
            ?: return@withContext null
        runCatching {
            BulleDatabase.get(context).lyricsDao().put(
                LyricsEntity(key = key, content = text, source = LyricsSource.LRCLIB.name, notFound = false, fetchedAt = System.currentTimeMillis()),
            )
        }
        lyrics
    }

    suspend fun cachedCount(context: Context): Int = withContext(Dispatchers.IO) {
        runCatching { BulleDatabase.get(context).lyricsDao().countFound() }.getOrDefault(0)
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        runCatching { BulleDatabase.get(context).lyricsDao().clear() }
        Unit
    }
}
