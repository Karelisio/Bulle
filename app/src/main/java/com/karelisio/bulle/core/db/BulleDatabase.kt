package com.karelisio.bulle.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.karelisio.bulle.modules.music.lyrics.LyricsDao
import com.karelisio.bulle.modules.music.lyrics.LyricsEntity
import com.karelisio.bulle.modules.volet.history.NotificationHistoryDao
import com.karelisio.bulle.modules.volet.history.NotificationHistoryEntity

/**
 * Base locale de Bulle (caches, historique des notifications du Volet…). Rien n'y est
 * synchronisé ni envoyé ailleurs. Schéma exporté dans app/schemas : toute évolution
 * passe par une migration explicite.
 */
@Database(
    entities = [LyricsEntity::class, NotificationHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BulleDatabase : RoomDatabase() {

    abstract fun lyricsDao(): LyricsDao

    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        /** 1 → 2 : table de l'historique des notifications (module Volet). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`notificationKey` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `text` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, `channelId` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_postedAt` ON `notification_history` (`postedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_notificationKey` ON `notification_history` (`notificationKey`)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        @Volatile
        private var instance: BulleDatabase? = null

        fun get(context: Context): BulleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, BulleDatabase::class.java, "bulle.db")
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }

        /** Tests : ferme et oublie l'instance (chaque test a son propre contexte). */
        @androidx.annotation.VisibleForTesting
        internal fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
