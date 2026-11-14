package com.karelisio.bulle.modules.music.lyrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Réponse (partielle) de l'API LRCLIB. */
@Serializable
data class LrclibTrack(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * Client minimal de lrclib.net (API gratuite, sans clé). Seuls titre, artiste, album et durée sont envoyés.
 * Appels bloquants : à lancer hors du thread principal.
 */
object LrclibClient {

    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "Bulle (https://github.com/karelisio/bulle)"
    private val json = Json { ignoreUnknownKeys = true }

    /** Résultat : texte (LRC de préférence), null = introuvable. Lève IOException en cas d'erreur réseau. */
    @Throws(IOException::class)
    fun find(title: String, artist: String, album: String, durationMs: Long): String? {
        val seconds = durationMs / 1000
        signatureUrl(title, artist, album, seconds)?.let { url ->
            get(url)?.let { body ->
                bestText(json.decodeFromString(LrclibTrack.serializer(), body))?.let { return it }
            }
        }
        // Repli : recherche plus souple (titre nettoyé), en gardant une durée proche si connue.
        val body = get("$BASE/search?${query(listOf("track_name" to cleanTitle(title), "artist_name" to artist))}") ?: return null
        val results = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LrclibTrack.serializer()), body)
        return pickSearchResult(results, seconds)?.let(::bestText)
    }

    /** Paroles synchronisées mot à mot (balises <mm:ss.xx>) parmi les résultats de recherche, ou null. */
    @Throws(IOException::class)
    fun findWordSynced(title: String, artist: String, durationMs: Long): String? {
        val body = get("$BASE/search?${query(listOf("track_name" to cleanTitle(title), "artist_name" to artist))}") ?: return null
        val results = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LrclibTrack.serializer()), body)
        return pickWordSynced(results, durationMs / 1000)
    }

    private val WORD_TAG = Regex("""<\d{1,2}:\d{2}(\.\d{1,3})?>""")

    internal fun pickWordSynced(results: List<LrclibTrack>, seconds: Long): String? = results
        .filter { seconds <= 0 || it.duration == null || abs(it.duration - seconds) <= 3.0 }
        .mapNotNull { it.syncedLyrics?.takeIf { s -> WORD_TAG.containsMatchIn(s) } }
        .firstOrNull()

    /**
     * URL de /api/get (recherche exacte par signature), ou null si elle n'est pas utilisable.
     * L'API exige titre, artiste, album ET durée : sans album, elle répond 400, ce qui levait une
     * IOException et empêchait le repli sur /api/search. On passe alors directement à la recherche.
     */
    internal fun signatureUrl(title: String, artist: String, album: String, seconds: Long): String? {
        if (seconds <= 0 || album.isBlank()) return null
        val params = listOf(
            "track_name" to title,
            "artist_name" to artist,
            "album_name" to album,
            "duration" to seconds.toString(),
        )
        return "$BASE/get?${query(params)}"
    }

    /** Parmi les résultats de recherche : durée à ±3 s si connue, paroles synchronisées en priorité. */
    internal fun pickSearchResult(results: List<LrclibTrack>, seconds: Long): LrclibTrack? {
        val close = results.filter { seconds <= 0 || it.duration == null || abs(it.duration - seconds) <= 3.0 }
        return close.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: close.firstOrNull { !it.plainLyrics.isNullOrBlank() }
    }

    private fun bestText(t: LrclibTrack): String? =
        t.syncedLyrics?.takeIf { it.isNotBlank() } ?: t.plainLyrics?.takeIf { it.isNotBlank() }

    /** Retire les mentions qui gênent la recherche : « (feat. …) », « - Remastered 2011 »… */
    internal fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|remaster(ed)?|live|radio edit|explicit|clean)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+(\d{4}\s+)?(remaster(ed)?|live|radio edit|mono|stereo|single version)\b.*$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { title }

    private fun query(params: List<Pair<String, String>>) =
        params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    /** GET : corps si 200, null si 404, IOException sinon. */
    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            return when (val code = conn.responseCode) {
                200 -> conn.inputStream.use { it.readBytes().decodeToString() }
                404 -> null
                else -> throw IOException("LRCLIB : HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
