package com.karelisio.bulle.core.style

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Un style enregistré par l'utilisateur (« Mes styles »). */
@Serializable
data class SavedStyle(val id: String, val pack: StylePack, val savedAt: Long = 0L)

@Serializable
data class SavedStyles(val items: List<SavedStyle> = emptyList())

/** Opérations pures sur la liste « Mes styles » (testées sans Android). */
object SavedStylesOps {
    const val MAX_ITEMS = 50

    fun add(
        list: SavedStyles,
        pack: StylePack,
        id: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): SavedStyles = SavedStyles((listOf(SavedStyle(id, pack, now)) + list.items).take(MAX_ITEMS))

    fun rename(list: SavedStyles, id: String, name: String): SavedStyles {
        val clean = StylePack.cleanName(name) ?: return list
        return SavedStyles(list.items.map { if (it.id == id) it.copy(pack = it.pack.copy(name = clean)) else it })
    }

    fun remove(list: SavedStyles, id: String): SavedStyles = SavedStyles(list.items.filterNot { it.id == id })

    /** Réinsère un style supprimé à sa place (annulation). */
    fun restore(list: SavedStyles, item: SavedStyle, index: Int): SavedStyles {
        if (list.items.any { it.id == item.id }) return list
        val items = list.items.toMutableList()
        items.add(index.coerceIn(0, items.size), item)
        return SavedStyles(items.take(MAX_ITEMS))
    }
}

private object SavedStylesSerializer : Serializer<SavedStyles> {
    override val defaultValue = SavedStyles()

    override suspend fun readFrom(input: InputStream): SavedStyles = try {
        BulleJson.decodeFromString(SavedStyles.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Styles illisibles", e)
    }

    override suspend fun writeTo(t: SavedStyles, output: OutputStream) {
        output.write(BulleJson.encodeToString(SavedStyles.serializer(), t).encodeToByteArray())
    }
}

private val Context.savedStylesStore: DataStore<SavedStyles> by dataStore(
    fileName = "style_packs.json",
    serializer = SavedStylesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { SavedStyles() },
)

/** « Mes styles » : fichier séparé des réglages (aucun champ ajouté à BulleSettings). */
object MyStylesStore {
    fun flow(context: Context): Flow<List<SavedStyle>> =
        context.applicationContext.savedStylesStore.data.map { it.items }.distinctUntilChanged()

    suspend fun update(context: Context, transform: (SavedStyles) -> SavedStyles) {
        context.applicationContext.savedStylesStore.updateData(transform)
    }
}
