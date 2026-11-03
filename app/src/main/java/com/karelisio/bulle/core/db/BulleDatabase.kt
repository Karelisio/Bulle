package com.karelisio.bulle.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.karelisio.bulle.modules.music.lyrics.LyricsDao
import com.karelisio.bulle.modules.music.lyrics.LyricsEntity

/**
 * Base locale de Bulle (caches…). Rien n'y est synchronisé ni envoyé ailleurs.
 * Schéma exporté dans app/schemas : toute évolution passe par une migration.
 */
@Database(entities = [LyricsEntity::class], version = 1, exportSchema = true)
abstract class BulleDatabase : RoomDatabase() {

    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var instance: BulleDatabase? = null

        fun get(context: Context): BulleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, BulleDatabase::class.java, "bulle.db")
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
