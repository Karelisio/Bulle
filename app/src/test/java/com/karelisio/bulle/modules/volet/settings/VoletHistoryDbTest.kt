package com.karelisio.bulle.modules.volet.settings

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.db.BulleDatabase
import com.karelisio.bulle.modules.volet.history.HistoryCandidate
import com.karelisio.bulle.modules.volet.history.HistoryLogic
import com.karelisio.bulle.modules.volet.history.NotificationHistory
import com.karelisio.bulle.modules.volet.history.NotificationHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Migration Room 1 → 2 et historique (recherche, purge, doublons). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoletHistoryDbTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = BulleDatabase.resetForTests()

    @Test
    fun migration1To2KeepsDataAndCreatesHistory() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // Base telle que créée par la version 1 (schéma de app/schemas/…/1.json).
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `lyrics` (`key` TEXT NOT NULL, `content` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                    "`notFound` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '12f83d9fb934a4e5c76c43f273795d83')")
            db.insert(
                "lyrics",
                null,
                ContentValues().apply {
                    put("key", "a|b")
                    put("content", "la la")
                    put("source", "lrclib")
                    put("notFound", 0)
                    put("fetchedAt", 1L)
                },
            )
            db.version = 1
        }
        // Room valide le schéma après la migration : une table non conforme lèverait une exception.
        val db = Room.databaseBuilder(context, BulleDatabase::class.java, name)
            .addMigrations(*BulleDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("la la", db.lyricsDao().get("a|b")?.content)
            val dao = db.notificationHistoryDao()
            dao.insert(entry("k", "Titre", "Texte", 10L))
            assertEquals(1, dao.count())
        } finally {
            db.close()
        }
    }

    @Test
    fun searchMatchesTitleTextAndApp() = runBlocking {
        val dao = NotificationHistory.dao(context)
        dao.insert(entry("1", "Réunion", "Salle B", 1L, app = "Agenda"))
        dao.insert(entry("2", "Colis", "Livré aujourd'hui", 2L, app = "Poste"))
        dao.insert(entry("3", "100% chargé", "", 3L, app = "Système"))
        assertEquals(listOf("2"), dao.search(HistoryLogic.escapeLike("livré")).first().map { it.notificationKey })
        assertEquals(listOf("1"), dao.search(HistoryLogic.escapeLike("agenda")).first().map { it.notificationKey })
        // « % » n'est pas un joker une fois échappé.
        assertEquals(listOf("3"), dao.search(HistoryLogic.escapeLike("%")).first().map { it.notificationKey })
        // Plus récent d'abord.
        assertEquals(listOf("3", "2", "1"), dao.all().first().map { it.notificationKey })
    }

    @Test
    fun purgeRemovesEntriesOlderThanSevenDays() = runBlocking {
        val now = TimeUnit.DAYS.toMillis(30)
        val dao = NotificationHistory.dao(context)
        dao.insert(entry("old", "Ancien", "", now - TimeUnit.DAYS.toMillis(8)))
        dao.insert(entry("new", "Récent", "", now - TimeUnit.DAYS.toMillis(6)))
        NotificationHistory.purge(context, now)
        assertEquals(listOf("new"), dao.all().first().map { it.notificationKey })
    }

    @Test
    fun recordSkipsIdenticalUpdatesAndOngoing() = runBlocking {
        val c = HistoryCandidate("k", "com.example", "Bonjour", "Salut", 5L, null, ongoing = false, groupSummary = false)
        assertTrue(NotificationHistory.record(context, c, now = 5L))
        assertFalse(NotificationHistory.record(context, c.copy(postedAt = 6L), now = 6L))
        assertTrue(NotificationHistory.record(context, c.copy(text = "Salut !", postedAt = 7L), now = 7L))
        assertFalse(NotificationHistory.record(context, c.copy(key = "o", ongoing = true), now = 8L))
        assertEquals(2, NotificationHistory.dao(context).count())
        NotificationHistory.clear(context)
        assertEquals(0, NotificationHistory.dao(context).count())
    }

    @Test
    fun startCollectsOnlyOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = NotificationHistory.start(context, scope, emptyFlow())
        val second = NotificationHistory.start(context, scope, emptyFlow())
        // Deuxième démarrage (service reconnecté) : la première collecte est arrêtée.
        assertTrue(first.isCancelled)
        assertTrue(second.isActive)
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    private fun entry(key: String, title: String, text: String, at: Long, app: String = "Appli") =
        NotificationHistoryEntity(
            notificationKey = key,
            packageName = "com.example",
            appName = app,
            title = title,
            text = text,
            postedAt = at,
            channelId = null,
        )
}
