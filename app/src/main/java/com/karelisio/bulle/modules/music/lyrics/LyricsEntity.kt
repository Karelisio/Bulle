package com.karelisio.bulle.modules.music.lyrics

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Paroles mises en cache localement (base Room de Bulle).
 * Une entrée [notFound] mémorise une recherche infructueuse pour ne pas re-solliciter LRCLIB.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    /** Clé « artiste|titre|durée arrondie » normalisée (voir [LyricsKey]). */
    @PrimaryKey val key: String,
    /** Texte LRC ou brut ; vide si [notFound]. */
    val content: String,
    /** Provenance ([LyricsSource.name]). */
    val source: String,
    val notFound: Boolean,
    /** Date d'enregistrement (System.currentTimeMillis). */
    val fetchedAt: Long,
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LyricsEntity)

    @Query("SELECT COUNT(*) FROM lyrics WHERE notFound = 0")
    suspend fun countFound(): Int

    @Query("DELETE FROM lyrics")
    suspend fun clear()
}

/** Clé de cache, insensible à la casse, aux espaces et à la ponctuation. */
object LyricsKey {
    fun of(artist: String, title: String, durationMs: Long): String {
        val seconds = if (durationMs > 0) Math.round(durationMs / 1000.0) else 0L
        return "${norm(artist)}|${norm(title)}|$seconds"
    }

    private val SEPARATORS = Regex("[\\s\\p{Punct}]+")

    private fun norm(s: String) = s.lowercase().replace(SEPARATORS, " ").trim()
}
