package com.karelisio.bulle.modules.volet.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Une notification gardée dans l'historique local (7 jours, jamais envoyée ailleurs). */
@Entity(
    tableName = "notification_history",
    indices = [Index("postedAt"), Index("notificationKey")],
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Clé système de la notification (StatusBarNotification.key). */
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    /** Date de publication (epoch ms). */
    val postedAt: Long,
    val channelId: String?,
)

@Dao
interface NotificationHistoryDao {

    @Insert
    suspend fun insert(entry: NotificationHistoryEntity): Long

    /** Toutes les entrées, les plus récentes d'abord. */
    @Query("SELECT * FROM notification_history ORDER BY postedAt DESC, id DESC")
    fun all(): Flow<List<NotificationHistoryEntity>>

    /**
     * Recherche (insensible à la casse pour l'ASCII) dans le titre, le texte et le nom de l'appli.
     * [query] doit être échappé avec [HistoryLogic.escapeLike].
     */
    @Query(
        """
        SELECT * FROM notification_history
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
           OR text LIKE '%' || :query || '%' ESCAPE '\'
           OR appName LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY postedAt DESC, id DESC
        """,
    )
    fun search(query: String): Flow<List<NotificationHistoryEntity>>

    /** Dernière entrée enregistrée pour une clé (pour ignorer les mises à jour identiques). */
    @Query("SELECT * FROM notification_history WHERE notificationKey = :key ORDER BY postedAt DESC, id DESC LIMIT 1")
    suspend fun latestFor(key: String): NotificationHistoryEntity?

    /** Supprime les entrées antérieures à [cutoff] ; renvoie le nombre de lignes supprimées. */
    @Query("DELETE FROM notification_history WHERE postedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("DELETE FROM notification_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int
}
